package io.jmmym.bedwarspro.listener;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.game.TeamJoinMetaDataValue;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.metadata.MetadataValue;

public class EntityListener extends BaseListener {

  private static BukkitRunnable mobClearTask;

  public static void startMobClearTask() {
    if (mobClearTask != null) return;
    mobClearTask = new BukkitRunnable() {
      @Override
      public void run() {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
          for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            if (entity.hasMetadata("bw-plugin-entity")) continue;
            if (entity instanceof Monster || entity instanceof Slime) {
              if (BedwarsPRO.getInstance().getGameManager() != null) {
                Game game = BedwarsPRO.getInstance().getGameManager().getGameByLocation(entity.getLocation());
                if (game != null && game.getState() == GameState.RUNNING) {
                  entity.remove();
                }
              }
            }
          }
        }
      }
    };
    mobClearTask.runTaskTimer(BedwarsPRO.getInstance(), 200L, 200L);
  }

  public static void stopMobClearTask() {
    if (mobClearTask != null) {
      mobClearTask.cancel();
      mobClearTask = null;
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onEntityDamage(EntityDamageEvent ede) {
    List<EntityType> canDamageTypes = new ArrayList<EntityType>();
    canDamageTypes.add(EntityType.PLAYER);

    if (BedwarsPRO.getInstance().getServer().getPluginManager().isPluginEnabled("AntiAura")
        || BedwarsPRO.getInstance().getServer().getPluginManager().isPluginEnabled("AAC")) {
      canDamageTypes.add(EntityType.SQUID);
    }

    if (canDamageTypes.contains(ede.getEntityType())) {
      return;
    }

    // 宠物（有 owner metadata）可以被伤害
    if (ede.getEntity().hasMetadata("owner")) {
      return;
    }

    Game game =
        BedwarsPRO.getInstance().getGameManager().getGameByLocation(ede.getEntity().getLocation());
    if (game == null) {
      return;
    }

    if (game.getState() == GameState.STOPPED) {
      return;
    }

    ede.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onEntityDamageByEntity(EntityDamageByEntityEvent ede) {
    List<EntityType> canDamageTypes = new ArrayList<EntityType>();
    canDamageTypes.add(EntityType.PLAYER);

    if (BedwarsPRO.getInstance().getServer().getPluginManager().isPluginEnabled("AntiAura")
        || BedwarsPRO.getInstance().getServer().getPluginManager().isPluginEnabled("AAC")) {
      canDamageTypes.add(EntityType.SQUID);
    }

    if (canDamageTypes.contains(ede.getEntityType())) {
      return;
    }

    // 宠物（有 owner metadata）可以被伤害
    if (ede.getEntity().hasMetadata("owner")) {
      return;
    }

    Game game =
        BedwarsPRO.getInstance().getGameManager().getGameByLocation(ede.getEntity().getLocation());
    if (game == null) {
      return;
    }

    if (game.getState() == GameState.STOPPED) {
      return;
    }

    ede.setCancelled(true);
  }

  @EventHandler
  public void onEntityInteract(EntityInteractEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }

    if (event.getBlock().getType() != Material.SOIL
        && event.getBlock().getType() != Material.WHEAT) {
      return;
    }

    Player player = (Player) event.getEntity();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.WAITING) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntitySpawn(CreatureSpawnEvent ese) {
    if (BedwarsPRO.getInstance().getGameManager() == null) {
      return;
    }

    if (ese.getLocation() == null) {
      return;
    }

    if (ese.getLocation().getWorld() == null) {
      return;
    }

    Game game = BedwarsPRO.getInstance().getGameManager().getGameByLocation(ese.getLocation());
    if (game == null) {
      return;
    }

    // 仅游戏运行中允许刷怪；等待大厅/游戏结束一律禁止（含刷怪蛋、自定义生成）
    if (game.getState() != GameState.RUNNING) {
      ese.setCancelled(true);
      return;
    }

    // 阻止所有非自定义的生物在游戏区域内生成
    if (ese.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
        || ese.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
        || ese.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
      ese.setCancelled(true);
      return;
    }

    if (ese.getEntityType().equals(EntityType.CREEPER)
        || ese.getEntityType().equals(EntityType.CAVE_SPIDER)
        || ese.getEntityType().equals(EntityType.SPIDER)
        || ese.getEntityType().equals(EntityType.ZOMBIE)
        || ese.getEntityType().equals(EntityType.SKELETON)
        || ese.getEntityType().equals(EntityType.SILVERFISH)) {
      ese.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onExplodeDestroy(EntityExplodeEvent eev) {
    if (eev.isCancelled()) {
      return;
    }

    if (eev.getEntity() == null) {
      return;
    }

    if (eev.getEntity().getWorld() == null) {
      return;
    }

    Game game =
        BedwarsPRO.getInstance().getGameManager().getGameByLocation(eev.getEntity().getLocation());

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.STOPPED) {
      return;
    }

    Iterator<Block> explodeBlocks = eev.blockList().iterator();
    boolean tntDestroyEnabled =
        BedwarsPRO.getInstance().getBooleanConfig("explodes.destroy-worldblocks", false);
    boolean tntDestroyBeds = BedwarsPRO
        .getInstance().getBooleanConfig("explodes.destroy-beds", false);

    if (!BedwarsPRO.getInstance().getBooleanConfig("explodes.drop-blocks", false)) {
      eev.setYield(0F);
    }

    Material targetMaterial = game.getTargetMaterial();
    while (explodeBlocks.hasNext()) {
      Block exploding = explodeBlocks.next();
      if (!game.getRegion().isInRegion(exploding.getLocation())) {
        explodeBlocks.remove();
        continue;
      }

      if ((!tntDestroyEnabled && !tntDestroyBeds) || (!tntDestroyEnabled && tntDestroyBeds
          && exploding.getType() != Material.BED_BLOCK && exploding.getType() != Material.BED)) {
        if (!game.getRegion().isPlacedBlock(exploding)) {
          if (BedwarsPRO.getInstance().isBreakableType(exploding.getType())) {
            game.getRegion().addBreakedBlock(exploding);
            continue;
          }

          explodeBlocks.remove();
        } else {
          game.getRegion().removePlacedBlock(exploding);
        }

        continue;
      }

      if (game.getRegion().isPlacedBlock(exploding)) {
        game.getRegion().removePlacedBlock(exploding);
        continue;
      }

      if (exploding.getType().equals(targetMaterial)) {
        if (!tntDestroyBeds) {
          explodeBlocks.remove();
          continue;
        }

        // only destroyable by tnt
        if (!eev.getEntityType().equals(EntityType.PRIMED_TNT)
            && !eev.getEntityType().equals(EntityType.MINECART_TNT)) {
          explodeBlocks.remove();
          continue;
        }

        // when it wasn't player who ignited the tnt
        TNTPrimed primedTnt = (TNTPrimed) eev.getEntity();
        if (!(primedTnt.getSource() instanceof Player)) {
          explodeBlocks.remove();
          continue;
        }

        Player p = (Player) primedTnt.getSource();
        if (!game.handleDestroyTargetMaterial(p, exploding)) {
          explodeBlocks.remove();
          continue;
        }
      } else {
        game.getRegion().addBreakedBlock(exploding);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInteractEntity(PlayerInteractAtEntityEvent event) {
    if (event.getRightClicked() == null) {
      return;
    }

    Entity entity = event.getRightClicked();
    Player player = event.getPlayer();
    if (!player.hasMetadata("bw-addteamjoin")) {
      if (!(entity instanceof LivingEntity)) {
        return;
      }

      LivingEntity livEntity = (LivingEntity) entity;
      Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
      if (game == null) {
        return;
      }

      if (game.getState() != GameState.WAITING) {
        return;
      }

      Team team = game.getTeam(ChatColor.stripColor(livEntity.getCustomName()));
      if (team == null) {
        return;
      }

      game.playerJoinTeam(player, team);
      event.setCancelled(true);
      return;
    }

    List<MetadataValue> values = player.getMetadata("bw-addteamjoin");
    if (values == null || values.size() == 0) {
      return;
    }

    event.setCancelled(true);
    TeamJoinMetaDataValue value = (TeamJoinMetaDataValue) values.get(0);
    if (!((boolean) value.value())) {
      return;
    }

    if (!(entity instanceof LivingEntity)) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
              ._l(player, "errors.entitynotcompatible")));
      return;
    }

    LivingEntity living = (LivingEntity) entity;
    living.setRemoveWhenFarAway(false);
    living.setCanPickupItems(false);
    living.setCustomName(value.getTeam().getChatColor() + value.getTeam().getDisplayName());
    living.setCustomNameVisible(
        BedwarsPRO.getInstance().getBooleanConfig("jointeam-entity.show-name", true));

    if (living.getType().equals(EntityType.valueOf("ARMOR_STAND"))) {
      Utils.equipArmorStand(living, value.getTeam());
    }

    player.removeMetadata("bw-addteamjoin", BedwarsPRO.getInstance());
    player.sendMessage(ChatWriter
        .pluginMessage(
            ChatColor.GREEN + BedwarsPRO._l(player, "success.teamjoinadded", ImmutableMap.of("team",
                value.getTeam().getChatColor() + value.getTeam().getDisplayName()
                    + ChatColor.GREEN))));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onRegainHealth(EntityRegainHealthEvent rhe) {
    if (rhe.getEntityType() != EntityType.PLAYER) {
      return;
    }

    Player player = (Player) rhe.getEntity();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() != GameState.RUNNING) {
      return;
    }

    if (player.getHealth() >= player.getMaxHealth()) {
      game.setPlayerDamager(player, null);
    }
  }
}
