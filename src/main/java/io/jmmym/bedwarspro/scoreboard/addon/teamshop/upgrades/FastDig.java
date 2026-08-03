package io.jmmym.bedwarspro.scoreboard.addon.teamshop.upgrades;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import lombok.Getter;
import lombok.Setter;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.utils.BedwarsUtil;

public class FastDig implements Upgrade {

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

	public FastDig(Game game, Team team, int level) {
		this.game = game;
		this.team = team;
		this.level = level;
	}

	public UpgradeType getType() {
		return UpgradeType.FAST_DIG;
	}

	public String getName() {
		return Config.teamshop_upgrade_name.get(getType());
	}

	public void runUpgrade() {
		if (level < 1) {
			return;
		}
		for (Player p : team.getPlayers()) {
			if (!BedwarsUtil.isSpectator(game, p)) {
				p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 30, getLevel() - 1), true);
			}
		}
	}
}
