package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

public class WorldProtectionListener implements Listener {

  private static final String PROTECTED_WORLD = "world";
  private static final String BUILD_PERMISSION = "bwpro.build";

  public WorldProtectionListener() {
    BedwarsPRO.getInstance().getServer().getPluginManager()
        .registerEvents(this, BedwarsPRO.getInstance());
  }

  private boolean canBuild(Player player) {
    return player.getGameMode() == GameMode.CREATIVE
        && player.hasPermission(BUILD_PERMISSION);
  }

  private boolean isProtected(Player player) {
    return player.getWorld().getName().equalsIgnoreCase(PROTECTED_WORLD)
        && !canBuild(player);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onBreak(BlockBreakEvent event) {
    if (isProtected(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlace(BlockPlaceEvent event) {
    if (isProtected(event.getPlayer())) {
      event.setCancelled(true);
      event.setBuild(false);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onBucketEmpty(PlayerBucketEmptyEvent event) {
    if (isProtected(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onBucketFill(PlayerBucketFillEvent event) {
    if (isProtected(event.getPlayer())) {
      event.setCancelled(true);
    }
  }
}
