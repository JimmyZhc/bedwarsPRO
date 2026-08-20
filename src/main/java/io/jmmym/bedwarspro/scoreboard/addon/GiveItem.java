package io.jmmym.bedwarspro.scoreboard.addon;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameStartedEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;

public class GiveItem implements Listener {

	@EventHandler
	public void onStarted(BedwarsGameStartedEvent e) {
		Arena arena = Main.getInstance().getArenaManager().getArena(e.getGame().getName());
		// 修复：添加空值检查
		if (arena == null) {
			BedwarsPRO.getInstance().getLogger().warning("GiveItem: arena is null for game " + e.getGame().getName());
			return;
		}
		arena.addGameTask(Bukkit.getScheduler().runTaskLater(Main.getPlugin(), () -> {
			for (Player player : e.getGame().getPlayers()) {
				Team team = e.getGame().getPlayerTeam(player);
				GiveItem.giveItem(player, team, false);
			}
		}, 5L));
	}

	public static void giveItem(Player player, Team team, boolean respawn) {
		Map<String, Object> map1 = new HashMap<String, Object>();
		Map<String, Object> map2 = new HashMap<String, Object>();
		Map<String, Object> map3 = new HashMap<String, Object>();
		Map<String, Object> map4 = new HashMap<String, Object>();
		for (String str : Config.giveitem_armor_helmet_item.keySet()) {
			if (str.equals("type")) {
				if (Config.giveitem_armor_helmet_item.get(str).equals("TEAM_ARMOR")) {
					map1.put(str, "LEATHER_HELMET");
				} else {
					map1.put(str, Config.giveitem_armor_helmet_item.get(str));
				}
			} else {
				map1.put(str, Config.giveitem_armor_helmet_item.get(str));
			}
		}
		for (String str : Config.giveitem_armor_chestplate_item.keySet()) {
			if (str.equals("type")) {
				if (Config.giveitem_armor_chestplate_item.get(str).equals("TEAM_ARMOR")) {
					map2.put(str, "LEATHER_CHESTPLATE");
				} else {
					map2.put(str, Config.giveitem_armor_chestplate_item.get(str));
				}
			} else {
				map2.put(str, Config.giveitem_armor_chestplate_item.get(str));
			}
		}
		for (String str : Config.giveitem_armor_leggings_item.keySet()) {
			if (str.equals("type")) {
				if (Config.giveitem_armor_leggings_item.get(str).equals("TEAM_ARMOR")) {
					map3.put(str, "LEATHER_LEGGINGS");
				} else {
					map3.put(str, Config.giveitem_armor_leggings_item.get(str));
				}
			} else {
				map3.put(str, Config.giveitem_armor_leggings_item.get(str));
			}
		}
		for (String str : Config.giveitem_armor_boots_item.keySet()) {
			if (str.equals("type")) {
				if (Config.giveitem_armor_boots_item.get(str).equals("TEAM_ARMOR")) {
					map4.put(str, "LEATHER_BOOTS");
				} else {
					map4.put(str, Config.giveitem_armor_boots_item.get(str));
				}
			} else {
				map4.put(str, Config.giveitem_armor_boots_item.get(str));
			}
		}
		ItemStack helmet = null;
		ItemStack chestplate = null;
		ItemStack leggings = null;
		ItemStack boots = null;
		try {
			helmet = ItemStack.deserialize(map1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			chestplate = ItemStack.deserialize(map2);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			leggings = ItemStack.deserialize(map3);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			boots = ItemStack.deserialize(map4);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (helmet != null && Config.giveitem_armor_helmet_item.get("type").equals("TEAM_ARMOR")) {
			LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
			meta.setColor(team.getColor().getColor());
			helmet.setItemMeta((ItemMeta) meta);
		}
		if (chestplate != null && Config.giveitem_armor_chestplate_item.get("type").equals("TEAM_ARMOR")) {
			LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
			meta.setColor(team.getColor().getColor());
			chestplate.setItemMeta((ItemMeta) meta);
		}
		if (leggings != null && Config.giveitem_armor_leggings_item.get("type").equals("TEAM_ARMOR")) {
			LeatherArmorMeta meta = (LeatherArmorMeta) leggings.getItemMeta();
			meta.setColor(team.getColor().getColor());
			leggings.setItemMeta((ItemMeta) meta);
		}
		if (boots != null && Config.giveitem_armor_boots_item.get("type").equals("TEAM_ARMOR")) {
			LeatherArmorMeta meta = (LeatherArmorMeta) boots.getItemMeta();
			meta.setColor(team.getColor().getColor());
			boots.setItemMeta((ItemMeta) meta);
		}
		if (Config.giveitem_armor_helmet_give.equalsIgnoreCase("true") || (Config.giveitem_armor_helmet_give.equalsIgnoreCase("start") && !respawn) || (Config.giveitem_armor_helmet_give.equalsIgnoreCase("respawn") && respawn)) {
			player.getInventory().setHelmet(helmet);
		}
		if (Config.giveitem_armor_chestplate_give.equalsIgnoreCase("true") || (Config.giveitem_armor_chestplate_give.equalsIgnoreCase("start") && !respawn) || (Config.giveitem_armor_chestplate_give.equalsIgnoreCase("respawn") && respawn)) {
			player.getInventory().setChestplate(chestplate);
		}
		if (Config.giveitem_armor_leggings_give.equalsIgnoreCase("true") || (Config.giveitem_armor_leggings_give.equalsIgnoreCase("start") && !respawn) || (Config.giveitem_armor_leggings_give.equalsIgnoreCase("respawn") && respawn)) {
			player.getInventory().setLeggings(leggings);
		}
		if (Config.giveitem_armor_boots_give.equalsIgnoreCase("true") || (Config.giveitem_armor_boots_give.equalsIgnoreCase("start") && !respawn) || (Config.giveitem_armor_boots_give.equalsIgnoreCase("respawn") && respawn)) {
			player.getInventory().setBoots(boots);
		}
		for (String items : Main.getInstance().getConfig().getConfigurationSection("giveitem.item").getKeys(false)) {
			String give_option = Main.getInstance().getConfig().getString("giveitem.item." + items + ".give", "true");
			int slot = Main.getInstance().getConfig().getInt("giveitem.item." + items + ".slot");
			if (give_option.equalsIgnoreCase("true") || (give_option.equalsIgnoreCase("start") && !respawn) || (give_option.equalsIgnoreCase("respawn") && respawn)) {
				try {
					ItemStack itemStack = ItemStack.deserialize((Map<String, Object>) Main.getInstance().getConfig().getList("giveitem.item." + items + ".item").get(0));
					// 团队武器升级：发放的剑/斧直接带上队伍锋利附魔（如重生发放的木剑）
					applyTeamWeaponEnchantment(itemStack, team);
					player.getInventory().setItem(slot, itemStack);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/** 团队武器升级：给剑/斧加上队伍锋利附魔（无升级时不修改）。 */
	private static void applyTeamWeaponEnchantment(ItemStack item, Team team) {
		if (item == null || item.getType() == Material.AIR || team == null) return;
		int level = team.getWeaponEnchantLevel();
		if (level <= 0) return;
		String typeName = item.getType().name();
		if (typeName.contains("SWORD") || typeName.contains("AXE")) {
			if (item.containsEnchantment(Enchantment.DAMAGE_ALL)) {
				item.removeEnchantment(Enchantment.DAMAGE_ALL);
			}
			item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, level);
		}
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer((Player) e.getWhoClicked());
		if (game == null || game.getState() != GameState.RUNNING) {
			return;
		}
		// 经验模式：护甲自由管理，头盔/胸甲/护腿/靴子都可以取下
		if (io.jmmym.bedwarspro.xp.XpManager.isXpMode(game)) {
			return;
		}
		Player player = (Player) e.getWhoClicked();
		if (game.getPlayerTeam(player) == null) {
			return;
		}
		Inventory inventory = e.getInventory();
		if (inventory.getHolder() == null) {
			return;
		}
		if (!(inventory.getHolder().equals(player.getInventory().getHolder()) && (inventory.getTitle().equals("container.crafting") || inventory.getTitle().equals("container.inventory")))) {
			return;
		}

		// 保护不可移动的皮革护甲（helmet/chestplate/leggings/boots）
		int rawSlot = e.getRawSlot();
		ItemStack currentItem = e.getCurrentItem();

		// 检查目标槽位是否为受保护的护甲槽位
		boolean isProtectedSlotHelmet = (rawSlot == 5) && !Config.giveitem_armor_helmet_move;
		boolean isProtectedSlotChest = (rawSlot == 6) && !Config.giveitem_armor_chestplate_move;
		boolean isProtectedSlotLeg = (rawSlot == 7) && !Config.giveitem_armor_leggings_move;
		boolean isProtectedSlotBoot = (rawSlot == 8) && !Config.giveitem_armor_boots_move;

		if (isProtectedSlotHelmet || isProtectedSlotChest || isProtectedSlotLeg || isProtectedSlotBoot) {
			// 保护：不允许点击、shift点击、放置、交换等所有操作
			// 但允许在槽位为空时放置物品
			if (currentItem == null || currentItem.getType() == Material.AIR) {
				// 槽位为空，允许放置
			} else {
				// 槽位有物品，阻止一切操作
				e.setCancelled(true);
				return;
			}
		}

		// 额外保护：检查是否是受保护的皮革护甲被移动
		// 防止从其他槽位shift点击到受保护槽位旁边的位置
		if (currentItem != null && currentItem.getType() != Material.AIR) {
			if (isLeatherTeamArmor(currentItem) && (isProtectedSlotHelmet || isProtectedSlotChest || isProtectedSlotLeg || isProtectedSlotBoot)) {
				e.setCancelled(true);
				return;
			}
		}

		// 防止拖动：如果是受保护护甲上的拖动操作
		if (e.getClick() != null && e.getClick().isShiftClick()) {
			ItemStack cursor = e.getCursor();
			if (cursor != null && isLeatherTeamArmor(cursor)) {
				e.setCancelled(true);
				return;
			}
			// 如果shift点击源槽位的物品
			if (rawSlot >= 5 && rawSlot <= 8) {
				boolean slotProtected = (rawSlot == 5 && !Config.giveitem_armor_helmet_move)
						|| (rawSlot == 6 && !Config.giveitem_armor_chestplate_move)
						|| (rawSlot == 7 && !Config.giveitem_armor_leggings_move)
						|| (rawSlot == 8 && !Config.giveitem_armor_boots_move);
				if (slotProtected && currentItem != null && isLeatherTeamArmor(currentItem)) {
					e.setCancelled(true);
					return;
				}
			}
		}
	}

	/** 判断是否为队伍皮革护甲（带WATER_WORKER附魔的皮革装备） */
	private boolean isLeatherTeamArmor(ItemStack item) {
		if (item == null || item.getType() == Material.AIR) return false;
		String name = item.getType().name();
		if (!name.startsWith("LEATHER_")) return false;
		// 1.8.8 没有 isUnbreakable()，用 WATER_WORKER 附魔作为队伍护甲标识
		return item.containsEnchantment(Enchantment.WATER_WORKER);
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		if (e.getEntity() instanceof Player) {
			Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer((Player) e.getEntity());
			if (game == null) {
				return;
			}
			Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
			if (arena == null) {
				return;
			}
			Player p = e.getEntity();
			if (game.getPlayerTeam(p) == null) {
				return;
			}
			if (game.getPlayerTeam(p).isDead(game)) {
				return;
			}
			arena.addGameTask(new BukkitRunnable() {
				Player player = e.getEntity();
				ItemStack stack1 = player.getInventory().getHelmet();
				ItemStack stack2 = player.getInventory().getChestplate();
				ItemStack stack3 = player.getInventory().getLeggings();
				ItemStack stack4 = player.getInventory().getBoots();

				@Override
				public void run() {
					Team team = game.getPlayerTeam(player);
					GiveItem.giveItem(player, team, true);
					if (Config.giveitem_keeparmor) {
						if (stack1 != null) {
							player.getInventory().setHelmet(stack1);
						}
						if (stack2 != null) {
							player.getInventory().setChestplate(stack2);
						}
						if (stack3 != null) {
							player.getInventory().setLeggings(stack3);
						}
						if (stack4 != null) {
							player.getInventory().setBoots(stack4);
						}
					}
				}
			}.runTaskLater(Main.getPlugin(), 1L));
		}
	}
}
