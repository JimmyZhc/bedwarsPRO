package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTask;
import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import java.util.Random;
import org.bukkit.entity.Player;

/**
 * Bot任务抽象基类。提供通用方法：随机概率、距离检测、方向移动等。
 */
public abstract class AbstractBotTask implements BotTask {

  protected final Random random = new Random();
  protected boolean complete = false;

  protected double getSuccessRate() {
    return 1.0;
  }

  protected boolean rollSuccess() {
    return random.nextDouble() < getSuccessRate();
  }

  protected double distanceXZ(org.bukkit.Location a, org.bukkit.Location b) {
    double dx = a.getX() - b.getX();
    double dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
  }

  protected double distanceXZ(org.bukkit.Location a, double x, double z) {
    double dx = a.getX() - x;
    double dz = a.getZ() - z;
    return Math.sqrt(dx * dx + dz * dz);
  }

  protected void moveTowards(Player player, org.bukkit.Location target) {
    double dx = target.getX() - player.getLocation().getX();
    double dy = target.getY() - player.getLocation().getY();
    double dz = target.getZ() - player.getLocation().getZ();
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length > 0) {
      dx /= length;
      dy /= length;
      dz /= length;
      double speed = 0.3;
      player.setVelocity(new org.bukkit.util.Vector(dx * speed, dy * speed + 0.1, dz * speed));
    }
  }

  protected void moveDirection(Player player, double dx, double dy, double dz) {
    double length = Math.sqrt(dx * dx + dy * dy + dz * dx);
    if (length > 0) {
      dx /= length;
      dy /= length;
      dz /= length;
    }
    player.setVelocity(new org.bukkit.util.Vector(dx * 0.3, dy * 0.3 + 0.1, dz * 0.3));
  }

  protected void lookAt(Player player, org.bukkit.Location target) {
    double dx = target.getX() - player.getLocation().getX();
    double dz = target.getZ() - player.getLocation().getZ();
    double yaw = Math.toDegrees(Math.atan2(-dx, dz));
    float currentYaw = player.getLocation().getYaw();
    float newYaw = (float) yaw;
    player.teleport(new org.bukkit.Location(
        player.getWorld(),
        player.getLocation().getX(),
        player.getLocation().getY(),
        player.getLocation().getZ(),
        newYaw,
        player.getLocation().getPitch()
    ));
  }

  protected boolean isInVoid(Player player) {
    return player.getLocation().getY() < 1;
  }

  protected boolean isNearEdge(Player player, double range) {
    org.bukkit.Location loc = player.getLocation();
    org.bukkit.block.Block block = loc.getBlock().getRelative(
        org.bukkit.block.BlockFace.DOWN);
    return block.getType() == org.bukkit.Material.AIR
        || block.getType() == org.bukkit.Material.STATIONARY_WATER
        || block.getType() == org.bukkit.Material.WATER;
  }

  protected boolean hasFood(Player player) {
    for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
      if (item != null && isFood(item.getType())) {
        return true;
      }
    }
    return false;
  }

  protected boolean isFood(org.bukkit.Material mat) {
    String name = mat.name();
    return mat == org.bukkit.Material.APPLE
        || mat == org.bukkit.Material.BREAD
        || mat == org.bukkit.Material.GRILLED_PORK
        || mat == org.bukkit.Material.PORK
        || mat == org.bukkit.Material.COOKED_BEEF
        || name.equals("BEEF") || name.equals("RAW_BEEF")
        || mat == org.bukkit.Material.COOKED_CHICKEN
        || name.equals("CHICKEN") || name.equals("RAW_CHICKEN")
        || mat == org.bukkit.Material.GOLDEN_APPLE
        || name.equals("GOLDEN_APPLE_ENCHANTED") || name.equals("ENCHANTED_GOLDEN_APPLE")
        || mat == org.bukkit.Material.MUSHROOM_SOUP
        || mat == org.bukkit.Material.COOKED_MUTTON
        || mat == org.bukkit.Material.MUTTON
        || mat == org.bukkit.Material.COOKED_RABBIT
        || mat == org.bukkit.Material.RABBIT_STEW
        || name.equals("STEAK") || mat == org.bukkit.Material.COOKED_BEEF;
  }

  protected boolean hasBlocks(Player player) {
    for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
      if (item != null && isPlaceable(item.getType())) {
        return true;
      }
    }
    return false;
  }

  protected boolean isPlaceable(org.bukkit.Material mat) {
    return mat.name().endsWith("_WOOL")
        || mat == org.bukkit.Material.COBBLESTONE
        || mat == org.bukkit.Material.DIRT
        || mat == org.bukkit.Material.OBSIDIAN
        || mat == org.bukkit.Material.STONE
        || mat.name().endsWith("_TERRACOTTA")
        || mat.name().endsWith("_STAINED_CLAY");
  }

  @Override
  public int getPriority() {
    return getTaskType().getPriority();
  }

  @Override
  public void initialize(Game game, Player bot) {
    complete = false;
  }

  @Override
  public boolean isComplete(Game game, Player bot) {
    return complete;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    complete = false;
  }
}
