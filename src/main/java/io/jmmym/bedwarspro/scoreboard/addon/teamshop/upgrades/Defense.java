package io.jmmym.bedwarspro.scoreboard.addon.teamshop.upgrades;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import lombok.Getter;
import lombok.Setter;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.addon.teamshop.TeamShop;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.utils.BedwarsUtil;

public class Defense implements Upgrade {

	@Getter
	private Game game;
	@Getter
	private Team team;
	@Getter
	@Setter
	private int level;
	@Getter
	@Setter
	private String buyer;

	public Defense(Game game, Team team, int level) {
		this.game = game;
		this.team = team;
		this.level = level;
	}

	public UpgradeType getType() {
		return UpgradeType.DEFENSE;
	}

	public String getName() {
		return Config.teamshop_upgrade_name.get(getType());
	}

	public void runUpgrade() {
		if (level < 1) {
			return;
		}
		TeamShop teamShop = Main.getInstance().getArenaManager().getArena(game.getName()).getTeamShop();
		for (Player player : game.getPlayers()) {
			if (BedwarsUtil.isSpectator(game, player) || player.getGameMode() == GameMode.SPECTATOR) {
				continue;
			}
			if (team.getTargetFeetBlock().distanceSquared(player.getLocation()) <= Math.pow(Config.teamshop_upgrade_defense_trigger_range, 2) && team != game.getPlayerTeam(player)) {
				if (teamShop.isCoolingPlayer(team, player) || teamShop.isImmunePlayer(player)) {
					continue;
				}
				level = 0;
				player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 200, 0), true);
				teamShop.removeTrap(this);
				teamShop.addCoolingPlayer(team, player);
				if (team.getPlayers().size() > 0) {
					Main.getInstance().getArenaManager().getArena(game.getName()).getTeamShop().updateTeamShop(team.getPlayers().get(0));
				}
				break;
			}
		}
	}
}
