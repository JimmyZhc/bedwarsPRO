package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.ResourceSpawner;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * 收集资源任务。走向资源生成器拾取掉落物。
 *
 * <p>成功率75%：模拟真人走位不精确，有时会错过掉落物。</p>
 * <p>拾取范围3格内：85%成功率。</p>
 */
public class CollectResourcesTask extends AbstractBotTask {

  private Location targetSpawner = null;
  private int ticksChasing = 0;
  private static final int MAX_CHASE_TICKS = 60;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.COLLECT_RESOURCES;
  }

  @Override
  protected double getSuccessRate() {
    return 0.75;
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline() || game.isSpectator(bot)) {
      return false;
    }

    // 背包满了就不捡了
    if (isInventoryFull(bot)) {
      return false;
    }

    // 附近有掉落物
    Entity nearestItem = findNearestDroppedItem(bot, 16);
    return nearestItem != null;
  }

  @Override
  public void execute(Game game, Player bot) {
    ticksChasing++;

    if (ticksChasing > MAX_CHASE_TICKS) {
      complete = true;
      return;
    }

    Entity nearestItem = findNearestDroppedItem(bot, 16);
    if (nearestItem == null) {
      complete = true;
      return;
    }

    double dist = distanceXZ(bot.getLocation(), nearestItem.getLocation());

    // 3格内：85%概率拾取
    if (dist < 3) {
      if (random.nextDouble() < 0.85) {
        // 走向掉落物
        moveTowards(bot, nearestItem.getLocation());
        // 小跳跃增加拾取机会
        if (random.nextDouble() < 0.3) {
          bot.setVelocity(bot.getLocation().getDirection().multiply(0.2).setY(0.1));
        }
      }
      return;
    }

    // 远距离：走向资源
    moveTowards(bot, nearestItem.getLocation());

    // 有概率走歪
    if (random.nextDouble() < 0.15) {
      double offset = (random.nextDouble() - 0.5) * 2;
      bot.setVelocity(bot.getVelocity().add(
          new org.bukkit.util.Vector(offset, 0, offset)));
    }
  }

  private Entity findNearestDroppedItem(Player player, double range) {
    Entity nearest = null;
    double nearestDist = range;

    for (Entity entity : player.getNearbyEntities(range, range, range)) {
      if (entity instanceof Item) {
        double dist = distanceXZ(player.getLocation(), entity.getLocation());
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = entity;
        }
      }
    }
    return nearest;
  }

  private boolean isInventoryFull(Player bot) {
    for (int i = 0; i < bot.getInventory().getSize(); i++) {
      if (bot.getInventory().getItem(i) == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    ticksChasing = 0;
    targetSpawner = null;
    complete = false;
  }
}
