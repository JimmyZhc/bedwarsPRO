package io.jmmym.bedwarspro.scoreboard.addon;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsPlayerJoinedEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerLeaveEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameManager;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.utils.BedwarsUtil;

public class HidePlayer implements Listener {

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if (!Config.hide_player) {
			return;
		}
		Player player = e.getPlayer();
		GameManager man = BedwarsPRO.getInstance().getGameManager();
		Bukkit.getOnlinePlayers().forEach(p -> {
			if (man.getGameOfPlayer(p) != null) {
				hidePlayer(p, player);
				hidePlayer(player, p);
			}
		});
	}

	@EventHandler
	public void onJoined(BedwarsPlayerJoinedEvent e) {
		if (!Config.hide_player) {
			return;
		}
		Game game = e.getGame();
		Player player = e.getPlayer();
		if (game.getState().equals(GameState.WAITING)) {
			List<Player> list = game.getPlayers();
			Bukkit.getOnlinePlayers().forEach(p -> {
				if (list.contains(p)) {
					showPlayer(p, player);
					showPlayer(player, p);
				} else {
					hidePlayer(p, player);
					hidePlayer(player, p);
				}
			});
		} else if (game.getState().equals(GameState.RUNNING)) {
			List<Player> list = game.getPlayers();
			Bukkit.getOnlinePlayers().forEach(p -> {
				if (list.contains(p)) {
					if (BedwarsUtil.isSpectator(game, p)) {
						hidePlayer(p, player);
						hidePlayer(player, p);
					} else {
						hidePlayer(p, player);
						showPlayer(player, p);
					}
				} else {
					hidePlayer(p, player);
					hidePlayer(player, p);
				}
			});
		}
	}

	@EventHandler
	public void onLeave(BedwarsPlayerLeaveEvent e) {
		if (!Config.hide_player) {
			return;
		}
		Player player = e.getPlayer();
		GameManager man = BedwarsPRO.getInstance().getGameManager();
		Bukkit.getOnlinePlayers().forEach(p -> {
			if (man.getGameOfPlayer(p) == null) {
				showPlayer(p, player);
				showPlayer(player, p);
			} else {
				hidePlayer(p, player);
				hidePlayer(player, p);
			}
		});
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		Player player = e.getEntity();
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game == null) {
			return;
		}
		Team team = game.getPlayerTeam(player);
		if (team == null) {
			return;
		}
		if (team.isDead(game)) {
			Bukkit.getOnlinePlayers().forEach(p -> {
				hidePlayer(p, player);
			});
		}
	}

	private void hidePlayer(Player p1, Player p2) {
		if (p1.getUniqueId().equals(p2.getUniqueId())) {
			return;
		}
		p1.hidePlayer(p2);
	}

	private void showPlayer(Player p1, Player p2) {
		if (p1.getUniqueId().equals(p2.getUniqueId())) {
			return;
		}
		p1.showPlayer(p2);
	}
}