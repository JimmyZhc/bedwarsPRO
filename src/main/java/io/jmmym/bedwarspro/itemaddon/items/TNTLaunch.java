package io.jmmym.bedwarspro.itemaddon.items;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameStartEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.itemaddon.Main;
import io.jmmym.bedwarspro.itemaddon.config.Config;
import io.jmmym.bedwarspro.itemaddon.event.BedwarsUseItemEvent;
import io.jmmym.bedwarspro.itemaddon.utils.LocationUtil;
import io.jmmym.bedwarspro.itemaddon.utils.TakeItemUtil;

public class TNTLaunch implements Listener {
    private final Map<Player, Long> cooldown = new HashMap<>();

    @EventHandler
    public void onStart(BedwarsGameStartEvent e) {
        for (Player player : e.getGame().getPlayers()) {
            cooldown.remove(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!Config.items_tnt_launch_enabled) {
            return;
        }
        Player player = e.getPlayer();
        Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
        if (e.getItem() == null || game == null) {
            return;
        }
        if (!game.getPlayers().contains(player)) {
            return;
        }
        if (game.getState() == GameState.RUNNING) {
            if ((e.getAction().equals(Action.RIGHT_CLICK_AIR) || e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) && e.getItem().getType() == Material.valueOf(Config.items_tnt_launch_item)) {
                if ((System.currentTimeMillis() - cooldown.getOrDefault(player, (long) 0)) <= Config.items_tnt_launch_cooldown * 1000) {
                    e.setCancelled(true);
                    player.sendMessage(Config.message_cooling.replace("{time}", String.format("%.1f", (((Config.items_tnt_launch_cooldown * 1000 - System.currentTimeMillis() + cooldown.getOrDefault(player, (long) 0)) / 1000))) + ""));
                } else {
                    ItemStack stack = e.getItem();
                    BedwarsUseItemEvent bedwarsUseItemEvent = new BedwarsUseItemEvent(game, player, EnumItem.TNT_LAUNCH, stack);
                    Bukkit.getPluginManager().callEvent(bedwarsUseItemEvent);
                    if (!bedwarsUseItemEvent.isCancelled()) {
                        cooldown.put(player, System.currentTimeMillis());
                        TNTPrimed tnt = player.getWorld().spawn(player.getLocation().clone().add(0, 1, 0), TNTPrimed.class);
                        tnt.setYield((float) Config.items_tnt_launch_range);
                        tnt.setIsIncendiary(false);
                        tnt.setVelocity(player.getLocation().getDirection().multiply(Config.items_tnt_launch_launch_velocity));
                        tnt.setFuseTicks(Config.items_tnt_launch_fuse_ticks);
                        tnt.setMetadata("TNTLaunch", new FixedMetadataValue(BedwarsPRO.getInstance(), game.getName() + "." + player.getName()));
                        TakeItemUtil.TakeItem(player, stack);
                    }
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!Config.items_tnt_launch_enabled) {
            return;
        }
        Entity damager = e.getDamager();
        if (!damager.hasMetadata("TNTLaunch")) {
            return;
        }
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player) entity;
        Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
        if (game == null) {
            return;
        }
        if (damager instanceof TNTPrimed) {
            if (!game.getPlayers().contains(player)) {
                return;
            }
            if (game.isSpectator(player)) {
                return;
            }
            if (game.getState() == GameState.RUNNING) {
                if (Config.items_tnt_launch_ejection_enabled) {
                    player.setVelocity(LocationUtil.getPosition(player.getLocation(), damager.getLocation(), 1).multiply(Config.items_tnt_launch_ejection_velocity));
                    if (Config.items_tnt_launch_ejection_no_fall) {
                        Main.getInstance().getNoFallManage().addPlayer(player);
                    }
                }
                e.setDamage(Config.items_tnt_launch_damage);
            }
        }
    }
}