package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotConfig;
import io.jmmym.bedwarspro.bot.BotPlayer;
import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 攻击敌人任务。检测附近敌人并发起攻击。
 *
 * <p>核心PVP逻辑，成功率55%-70%（根据配置难度调整）。</p>
 * <p>包含"走位偏差"：不会完美追踪目标。</p>
 * <p>血量低于逃跑线时撤退。</p>
 */
public class AttackEnemyTask extends AbstractBotTask {

  private Player target = null;
  private int chaseTicks = 0;
  private static final int MAX_CHASE_TICKS = 80;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.ATTACK_ENEMY;
  }

  @Override
  protected double getSuccessRate() {
    BotConfig config = BedwarsPRO.getInstance().getBotConfig();
    return 0.55 + (config.getAiDifficulty() * 0.3);
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline() || game.isSpectator(bot)) {
      return false;
    }

    BotConfig config = BedwarsPRO.getInstance().getBotConfig();

    // 血量太低时不主动攻击
    if (bot.getHealth() <= config.getFleeHealth()) {
      return false;
    }

    // 找到最近的敌人
    target = findNearestEnemy(game, bot, config.getTargetRange());
    return target != null;
  }

  @Override
  public void execute(Game game, Player bot) {
    chaseTicks++;

    if (chaseTicks > MAX_CHASE_TICKS || target == null || !target.isOnline() || target.isDead()) {
      complete = true;
      return;
    }

    BotConfig config = BedwarsPRO.getInstance().getBotConfig();

    // 血量低于逃跑线：撤退
    if (bot.getHealth() <= config.getFleeHealth()) {
      flee(bot);
      complete = true;
      return;
    }

    double dist = distanceXZ(bot.getLocation(), target.getLocation());

    // 近距离攻击（4格内）
    if (dist < 4) {
      if (rollSuccess()) {
        // 攻击！但有偏差
        lookAt(bot, target.getLocation());

        // 走位：不完美追踪，有随机偏移
        double offsetX = (random.nextDouble() - 0.5) * 1.5;
        double offsetZ = (random.nextDouble() - 0.5) * 1.5;
        Location attackPos = target.getLocation().clone().add(offsetX, 0, offsetZ);
        moveTowards(bot, attackPos);

        // 攻击速度：不是每次都打中
        if (random.nextDouble() < config.getAccuracy()) {
          // 对目标造成伤害（模拟攻击）
          double damage = 2 + random.nextInt(3);
          target.damage(damage, bot);
        }
      }
    } else if (dist < config.getTargetRange()) {
      // 中距离追击
      moveTowards(bot, target.getLocation());

      // 追击时有15%概率走歪
      if (random.nextDouble() < 0.15) {
        double sideOffset = (random.nextDouble() - 0.5) * 3;
        bot.setVelocity(bot.getVelocity().add(
            new org.bukkit.util.Vector(sideOffset, 0, sideOffset)));
      }
    } else {
      complete = true;
    }
  }

  private void flee(Player bot) {
    // 朝远离敌人的方向跑
    if (target != null) {
      double dx = bot.getLocation().getX() - target.getLocation().getX();
      double dz = bot.getLocation().getZ() - target.getLocation().getZ();
      double length = Math.sqrt(dx * dx + dz * dz);
      if (length > 0) {
        dx /= length;
        dz /= length;
      }
      bot.setVelocity(new org.bukkit.util.Vector(dx * 0.4, 0.2, dz * 0.4));
    }
  }

  private Player findNearestEnemy(Game game, Player bot, double range) {
    Player nearest = null;
    double nearestDist = range;

    Team myTeam = game.getPlayerTeam(bot);

    for (Player player : game.getPlayers()) {
      if (player.equals(bot) || player.isDead() || !player.isOnline()) {
        continue;
      }
      if (game.isSpectator(player)) {
        continue;
      }

      // 不攻击队友
      Team theirTeam = game.getPlayerTeam(player);
      if (theirTeam != null && theirTeam.equals(myTeam)) {
        continue;
      }

      double dist = distanceXZ(bot.getLocation(), player.getLocation());
      if (dist < nearestDist) {
        nearestDist = dist;
        nearest = player;
      }
    }
    return nearest;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    target = null;
    chaseTicks = 0;
    complete = false;
  }
}
