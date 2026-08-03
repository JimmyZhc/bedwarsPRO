package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PetListener implements Listener {

    public PetListener() {
        // 启动自动锁定敌方玩家的定时任务（每秒执行一次）
        new BukkitRunnable() {
            @Override
            public void run() {
                autoTargetEnemies();
            }
        }.runTaskTimer(BedwarsPRO.getInstance(), 20L, 20L);
    }

    /**
     * 让所有宠物自动锁定附近的敌方玩家
     */
    private void autoTargetEnemies() {
        if (BedwarsPRO.getInstance().getGameManager() == null) {
            return;
        }

        for (Game game : BedwarsPRO.getInstance().getGameManager().getGames()) {
            if (game == null || game.getState() != GameState.RUNNING) {
                continue;
            }
            if (game.getRegion() == null || game.getRegion().getWorld() == null) {
                continue;
            }

            // 遍历世界中的所有实体
            for (Entity entity : game.getRegion().getWorld().getEntitiesByClass(Creature.class)) {
                if (!entity.hasMetadata("owner")) {
                    continue;
                }
                if (!(entity instanceof Creature)) {
                    continue;
                }

                Creature pet = (Creature) entity;

                // 如果已经有目标且目标有效，跳过
                if (pet.getTarget() != null && pet.getTarget() instanceof Player && !pet.getTarget().isDead()) {
                    Player currentTarget = (Player) pet.getTarget();
                    // 检查当前目标是否仍然是敌方
                    if (isValidTarget(pet, currentTarget)) {
                        continue;
                    }
                }

                // 查找最近的敌方玩家
                Player nearestEnemy = findNearestEnemy(pet, game);
                if (nearestEnemy != null) {
                    pet.setTarget(nearestEnemy);
                }
            }
        }
    }

    /**
     * 查找宠物附近最近的敌方玩家
     */
    private Player findNearestEnemy(Creature pet, Game game) {
        String ownerUUID = pet.getMetadata("owner").get(0).asString();
        Player owner = BedwarsPRO.getInstance().getServer().getPlayer(java.util.UUID.fromString(ownerUUID));
        if (owner == null) {
            return null;
        }

        Team ownerTeam = game.getPlayerTeam(owner);
        if (ownerTeam == null) {
            return null;
        }

        Location petLoc = pet.getLocation();
        double maxRange = 16.0; // 16格搜索范围
        Player nearest = null;
        double nearestDist = maxRange * maxRange;

        for (Player player : game.getPlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            if (player.equals(owner)) {
                continue;
            }
            if (game.isSpectator(player)) {
                continue;
            }

            Team playerTeam = game.getPlayerTeam(player);
            if (playerTeam == null || playerTeam.equals(ownerTeam)) {
                continue;
            }

            double dist = player.getLocation().distanceSquared(petLoc);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }

        return nearest;
    }

    /**
     * 检查目标是否是有效的敌方玩家
     */
    private boolean isValidTarget(Creature pet, Player target) {
        String ownerUUID = pet.getMetadata("owner").get(0).asString();
        Player owner = BedwarsPRO.getInstance().getServer().getPlayer(java.util.UUID.fromString(ownerUUID));
        if (owner == null) {
            return false;
        }

        Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(target);
        if (game == null) {
            return false;
        }

        Game ownerGame = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(owner);
        if (ownerGame == null || !ownerGame.equals(game)) {
            return false;
        }

        if (target.equals(owner)) {
            return false;
        }

        Team ownerTeam = game.getPlayerTeam(owner);
        Team targetTeam = game.getPlayerTeam(target);
        if (targetTeam == null) {
            return false;
        }
        if (ownerTeam != null && ownerTeam.equals(targetTeam)) {
            return false;
        }

        return true;
    }

    @EventHandler
    public void onPetTarget(EntityTargetLivingEntityEvent event) {
        Entity attacker = event.getEntity();

        if (!attacker.hasMetadata("owner")) {
            return;
        }

        if (!(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        LivingEntity target = (LivingEntity) event.getTarget();

        if (target.hasMetadata("owner")) {
            cancelAndClearTarget(event, attacker);
            return;
        }

        if (!(target instanceof Player)) {
            cancelAndClearTarget(event, attacker);
            return;
        }

        Player targetPlayer = (Player) target;
        String ownerUUID = attacker.getMetadata("owner").get(0).asString();
        Player owner = BedwarsPRO.getInstance().getServer().getPlayer(java.util.UUID.fromString(ownerUUID));

        if (owner == null) {
            cancelAndClearTarget(event, attacker);
            return;
        }

        Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(targetPlayer);
        if (game == null) {
            cancelAndClearTarget(event, attacker);
            return;
        }

        Game ownerGame = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(owner);
        if (ownerGame == null || !ownerGame.equals(game)) {
            cancelAndClearTarget(event, attacker);
            return;
        }

        if (targetPlayer.equals(owner)) {
            cancelAndClearTarget(event, attacker);
            return;
        }

        Team ownerTeam = game.getPlayerTeam(owner);
        Team targetTeam = game.getPlayerTeam(targetPlayer);
        if (targetTeam == null) {
            cancelAndClearTarget(event, attacker);
            return;
        }
        if (ownerTeam != null && ownerTeam.equals(targetTeam)) {
            cancelAndClearTarget(event, attacker);
            return;
        }
        // 允许攻击敌对玩家
    }

    private void cancelAndClearTarget(EntityTargetLivingEntityEvent event, Entity attacker) {
        event.setCancelled(true);
        if (attacker instanceof Creature) {
            ((Creature) attacker).setTarget(null);
        }
    }

    // 防止宠物燃烧（骷髅在阳光下不会燃烧）
    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity().hasMetadata("owner")) {
            event.setCancelled(true);
        }
    }

    // 宠物死亡事件：清除掉落物 + 只向主人发送自定义消息
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!entity.hasMetadata("owner")) {
            return;
        }

        // 清除掉落物和经验
        event.getDrops().clear();
        event.setDroppedExp(0);

        // 获取主人
        String ownerUUID = entity.getMetadata("owner").get(0).asString();
        Player owner = BedwarsPRO.getInstance().getServer().getPlayer(java.util.UUID.fromString(ownerUUID));
        if (owner == null) return;

        // 获取击杀者
        Player killer = ((LivingEntity) entity).getKiller();
        if (killer == null) return;

        // 只向主人发送自定义消息
        String message = ChatColor.RED + "你的宠物被 " + killer.getName() + " 杀死了";
        owner.sendMessage(message);
    }
}
