package io.jmmym.bedwarspro.scoreboard.utils;

import org.bukkit.entity.Player;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;

public class BedwarsUtil {

	public static boolean isRespawning(Player player) {
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game == null) {
			return false;
		}
		return isRespawning(game, player);
	}

	public static boolean isRespawning(Game game, Player player) {
		Arena arena = Main.getInstance().getArenaManager().getArena(game.getName());
		if (arena == null) {
			return false;
		}
		// 修复：添加空值检查，防止 getRespawn() 返回 null
		if (arena.getRespawn() == null) {
			return false;
		}
		return arena.getRespawn().isRespawning(player);
	}

	public static boolean isSpectator(Player player) {
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game == null) {
			return false;
		}
		return isSpectator(game, player);
	}

	public static boolean isSpectator(Game game, Player player) {
		return game.isSpectator(player) || isRespawning(game, player);
	}

	public static boolean isDieOut(Game game, Team team) {
		if (!team.isDead(game)) {
			return false;
		}
		for (Player player : team.getPlayers()) {
			if (!game.isSpectator(player)) {
				return false;
			}
		}
		return true;
	}
}
