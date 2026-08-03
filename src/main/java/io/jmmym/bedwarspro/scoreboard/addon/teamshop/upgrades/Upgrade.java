package io.jmmym.bedwarspro.scoreboard.addon.teamshop.upgrades;

import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;

public interface Upgrade {

	public UpgradeType getType();

	public String getName();

	public Game getGame();

	public Team getTeam();

	public int getLevel();

	public void setLevel(int level);

	public String getBuyer();

	public void setBuyer(String buyer);

	public void runUpgrade();
}
