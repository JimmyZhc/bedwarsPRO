package io.jmmym.bedwarspro.scoreboard;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;
import io.jmmym.bedwarspro.BedwarsPRO;
import lombok.Getter;
import io.jmmym.bedwarspro.scoreboard.addon.ChatFormat;

import io.jmmym.bedwarspro.scoreboard.addon.DeathItem;
import io.jmmym.bedwarspro.scoreboard.addon.FastRespawn;
import io.jmmym.bedwarspro.scoreboard.addon.GiveItem;
import io.jmmym.bedwarspro.scoreboard.addon.HidePlayer;
import io.jmmym.bedwarspro.scoreboard.addon.LobbyScoreBoard;
import io.jmmym.bedwarspro.scoreboard.addon.SpawnNoBuild;
import io.jmmym.bedwarspro.scoreboard.addon.Spectator;
import io.jmmym.bedwarspro.scoreboard.addon.Title;
import io.jmmym.bedwarspro.scoreboard.addon.WitherBow;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.commands.BedwarsPROCommandTabCompleter;
import io.jmmym.bedwarspro.scoreboard.commands.CommandTabCompleter;
import io.jmmym.bedwarspro.scoreboard.commands.Commands;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.config.LocaleConfig;
import io.jmmym.bedwarspro.scoreboard.edit.EditGame;
import io.jmmym.bedwarspro.scoreboard.listener.EventListener;
import io.jmmym.bedwarspro.scoreboard.listener.GameListener;
import io.jmmym.bedwarspro.scoreboard.listener.ShopListener;
import io.jmmym.bedwarspro.scoreboard.listener.XPEventListener;
import io.jmmym.bedwarspro.scoreboard.manager.ArenaManager;
import io.jmmym.bedwarspro.scoreboard.manager.EditHolographicManager;
import io.jmmym.bedwarspro.scoreboard.manager.HolographicManager;
import io.jmmym.bedwarspro.scoreboard.menu.MenuManager;
import io.jmmym.bedwarspro.scoreboard.network.UpdateCheck;

public class Main {

	@Getter
	private static Main instance;
	@Getter
	private ArenaManager arenaManager;
	@Getter
	private EditHolographicManager editHolographicManager;
	@Getter
	private HolographicManager holographicManager;
	@Getter
	private MenuManager menuManager;
	@Getter
	private LocaleConfig localeConfig;
	@Getter
	private boolean enabledCitizens;
	@Getter
	private EventListener eventListener;

	public static Plugin getPlugin() {
		return BedwarsPRO.getInstance();
	}

	public PluginDescriptionFile getDescription() {
		return BedwarsPRO.getInstance().getDescription();
	}

	public FileConfiguration getConfig() {
		return BedwarsPRO.getInstance().getConfig();
	}

	public File getDataFolder() {
		return BedwarsPRO.getInstance().getDataFolder();
	}

	public InputStream getResource(String path) {
		return BedwarsPRO.getInstance().getResource(path);
	}

	public void saveResource(String resourcePath) {
		BedwarsPRO.getInstance().saveResource(resourcePath, false);
	}

	public void saveDefaultConfig() {
		BedwarsPRO.getInstance().saveDefaultConfig();
	}

	public void reloadConfig() {
		BedwarsPRO.getInstance().reloadConfig();
	}

	public static String getVersion() {
		return "2.13.1";
	}

	public void init() {
		instance = this;
		localeConfig = new LocaleConfig();
		Main.getInstance().getLocaleConfig().loadLocaleConfig();
		File configFile = new File(BedwarsPRO.getInstance().getDataFolder(), "config.yml");
		if (!configFile.exists()) {
			BedwarsPRO.getInstance().saveResource("config.yml", false);
		}

		String expectedKey = "Modified By JmmYm";
		YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(configFile);
		String userKey = yamlConfig.getString("license-key");
		if (!expectedKey.equals(userKey)) {
			if (userKey == null) {
				yamlConfig.set("license-key", "");
				try {
					yamlConfig.save(configFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "==========================================");
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  License key error or missing! Plugin disabled.");
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  Set license-key in config.yml");
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "==========================================");
			BedwarsPRO.getInstance().getLogger().severe("License key verification failed, plugin disabled.");
			BedwarsPRO.getInstance().getServer().getPluginManager().disablePlugin(BedwarsPRO.getInstance());
			return;
		}
		BedwarsPRO.getInstance().getLogger().info("License key verified!");
		arenaManager = new ArenaManager();
		editHolographicManager = new EditHolographicManager();
		holographicManager = new HolographicManager();
		menuManager = new MenuManager();
		new BukkitRunnable() {
			@Override
			public void run() {
				if (Bukkit.getPluginManager().getPlugin("BedwarsPRO") == null || Bukkit.getPluginManager().getPlugin("Citizens") == null || Bukkit.getPluginManager().getPlugin("ProtocolLib") == null || (Bukkit.getPluginManager().isPluginEnabled("BedwarsPRO") && Bukkit.getPluginManager().isPluginEnabled("Citizens") && Bukkit.getPluginManager().isPluginEnabled("ProtocolLib"))) {
					cancel();
					doInit();
				}
			}
		}.runTaskTimer(BedwarsPRO.getInstance(), 1L, 1L);
	}

	public void shutdown() {
		if (instance == null) {
			return;
		}
		menuManager.getPlayers().forEach(player -> {
			if (player.isOnline()) {
				player.closeInventory();
			}
		});
		for (Arena arena : arenaManager.getArenas().values()) {
			arena.onDisable();
		}
		editHolographicManager.removeAll();
	}

	private void doInit() {
		Boolean debug = false;
		try {
			debug = BedwarsPRO.getInstance().getConfig().getBoolean("init_debug");
		} catch (Exception e) {
		}
		String prefix = "[" + BedwarsPRO.getInstance().getDescription().getName() + "] ";
		printMessage(prefix + getLocaleConfig().getLanguage("loading"));
		boolean isDependent = true;
		if (Bukkit.getPluginManager().getPlugin("BedwarsPRO") == null) {
			printMessage(prefix + getLocaleConfig().getLanguage("no_BedwarsPRO"));
			isDependent = false;
		}
		if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
			printMessage(prefix + getLocaleConfig().getLanguage("no_protocollib"));
			isDependent = false;
		}
		if (!isDependent) {
			printMessage(prefix + getLocaleConfig().getLanguage("loading_failed"));
			BedwarsPRO.getInstance().getServer().getPluginManager().disablePlugin(BedwarsPRO.getInstance());
			return;
		}
		enabledCitizens = Bukkit.getPluginManager().getPlugin("Citizens") != null;
		if (Bukkit.getPluginManager().isPluginEnabled("BedwarsXP")) {
			try {
				Plugin plugin = Bukkit.getPluginManager().getPlugin("BedwarsXP");
				for (RegisteredListener listener : HandlerList.getRegisteredListeners(plugin)) {
					if (listener.getListener() instanceof ldcr.BedwarsXP.EventListeners) {
						HandlerList.unregisterAll(listener.getListener());
					}
				}
				Bukkit.getPluginManager().registerEvents(new XPEventListener(), BedwarsPRO.getInstance());
			} catch (Exception e) {
				printMessage(prefix + getLocaleConfig().getLanguage("bedwarsxp"));
				printMessage(prefix + getLocaleConfig().getLanguage("loading_failed"));
				BedwarsPRO.getInstance().getServer().getPluginManager().disablePlugin(BedwarsPRO.getInstance());
				return;
			}
		}
		try {
			Config.loadConfig();
		} catch (Exception e) {
			printMessage(prefix + getLocaleConfig().getLanguage("config_failed"));
			printMessage(prefix + getLocaleConfig().getLanguage("loading_failed"));
			// 强制打印完整堆栈信息以便调试
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "========== 详细错误信息 ==========");
			e.printStackTrace();
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "===================================");
			BedwarsPRO.getInstance().getServer().getPluginManager().disablePlugin(BedwarsPRO.getInstance());
			return;
		}
		try {
			printMessage(prefix + getLocaleConfig().getLanguage("register_listener"));
			this.registerEvents();
			printMessage(prefix + getLocaleConfig().getLanguage("listener_success"));
		} catch (Exception e) {
			printMessage(prefix + getLocaleConfig().getLanguage("listener_failed"));
			printMessage(prefix + getLocaleConfig().getLanguage("loading_failed"));
			BedwarsPRO.getInstance().getServer().getPluginManager().disablePlugin(BedwarsPRO.getInstance());
			if (debug) {
				e.printStackTrace();
			}
			return;
		}
		try {
			printMessage(prefix + getLocaleConfig().getLanguage("register_command"));
			Bukkit.getPluginCommand("bedwarsscoreboardaddon").setExecutor(new Commands());
			Bukkit.getPluginCommand("bedwarsscoreboardaddon").setTabCompleter(new CommandTabCompleter());
			Bukkit.getPluginCommand("bw").setTabCompleter(new BedwarsPROCommandTabCompleter());
			printMessage(prefix + getLocaleConfig().getLanguage("command_success"));
		} catch (Exception e) {
			printMessage(prefix + getLocaleConfig().getLanguage("command_failed"));
			printMessage(prefix + getLocaleConfig().getLanguage("loading_failed"));
			BedwarsPRO.getInstance().getServer().getPluginManager().disablePlugin(BedwarsPRO.getInstance());
			if (debug) {
				e.printStackTrace();
			}
			return;
		}
		printMessage(prefix + getLocaleConfig().getLanguage("load_success"));
		try {
			BedwarsPRO.getInstance().getConfig().set("teamname-on-tab", false);
			BedwarsPRO.getInstance().saveConfig();
		} catch (Exception e) {
		}
	}

	private void printMessage(String str) {
		Bukkit.getConsoleSender().sendMessage(str);
	}

	private void registerEvents() {
		eventListener = new EventListener();
		Bukkit.getPluginManager().registerEvents(eventListener, BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new GameListener(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new LobbyScoreBoard(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new SpawnNoBuild(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new UpdateCheck(), BedwarsPRO.getInstance());
		if (enabledCitizens) {
			Bukkit.getPluginManager().registerEvents(new ShopListener(), BedwarsPRO.getInstance());
		}
		Bukkit.getPluginManager().registerEvents(new FastRespawn(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new ChatFormat(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new HidePlayer(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new WitherBow(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new DeathItem(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new Spectator(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new GiveItem(), BedwarsPRO.getInstance());
		Bukkit.getPluginManager().registerEvents(new EditGame(), BedwarsPRO.getInstance());

		Bukkit.getPluginManager().registerEvents(new Title(), BedwarsPRO.getInstance());
	}
}