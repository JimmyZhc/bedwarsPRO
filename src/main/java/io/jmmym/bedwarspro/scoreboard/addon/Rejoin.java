package io.jmmym.bedwarspro.scoreboard.addon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.PlayerSettings;
import io.jmmym.bedwarspro.game.Team;
import lombok.Getter;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.events.BoardAddonPlayerAddRejoinEvent;
import io.jmmym.bedwarspro.scoreboard.events.BoardAddonPlayerRejoinEvent;
import io.jmmym.bedwarspro.scoreboard.events.BoardAddonPlayerRejoinedEvent;
import io.jmmym.bedwarspro.scoreboard.events.BoardAddonPlayerRemoveRejoinEvent;
import io.jmmym.bedwarspro.scoreboard.utils.Utils;

public class Rejoin {

	@Getter
	private Game game;
	@Getter
	private Arena arena;
	private Map<String, RejoinData> players;
	private Map<String, List<String>> teams;

	public Rejoin(Arena arena) {
		this.arena = arena;
		this.game = arena.getGame();
		players = new HashMap<String, RejoinData>();
		teams = new HashMap<String, List<String>>();
	}

	public void addPlayer(Player player) {
		Team team = game.getPlayerTeam(player);
		if (team == null) {
			return;
		}
		BoardAddonPlayerAddRejoinEvent event = new BoardAddonPlayerAddRejoinEvent(game, player, this);
		Bukkit.getPluginManager().callEvent(event);
		if (!event.isCancelled()) {
			players.put(player.getName(), new RejoinData(player, game, this));
			List<String> list = teams.getOrDefault(team.getName(), new ArrayList<>());
			list.add(player.getName());
			teams.put(team.getName(), list);
		}
	}

	public void removeTeam(String team) {
		if (teams.containsKey(team)) {
			for (String player : teams.get(team)) {
				removePlayer(player);
			}
			teams.remove(team);
		}
	}

	public void removePlayer(String player) {
		if (players.containsKey(player)) {
			players.remove(player);
			Bukkit.getPluginManager().callEvent(new BoardAddonPlayerRemoveRejoinEvent(game, player, this));
		}
	}

	public Map<String, RejoinData> getPlayers() {
		return players;
	}

	public void rejoin(Player player) {
		if (players.containsKey(player.getName())) {
			BoardAddonPlayerRejoinEvent event = new BoardAddonPlayerRejoinEvent(game, player, this);
			Bukkit.getPluginManager().callEvent(event);
			if (!event.isCancelled()) {
				players.get(player.getName()).rejoin();
			}
		}
	}

	public static class RejoinData {
		private UUID uuid;
		private Game game;
		private Rejoin rejoin;
		private String teamname;
		private PlayerSettings playersettings;
		private List<ItemStack> armors;

		public RejoinData(Player player, Game game, Rejoin rejoin) {
			Team team = game.getPlayerTeam(player);
			if (team == null) {
				return;
			}
			teamname = team.getName();
			uuid = player.getUniqueId();
			this.game = game;
			this.rejoin = rejoin;
			playersettings = game.getPlayerSettings(player);
			armors = new ArrayList<ItemStack>();
			if (Config.giveitem_keeparmor) {
				if (player.getInventory().getHelmet() != null) {
					armors.add(player.getInventory().getHelmet());
				} else {
					armors.add(new ItemStack(Material.AIR));
				}
				if (player.getInventory().getChestplate() != null) {
					armors.add(player.getInventory().getChestplate());
				} else {
					armors.add(new ItemStack(Material.AIR));
				}
				if (player.getInventory().getLeggings() != null) {
					armors.add(player.getInventory().getLeggings());
				} else {
					armors.add(new ItemStack(Material.AIR));
				}
				if (player.getInventory().getBoots() != null) {
					armors.add(player.getInventory().getBoots());
				} else {
					armors.add(new ItemStack(Material.AIR));
				}
			}
		}

		public void rejoin() {
			if (teamname == null) {
				return;
			}
			Player player = Bukkit.getPlayer(uuid);
			if (player == null || !player.isOnline()) {
				return;
			}
			BedwarsPRO.getInstance().getGameManager().addGamePlayer(player, game);
			if (!game.getPlayers().contains(player)) {
				game.getPlayers().add(player);
			}
			game.getFreePlayers().remove(player);
			game.getPlayerSettings().put(player, playersettings);
			Team team = game.getTeam(teamname);
			if (team == null) {
				return;
			}
			team.addPlayer(player);
			player.setVelocity(new Vector(0, 0, 0));
			player.setGameMode(GameMode.SPECTATOR);
			for (Player p : game.getPlayers()) {
				p.sendMessage(Config.rejoin_message_rejoin.replace("{player}", player.getName()));
			}
			Utils.clearTitle(player);
			new BukkitRunnable() {
				@Override
				public void run() {
					if (player.isOnline() && game.getState() == GameState.RUNNING && game.getPlayers().contains(player)) {
						if (Main.getInstance().getArenaManager().getArenas().containsKey(game.getName())) {
							player.getInventory().clear();
							player.getInventory().setHelmet(new ItemStack(Material.AIR));
							player.getInventory().setChestplate(new ItemStack(Material.AIR));
							player.getInventory().setLeggings(new ItemStack(Material.AIR));
							player.getInventory().setBoots(new ItemStack(Material.AIR));
							GiveItem.giveItem(player, team, true);

							if (Config.giveitem_keeparmor && armors.size() > 0) {
								player.getInventory().setHelmet(armors.get(0));
								player.getInventory().setChestplate(armors.get(1));
								player.getInventory().setLeggings(armors.get(2));
								player.getInventory().setBoots(armors.get(3));
							}
							Arena arena = Main.getInstance().getArenaManager().getArenas().get(game.getName());
							player.setMaxHealth(arena.getHealthLevel().getNowHealth());
							player.setHealth(player.getMaxHealth());
							if (Config.respawn_enabled) {
								Respawn respawn = arena.getRespawn();
								respawn.onRespawn(player, true);
							} else {
								player.setVelocity(new Vector(0, 0, 0));
								player.teleport(team.getSpawnLocation());
							}
						}
					}
				}
			}.runTaskLater(Main.getPlugin(), 16L);
			Bukkit.getPluginManager().callEvent(new BoardAddonPlayerRejoinedEvent(game, player, rejoin));
		}
	}
}
