package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 返回基地任务。血量低时返回己方出生点附近。
 *
 * <p>成功率80%：返回基地是比较简单的行为。</p>
 */
public class ReturnToBaseTask extends AbstractBotTask {

  private int ticksReturning = 0;
  private static final int MAX_RETURN_TICKS = 100;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.RETURN_TO_BASE;
  }

  @Override
  protected double getSuccessRate() {
    return 0.8;
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline() || game.isSpectator(bot)) {
      return false;
    }

    // 血量低于8时返回
    if (bot.getHealth() > 8) {
      return false;
    }

    Team team = game.getPlayerTeam(bot);
    if (team == null) return false;

    // 不在出生点附近时才返回
    Location spawn = team.getSpawnLocation();
    if (spawn == null) return false;

    double dist = distanceXZ(bot.getLocation(), spawn);
    return dist > 10;
  }

  @Override
  public void execute(Game game, Player bot) {
    ticksReturning++;
    if (ticksReturning > MAX_RETURN_TICKS) {
      complete = true;
      return;
    }

    Team team = game.getPlayerTeam(bot);
    if (team == null) {
      complete = true;
      return;
    }

    Location spawn = team.getSpawnLocation();
    if (spawn == null) {
      complete = true;
      return;
    }

    double dist = distanceXZ(bot.getLocation(), spawn);
    if (dist <= 5) {
      complete = true;
      return;
    }

    // 走向出生点
    if (rollSuccess()) {
      moveTowards(bot, spawn);
    } else {
      // 20%概率走错方向
      double randomAngle = random.nextDouble() * Math.PI * 2;
      double dx = Math.cos(randomAngle) * 0.3;
      double dz = Math.sin(randomAngle) * 0.3;
      bot.setVelocity(new org.bukkit.util.Vector(dx, 0.1, dz));
    }
  }

  @Override
  public void cleanup(Game game, Player bot) {
    ticksReturning = 0;
    complete = false;
  }
}
