package io.jmmym.bedwarspro.scoreboard.utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;

public class ScoreboardUtil {

	private static Map<Player, Scoreboard> scoreboards = new HashMap<Player, Scoreboard>();
	private static Map<Player, Map<Player, Integer>> player_health = new HashMap<Player, Map<Player, Integer>>();

	public static Map<Player, Scoreboard> getScoreboards() {
		return scoreboards;
	}

	public static void removePlayer(Player player) {
		if (scoreboards.containsKey(player)) {
			scoreboards.remove(player);
		}
		if (player_health.containsKey(player)) {
			player_health.remove(player);
		}
	}

	private static List<String> getQuellLines(List<String> lines) {
		List<String> quell_lines = new ArrayList<String>();
		for (String line : lines) {
			String l = line;
			while (true) {
				if (l == null || !quell_lines.contains(l)) {
					quell_lines.add(l != null && l.length() > 40 ? l.substring(0, 40) : l);
					break;
				}
				l += "§r";
			}
		}
		if (quell_lines.size() < 15) {
			for (int i = 0; i < 15 - lines.size(); i++) {
				quell_lines.add(0, null);
			}
		}
		return quell_lines;
	}

	private static String[] toElementArray(String title, List<String> lines) {
		List<String> list = new ArrayList<String>();
		if (title == null) {
			title = "BedWars";
		}
		// 转换标题颜色
		title = org.bukkit.ChatColor.translateAlternateColorCodes('&', title);
		list.add(title.length() > 32 ? title.substring(0, 32) : title);

		// 转换每一行的颜色
		List<String> quellLines = getQuellLines(lines);
		for (String line : quellLines) {
			if (line != null) {
				line = org.bukkit.ChatColor.translateAlternateColorCodes('&', line);
			}
			list.add(line);
		}
		return list.toArray(new String[list.size()]);
	}

	public static void setLobbyScoreboard(Player player, String title, List<String> lines, Game game) {
		String[] elements = toElementArray(title, lines);
		Scoreboard scoreboard = player.getScoreboard();
		try {
			if (scoreboard == null || scoreboard == Bukkit.getScoreboardManager().getMainScoreboard() || scoreboard.getObjectives().size() != 1) {
				player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
				scoreboard = player.getScoreboard();
			}
			try {
				if (scoreboard.getObjective("bwsba-lobby") == null) {
					scoreboard.registerNewObjective("bwsba-lobby", "dummy");
					scoreboard.getObjective("bwsba-lobby").setDisplaySlot(DisplaySlot.SIDEBAR);
				}
			} catch (IllegalStateException ex) {
				// objective 已存在，忽略
			}
			scoreboard.getObjective(DisplaySlot.SIDEBAR).setDisplayName(elements[0]);
			for (int i = 1; i < elements.length; ++i) {
				if (elements[i] != null && scoreboard.getObjective(DisplaySlot.SIDEBAR).getScore(elements[i]).getScore() != 16 - i) {
					scoreboard.getObjective(DisplaySlot.SIDEBAR).getScore(elements[i]).setScore(16 - i);
					for (String string : scoreboard.getEntries()) {
						if (scoreboard.getObjective("bwsba-lobby").getScore(string).getScore() == 16 - i && !string.equals(elements[i])) {
							scoreboard.resetScores(string);
						}
					}
				}
			}
			for (String entry : scoreboard.getEntries()) {
				boolean toErase = true;
				for (String element : elements) {
					if (element != null && element.equals(entry) && scoreboard.getObjective("bwsba-lobby").getScore(entry).getScore() == 16 - Arrays.asList(elements).indexOf(element)) {
						toErase = false;
						break;
					}
				}
				if (toErase) {
					scoreboard.resetScores(entry);
				}
			}
			for (Team t : game.getTeams().values()) {
				org.bukkit.scoreboard.Team team = scoreboard.getTeam(game.getName() + ":" + t.getName());
				if (team == null) {
					team = scoreboard.registerNewTeam(game.getName() + ":" + t.getName());
				}
				team.setAllowFriendlyFire(false);
				team.setPrefix(t.getChatColor().toString());
				for (Player pl : t.getPlayers()) {
					if (!team.hasPlayer((OfflinePlayer) pl)) {
						team.addPlayer((OfflinePlayer) pl);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static WrappedChatComponent chatComp(String text) {
		return WrappedChatComponent.fromText(text);
	}

	private static void sendShowHealthPacket(Player player) {
		ProtocolManager man = ProtocolLibrary.getProtocolManager();
		if (Config.tab_health) {
			try {
				PacketContainer packet = man.createPacket(PacketType.Play.Server.SCOREBOARD_OBJECTIVE);
				packet.getIntegers().write(0, 0);
				packet.getStrings().write(0, "bwsba-game-list");
				packet.getChatComponents().write(0, chatComp("bwsba-game-list"));
				packet.getStrings().write(1, "dummy");
				man.sendServerPacket(player, packet);
			} catch (Exception e) {
			}
			try {
				PacketContainer packet = man.createPacket(PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE);
				packet.getIntegers().write(0, 0);
				packet.getStrings().write(0, "bwsba-game-list");
				man.sendServerPacket(player, packet);
			} catch (Exception e) {
			}
		}
		if (Config.tag_health) {
			try {
				PacketContainer packet = man.createPacket(PacketType.Play.Server.SCOREBOARD_OBJECTIVE);
				packet.getIntegers().write(0, 0);
				packet.getStrings().write(0, "bwsba-game-name");
				packet.getChatComponents().write(0, chatComp("bwsba-game-name"));
				packet.getStrings().write(1, "dummy");
				man.sendServerPacket(player, packet);
			} catch (Exception e) {
			}
			try {
				PacketContainer packet = man.createPacket(PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE);
				packet.getIntegers().write(0, 2);
				packet.getStrings().write(0, "bwsba-game-name");
				man.sendServerPacket(player, packet);
			} catch (Exception e) {
			}
			try {
				PacketContainer packet = man.createPacket(PacketType.Play.Server.SCOREBOARD_OBJECTIVE);
				packet.getIntegers().write(0, 2);
				packet.getStrings().write(0, "bwsba-game-name");
				packet.getChatComponents().write(0, chatComp("\u00a7c\u2764"));
				man.sendServerPacket(player, packet);
			} catch (Exception e) {
			}
		}
	}

	private static void sendHealthValuePacket(Player player, Player target, int value) {
		if (target == null || !target.isOnline() || !target.isValid()) {
			return;
		}
		ProtocolManager man = ProtocolLibrary.getProtocolManager();
		if (Config.tab_health) {
			try {
				PacketContainer packet = man.createPacket(PacketType.Play.Server.SCOREBOARD_SCORE);
				packet.getIntegers().write(0, value);
				packet.getStrings().write(0, target.getName());
				packet.getStrings().write(1, "bwsba-game-list");
				man.sendServerPacket(player, packet);
			} catch (Exception e) {
			}
		}
		if (Config.tag_health) {
			try {
				PacketContainer packet = man.createPacket(PacketType.Play.Server.SCOREBOARD_SCORE);
				packet.getIntegers().write(0, value);
				packet.getStrings().write(0, target.getName());
				packet.getStrings().write(1, "bwsba-game-name");
				man.sendServerPacket(player, packet);
			} catch (Exception e) {
			}
		}
	}

	public static void setGameScoreboard(Player player, String title, List<String> lines, Game game) {
		boolean exist = scoreboards.containsKey(player);
		if (!exist) {
			scoreboards.put(player, Bukkit.getScoreboardManager().getNewScoreboard());
		}
		String[] elements = toElementArray(title, lines);
		Scoreboard scoreboard = scoreboards.get(player);
		try {
			try {
				if (scoreboard.getObjective("bwsba-game") == null) {
					scoreboard.registerNewObjective("bwsba-game", "dummy");
					scoreboard.getObjective("bwsba-game").setDisplaySlot(DisplaySlot.SIDEBAR);
				}
			} catch (IllegalStateException ex) {
				// objective 已存在，忽略
			}
			if (!exist) {
				Scoreboard oldScoreboard = player.getScoreboard();
				if (oldScoreboard != null) {
					org.bukkit.scoreboard.Objective oldSidebar = oldScoreboard.getObjective(DisplaySlot.SIDEBAR);
					if (oldSidebar != null) {
						try {
							oldSidebar.unregister();
						} catch (Exception ex) {
						}
					}
					if (oldScoreboard.getObjective("bwsba-lobby") != null) {
						try {
							oldScoreboard.getObjective("bwsba-lobby").unregister();
						} catch (Exception ex) {
						}
					}
				}
				sendShowHealthPacket(player);
			}
			scoreboard.getObjective(DisplaySlot.SIDEBAR).setDisplayName(elements[0]);
			for (int i = 1; i < elements.length; ++i) {
				if (elements[i] != null && scoreboard.getObjective(DisplaySlot.SIDEBAR).getScore(elements[i]).getScore() != 16 - i) {
					scoreboard.getObjective(DisplaySlot.SIDEBAR).getScore(elements[i]).setScore(16 - i);
					for (String string : scoreboard.getEntries()) {
						if (scoreboard.getObjective("bwsba-game").getScore(string).getScore() == 16 - i && !string.equals(elements[i])) {
							scoreboard.resetScores(string);
						}
					}
				}
			}
			for (String entry : scoreboard.getEntries()) {
				boolean toErase = true;
				for (String element : elements) {
					if (element != null && element.equals(entry) && scoreboard.getObjective("bwsba-game").getScore(entry).getScore() == 16 - Arrays.asList(elements).indexOf(element)) {
						toErase = false;
						break;
					}
				}
				if (toErase) {
					scoreboard.resetScores(entry);
				}
			}
			if (!player_health.containsKey(player)) {
				player_health.put(player, new HashMap<Player, Integer>());
			}
			Map<Player, Integer> map = player_health.get(player);
			for (Player pl : game.getPlayers()) {
				int health = Integer.valueOf(new DecimalFormat("##").format(pl.getHealth()));
				if (map.getOrDefault(pl, 0) != health) {
					sendHealthValuePacket(player, pl, health);
					map.put(pl, health);
				}
			}
			Team player_team = game.getPlayerTeam(player);
		Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
		List<UUID> players = (arena != null && arena.getInvisiblePlayer() != null)
			? arena.getInvisiblePlayer().getPlayers()
			: new ArrayList<UUID>();
			for (Team team : game.getTeams().values()) {
				org.bukkit.scoreboard.Team score_team = scoreboard.getTeam(game.getName() + ":" + team.getName());
				if (score_team == null) {
					score_team = scoreboard.registerNewTeam(game.getName() + ":" + team.getName());
				}
				if (!Config.playertag_prefix.equals("")) {
					score_team.setPrefix(Config.playertag_prefix.replace("{color}", team.getChatColor() + "").replace("{color_initials}", team.getChatColor().name().substring(0, 1)).replace("{color_name}", upperInitials(team.getChatColor().name())).replace("{team_initials}", team.getName().substring(0, 1)).replace("{team}", team.getName()));
				}
				if (!Config.playertag_suffix.equals("")) {
					score_team.setSuffix(Config.playertag_suffix.replace("{color}", team.getChatColor() + "").replace("{color_initials}", team.getChatColor().name().substring(0, 1)).replace("{color_name}", upperInitials(team.getChatColor().name())).replace("{team_initials}", team.getName().substring(0, 1)).replace("{team}", team.getName()));
				}
				score_team.setAllowFriendlyFire(false);
				for (Player pl : team.getPlayers()) {
					if (!score_team.hasPlayer((OfflinePlayer) pl)) {
						if (!players.contains(pl.getUniqueId()) || (player_team != null && player_team.getPlayers().contains(pl))) {
							score_team.addPlayer((OfflinePlayer) pl);
						}
						// 不可见玩家不加入敌方 scoreboard team，由其他机制处理
					}
				}
			}
			if (player_team != null && players.contains(player.getUniqueId())) {
				for (Team team : game.getTeams().values()) {
					if (!team.getName().equals(player_team.getName())) {
						for (Player pl : team.getPlayers()) {
							Scoreboard scoreboard2 = pl.getScoreboard();
							for (org.bukkit.scoreboard.Team score_team : scoreboard2.getTeams()) {
								if (player_team.getPlayers().contains(player)) {
									score_team.removePlayer(player);
								}
							}
						}
					}
				}
			}
			if (player.getScoreboard() == null || !player.getScoreboard().equals(scoreboard)) {
				player.setScoreboard(scoreboard);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String upperInitials(String str) {
		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
	}
}