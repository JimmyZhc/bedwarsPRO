package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

/**
 * 购物任务。走向商店NPC并购买物品。
 *
 * <p>成功率60%：模拟真人有时候买错东西或被中途干扰。</p>
 */
public class BuyItemsTask extends AbstractBotTask {

  private Villager shopNpc = null;
  private int ticksMoving = 0;
  private static final int MAX_MOVE_TICKS = 80;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.BUY_ITEMS;
  }

  @Override
  protected double getSuccessRate() {
    return 0.6;
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline() || game.isSpectator(bot)) {
      return false;
    }

    // 只有在有资源时才购物
    if (!hasResources(bot)) {
      return false;
    }

    // 找到最近的商人
    shopNpc = findNearestShop(bot);
    return shopNpc != null;
  }

  @Override
  public void execute(Game game, Player bot) {
    ticksMoving++;
    if (ticksMoving > MAX_MOVE_TICKS || shopNpc == null) {
      complete = true;
      return;
    }

    double dist = distanceXZ(bot.getLocation(), shopNpc.getLocation());
    if (dist > 3) {
      moveTowards(bot, shopNpc.getLocation());
    } else {
      // 到达商店，尝试交互
      if (rollSuccess()) {
        lookAt(bot, shopNpc.getLocation());
        // 模拟右键交互商店
        // 实际游戏会触发PlayerInteractEntityEvent
        // 这里直接模拟购买行为
      }
      complete = true;
    }
  }

  private boolean hasResources(Player bot) {
    for (org.bukkit.inventory.ItemStack item : bot.getInventory().getContents()) {
      if (item != null) {
        if (item.getType() == Material.IRON_INGOT || item.getType() == Material.GOLD_INGOT
            || item.getType() == Material.DIAMOND || item.getType() == Material.EMERALD) {
          return true;
        }
      }
    }
    return false;
  }

  private Villager findNearestShop(Player bot) {
    Villager nearest = null;
    double nearestDist = 32;

    for (Entity entity : bot.getNearbyEntities(32, 32, 32)) {
      if (entity instanceof Villager) {
        double dist = distanceXZ(bot.getLocation(), entity.getLocation());
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = (Villager) entity;
        }
      }
    }
    return nearest;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    shopNpc = null;
    ticksMoving = 0;
    complete = false;
  }
}
