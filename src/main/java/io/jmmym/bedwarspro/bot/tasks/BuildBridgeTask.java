package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 搭桥任务。在两岛之间放置方块搭桥。
 *
 * <p>成功率55%：模拟真人搭桥有时候搭歪、掉下去。</p>
 * <p>有15%概率搭到错误方向。</p>
 */
public class BuildBridgeTask extends AbstractBotTask {

  private Location targetLocation = null;
  private int blocksPlaced = 0;
  private static final int MAX_BRIDGE_LENGTH = 30;
  private static final int PLACE_INTERVAL = 4;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.BUILD_BRIDGE;
  }

  @Override
  protected double getSuccessRate() {
    return 0.55;
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline() || game.isSpectator(bot)) {
      return false;
    }

    if (!hasBlocks(bot)) {
      return false;
    }

    // 在悬崖边或桥头
    return isAtBridgeEdge(bot) && bot.isSneaking();
  }

  @Override
  public void execute(Game game, Player bot) {
    if (blocksPlaced >= MAX_BRIDGE_LENGTH) {
      complete = true;
      return;
    }

    // 找到前方需要放置方块的位置
    Location placeLoc = findBridgePlacementSpot(bot);
    if (placeLoc == null) {
      complete = true;
      return;
    }

    // 15%概率搭歪（放到错误位置）
    if (random.nextDouble() < 0.15) {
      double offsetX = (random.nextDouble() - 0.5) * 2;
      double offsetZ = (random.nextDouble() - 0.5) * 2;
      placeLoc = placeLoc.clone().add(offsetX, 0, offsetZ);
    }

    Block targetBlock = placeLoc.getBlock();

    // 55%成功率放置
    if (targetBlock.getType() == Material.AIR || targetBlock.getType() == Material.WATER) {
      if (rollSuccess()) {
        ItemStack blockItem = findBridgeBlock(bot);
        if (blockItem != null) {
          targetBlock.setType(blockItem.getType());
          blocksPlaced++;

          // 从背包消耗方块
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

          // 放置后前进
          Location forward = bot.getLocation().clone().add(
              bot.getLocation().getDirection().setY(0).normalize().multiply(1.2));
          forward.setY(targetBlock.getLocation().getY() + 1);
          bot.teleport(forward);
        }
      }
    }
  }

  private boolean isAtBridgeEdge(Player bot) {
    Block below = bot.getLocation().getBlock().getRelative(BlockFace.DOWN);
    Block ahead = bot.getLocation().getBlock().getRelative(
        bot.getLocation().getDirection().setY(0).normalize().getBlockX() > 0 ? BlockFace.EAST :
        bot.getLocation().getDirection().setX(0).getZ() > 0 ? BlockFace.SOUTH : BlockFace.WEST);

    return below.getType() != Material.AIR
        && (ahead.getType() == Material.AIR || ahead.getType() == Material.WATER);
  }

  private Location findBridgePlacementSpot(Player bot) {
    org.bukkit.util.Vector dir = bot.getLocation().getDirection().setY(0).normalize();
    Location ahead = bot.getLocation().clone().add(dir.multiply(1.5));
    ahead.setY(bot.getLocation().getY() - 1);

    if (ahead.getBlock().getType() == Material.AIR || ahead.getBlock().getType() == Material.WATER) {
      return ahead;
    }
    return null;
  }

  private ItemStack findBridgeBlock(Player bot) {
    for (ItemStack item : bot.getInventory().getContents()) {
      if (item != null && isPlaceable(item.getType())) {
        return item;
      }
    }
    return null;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    blocksPlaced = 0;
    targetLocation = null;
    complete = false;
  }
}
