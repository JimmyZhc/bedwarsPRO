package io.jmmym.bedwarspro.xp;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.villager.VillagerTrade;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 经验起床（XP Bedwars）经验管理：
 * 按对局保存玩家经验，提供资源→经验换算与存取接口。
 * 配置位于 config.yml 的 xp-bedwars 节。
 */
public final class XpManager {

  /** gameName -> (playerUUID -> xp) */
  private static final Map<String, Map<UUID, Integer>> XP = new HashMap<>();

  private XpManager() {
  }

  /**
   * 对局是否启用经验模式：
   * 1. 对局单独设置（bwsba 编辑 GUI 写入 game.yml 的 xp-mode）优先
   * 2. 否则回退全局 xp-bedwars.enabled 总开关 + games 列表
   */
  public static boolean isXpMode(Game game) {
    if (game == null) {
      return false;
    }
    if (game.getXpMode() != null) {
      return game.getXpMode();
    }
    if (!BedwarsPRO.getInstance().getBooleanConfig("xp-bedwars.enabled", false)) {
      return false;
    }
    for (String name : BedwarsPRO.getInstance().getConfig().getStringList("xp-bedwars.games")) {
      if (name != null && name.equalsIgnoreCase(game.getName())) {
        return true;
      }
    }
    return false;
  }

  /** 读取资源→经验映射（config: xp-bedwars.resource-xp） */
  private static Map<Material, Integer> resourceXp() {
    Map<Material, Integer> map = new HashMap<>();
    ConfigurationSection section =
        BedwarsPRO.getInstance().getConfig().getConfigurationSection("xp-bedwars.resource-xp");
    if (section == null) {
      return map;
    }
    for (String key : section.getKeys(false)) {
      try {
        Material mat = Material.getMaterial(key);
        if (mat != null) {
          map.put(mat, section.getInt(key, 0));
        }
      } catch (Exception ignored) {
      }
    }
    return map;
  }

  /** 单个物品可换算的经验（不在映射返回 0） */
  public static int itemXp(ItemStack item) {
    if (item == null) {
      return 0;
    }
    Integer v = resourceXp().get(item.getType());
    return v == null ? 0 : v * item.getAmount();
  }

  /** 交易价格换算成总经验；-1 表示该商品在经验模式不可用 */
  public static int costToXp(VillagerTrade trade) {
    if (trade == null || trade.getItem1() == null) {
      return -1;
    }
    ItemStack item1 = trade.getItem1();
    // 经验商店（xp_shop.yml）：price 直接为所需经验值（加载器用经验瓶承载数量标记）
    if (item1.getType() == Material.EXP_BOTTLE) {
      return item1.getAmount();
    }
    Map<Material, Integer> map = resourceXp();
    Integer v1 = map.get(item1.getType());
    if (v1 == null) {
      return -1;
    }
    int total = v1 * item1.getAmount();
    if (trade.getItem2() != null) {
      Integer v2 = map.get(trade.getItem2().getType());
      if (v2 == null) {
        return -1;
      }
      total += v2 * trade.getItem2().getAmount();
    }
    return total;
  }

  public static int getXp(Game game, Player player) {
    Map<UUID, Integer> m = XP.get(game.getName());
    if (m == null) {
      return 0;
    }
    Integer v = m.get(player.getUniqueId());
    return v == null ? 0 : v;
  }

  public static void setXp(Game game, Player player, int value) {
    int v = Math.max(0, value);
    XP.computeIfAbsent(game.getName(), k -> new HashMap<>()).put(player.getUniqueId(), v);
    // 仅在经验模式对局内把经验同步到原版经验条，避免污染非经验模式对局 / 大厅玩家的等级
    if (isXpMode(game)) {
      syncVanillaXp(player, v);
    }
  }

  /** 把经验同步到原版经验条（等级数字显示，清空经验条） */
  @SuppressWarnings("deprecation")
  public static void syncVanillaXp(Player player, int xp) {
    if (player == null || !player.isOnline()) {
      return;
    }
    try {
      player.setLevel(xp);
      player.setExp(0.0F);
    } catch (Exception ignored) {
    }
  }

  /** 加经验（受 xp-bedwars.max-xp 上限限制），返回加后值 */
  public static int addXp(Game game, Player player, int amount) {
    int maxXp = BedwarsPRO.getInstance().getIntConfig("xp-bedwars.max-xp", 999);
    int next = getXp(game, player) + amount;
    if (maxXp > 0 && next > maxXp) {
      next = maxXp;
    }
    setXp(game, player, next);
    return next;
  }

  public static boolean hasEnoughXp(Game game, Player player, int amount) {
    return getXp(game, player) >= amount;
  }

  public static boolean takeXp(Game game, Player player, int amount) {
    if (!hasEnoughXp(game, player, amount)) {
      return false;
    }
    setXp(game, player, getXp(game, player) - amount);
    return true;
  }

  /**
   * 经验模式击杀转移：击杀者获得受害者当前全部经验，受害者清零。
   * 返回击杀者实际获得的经验值（受 max-xp 上限影响）。
   */
  public static int transferXpOnKill(Game game, Player killer, Player victim) {
    if (game == null || killer == null || victim == null || killer.equals(victim)) {
      return 0;
    }
    int victimXp = getXp(game, victim);
    if (victimXp <= 0) {
      return 0;
    }
    setXp(game, victim, 0);
    int before = getXp(game, killer);
    int after = addXp(game, killer, victimXp);
    return after - before;
  }

  /** 对局结束时清理该对局全部玩家经验 */
  public static void clearGame(Game game) {
    if (game != null) {
      XP.remove(game.getName());
    }
  }
}
