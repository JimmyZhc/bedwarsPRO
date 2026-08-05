package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 破坏床任务。寻找敌人床并破坏。
 *
 * <p>成功率50%：拆床是高难度行为，模拟真人路径寻找不完美。</p>
 */
public class DestroyBedTask extends AbstractBotTask {

  private Location targetBed = null;
  private int ticksMoving = 0;
  private static final int MAX_MOVE_TICKS = 120;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.DESTROY_BED;
  }

  @Override
  protected double getSuccessRate() {
    return 0.5;
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline() || game.isSpectator(bot)) {
      return false;
    }
    if (!hasPickaxe(bot)) {
      return false;
    }
    targetBed = findEnemyBed(game, bot);
    return targetBed != null;
  }

  @Override
  public void execute(Game game, Player bot) {
    ticksMoving++;
    if (ticksMoving > MAX_MOVE_TICKS || targetBed == null) {
      complete = true;
      return;
    }

    double dist = distanceXZ(bot.getLocation(), targetBed);
    if (dist > 2) {
      moveTowards(bot, targetBed);
      if (random.nextDouble() < 0.2) {
        double offset = (random.nextDouble() - 0.5) * 2;
        bot.setVelocity(bot.getVelocity().add(
            new org.bukkit.util.Vector(offset, 0, offset)));
      }
    } else {
      Block bedBlock = targetBed.getBlock();
      if (bedBlock.getType() == Material.BED_BLOCK || bedBlock.getType() == Material.BED) {
        if (rollSuccess()) {
          bedBlock.setType(Material.AIR);
          Block secondBed = findSecondBedBlock(bedBlock);
          if (secondBed != null) {
            secondBed.setType(Material.AIR);
          }
        }
      }
      complete = true;
    }
  }

  private boolean hasPickaxe(Player bot) {
    for (ItemStack item : bot.getInventory().getContents()) {
      if (item != null && item.getType().name().contains("PICKAXE")) {
        return true;
      }
    }
    return false;
  }

  private Location findEnemyBed(Game game, Player bot) {
    Team myTeam = game.getPlayerTeam(bot);
    Location nearest = null;
    double nearestDist = Double.MAX_VALUE;

    for (Team team : game.getTeams().values()) {
      if (team.equals(myTeam)) continue;
      if (team.isDead(game)) continue;
      Location bedLoc = team.getTargetHeadBlock();
      if (bedLoc == null) continue;
      double dist = distanceXZ(bot.getLocation(), bedLoc);
      if (dist < nearestDist) {
        nearestDist = dist;
        nearest = bedLoc;
      }
    }
    return nearest;
  }

  private Block findSecondBedBlock(Block bedBlock) {
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        if (x == 0 && z == 0) continue;
        Block adjacent = bedBlock.getRelative(x, 0, z);
        if (adjacent.getType() == Material.BED_BLOCK || adjacent.getType() == Material.BED) {
          return adjacent;
        }
      }
    }
    return null;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    targetBed = null;
    ticksMoving = 0;
    complete = false;
  }
}
