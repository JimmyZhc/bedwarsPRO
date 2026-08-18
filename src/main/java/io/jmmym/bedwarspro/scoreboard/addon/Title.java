package io.jmmym.bedwarspro.scoreboard.addon;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import io.jmmym.bedwarspro.events.BedwarsGameStartedEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerJoinedEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerKilledEvent;
import io.jmmym.bedwarspro.events.BedwarsTargetBlockDestroyedEvent;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameOverEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.xp.XpManager;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.utils.BedwarsUtil;
import io.jmmym.bedwarspro.scoreboard.utils.Utils;

public class Title implements Listener {

	private Map<String, Integer> Times = new HashMap<String, Integer>();
	private Map<String, Integer> killCounts = new HashMap<String, Integer>();
	// 保存每个玩家上次显示几杀的 BukkitRunnable，用于防止快速击杀时被打断
	private Map<String, BukkitRunnable> killTitleTasks = new HashMap<String, BukkitRunnable>();

	@EventHandler(priority = EventPriority.LOWEST)
	public void onStarted(BedwarsGameStartedEvent e) {
		Game game = e.getGame();
		
		// 延迟执行，等待 GameListener 创建 Arena
		new BukkitRunnable() {
			@Override
			public void run() {
				Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
				if (arena == null) {
					// 如果 arena 还没创建，再等一下
					new BukkitRunnable() {
						@Override
						public void run() {
							Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
							if (arena == null) {
								return;
							}
							startTitleLogic(game, arena, e);
						}
					}.runTaskLater(Main.getPlugin(), 10L);
					return;
				}
				startTitleLogic(game, arena, e);
			}
		}.runTaskLater(Main.getPlugin(), 1L);
	}
	
	private void startTitleLogic(Game game, Arena arena, BedwarsGameStartedEvent e) {
		Times.put(e.getGame().getName(), e.getGame().getTimeLeft());
		
		arena.addGameTask(new BukkitRunnable() {
			@Override
			public void run() {
				org.bukkit.World gameWorld = game.getRegion().getWorld();
				org.bukkit.World lobbyWorld = game.getLobby().getWorld();
				
				clearResourceDrops(gameWorld);
				if (!gameWorld.equals(lobbyWorld)) {
					clearResourceDrops(lobbyWorld);
				}
			}
		}.runTaskLater(Main.getPlugin(), 40L));
		
		if (Config.start_title_enabled) {
			for (Player player : e.getGame().getPlayers()) {
				Utils.clearTitle(player);
			}
			int delay = game.getRegion().getWorld().getName().equals(game.getLobby().getWorld().getName()) ? 5 : 30;
			arena.addGameTask(new BukkitRunnable() {
				int rn = 0;

				@Override
				public void run() {
					if (rn < Config.start_title_title.size()) {
						for (Player player : e.getGame().getPlayers()) {
							Utils.sendTitle(player, 0, 80, 5, Config.start_title_title.get(rn), Config.start_title_subtitle);
						}
						rn++;
					} else {
						this.cancel();
					}
				}
			}.runTaskTimer(Main.getPlugin(), delay, 0L));
		}
		if (game.getLobby().getWorld().equals(game.getRegion().getWorld())) {
			PlaySound.playSound(e.getGame(), Config.play_sound_sound_start);
		} else {
			arena.addGameTask(new BukkitRunnable() {
				@Override
				public void run() {
					PlaySound.playSound(e.getGame(), Config.play_sound_sound_start);
				}
			}.runTaskLater(Main.getPlugin(), 30L));
		}
	}

	@EventHandler
	public void onDestroyed(BedwarsTargetBlockDestroyedEvent e) {
		Game game = e.getGame();
		Team team = e.getTeam();
		Player destroyer = e.getPlayer();
		
		String teamColor = team.getChatColor().toString();
		String destroyerColor = destroyer != null ? game.getPlayerTeam(destroyer).getChatColor().toString() : "§f";
		
		for (Player player : game.getPlayers()) {
			String title;
			String subtitle;
			if (destroyer != null && player.equals(destroyer)) {
				title = teamColor + team.getName();
				subtitle = "§a床已被你摧毁";
			} else {
				title = teamColor + team.getName() + "§c床已被摧毁";
				subtitle = "§7破坏者：" + destroyerColor + (destroyer != null ? destroyer.getName() : "未知");
			}
			Utils.sendTitle(player, 10, 40, 10, title, subtitle);
		}
	}

	

	@EventHandler
	public void onOver(BedwarsGameOverEvent e) {
		if (Config.victory_title_enabled) {
			Game game = e.getGame();
			Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
			Team team = e.getWinner();
			int time = Times.getOrDefault(e.getGame().getName(), 3600) - e.getGame().getTimeLeft();
			String formattime = time / 60 + ":" + ((time % 60 < 10) ? ("0" + time % 60) : (time % 60));
			new BukkitRunnable() {
				@Override
				public void run() {
					if (team != null && team.getPlayers() != null) {
						for (Player player : team.getPlayers()) {
							if (player.isOnline()) {
								Utils.clearTitle(player);
							}
						}
					}
				}
			}.runTaskLater(Main.getPlugin(), 1L);
			arena.addGameTask(new BukkitRunnable() {
				int rn = 0;

				@Override
				public void run() {
					if (rn < Config.victory_title_title.size()) {
						if (team != null && team.getPlayers() != null) {
							for (Player player : team.getPlayers()) {
								if (player.isOnline()) {
									Utils.sendTitle(player, 0, 80, 5, Config.victory_title_title.get(rn).replace("{time}", formattime).replace("{color}", team.getChatColor() + "").replace("{team}", team.getName()), Config.victory_title_subtitle.replace("{time}", formattime).replace("{color}", team.getChatColor() + "").replace("{team}", team.getName()));
								}
							}
							rn++;
						} else {
							this.cancel();
						}
					} else {
						this.cancel();
					}
				}
			}.runTaskTimer(Main.getPlugin(), 40L, 0L));
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				PlaySound.playSound(e.getGame(), Config.play_sound_sound_over);
			}
		}.runTaskLater(Main.getPlugin(), 40L);
		
		showGameOverStats(e);
	}
	
	private void showGameOverStats(BedwarsGameOverEvent e) {
		Game game = e.getGame();
		Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
		io.jmmym.bedwarspro.scoreboard.storage.PlayerGameStorage storage = arena.getPlayerGameStorage();
		
		Map<String, Integer> maxStreak = storage.getPlayerMaxKillStreak();
		Map<String, Integer> totalKills = storage.getPlayerTotalKills();
		Map<String, Integer> dies = storage.getPlayerDies();
		
		String bestPlayer = "";
		int highestStreak = 0;
		double highestKDA = 0;
		
		for (String playerName : totalKills.keySet()) {
			int streak = maxStreak.getOrDefault(playerName, 0);
			int kills = totalKills.getOrDefault(playerName, 0);
			int death = dies.getOrDefault(playerName, 0);
			double kda = death > 0 ? (double) kills / death : kills;
			
			if (streak > highestStreak || (streak == highestStreak && kda > highestKDA)) {
				highestStreak = streak;
				highestKDA = kda;
				bestPlayer = playerName;
			}
		}
		
		if (bestPlayer.isEmpty() && game.getPlayers().size() > 0) {
			bestPlayer = game.getPlayers().iterator().next().getName();
		}
		
		final String finalBestPlayer = bestPlayer;
		final int finalHighestStreak = highestStreak;
		final String kdaStr = String.format("%.2f", highestKDA);
		
		arena.addGameTask(new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : game.getPlayers()) {
					if (player.isOnline()) {
						Utils.sendTitle(player, 10, 60, 10, "§e游戏结束", "§e正在统计本局比赛..");
					}
				}
			}
		}.runTaskLater(Main.getPlugin(), 120L));
		
		arena.addGameTask(new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : game.getPlayers()) {
					if (player.isOnline()) {
						String playerName = player.getName();
						int playerStreak = maxStreak.getOrDefault(playerName, 0);
						int playerKills = totalKills.getOrDefault(playerName, 0);
						int playerDeath = dies.getOrDefault(playerName, 0);
						double playerKDA = playerDeath > 0 ? (double) playerKills / playerDeath : playerKills;
						String playerKDAStr = String.format("%.2f", playerKDA);
						Utils.sendTitle(player, 10, 60, 10, "§c最高连杀：" + playerStreak, "§cKDA:" + playerKDAStr);
					}
				}
			}
		}.runTaskLater(Main.getPlugin(), 190L));
		
		arena.addGameTask(new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : game.getPlayers()) {
					if (player.isOnline()) {
						Utils.sendTitle(player, 10, 80, 10, "§d" + finalBestPlayer, "§6全§e场§b最§a佳");
					}
				}
			}
		}.runTaskLater(Main.getPlugin(), 260L));
	}

	@EventHandler
	public void onJoined(BedwarsPlayerJoinedEvent e) {
		for (Player player : e.getGame().getPlayers()) {
			if (player.getName().contains(",") || player.getName().contains("[") || player.getName().contains("]")) {
				player.kickPlayer("");
			}
			if (!(e.getGame().getState() != GameState.WAITING && e.getGame().getState() == GameState.RUNNING)) {
				if (Config.jointitle_enabled) {
					Utils.sendTitle(player, e.getPlayer(), 5, 50, 5, Config.jointitle_title.replace("{player}", e.getPlayer().getName()), Config.jointitle_subtitle.replace("{player}", e.getPlayer().getName()));
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDamageTitle(EntityDamageByEntityEvent e) {
		if (!Config.damagetitle_enabled || e.isCancelled() || !(e.getDamager() instanceof Player) || !(e.getEntity() instanceof Player)) {
			return;
		}
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer((Player) e.getDamager());
		if (game == null || game.getState() != GameState.RUNNING) {
			return;
		}
		if (!(game.getPlayers().contains((Player) e.getDamager()) && game.getPlayers().contains((Player) e.getEntity()))) {
			return;
		}
		Player player = (Player) e.getEntity();
		Player damager = (Player) e.getDamager();
		if (BedwarsUtil.isSpectator(game, damager) || BedwarsUtil.isSpectator(game, player)) {
			return;
		}
		if (!Config.damagetitle_title.equals("") || !Config.damagetitle_subtitle.equals("")) {
			DecimalFormat df = new DecimalFormat("0.00");
			DecimalFormat df2 = new DecimalFormat("#");
			double health = player.getHealth() - e.getFinalDamage();
			health = health < 0 ? 0 : health;
			Utils.sendTitle((Player) e.getDamager(), player, 0, 20, 0, Config.damagetitle_title.replace("{player}", player.getName()).replace("{damage}", df.format(e.getDamage())).replace("{health}", df2.format(health)).replace("{maxhealth}", df2.format(player.getMaxHealth())), Config.damagetitle_subtitle.replace("{player}", player.getName()).replace("{damage}", df.format(e.getDamage())).replace("{health}", df2.format(health)).replace("{maxhealth}", df2.format(player.getMaxHealth())));
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBowDamage(EntityDamageByEntityEvent e) {
		if (!Config.bowdamage_enabled || e.isCancelled()) {
			return;
		}
		if (!(e.getDamager() instanceof Arrow) || !(e.getEntity() instanceof Player)) {
			return;
		}
		Arrow arrow = (Arrow) e.getDamager();
		if (!(arrow.getShooter() instanceof Player)) {
			return;
		}
		Player shooter = (Player) arrow.getShooter();
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(shooter);
		if (game == null) {
			return;
		}
		if (game.getState() != GameState.RUNNING) {
			return;
		}
		Player player = (Player) e.getEntity();
		Integer damage = (int) e.getFinalDamage();
		if (game.getPlayerTeam(shooter) == game.getPlayerTeam(player)) {
			e.setCancelled(true);
		}
		if (player.isDead()) {
			return;
		}
		double health = player.getHealth() - e.getFinalDamage();
		health = health < 0 ? 0 : health;
		DecimalFormat df = new DecimalFormat("#");
		if (!Config.bowdamage_title.equals("") || !Config.bowdamage_subtitle.equals("")) {
			Utils.sendTitle(shooter, player, 0, 20, 0, Config.bowdamage_title.replace("{player}", player.getName()).replace("{damage}", damage + "").replace("{health}", df.format(health)).replace("{maxhealth}", df.format(player.getMaxHealth())), Config.bowdamage_subtitle.replace("{player}", player.getName()).replace("{damage}", damage + "").replace("{health}", df.format(health)).replace("{maxhealth}", df.format(player.getMaxHealth())));
		}
		if (!Config.bowdamage_message.equals("")) {
			Utils.sendMessage(shooter, player, Config.bowdamage_message.replace("{player}", player.getName()).replace("{damage}", damage + "").replace("{health}", df.format(health)).replace("{maxhealth}", df.format(player.getMaxHealth())));
		}
	}

	@EventHandler
	public void onPlayerKilled(BedwarsPlayerKilledEvent e) {
		Player killer = e.getKiller();
		Player victim = e.getPlayer();
		if (killer == null || victim == null) {
			return;
		}
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(killer);
		if (game == null) {
			return;
		}
		Team killerTeam = game.getPlayerTeam(killer);
		Team victimTeam = game.getPlayerTeam(victim);
		if (killerTeam == null || victimTeam == null) {
			return;
		}
		if (killerTeam.equals(victimTeam)) {
			return;
		}
		String killerName = killer.getName();
		int count = killCounts.getOrDefault(killerName, 0) + 1;
		killCounts.put(killerName, count);
		final int finalCount = count;

		// 经验模式：击杀转移受害者全部经验，主标题显示「经验 +XXX」；物品模式保持「X杀」
		final boolean xpMode = XpManager.isXpMode(game);
		final int gainedXp = xpMode ? XpManager.transferXpOnKill(game, killer, victim) : 0;

		// 如果之前已经有一个发送任务正在运行，先取消，避免相互干扰
		BukkitRunnable oldTask = killTitleTasks.remove(killerName);
		if (oldTask != null) {
			oldTask.cancel();
		}

		// 创建重复发送任务（每5tick一次，持续8次 = 2秒显示时间）
		BukkitRunnable repeatedTask = new BukkitRunnable() {
			int remaining = 8;
			@Override
			public void run() {
				if (!killer.isOnline() || remaining <= 0) {
					this.cancel();
					killTitleTasks.remove(killerName);
					return;
				}
				// fadeIn=0, stay=10, fadeOut=5, 确保快速发送也能显示
				if (xpMode) {
					Utils.sendTitle(killer, 0, 10, 5, "§a经验 +" + gainedXp, null);
				} else {
					Utils.sendTitle(killer, 0, 10, 5, "§a" + finalCount + "杀", null);
				}
				remaining--;
			}
		};
		killTitleTasks.put(killerName, repeatedTask);
		repeatedTask.runTaskTimer(BedwarsPRO.getInstance(), 2L, 5L);
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent e) {
		Player player = e.getEntity();
		killCounts.remove(player.getName());
		BukkitRunnable oldTask = killTitleTasks.remove(player.getName());
		if (oldTask != null) {
			oldTask.cancel();
		}
	}
	
	private void clearResourceDrops(org.bukkit.World world) {
		for (org.bukkit.entity.Entity entity : world.getEntities()) {
			if (entity instanceof org.bukkit.entity.Item) {
				org.bukkit.entity.Item item = (org.bukkit.entity.Item) entity;
				org.bukkit.inventory.ItemStack stack = item.getItemStack();
				org.bukkit.Material type = stack.getType();
				if (type == org.bukkit.Material.IRON_INGOT ||
					type == org.bukkit.Material.GOLD_INGOT ||
					type == org.bukkit.Material.DIAMOND ||
					type == org.bukkit.Material.EMERALD) {
					entity.remove();
				}
			}
		}
	}
}
