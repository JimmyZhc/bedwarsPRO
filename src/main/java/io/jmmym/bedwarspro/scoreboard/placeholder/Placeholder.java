package io.jmmym.bedwarspro.scoreboard.placeholder;

import org.bukkit.entity.Player;

import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;

public abstract class Placeholder {

	public abstract String onPlayerPlaceholderRequest(Game game, Player player);

	public abstract String onGamePlaceholderRequest(Game game);

	public abstract String onTeamPlaceholderRequest(Team team);

}
