package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class RandomTickZeroListener implements Listener {

    public RandomTickZeroListener() {
        Bukkit.getPluginManager().registerEvents(this, BedwarsPRO.getInstance());
        for (World world : Bukkit.getWorlds()) {
            forceZero(world);
        }
        Bukkit.getScheduler().runTaskTimer(BedwarsPRO.getInstance(), () -> {
            for (World world : Bukkit.getWorlds()) {
                forceZero(world);
            }
        }, 200L, 200L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        forceZero(event.getWorld());
    }

    private void forceZero(World world) {
        try {
            world.setGameRuleValue("randomTickSpeed", "0");
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to set randomTickSpeed on world " + world.getName() + ": " + e.getMessage());
        }
    }
}