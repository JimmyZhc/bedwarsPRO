package io.jmmym.bedwarspro.itemaddon.items;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameStartedEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerLeaveEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.itemaddon.Main;
import io.jmmym.bedwarspro.itemaddon.config.Config;
import io.jmmym.bedwarspro.itemaddon.event.BedwarsUseItemEvent;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;

public class MagicMilk implements Listener {
    private final Map<Player, Long> cooldown;
    private final Map<String, Map<Player, Long>> immune_players;

    public MagicMilk() {
        cooldown = new HashMap<>();
        immune_players = new HashMap<>();
        if (io.jmmym.bedwarspro.scoreboard.Main.getInstance() == null) {
            return;
        }
        Bukkit.getScheduler().runTaskTimer(BedwarsPRO.getInstance(), () -> {
            immune_players.forEach((game, map) -> {
                Iterator<Entry<Player, Long>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<Player, Long> entry = iterator.next();
                    if ((System.currentTimeMillis() - entry.getValue()) >= Config.items_magic_milk_duration * 1000) {
                        Player player = entry.getKey();
                        Arena arena = io.jmmym.bedwarspro.scoreboard.Main.getInstance().getArenaManager().getArena(game);
                        if (arena != null) {
                            arena.getTeamShop().removeImmunePlayer(player);
                        }
                        iterator.remove();
                        player.sendMessage(Config.getLanguage("item.magic_milk_off"));
                    }
                }
            });
        }, 10, 10);
    }

    @EventHandler
    public void onStart(BedwarsGameStartedEvent e) {
        Game game = e.getGame();
        for (Player player : game.getPlayers()) {
            if (cooldown.containsKey(player)) {
                cooldown.remove(player);
            }
        }
        immune_players.put(game.getName(), new HashMap<>());
    }

    @EventHandler
    public void onPlayerLeave(BedwarsPlayerLeaveEvent e) {
        Player player = e.getPlayer();
        Game game = e.getGame();
        if (!immune_players.containsKey(game.getName()) || !immune_players.get(game.getName()).containsKey(player)) {
            return;
        }
        immune_players.get(game.getName()).remove(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
        if (game == null || game.getState() != GameState.RUNNING || game.isSpectator(player)) {
            return;
        }
        Team team = game.getPlayerTeam(player);
        if (team == null || !team.isDead(game)) {
            return;
        }
        if (!immune_players.containsKey(game.getName()) || !immune_players.get(game.getName()).containsKey(player)) {
            return;
        }
        immune_players.get(game.getName()).remove(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerItemConsume(PlayerItemConsumeEvent e) {
        Player player = e.getPlayer();
        if (!Config.items_magic_milk_enabled) {
            return;
        }
        ItemStack item = e.getItem();
        if (item == null || !item.getType().equals(Material.MILK_BUCKET)) {
            return;
        }
        Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
        if (game == null || game.getState() != GameState.RUNNING || game.isSpectator(player)) {
            return;
        }
        e.setCancelled(true);
        if (io.jmmym.bedwarspro.scoreboard.Main.getInstance() == null) {
            return;
        }
        if ((System.currentTimeMillis() - cooldown.getOrDefault(player, (long) 0)) <= Config.items_magic_milk_cooldown * 1000) {
            player.sendMessage(Config.message_cooling.replace("{time}", String.format("%.1f", (((Config.items_magic_milk_cooldown * 1000 - System.currentTimeMillis() + cooldown.getOrDefault(player, (long) 0)) / 1000))) + ""));
            return;
        }
        cooldown.put(player, System.currentTimeMillis());
        Arena arena = io.jmmym.bedwarspro.scoreboard.Main.getInstance().getArenaManager().getArena(game.getName());
        if (arena == null) {
            return;
        }
        BedwarsUseItemEvent bedwarsUseItemEvent = new BedwarsUseItemEvent(game, player, EnumItem.MAGIC_MILK, item);
        Bukkit.getPluginManager().callEvent(bedwarsUseItemEvent);
        if (bedwarsUseItemEvent.isCancelled()) {
            return;
        }
        if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
            player.setItemInHand(new ItemStack(Material.AIR));
        } else {
            ItemStack itemStack = player.getInventory().getItemInMainHand();
            if (itemStack != null && itemStack.getType().equals(Material.MILK_BUCKET)) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
        }
        arena.getTeamShop().addImmunePlayer(player);
        immune_players.get(game.getName()).put(player, System.currentTimeMillis());
    }
}