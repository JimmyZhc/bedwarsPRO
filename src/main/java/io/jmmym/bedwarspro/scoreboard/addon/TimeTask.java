package io.jmmym.bedwarspro.scoreboard.addon;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.PigZombie;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import lombok.Getter;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.utils.ColorUtil;
import io.jmmym.bedwarspro.scoreboard.utils.Utils;

public class TimeTask {

	@Getter
	private Game game;
	@Getter
	private Arena arena;
	private Random random = new Random();

	public TimeTask(Arena arena) {
		this.arena = arena;
		this.game = arena.getGame();
		for (String cmd : Config.timecommand_startcommand) {
			if (cmd.equals("")) {
				continue;
			}
			if (cmd.contains("{player}")) {
				for (Player player : game.getPlayers()) {
					Bukkit.getServer().dispatchCommand((CommandSender) Bukkit.getServer().getConsoleSender(), ColorUtil.color(cmd.replace("{player}", player.getName())));
				}
			} else {
				Bukkit.getServer().dispatchCommand((CommandSender) Bukkit.getServer().getConsoleSender(), ColorUtil.color(cmd));
			}
		}
		// 修复：添加空值检查，防止 getConfigurationSection 返回 null
		ConfigurationSection timeCommandSection = Main.getInstance().getConfig().getConfigurationSection("timecommand");
		if (timeCommandSection != null) {
			for (String cmds : timeCommandSection.getKeys(false)) {
				if (cmds.equals("startcommand")) {
					continue;
				}
				arena.addGameTask(new TimeCommandRunnable(arena, cmds).runTaskTimer(Main.getPlugin(), 0L, 21L));
			}
		}
	}

	private void executeCustomCommand(String cmd) {
		executeCustomCommandStatic(game, arena, cmd);
	}

	private static void executeCustomCommandStatic(Game game, Arena arena, String cmd) {
		if (cmd.startsWith("trigger_random_event")) {
			triggerRandomEventStatic(game, arena);
		} else if (cmd.startsWith("zombie_spawn")) {
			String[] args = cmd.split(" ");
			if (args.length >= 2) {
				spawnZombiesStatic(game, args[1]);
			}
		} else if (cmd.startsWith("zombie_pigman_spawn")) {
			String[] args = cmd.split(" ");
			if (args.length >= 2) {
				spawnZombiePigmenStatic(game, args[1]);
			}
		} else if (cmd.startsWith("destroy_all_beds")) {
			destroyAllBedsStatic(game);
		} else if (cmd.startsWith("random_teleport_all")) {
			String[] args = cmd.split(" ");
			if (args.length >= 2) {
				try {
					int delay = Integer.parseInt(args[1]);
					randomTeleportAllStatic(game, delay);
				} catch (NumberFormatException e) {
					randomTeleportAllStatic(game, 75);
				}
			}
		} else if (cmd.startsWith("title")) {
			// 使用 Utils.sendTitle() 发送标题（NMS数据包方式）
			sendTitleViaNMS(game, cmd);
		} else {
			Bukkit.getServer().dispatchCommand((CommandSender) Bukkit.getServer().getConsoleSender(), ColorUtil.color(cmd));
		}
	}
	
	private static void sendTitleViaNMS(Game game, String cmd) {
		// 解析命令格式: title @a title {"text":"...","color":"..."}
		// 或: title @a subtitle {"text":"...","color":"..."}
		String jsonPart = cmd.substring(cmd.indexOf("{") + 1, cmd.lastIndexOf("}"));
		String text = "";
		String color = "white";
		for (String pair : jsonPart.split(",")) {
			String[] kv = pair.split(":");
			if (kv.length >= 2) {
				String key = kv[0].trim().replace("\"", "");
				String value = kv[1].trim().replace("\"", "");
				if ("text".equals(key)) {
					text = value;
				} else if ("color".equals(key)) {
					color = value;
				}
			}
		}
		
		// 将颜色转换为 ChatColor 代码
		org.bukkit.ChatColor chatColor = org.bukkit.ChatColor.WHITE;
		try {
			chatColor = org.bukkit.ChatColor.valueOf(color.toUpperCase());
		} catch (Exception e) {
			// 颜色无效则使用白色
		}
		
		// 根据命令类型发送标题或副标题
		if (cmd.startsWith("title @a title")) {
			// 发送主标题
			String titleText = chatColor + text;
			for (Player player : game.getPlayers()) {
				Utils.sendTitle(player, 10, 50, 10, titleText, null);
			}
		} else if (cmd.startsWith("title @a subtitle")) {
			// 发送副标题
			String subtitleText = chatColor + text;
			for (Player player : game.getPlayers()) {
				Utils.sendTitle(player, 10, 50, 10, null, subtitleText);
			}
		}
	}

	private static void sendTitleToAllPlayers(Game game, String cmd) {
		// 解析 JSON 获取 text 和 color
		String jsonPart = cmd.substring(cmd.indexOf("{") + 1, cmd.lastIndexOf("}"));
		String text = "";
		String color = "white";
		for (String pair : jsonPart.split(",")) {
			String[] kv = pair.split(":");
			if (kv.length >= 2) {
				String key = kv[0].trim().replace("\"", "");
				String value = kv[1].trim().replace("\"", "");
				if ("text".equals(key)) {
					text = value;
				} else if ("color".equals(key)) {
					color = value;
				}
			}
		}
		org.bukkit.ChatColor chatColor = org.bukkit.ChatColor.WHITE;
		try {
			chatColor = org.bukkit.ChatColor.valueOf(color.toUpperCase());
		} catch (Exception e) {
		}
		
		// 解析命令类型
		if (cmd.startsWith("title @a subtitle")) {
			// 发送副标题
			String subtitleText = text != null ? chatColor + text : "";
			for (Player player : game.getPlayers()) {
				// 只设置副标题，不改变主标题
				player.sendTitle(null, subtitleText);
			}
		} else if (cmd.startsWith("title @a title")) {
			// 发送主标题
			String titleText = text != null ? chatColor + text : "";
			for (Player player : game.getPlayers()) {
				// 只设置主标题，不改变副标题
				player.sendTitle(titleText, null);
			}
		}
	}

	private void triggerRandomEvent() {
		triggerRandomEventStatic(game, arena);
	}

	private static void triggerRandomEventStatic(Game game, Arena arena) {
		List<Event> events = new java.util.ArrayList<>();
		events.add(new Event("全体速度提升", PotionEffectType.SPEED, 1, 30));
		events.add(new Event("全体力量提升", PotionEffectType.INCREASE_DAMAGE, 0, 30));
		events.add(new Event("全体跳跃提升", PotionEffectType.JUMP, 2, 30));
		events.add(new Event("全体生命恢复", PotionEffectType.REGENERATION, 1, 20));
		events.add(new Event("全体抗性提升", PotionEffectType.DAMAGE_RESISTANCE, 0, 25));

		int index = ThreadLocalRandom.current().nextInt(events.size());
		Event event = events.get(index);



		if (arena == null) {
			arena = Main.getInstance().getArenaManager().getArena(game.getName());
		}
		if (arena != null) {
			ScoreBoard scoreboard = arena.getScoreBoard();
			if (scoreboard != null) {
				scoreboard.setCurrentRandomEvent(event.getName(), event.getDuration());
			}
		}

		for (Player player : game.getPlayers()) {
			if (player.isOnline()) {
				player.addPotionEffect(new PotionEffect(event.getType(), event.getDuration() * 20, event.getAmplifier(), false, true));
				player.sendTitle("", "§e" + event.getName());
			}
		}
	}

	private void spawnZombies(String locationType) {
		spawnZombiesStatic(game, locationType);
	}

	private static void spawnZombiesStatic(Game game, String locationType) {
		World world = game.getRegion().getWorld();
		if (world == null) {
			return;
		}

		List<Location> spawnLocations = new java.util.ArrayList<>();

		if ("gold_spawners".equals(locationType)) {
			for (io.jmmym.bedwarspro.game.ResourceSpawner spawner : game.getResourceSpawners()) {
				String spawnerName = spawner.getName();
				boolean isGoldSpawner = false;
				if (spawnerName != null && (spawnerName.toLowerCase().contains("gold") || spawnerName.contains("金"))) {
					isGoldSpawner = true;
				} else {
					List<org.bukkit.inventory.ItemStack> resources = spawner.getResources();
					if (resources != null) {
						for (org.bukkit.inventory.ItemStack item : resources) {
							if (item.getType() == org.bukkit.Material.GOLD_INGOT) {
								isGoldSpawner = true;
								break;
							}
						}
					}
				}
				if (isGoldSpawner) {
					Location loc = spawner.getLocation();
					spawnLocations.add(loc.clone().add(1, 0, 0));
					spawnLocations.add(loc.clone().add(-1, 0, 0));
				}
			}
		} else if ("second_island".equals(locationType)) {
			for (Team team : game.getTeams().values()) {
				Location spawn = team.getSpawnLocation();
				if (spawn != null) {
					spawnLocations.add(spawn.clone().add(5, 0, 5));
					spawnLocations.add(spawn.clone().add(-5, 0, 5));
					spawnLocations.add(spawn.clone().add(5, 0, -5));
					spawnLocations.add(spawn.clone().add(-5, 0, -5));
				}
			}
		}

		int spawnedCount = 0;
		for (Location loc : spawnLocations) {
			int x = loc.getBlockX();
			int z = loc.getBlockZ();
			int y = world.getHighestBlockYAt(x, z) + 1;
			Location safeLoc = new Location(world, x + 0.5, y, z + 0.5);

			try {
				Main.getInstance().getEventListener().addPendingSpawnLocation(safeLoc);
				Zombie zombie = (Zombie) world.spawnEntity(safeLoc, EntityType.ZOMBIE);
				zombie.setMaxHealth(20);
				zombie.setHealth(20);
				zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
				zombie.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0));

				org.bukkit.inventory.ItemStack helmet = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_HELMET);
				zombie.getEquipment().setHelmet(helmet);
				zombie.getEquipment().setHelmetDropChance(0f);

				org.bukkit.inventory.ItemStack egg = new org.bukkit.inventory.ItemStack(org.bukkit.Material.EGG);
				zombie.getEquipment().setItemInHand(egg);
				zombie.getEquipment().setItemInHandDropChance(1f);

				spawnedCount++;
			} catch (Exception e) {
			}
		}
	}

	private static void spawnZombiePigmenStatic(Game game, String locationType) {
		World world = game.getRegion().getWorld();
		if (world == null) {
			return;
		}

		List<Location> spawnLocations = new java.util.ArrayList<>();

		if ("diamond_spawners".equals(locationType)) {
			for (io.jmmym.bedwarspro.game.ResourceSpawner spawner : game.getResourceSpawners()) {
				String spawnerName = spawner.getName();
				boolean isDiamondSpawner = false;
				if (spawnerName != null && (spawnerName.toLowerCase().contains("diamond") || spawnerName.contains("钻"))) {
					isDiamondSpawner = true;
				} else {
					List<org.bukkit.inventory.ItemStack> resources = spawner.getResources();
					if (resources != null) {
						for (org.bukkit.inventory.ItemStack item : resources) {
							if (item.getType() == org.bukkit.Material.DIAMOND) {
								isDiamondSpawner = true;
								break;
							}
						}
					}
				}
				if (isDiamondSpawner) {
					Location loc = spawner.getLocation();
					spawnLocations.add(loc.clone().add(1, 0, 0));
					spawnLocations.add(loc.clone().add(-1, 0, 0));
				}
			}
		}

		int spawnedCount = 0;
		for (Location loc : spawnLocations) {
			int x = loc.getBlockX();
			int z = loc.getBlockZ();
			int y = world.getHighestBlockYAt(x, z) + 1;
			Location safeLoc = new Location(world, x + 0.5, y, z + 0.5);

			try {
				Main.getInstance().getEventListener().addPendingSpawnLocation(safeLoc);
				PigZombie pigman = (PigZombie) world.spawnEntity(safeLoc, EntityType.PIG_ZOMBIE);
				pigman.setMaxHealth(30);
				pigman.setHealth(30);
				pigman.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
				pigman.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0));

				org.bukkit.inventory.ItemStack obsidian = new org.bukkit.inventory.ItemStack(org.bukkit.Material.OBSIDIAN);
				pigman.getEquipment().setItemInHand(obsidian);
				pigman.getEquipment().setItemInHandDropChance(1f);

				spawnedCount++;
			} catch (Exception e) {
			}
		}
	}

	private static void destroyAllBedsStatic(Game game) {
		for (Team team : game.getTeams().values()) {
			if (!team.isDead(game)) {
				org.bukkit.Material type = team.getTargetHeadBlock().getBlock().getType();
				if (type.equals(game.getTargetMaterial())) {
					if (type.equals(org.bukkit.Material.BED_BLOCK)) {
						if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
							team.getTargetFeetBlock().getBlock().setType(org.bukkit.Material.AIR);
						} else {
							team.getTargetHeadBlock().getBlock().setType(org.bukkit.Material.AIR);
						}
					} else {
						team.getTargetHeadBlock().getBlock().setType(org.bukkit.Material.AIR);
					}
				}
			}
		}
	}

	private void randomTeleportAll(int delaySeconds) {
		randomTeleportAllStatic(game, delaySeconds);
	}

	private static void randomTeleportAllStatic(Game game, int delaySeconds) {
		Bukkit.broadcastMessage(ColorUtil.color("&6[事件] &e" + delaySeconds + "秒后将随机传送所有玩家！"));

		new BukkitRunnable() {
			int countdown = delaySeconds;

			@Override
			public void run() {
				if (countdown > 0) {
					if (countdown <= 5) {
						Bukkit.broadcastMessage(ColorUtil.color("&c[传送警告] &e" + countdown + "秒！"));
					}
					countdown--;
				} else {
					cancel();
					executeRandomTeleportStatic(game);
				}
			}
		}.runTaskTimer(Main.getPlugin(), 0L, 20L);
	}

	private void executeRandomTeleport() {
		executeRandomTeleportStatic(game);
	}

	private static void executeRandomTeleportStatic(Game game) {
		List<Player> players = new java.util.ArrayList<>(game.getPlayers());
		List<Location> safeLocations = new java.util.ArrayList<>();

		for (Team team : game.getTeams().values()) {
			Location spawn = team.getSpawnLocation();
			if (spawn != null) {
				safeLocations.add(spawn);
			}
		}

		for (Player player : players) {
			if (!game.isSpectator(player) && player.isOnline()) {
				if (!safeLocations.isEmpty()) {
					Location randomLoc = safeLocations.get(ThreadLocalRandom.current().nextInt(safeLocations.size()));
					player.teleport(randomLoc);
				}
			}
		}

		Bukkit.broadcastMessage(ColorUtil.color("&6[事件] &e所有玩家已随机传送！"));
	}

	public void refresh() {
	}

	private static class Event {
		private String name;
		private PotionEffectType type;
		private int amplifier;
		private int duration;

		public Event(String name, PotionEffectType type, int amplifier, int duration) {
			this.name = name;
			this.type = type;
			this.amplifier = amplifier;
			this.duration = duration;
		}

		public String getName() {
			return name;
		}

		public PotionEffectType getType() {
			return type;
		}

		public int getAmplifier() {
			return amplifier;
		}

		public int getDuration() {
			return duration;
		}
	}

	private static class TimeCommandRunnable extends BukkitRunnable {
		private Arena arena;
		private Game game;
		private String cmdId;
		private int gametime;
		private List<String> cmdlist;
		private boolean triggered = false;
		private int lastTimeLeft = -1;

		public TimeCommandRunnable(Arena arena, String cmdId) {
			this.arena = arena;
			this.game = arena.getGame();
			this.cmdId = cmdId;
			this.gametime = Main.getInstance().getConfig().getInt("timecommand." + cmdId + ".gametime");
			this.cmdlist = Main.getInstance().getConfig().getStringList("timecommand." + cmdId + ".command");
		}

		@Override
		public void run() {
			if (triggered) {
				cancel();
				return;
			}

			int currentTime = game.getTimeLeft();
			if (lastTimeLeft == -1) {
				lastTimeLeft = currentTime;
				return;
			}

			if (lastTimeLeft > gametime && currentTime <= gametime) {
				int delayTicks = 0;
				for (String cmd : cmdlist) {
					if (cmd.equals("")) {
						continue;
					}
					final String cmdToExecute = cmd;
					final int delay = delayTicks;
					// 所有命令统一延迟执行，确保顺序执行
					new BukkitRunnable() {
						@Override
						public void run() {
							executeCustomCommandStatic(game, arena, cmdToExecute);
						}
					}.runTaskLater(Main.getPlugin(), delay);
					delayTicks += 5; // 每个命令间隔 5 tick
				}
				triggered = true;
				cancel();
			}

			lastTimeLeft = currentTime;
		}
	}
}
