package io.jmmym.bedwarspro.scoreboard.addon;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsPlayerJoinedEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.utils.ScoreboardUtil;

public class LobbyScoreBoard implements Listener {

	@EventHandler
	public void onJoined(BedwarsPlayerJoinedEvent e) {
		if (!Config.lobby_scoreboard_enabled) {
			return;
		}
		Game game = e.getGame();
		Player player = e.getPlayer();
		new BukkitRunnable() {
			int i = 0;

			@Override
			public void run() {
				if (player.isOnline() && e.getGame().getPlayers().contains(player) && e.getGame().getState() == GameState.WAITING) {
					i--;
					if (i <= 0) {
						i = Config.lobby_scoreboard_interval;
						updateScoreboard(player, game);
					}
				} else {
					this.cancel();
				}
			}
		}.runTaskTimer(Main.getPlugin(), 0L, 1L);
	}

	private void updateScoreboard(Player player, Game game) {
		String title = "§d§l起床战争";
		BedwarsPRO.getInstance().getConfig().set("lobby-scoreboard.title", title);
		ScoreboardUtil.setLobbyScoreboard(player, title, getLines(player, game), game);
	}

	private String centerText(String text) {
		int maxLength = 32;
		String stripped = text.replaceAll("§[0-9a-fk-or]", "");
		int textLength = 0;
		for (char c : stripped.toCharArray()) {
			textLength += (c >= '\u4e00' && c <= '\u9fff') ? 2 : 1;
		}
		int spaces = (maxLength - textLength) / 2;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < spaces; i++) {
			sb.append(" ");
		}
		return sb.toString() + text;
	}

	private List<String> getLines(Player player, Game game) {
		List<String> lines = new ArrayList<String>();

		lines.add("");

		lines.add("§f本局地图：");
		lines.add("§a" + game.getName());

		lines.add("§f房间人数：");
		lines.add("§e" + game.getPlayers().size() + "/" + game.getMaxPlayers());

		String countdown = "";
		if (game.getLobbyCountdown() != null) {
			int lobbytime = game.getLobbyCountdown().getLobbytime();
			int counter = game.getLobbyCountdown().getCounter() + 1;
			counter = counter > lobbytime ? lobbytime : counter;
			countdown = "§f游戏将在§e" + counter + "§f秒后开始";
		} else {
			countdown = "§f等待玩家加入...";
		}
		lines.add(countdown);

		lines.add("");

		lines.add("   §c✿§b栖云居§c✿");

		return lines;
	}
}
