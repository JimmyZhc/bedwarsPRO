package io.jmmym.bedwarspro.scoreboard.listener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;

public class ShopListener implements Listener {

	public ShopListener() {
		packetListener();
	}

	private void packetListener() {
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(Main.getPlugin(), ListenerPriority.HIGHEST, new PacketType[] { PacketType.Play.Server.ENTITY_METADATA }) {
			@Override
			public void onPacketSending(PacketEvent e) {
				try {
					// 与 GrimAC(PacketEvents) 等反作弊共存时，若仍出现数据包冲突，
					// 可在 config.yml 设 hide-npc-name-tags: false 关闭本模块（唯一修改数据包内容的监听）
					if (!BedwarsPRO.getInstance().getBooleanConfig("hide-npc-name-tags", true)) {
						return;
					}
					PacketContainer packet = e.getPacket();
					int id = packet.getIntegers().read(0);
					if (isShopNPC(id)) {
						List<WrappedWatchableObject> list = packet.getWatchableCollectionModifier().read(0);
						if (list == null) {
							list = new ArrayList<WrappedWatchableObject>();
						}
						// 直接修改/新增 WatchableObject，避免 new WrappedDataWatcher()
						// 在部分 ProtocolLib 版本（LegacyDataWatcher.newHandle）上崩溃
						boolean found = false;
						for (WrappedWatchableObject w : list) {
							if (w.getIndex() == 3) {
								if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
									w.setValue((byte) 0);
								} else {
									w.setValue(false);
								}
								found = true;
								break;
							}
						}
						if (!found) {
							try {
								if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
									list.add(new WrappedWatchableObject(3, (byte) 0));
								} else {
									// 1.9+ 必须显式指定 serializer，否则 DataWatcherObject.b()==null，
									// GrimAC(PacketEvents) 读该包会抛 NPE/越界
									WrappedDataWatcher.Serializer boolSerializer = WrappedDataWatcher.Registry.get(Boolean.class);
									list.add(new WrappedWatchableObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, boolSerializer), false));
								}
							} catch (Exception ignored) {
								// 构造失败则放弃隐藏标签，不影响游戏
							}
						}
						packet.getWatchableCollectionModifier().write(0, list);
					}
				} catch (Exception ex) {
					// 忽略：隐藏标签失败不影响正常游戏
				}
			}
		});
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCitizensEnable(CitizensEnableEvent e) {
		File folder = Config.getNPCFile();
		FileConfiguration npcconfig = YamlConfiguration.loadConfiguration(folder);
		if (npcconfig.getKeys(false).contains("npcs")) {
			List<String> npcs = npcconfig.getStringList("npcs");
			List<NPC> gamenpcs = new ArrayList<NPC>();
			for (NPC npc : CitizensAPI.getNPCRegistry().sorted()) {
				if (npcs.contains(npc.getId() + "")) {
					gamenpcs.add(npc);
				}
			}
			for (NPC npc : gamenpcs) {
				CitizensAPI.getNPCRegistry().deregister(npc);
			}
			npcconfig.set("npcs", new ArrayList<String>());
			try {
				npcconfig.save(folder);
			} catch (IOException e1) {
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onNPCLeftClick(NPCLeftClickEvent e) {
		e.setCancelled(onNPCClick(e.getClicker(), e.getNPC(), e.isCancelled()));
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onNPCRightClick(NPCRightClickEvent e) {
		e.setCancelled(onNPCClick(e.getClicker(), e.getNPC(), e.isCancelled()));
	}

	private boolean onNPCClick(Player player, NPC npc, boolean isCancelled) {
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game == null) {
			return isCancelled;
		}
		Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
		if (arena == null || arena.getShop() == null) {
			return isCancelled;
		}
		return arena.getShop().onNPCClick(player, npc, isCancelled);
	}

	private boolean isShopNPC(int id) {
		for (Arena arena : Main.getInstance().getArenaManager().getArenas().values()) {
			if (arena.getShop() != null && arena.getShop().isShopNPC(id)) {
				return true;
			}
		}
		return false;
	}
}
