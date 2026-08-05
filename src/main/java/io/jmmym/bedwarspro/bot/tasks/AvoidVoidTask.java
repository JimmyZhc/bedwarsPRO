package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 避免虚空任务。检测边缘和虚空，自动后退。
 *
 * <p>成功率70%：模拟真人在桥上被打时的反应——有时会反应不过来。</p>
 * <p>近距离检测（2格内）成功率提高到85%。</p>
 */
public class AvoidVoidTask extends AbstractBotTask {

  private Location safeSpot = null;
  private int ticksActive = 0;
  private static final int MAX_ACTIVE_TICKS = 40;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.VOID_AVOID;
  }

  @Override
  protected double getSuccessRate() {
    return 0.7;
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline()) {
      return false;
    }
    if (game.isSpectator(bot)) {
      return false;
    }

    Location loc = bot.getLocation();
    Block below = loc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
    Block at = loc.getBlock();

    // 直接在虚空中
    if (loc.getY() < 1) {
      return true;
    }

    // 脚下是空气（正在下落）
    if (below.getType() == Material.AIR && loc.getY() > 2) {
      // 检查是否在桥/边缘附近
      return isNearBridgeEdge(loc);
    }

    // 检查前方2格是否有虚空
    Vector facing = loc.getDirection().setY(0).normalize();
    for (int i = 1; i <= 2; i++) {
      Location ahead = loc.clone().add(facing.clone().multiply(i));
      Block belowAhead = ahead.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
      if (belowAhead.getType() == Material.AIR || belowAhead.getType() == Material.WATER) {
        return random.nextDouble() < 0.5;
      }
    }

    return false;
  }

  private boolean isNearBridgeEdge(Location loc) {
    Block center = loc.getBlock();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        Block b = center.getRelative(x, -1, z);
        if (b.getType() != Material.AIR && b.getType() != Material.WATER) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void execute(Game game, Player bot) {
    ticksActive++;

    if (ticksActive > MAX_ACTIVE_TICKS) {
      complete = true;
      return;
    }

    // 计算安全方向（远离边缘）
    Location loc = bot.getLocation();
    Vector pushDir = getSafeDirection(loc);

    if (pushDir != null) {
      // 70%成功率：有时反应太慢
      if (rollSuccess()) {
        bot.setVelocity(pushDir.multiply(0.5).setY(0.3));
        // 每3tick尝试跳跃自救
        if (ticksActive % 3 == 0) {
          bot.setVelocity(pushDir.multiply(0.3).setY(0.42));
        }
      }
    } else {
      // 没有明确安全方向，随机跳跃
      if (random.nextDouble() < 0.6) {
        bot.setVelocity(new Vector(
            (random.nextDouble() - 0.5) * 0.3,
            0.35,
            (random.nextDouble() - 0.5) * 0.3
        ));
      }
    }

    // 如果回到安全地面，结束任务
    Block below = loc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
    if (below.getType() != Material.AIR && loc.getY() > 2) {
      complete = true;
    }
  }

  private Vector getSafeDirection(Location loc) {
    Block center = loc.getBlock();
    double maxDensity = -1;
    Vector safest = null;

    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        int solidCount = 0;
        for (int dy = -2; dy <= 0; dy++) {
          Block b = center.getRelative(x, dy, z);
          if (b.getType() != Material.AIR && b.getType() != Material.WATER) {
            solidCount++;
          }
        }
        if (solidCount > maxDensity && (x != 0 || z != 0)) {
          maxDensity = solidCount;
          safest = new Vector(x, 0, z).normalize();
        }
      }
    }
    return safest;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    ticksActive = 0;
    safeSpot = null;
    complete = false;
  }
}
