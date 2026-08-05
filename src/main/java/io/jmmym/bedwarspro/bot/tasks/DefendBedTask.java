package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 保护床任务。在床周围放置方块作为保护层。
 *
 * <p>成功率60%：模拟真人搭防有时候放错方块、犹豫选哪个位置的情况。</p>
 */
public class DefendBedTask extends AbstractBotTask {

  private List<Location> targetBlocks = new ArrayList<>();
  private int placeIndex = 0;
  private int ticksSinceLastPlace = 0;
  private static final int PLACE_DELAY = 8;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.BED_DEFENSE;
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

    Team team = game.getPlayerTeam(bot);
    if (team == null) return false;

    // 床已被破坏，不需要防守
    if (team.isDead(game)) return false;

    // 需要有方块
    if (!hasBlocks(bot)) return false;

    Location bed = team.getTargetHeadBlock();
    if (bed == null) return false;

    // 检查床周围是否已经有足够保护
    int protectionLayers = countProtectionLayers(bed);
    return protectionLayers < 8;
  }

  private int countProtectionLayers(Location bed) {
    int count = 0;
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        for (int y = 0; y <= 1; y++) {
          Block b = bed.getBlock().getRelative(x, y, z);
          if (b.getType() != Material.AIR && b.getType() != Material.BED_BLOCK
              && b.getType() != Material.BED) {
            count++;
          }
        }
      }
    }
    return count;
  }

  @Override
  public void execute(Game game, Player bot) {
    Team team = game.getPlayerTeam(bot);
    if (team == null) {
      complete = true;
      return;
    }

    Location bed = team.getTargetHeadBlock();
    if (bed == null) {
      complete = true;
      return;
    }

    ticksSinceLastPlace++;

    // 如果不在床附近，先走过去
    if (distanceXZ(bot.getLocation(), bed) > 4) {
      moveTowards(bot, bed);
      return;
    }

    // 建造保护层
    if (targetBlocks.isEmpty()) {
      targetBlocks = generateProtectionPositions(bed);
    }

    if (placeIndex >= targetBlocks.size()) {
      complete = true;
      return;
    }

    if (ticksSinceLastPlace < PLACE_DELAY) {
      return;
    }

    Location target = targetBlocks.get(placeIndex);
    Block targetBlock = target.getBlock();

    // 60%成功率：有时放错位置或犹豫
    if (targetBlock.getType() == Material.AIR || targetBlock.getType() == Material.WATER) {
      if (rollSuccess()) {
        ItemStack blockItem = findPlaceableBlock(bot);
        if (blockItem != null) {
          lookAt(bot, target);
          // 使用右键放置
          bot.getWorld().getBlockAt(targetBlock.getLocation()).setType(blockItem.getType());
          // 从背包消耗
          for (int i = 0; i < bot.getInventory().getSize(); i++) {
            ItemStack item = bot.getInventory().getItem(i);
            if (item != null && item.getType() == blockItem.getType()) {
              if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
              } else {
                bot.getInventory().clear(i);
              }
              break;
            }
          }
        }
      }
      placeIndex++;
      ticksSinceLastPlace = 0;
    } else {
      placeIndex++;
      ticksSinceLastPlace = 0;
    }
  }

  private ItemStack findPlaceableBlock(Player bot) {
    for (ItemStack item : bot.getInventory().getContents()) {
      if (item != null && isPlaceable(item.getType())) {
        return item;
      }
    }
    return null;
  }

  private List<Location> generateProtectionPositions(Location bed) {
    List<Location> positions = new ArrayList<>();
    // 床周围第一层
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        for (int y = 0; y <= 1; y++) {
          if (x == 0 && z == 0 && y == 0) continue;
          Location pos = bed.clone().add(x, y, z);
          if (pos.getBlock().getType() == Material.AIR
              || pos.getBlock().getType() == Material.WATER) {
            positions.add(pos);
          }
        }
      }
    }
    return positions;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    targetBlocks.clear();
    placeIndex = 0;
    ticksSinceLastPlace = 0;
    complete = false;
  }
}
