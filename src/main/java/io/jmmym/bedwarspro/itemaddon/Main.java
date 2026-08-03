package io.jmmym.bedwarspro.itemaddon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.itemaddon.command.CommandTabCompleter;
import io.jmmym.bedwarspro.itemaddon.command.Commands;
import io.jmmym.bedwarspro.itemaddon.config.Config;
import io.jmmym.bedwarspro.itemaddon.config.LocaleConfig;
import io.jmmym.bedwarspro.itemaddon.items.BridgeEgg;
import io.jmmym.bedwarspro.itemaddon.items.CompactTower;
import io.jmmym.bedwarspro.itemaddon.items.EnderPearlChair;
import io.jmmym.bedwarspro.itemaddon.items.ExplosionProof;
import io.jmmym.bedwarspro.itemaddon.items.FireBall;
import io.jmmym.bedwarspro.itemaddon.items.LightTNT;
import io.jmmym.bedwarspro.itemaddon.items.MagicMilk;
import io.jmmym.bedwarspro.itemaddon.items.Parachute;
import io.jmmym.bedwarspro.itemaddon.items.TNTLaunch;
import io.jmmym.bedwarspro.itemaddon.items.TeamIronGolem;
import io.jmmym.bedwarspro.itemaddon.items.TeamSilverFish;
import io.jmmym.bedwarspro.itemaddon.items.Trampoline;
import io.jmmym.bedwarspro.itemaddon.items.WalkPlatform;
import io.jmmym.bedwarspro.itemaddon.listener.EventListener;
import io.jmmym.bedwarspro.itemaddon.manage.NoFallManage;
import io.jmmym.bedwarspro.itemaddon.network.UpdateCheck;

public class Main {

    private static Main instance;
    private BedwarsPRO plugin;
    private NoFallManage noFallManage;
    private LocaleConfig localeConfig;

    public static Main getInstance() {
        return instance;
    }

    public NoFallManage getNoFallManage() {
        return noFallManage;
    }

    public LocaleConfig getLocaleConfig() {
        return localeConfig;
    }

    public BedwarsPRO getPlugin() {
        return plugin;
    }

    public static BedwarsPRO getPluginInstance() {
        return instance != null ? instance.plugin : null;
    }

    public static String getVersion() {
        return "1.7.0";
    }

    public FileConfiguration getConfig() {
        FileConfiguration config = Config.getConfig();
        return config == null ? plugin.getConfig() : config;
    }

    public void init(BedwarsPRO plugin) {
        this.plugin = plugin;
        instance = this;
        noFallManage = new NoFallManage();
        localeConfig = new LocaleConfig();
        getLocaleConfig().loadLocaleConfig();
        // Banner disabled
        Config.loadConfig();
        plugin.getCommand("bedwarsitemaddon").setExecutor(new Commands());
        plugin.getCommand("bedwarsitemaddon").setTabCompleter(new CommandTabCompleter());
        registerEvents();
    }

    public void onLoad() {
        try {
            Path path = Paths.get(plugin.getDataFolder().getParentFile().getAbsolutePath()).getParent().resolve("server.properties");
            boolean reboot = false;
            List<String> lines = Files.readAllLines(path);
            if (lines.contains("allow-flight=false")) {
                lines.remove("allow-flight=false");
                lines.add("allow-flight=true");
                reboot = true;
            }
            if (lines.contains("spawn-animals=false")) {
                lines.remove("spawn-animals=false");
                lines.add("spawn-animals=true");
                reboot = true;
            }
            Files.write(path, lines, StandardOpenOption.TRUNCATE_EXISTING);
            if (reboot) {
                Bukkit.shutdown();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new EventListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new UpdateCheck(), plugin);
        Bukkit.getPluginManager().registerEvents(new FireBall(), plugin);
        Bukkit.getPluginManager().registerEvents(new LightTNT(), plugin);
        Bukkit.getPluginManager().registerEvents(new BridgeEgg(), plugin);
        Bukkit.getPluginManager().registerEvents(new Parachute(), plugin);
        Bukkit.getPluginManager().registerEvents(new TNTLaunch(), plugin);
        Bukkit.getPluginManager().registerEvents(new MagicMilk(), plugin);
        Bukkit.getPluginManager().registerEvents(new Trampoline(), plugin);
        Bukkit.getPluginManager().registerEvents(new CompactTower(), plugin);
        Bukkit.getPluginManager().registerEvents(new WalkPlatform(), plugin);
        Bukkit.getPluginManager().registerEvents(new TeamIronGolem(), plugin);
        Bukkit.getPluginManager().registerEvents(new TeamSilverFish(), plugin);
        Bukkit.getPluginManager().registerEvents(new ExplosionProof(), plugin);
        Bukkit.getPluginManager().registerEvents(new EnderPearlChair(), plugin);
    }

    public void onDisable() {
        HandlerList.unregisterAll(plugin);
        instance = null;
    }
}