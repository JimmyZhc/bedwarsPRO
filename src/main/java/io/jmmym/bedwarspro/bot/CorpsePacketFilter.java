package io.jmmym.bedwarspro.bot;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import io.jmmym.bedwarspro.BedwarsPRO;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Corpse 尸体包过滤器（ProtocolLib，网络层拦截）。
 *
 * 背景：Corpse 插件（独立 jar，独立 classloader）在玩家/假人死亡时会创建虚拟尸体：
 * - 向附近玩家发 ADD_PLAYER（profile 是"随机 UUID + 玩家名"）→ Tab 里多一个同名条目；
 * - 向附近玩家发 NamedEntitySpawn（实体 ID = 尸体 ID）→ 地上多一具遗体。
 * 假人不走 PlayerRespawnEvent，Corpse 永远不会主动清掉这些包造成的残留；
 * 而我们自己的清理代码用 Class.forName 反射 Corpse 的类，跨插件 classloader 根本
 * 加载不到（实测 jar 内无 unldenis 类、pom 无依赖），只能静默失败——
 * 所以"遗体不清除 + Tab 同名重复"一直修不好。
 *
 * 本过滤器从根源解决：直接在网络层丢弃这些包，让尸体对客户端"从未出现过"。
 * 判定规则：profile 的 UUID 不是任何在线假人的真实 UUID（即随机 UUID），
 * 但名字却匹配某个假人 → 一定是 Corpse 尸体，直接拦截，无需访问 Corpse 的任何类。
 * 真实假人的 ADD_PLAYER / NamedEntitySpawn 使用其固定 UUID，不受影响。
 */
public final class CorpsePacketFilter {

  private static PacketAdapter listener;
  private static volatile boolean registered = false;

  private CorpsePacketFilter() {
  }

  /** 注册包拦截器（需在 ProtocolLib 加载后调用，plugin.yml 已声明 depend）。 */
  public static void init(Plugin plugin) {
    if (registered || !Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
      return;
    }
    try {
      listener = new PacketAdapter(plugin, ListenerPriority.HIGH,
          PacketType.Play.Server.PLAYER_INFO,
          PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
        @Override
        public void onPacketSending(PacketEvent event) {
          try {
            if (event.getPacketType() == PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
              handleNamedEntitySpawn(event);
            } else {
              handlePlayerInfo(event);
            }
          } catch (Throwable ignored) {
          }
        }
      };
      ProtocolLibrary.getProtocolManager().addPacketListener(listener);
      registered = true;
      debug("[Bot] Corpse包过滤器已注册（拦截假人尸体实体/Tab条目）");
    } catch (Throwable t) {
      debug("[Bot] Corpse包过滤器注册失败: " + t.getMessage());
    }
  }

  /** 注销包拦截器（插件卸载时清理，避免泄漏）。 */
  public static void shutdown() {
    if (!registered) {
      return;
    }
    try {
      ProtocolLibrary.getProtocolManager().removePacketListener(listener);
    } catch (Throwable ignored) {
    }
    listener = null;
    registered = false;
  }

  /**
   * 判断一个 profile（名字 + UUID）是否为"假人尸体"：
   * UUID 是随机生成的（不属于任何已注册假人），但名字却等于某个假人的名字。
   */
  private static boolean isBotCorpseProfile(String name, UUID uuid) {
    if (name == null || uuid == null) {
      return false;
    }
    BotManager bm = BedwarsPRO.getInstance().getBotManager();
    if (bm == null) {
      return false;
    }
    // UUID 是已注册假人的真实 UUID → 是假人本体，不是尸体
    if (bm.isBot(uuid)) {
      return false;
    }
    for (BotPlayer bp : bm.getAllBots()) {
      Player p = bp.getBukkitPlayer();
      if (p != null && name.equals(p.getName())) {
        return true;
      }
    }
    return false;
  }

  /** 拦截 PLAYER_INFO(ADD_PLAYER)：去掉尸体条目（随机 UUID + 假人名）。 */
  private static void handlePlayerInfo(PacketEvent event) {
    EnumWrappers.PlayerInfoAction action;
    List<PlayerInfoData> list;
    try {
      action = event.getPacket().getPlayerInfoAction().read(0);
      list = event.getPacket().getPlayerInfoDataLists().read(0);
    } catch (Throwable ignored) {
      return;
    }
    if (action != EnumWrappers.PlayerInfoAction.ADD_PLAYER) {
      return;
    }
    if (list == null || list.isEmpty()) {
      return;
    }
    List<PlayerInfoData> keep = new ArrayList<>();
    int corpseCount = 0;
    for (PlayerInfoData data : list) {
      if (data == null || data.getProfile() == null) {
        keep.add(data);
        continue;
      }
      if (isBotCorpseProfile(data.getProfile().getName(), data.getProfile().getUUID())) {
        corpseCount++;
      } else {
        keep.add(data);
      }
    }
    if (corpseCount == 0) {
      return;
    }
    if (keep.isEmpty()) {
      // 整包都是尸体条目 → 直接丢弃，Tab 条目永远不会出现
      event.setCancelled(true);
    } else {
      // 混合包 → 只保留真实条目（防御：个别服务器可能把多个玩家塞进一个包）
      event.getPacket().getPlayerInfoDataLists().write(0, keep);
    }
    debug("[Bot] 已拦截" + corpseCount + "条Corpse Tab条目");
  }

  /** 拦截 NamedEntitySpawn：随机 UUID + 假人名 = 尸体实体，直接丢弃，地上不再有遗体。 */
  private static void handleNamedEntitySpawn(PacketEvent event) {
    UUID uuid = null;
    try {
      uuid = event.getPacket().getUUIDs().read(0);
    } catch (Throwable ignored) {
    }
    if (uuid == null) {
      return;
    }
    // 假人本体（固定 UUID）放行；尸体（随机 UUID）名字匹配则拦截
    String name = null;
    try {
      name = event.getPacket().getStrings().read(0);
    } catch (Throwable ignored) {
    }
    if (name == null) {
      return;
    }
    BotManager bm = BedwarsPRO.getInstance().getBotManager();
    if (bm == null || bm.isBot(uuid)) {
      return;
    }
    for (BotPlayer bp : bm.getAllBots()) {
      Player p = bp.getBukkitPlayer();
      if (p != null && name.equals(p.getName())) {
        event.setCancelled(true);
        debug("[Bot] 已拦截Corpse尸体实体: " + name);
        return;
      }
    }
  }

  private static void debug(String msg) {
    if (BedwarsPRO.getInstance().isBotDebug()) {
      BedwarsPRO.getInstance().getLogger().info(msg);
    }
  }
}
