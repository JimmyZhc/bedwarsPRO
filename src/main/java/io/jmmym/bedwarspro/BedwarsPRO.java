package io.jmmym.bedwarspro;

import com.bugsnag.Bugsnag;
import com.bugsnag.Report;
import com.bugsnag.callbacks.Callback;
import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.commands.*;
import io.jmmym.bedwarspro.bot.BotConfig;
import io.jmmym.bedwarspro.bot.BotManager;
import io.jmmym.bedwarspro.database.DatabaseManager;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameManager;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.ResourceSpawner;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.listener.*;
import io.jmmym.bedwarspro.localization.LocalizationConfig;
import io.jmmym.bedwarspro.shop.Specials.SpecialItem;
import io.jmmym.bedwarspro.statistics.PlayerStatistic;
import io.jmmym.bedwarspro.statistics.PlayerStatisticManager;
import io.jmmym.bedwarspro.statistics.StorageType;
import io.jmmym.bedwarspro.task.TaskManager;
import io.jmmym.bedwarspro.task.TaskMessages;
import io.jmmym.bedwarspro.updater.ConfigUpdater;
import io.jmmym.bedwarspro.updater.PluginUpdater;
import io.jmmym.bedwarspro.updater.PluginUpdater.UpdateCallback;
import io.jmmym.bedwarspro.updater.PluginUpdater.UpdateResult;
import io.jmmym.bedwarspro.utils.BStatsMetrics;
import io.jmmym.bedwarspro.utils.BedwarsCommandExecutor;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.McStatsMetrics;
import io.jmmym.bedwarspro.utils.SupportData;
import io.jmmym.bedwarspro.utils.Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.ScoreboardManager;

public class BedwarsPRO extends JavaPlugin {

  public static int PROJECT_ID = 91743;
  private static BedwarsPRO instance = null;
  private static Boolean locationSerializable = null;
  private List<Material> breakableTypes = null;
  @Getter
  private Bugsnag bugsnag;
  private ArrayList<BaseCommand> commands = new ArrayList<>();
  private Package craftbukkit = null;
  private DatabaseManager dbManager = null;
  @Getter
  private GameManager gameManager = null;
  private IHologramInteraction holographicInteraction = null;
  private boolean isSpigot = false;
  @Getter
  private HashMap<String, LocalizationConfig> localization = new HashMap<>();
  private Package minecraft = null;
  @Getter
  private HashMap<UUID, String> playerLocales = new HashMap<>();
  private PlayerStatisticManager playerStatisticManager = null;
  private ScoreboardManager scoreboardManager = null;
  private YamlConfiguration shopConfig = null;
  private BukkitTask timeTask = null;
  private BukkitTask updateChecker = null;
  private String version = null;
  private io.jmmym.bedwarspro.scoreboard.Main scoreboardAddon;
  private io.jmmym.bedwarspro.itemaddon.Main itemAddon;
  @Getter
  private TaskManager taskManager = null;
  @Getter
  private BotManager botManager = null;
  @Getter
  private BotConfig botConfig = null;

  public static String _l(CommandSender commandSender, String key, String singularValue,
                          Map<String, String> params) {
    return BedwarsPRO
            ._l(BedwarsPRO.getInstance().getSenderLocale(commandSender), key, singularValue, params);
  }

  public static String _l(String locale, String key, String singularValue,
                          Map<String, String> params) {
    if ("1".equals(params.get(singularValue))) {
      return BedwarsPRO._l(locale, key + "-one", params);
    }
    return BedwarsPRO._l(locale, key, params);
  }

  public static String _l(CommandSender commandSender, String key, Map<String, String> params) {
    return BedwarsPRO._l(BedwarsPRO.getInstance().getSenderLocale(commandSender), key, params);
  }

  public static String _l(String locale, String key, Map<String, String> params) {
    if (!BedwarsPRO.getInstance().localization.containsKey(locale)) {
      BedwarsPRO.getInstance().loadLocalization(locale);
    }
    return (String) BedwarsPRO.getInstance().getLocalization().get(locale).get(key, params);
  }

  public static String _l(CommandSender commandSender, String key) {
    return BedwarsPRO._l(BedwarsPRO.getInstance().getSenderLocale(commandSender), key);
  }

  public static String _l(String key) {
    return BedwarsPRO._l(BedwarsPRO.getInstance().getConfig().getString("locale"), key);
  }

  public static String _l(String locale, String key) {
    if (!BedwarsPRO.getInstance().localization.containsKey(locale)) {
      BedwarsPRO.getInstance().loadLocalization(locale);
    }
    return (String) BedwarsPRO.getInstance().getLocalization().get(locale).get(key);
  }

  public static BedwarsPRO getInstance() {
    return BedwarsPRO.instance;
  }

  public boolean allPlayersBackToMainLobby() {
    if (this.getConfig().contains("endgame.all-players-to-mainlobby")
            && this.getConfig().isBoolean("endgame.all-players-to-mainlobby")) {
      return this.getConfig().getBoolean("endgame.all-players-to-mainlobby");
    }

    return false;

  }

  private void checkUpdates() {
    try {
      if (this.getBooleanConfig("check-updates", true)) {
        this.updateChecker = new BukkitRunnable() {

          @Override
          public void run() {
            final BukkitRunnable task = this;
            UpdateCallback callback = new UpdateCallback() {

              @Override
              public void onFinish(PluginUpdater updater) {
                if (updater.getResult() == UpdateResult.SUCCESS) {
                  task.cancel();
                }
              }
            };

            new PluginUpdater(
                    BedwarsPRO.getInstance(), BedwarsPRO.PROJECT_ID, BedwarsPRO.getInstance().getFile(),
                    PluginUpdater.UpdateType.DEFAULT, callback,
                    BedwarsPRO.getInstance().getBooleanConfig("update-infos", true));
          }

        }.runTaskTimerAsynchronously(BedwarsPRO.getInstance(), 40L, 36000L);
      }
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      this.getServer().getConsoleSender().sendMessage(
              ChatWriter.pluginMessage(ChatColor.RED + "Check for updates not successful: Error!"));
    }
  }


  private void disableBugsnag() {
    this.bugsnag.addCallback(new Callback() {
      @Override
      public void beforeNotify(Report report) {
        report.cancel();
      }
    });
  }

  public void dispatchRewardCommands(List<String> commands, Map<String, String> replacements) {
    for (String command : commands) {
      command = command.trim();
      if ("".equals(command)) {
        continue;
      }

      if ("none".equalsIgnoreCase(command)) {
        break;
      }

      if (command.startsWith("/")) {
        command = command.substring(1);
      }

      for (Entry<String, String> entry : replacements.entrySet()) {
        command = command.replace(entry.getKey(), entry.getValue());
      }

      BedwarsPRO.getInstance().getServer()
              .dispatchCommand(BedwarsPRO.getInstance().getServer().getConsoleSender(), command);
    }
  }

  private void enableBugsnag() {
    this.bugsnag.addCallback(new Callback() {
      @Override
      public void beforeNotify(Report report) {
        Boolean shouldBeSent = false;
        for (StackTraceElement stackTraceElement : report.getException().getStackTrace()) {
          if (stackTraceElement.toString().contains("io.jmmym.bedwarspro.BedwarsPRO")) {
            shouldBeSent = true;
            break;
          }
        }
        if (!shouldBeSent) {
          report.cancel();
        }

        report.setUserId(SupportData.getIdentifier());
        if (!SupportData.getPluginVersionBuild().equalsIgnoreCase("unknown")) {
          report.addToTab("Server", "Version Build",
                  BedwarsPRO.getInstance().getDescription().getVersion() + " "
                          + SupportData.getPluginVersionBuild());
        }
        report.addToTab("Server", "Version", SupportData.getServerVersion());
        report.addToTab("Server", "Version Bukkit", SupportData.getBukkitVersion());
        report.addToTab("Server", "Server Mode", SupportData.getServerMode());
        report.addToTab("Server", "Plugins", SupportData.getPlugins());
      }
    });
  }

  private ArrayList<BaseCommand> filterCommandsByPermission(ArrayList<BaseCommand> commands,
                                                            String permission) {
    Iterator<BaseCommand> it = commands.iterator();

    while (it.hasNext()) {
      BaseCommand command = it.next();
      if (!command.getPermission().equals(permission)) {
        it.remove();
      }
    }

    return commands;
  }

  public List<String> getAllowedCommands() {
    FileConfiguration config = this.getConfig();
    if (config.contains("allowed-commands") && config.isList("allowed-commands")) {
      return config.getStringList("allowed-commands");
    }

    return new ArrayList<String>();
  }

  @SuppressWarnings("unchecked")
  public ArrayList<BaseCommand> getBaseCommands() {
    ArrayList<BaseCommand> commands = (ArrayList<BaseCommand>) this.commands.clone();
    commands = this.filterCommandsByPermission(commands, "base");

    return commands;
  }

  public boolean getBooleanConfig(String key, boolean defaultBool) {
    FileConfiguration config = this.getConfig();
    if (config.contains(key) && config.isBoolean(key)) {
      return config.getBoolean(key);
    }
    return defaultBool;
  }

  public String getBungeeHub() {
    if (this.getConfig().contains("bungeecord.hubserver")) {
      return this.getConfig().getString("bungeecord.hubserver");
    }

    return null;
  }

  public ArrayList<BaseCommand> getCommands() {
    return this.commands;
  }

  @SuppressWarnings("unchecked")
  public ArrayList<BaseCommand> getCommandsByPermission(String permission) {
    ArrayList<BaseCommand> commands = (ArrayList<BaseCommand>) this.commands.clone();
    commands = this.filterCommandsByPermission(commands, permission);

    return commands;
  }

  public Package getCraftBukkit() {
    try {
      if (this.craftbukkit == null) {
        return Package.getPackage("org.bukkit.craftbukkit."
                + Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3]);
      } else {
        return this.craftbukkit;
      }
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED
              + BedwarsPRO._l(this.getServer().getConsoleSender(), "errors.packagenotfound",
              ImmutableMap.of("package", "craftbukkit"))));
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  public Class getCraftBukkitClass(String classname) {
    try {
      if (this.craftbukkit == null) {
        this.craftbukkit = this.getCraftBukkit();
      }

      return Class.forName(this.craftbukkit.getName() + "." + classname);
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      this.getServer().getConsoleSender()
              .sendMessage(ChatWriter.pluginMessage(
                      ChatColor.RED + BedwarsPRO
                              ._l(this.getServer().getConsoleSender(), "errors.classnotfound",
                                      ImmutableMap.of("package", "craftbukkit", "class", classname))));
      return null;
    }
  }

  public String getCurrentVersion() {
    return this.version;
  }

  public DatabaseManager getDatabaseManager() {
    return this.dbManager;
  }

  public String getFallbackLocale() {
    return "en_US";
  }

  public IHologramInteraction getHolographicInteractor() {
    return this.holographicInteraction;
  }

  public int getIntConfig(String key, int defaultInt) {
    FileConfiguration config = this.getConfig();
    if (config.contains(key) && config.isInt(key)) {
      return config.getInt(key);
    }
    return defaultInt;
  }

  private boolean getIsSpigot() {
    try {
      Package spigotPackage = Package.getPackage("org.spigotmc");
      return (spigotPackage != null);
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
    }

    return false;
  }

  /**
   * Returns the max length of a game in seconds
   *
   * @return The length of the game in seconds
   */
  public int getMaxLength() {
    if (this.getConfig().contains("gamelength") && this.getConfig().isInt("gamelength")) {
      return this.getConfig().getInt("gamelength") * 60;
    }

    // fallback time is 60 minutes
    return 60 * 60;
  }

  public Package getMinecraftPackage() {
    try {
      if (this.minecraft == null) {
        return Package.getPackage("net.minecraft.server."
                + Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3]);
      } else {
        return this.minecraft;
      }
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED
              + BedwarsPRO._l(this.getServer().getConsoleSender(), "errors.packagenotfound",
              ImmutableMap.of("package", "minecraft server"))));
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  public Class getMinecraftServerClass(String classname) {
    try {
      if (this.minecraft == null) {
        this.minecraft = this.getMinecraftPackage();
      }

      return Class.forName(this.minecraft.getName() + "." + classname);
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      this.getServer().getConsoleSender()
              .sendMessage(ChatWriter.pluginMessage(
                      ChatColor.RED + BedwarsPRO
                              ._l(this.getServer().getConsoleSender(), "errors.classnotfound",
                                      ImmutableMap.of("package", "minecraft server", "class", classname))));
      return null;
    }
  }

  public String getMissingHoloDependency() {
    if (!BedwarsPRO.getInstance().isHologramsEnabled()) {
      String missingHoloDependency = null;
      if (this.getServer().getPluginManager().isPluginEnabled("HologramAPI")
              || this.getServer().getPluginManager().isPluginEnabled("HolographicDisplays")) {
        if (this.getServer().getPluginManager().isPluginEnabled("HologramAPI")) {
          missingHoloDependency = "PacketListenerApi";
          return missingHoloDependency;
        }
        if (this.getServer().getPluginManager().isPluginEnabled("HolographicDisplays")) {
          missingHoloDependency = "ProtocolLib";
          return missingHoloDependency;
        }
      } else {
        missingHoloDependency = "HolographicDisplays and ProtocolLib";
        return missingHoloDependency;
      }
    }
    return null;
  }

  public PlayerStatisticManager getPlayerStatisticManager() {
    return this.playerStatisticManager;
  }

  public Integer getRespawnProtectionTime() {
    FileConfiguration config = this.getConfig();
    if (config.contains("respawn-protection") && config.isInt("respawn-protection")) {
      return config.getInt("respawn-protection");
    }
    return 0;
  }

  public ScoreboardManager getScoreboardManager() {
    return this.scoreboardManager;
  }

  public String getSenderLocale(CommandSender commandSender) {
    String locale = BedwarsPRO.getInstance().getConfig().getString("locale");
    if (commandSender instanceof Player) {
      Player player = (Player) commandSender;
      if (BedwarsPRO.getInstance().getPlayerLocales().containsKey(player.getUniqueId())) {
        locale = BedwarsPRO.getInstance().getPlayerLocales().get(player.getUniqueId());
      }
    }
    return locale;
  }

  @SuppressWarnings("unchecked")
  public ArrayList<BaseCommand> getSetupCommands() {
    ArrayList<BaseCommand> commands = (ArrayList<BaseCommand>) this.commands.clone();
    commands = this.filterCommandsByPermission(commands, "setup");

    return commands;
  }

  public FileConfiguration getShopConfig() {
    return this.shopConfig;
  }

  public StorageType getStatisticStorageType() {
    String storage = this.getStringConfig("statistics.storage", "yaml");
    return StorageType.getByName(storage);
  }

  public String getStringConfig(String key, String defaultString) {
    FileConfiguration config = this.getConfig();
    if (config.contains(key) && config.isString(key)) {
      return config.getString(key);
    }
    return defaultString;
  }

  public Class<?> getVersionRelatedClass(String className) {
    try {
      Class<?> clazz = Class.forName(
              "io.jmmym.bedwarspro.com." + this.getCurrentVersion().toLowerCase() + "." + className);
      return clazz;
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      this.getServer().getConsoleSender()
              .sendMessage(ChatWriter.pluginMessage(ChatColor.RED
                      + "Couldn't find version related class io.jmmym.bedwarspro.com."
                      + this.getCurrentVersion() + "." + className));
    }

    return null;
  }

  public String getYamlDump(YamlConfiguration config) {
    try {
      String fullstring = config.saveToString();
      String endstring = fullstring;
      endstring = Utils.unescape_perl_string(fullstring);

      return endstring;
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      ex.printStackTrace();
    }

    return null;
  }

  public boolean isBreakableType(Material type) {
    return ((BedwarsPRO.getInstance().getConfig().getBoolean("breakable-blocks.use-as-blacklist")
            && !this.breakableTypes.contains(type))
            || (!BedwarsPRO.getInstance().getConfig().getBoolean("breakable-blocks.use-as-blacklist")
            && this.breakableTypes.contains(type)));
  }

  public boolean isBungee() {
    return this.getConfig().getBoolean("bungeecord.enabled");
  }

  public boolean isHologramsEnabled() {
    return (this.getServer().getPluginManager().isPluginEnabled("HologramAPI")
            && this.getServer().getPluginManager().isPluginEnabled("PacketListenerApi"))
            || (this.getServer().getPluginManager().isPluginEnabled("HolographicDisplays")
            && this.getServer().getPluginManager().isPluginEnabled("ProtocolLib"));
  }

  public boolean isLocationSerializable() {
    if (BedwarsPRO.locationSerializable == null) {
      try {
        Location.class.getMethod("serialize");
        BedwarsPRO.locationSerializable = true;
      } catch (Exception ex) {
        BedwarsPRO.getInstance().getBugsnag().notify(ex);
        BedwarsPRO.locationSerializable = false;
      }
    }

    return BedwarsPRO.locationSerializable;
  }

  public boolean isMineshafterPresent() {
    try {
      Class.forName("mineshafter.MineServer");
      return true;
    } catch (Exception e) {
      // NO ERROR
      return false;
    }
  }

  public boolean isSpigot() {
    return this.isSpigot;
  }

  public void loadConfigInUTF() {
    File configFile = new File(this.getDataFolder(), "config.yml");
    if (!configFile.exists()) {
      return;
    }

    try {
      BufferedReader reader =
              new BufferedReader(new InputStreamReader(new FileInputStream(configFile), "UTF-8"));
      this.getConfig().load(reader);
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      e.printStackTrace();
    }

    if (this.getConfig() == null) {
      return;
    }

    // load breakable materials
    this.breakableTypes = new ArrayList<Material>();
    for (String material : this.getConfig().getStringList("breakable-blocks.list")) {
      if (material.equalsIgnoreCase("none")) {
        continue;
      }

      Material mat = Utils.parseMaterial(material);
      if (mat == null) {
        continue;
      }

      if (this.breakableTypes.contains(mat)) {
        continue;
      }

      this.breakableTypes.add(mat);
    }
  }

  private void loadDatabase() {
    if (!this.getBooleanConfig("statistics.enabled", false)
            || !"database".equals(this.getStringConfig("statistics.storage", "yaml"))) {
      return;
    }

    this.getServer().getConsoleSender()
            .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "Initialize database ..."));

    String host = this.getStringConfig("database.host", null);
    int port = this.getIntConfig("database.port", 3306);
    String user = this.getStringConfig("database.user", null);
    String password = this.getStringConfig("database.password", null);
    String db = this.getStringConfig("database.db", null);
    String tablePrefix = this.getStringConfig("database.table-prefix", "bw_");

    if (host == null || user == null || password == null || db == null) {
      return;
    }

    this.dbManager = new DatabaseManager(host, port, user, password, db, tablePrefix);
    this.dbManager.initialize();

    this.getServer().getConsoleSender()
            .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "Update database ..."));

    this.getServer().getConsoleSender()
            .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "Done."));
  }

  private void loadLocalization(String locale) {
    if (!this.localization.containsKey(locale)) {
      this.localization.put(locale, new LocalizationConfig(locale));
    }
  }

  public void loadShop() {
    File file = new File(BedwarsPRO.getInstance().getDataFolder(), "shop.yml");
    if (!file.exists()) {
      // create default file
      this.saveResource("shop.yml", false);

      // wait until it's really saved
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        BedwarsPRO.getInstance().getBugsnag().notify(e);
        e.printStackTrace();
      }
    }

    this.shopConfig = new YamlConfiguration();

    try {
      BufferedReader reader =
              new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
      this.shopConfig.load(reader);
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      this.getServer().getConsoleSender().sendMessage(
              ChatWriter.pluginMessage(ChatColor.RED + "Couldn't load shop! Error in parsing shop!"));
      e.printStackTrace();
    }
  }

  private void loadStatistics() {
    this.playerStatisticManager = new PlayerStatisticManager();
    this.playerStatisticManager.initialize();
  }

  private String loadVersion() {
    String packName = Bukkit.getServer().getClass().getPackage().getName();
    return packName.substring(packName.lastIndexOf('.') + 1);
  }

  public boolean metricsEnabled() {
    if (this.getConfig().contains("plugin-metrics")
            && this.getConfig().isBoolean("plugin-metrics")) {
      return this.getConfig().getBoolean("plugin-metrics");
    }

    return false;
  }

  @Override
  public void onDisable() {
    this.stopTimeListener();
    if (this.gameManager != null) {
      this.gameManager.unloadGames();
    }

    // 关闭Bot系统
    if (this.botManager != null) {
      try {
        this.botManager.shutdown();
      } catch (Exception e) {
        this.getLogger().warning("关闭Bot系统失败: " + e.getMessage());
      }
    }

    // 保存每日任务系统状态
    if (this.taskManager != null) {
      try {
        this.taskManager.saveAll();
      } catch (Exception e) {
        this.getLogger().warning("保存每日任务状态失败: " + e.getMessage());
      }
    }

    if (this.isHologramsEnabled() && this.holographicInteraction != null) {
      this.holographicInteraction.unloadHolograms();
    }

    // Disable scoreboard addon
    if (this.scoreboardAddon != null) {
      try {
        this.scoreboardAddon.shutdown();
      } catch (Exception e) {
        // Ignore
      }
    }

    // Disable item addon
    if (this.itemAddon != null) {
      try {
        this.itemAddon.onDisable();
      } catch (Exception e) {
        // Ignore
      }
    }
  }

  @Override
  public void onEnable() {
    BedwarsPRO.instance = this;
    // ---- 1. 保存默认配置文件（确保 config.yml 存在） ----
    saveDefaultConfig(); // 这一步会从 JAR 中复制 config.yml 到插件目录
    
    // ---- 密钥验证（直接从文件系统读取） ----
    String expectedKey = "Modified By JmmYm";
    String userKey = "";
    try {
      java.io.File configFile = new java.io.File(this.getDataFolder(), "config.yml");
      if (configFile.exists()) {
        org.bukkit.configuration.file.YamlConfiguration yamlConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        userKey = yamlConfig.getString("license-key");
        if (userKey == null) {
          userKey = "";
        }
      }
    } catch (Exception e) {
      getLogger().warning("读取配置文件失败: " + e.getMessage());
    }
    
    if (!expectedKey.equals(userKey)) {
      // 彩色控制台输出
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "==========================================");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  密钥错误或缺失！插件将停止加载。");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  请在 config.yml 中设置 license-key: ");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "==========================================");
      // 同时记录错误日志（无颜色）
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    getLogger().info("Modified By JmmYm！");
    
    // ---- 加载配置 ----
    loadConfigInUTF();

    // ---- 修改 server.properties ----
    fixServerProperties();

    if (this.getDescription().getVersion().contains("-SNAPSHOT")
            && System.getProperty("IReallyKnowWhatIAmDoingISwear") == null) {
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "*** Warning, you are using a development build ***"));
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "*** You will get NO support regarding this build ***"));
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "*** Please download a stable build from https://github.com/BedwarsPRO/BedwarsPRO/releases ***"));
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "*** Server will start in 10 seconds ***"));
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    this.registerBugsnag();

    // register classes
    this.registerConfigurationClasses();

    // save default config
    this.saveDefaultConfig();
    this.loadConfigInUTF();

    this.getConfig().options().copyDefaults(true);
    this.getConfig().options().copyHeader(true);

    this.craftbukkit = this.getCraftBukkit();
    this.minecraft = this.getMinecraftPackage();
    this.version = this.loadVersion();

    ConfigUpdater configUpdater = new ConfigUpdater();
    configUpdater.addConfigs();
    this.saveConfiguration();
    this.loadConfigInUTF();

    if (this.getBooleanConfig("send-error-data", true) && this.bugsnag != null) {
      this.enableBugsnag();
    } else {
      this.disableBugsnag();
    }

    this.loadShop();

    this.isSpigot = this.getIsSpigot();
    this.loadDatabase();

    this.registerCommands();

    this.registerListener();
    // 注册 PetListener（强制）
    getServer().getPluginManager().registerEvents(new PetListener(), this);
    BedwarsPRO.getInstance().getLogger().info("[BedwarsPRO] PetListener 已强制注册！");

    this.gameManager = new GameManager();

    // bungeecord
    if (BedwarsPRO.getInstance().isBungee()) {
      this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    this.loadStatistics();
    this.loadLocalization(this.getConfig().getString("locale"));

    this.checkUpdates();

    // Loading
    this.scoreboardManager = Bukkit.getScoreboardManager();
    this.gameManager.loadGames();
    this.startTimeListener();
    this.startMetricsIfEnabled();

    // ---- 每日任务系统初始化 ----
    TaskMessages.init(this);
    this.taskManager = new TaskManager(this);
    TaskManager.setInstance(this.taskManager);
    try {
      this.taskManager.init();
      // 注册 /bwpro 命令执行器与 Tab 补齐
      if (this.getCommand("bwpro") != null) {
        TaskCommand taskCmd = new TaskCommand();
        this.getCommand("bwpro").setExecutor(taskCmd);
        this.getCommand("bwpro").setTabCompleter(new TaskCommandTabCompleter());
      }
      // 注册任务监听器
      new TaskListener();
      this.getLogger().info("每日任务系统初始化完成。");
    } catch (Exception e) {
      this.getLogger().severe("每日任务系统初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- Bot系统初始化 ----
    try {
      this.botConfig = new BotConfig(this);
      this.botManager = new BotManager(this);
      this.getLogger().info("Bot系统初始化完成。");
    } catch (Exception e) {
      this.getLogger().severe("Bot系统初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- 启动时显示定制信息（已修正类型转换） ----
    String pluginVersion = this.getDescription().getVersion();
    LocalizationConfig loc = getLocaleConfig();
    // 获取对象并安全转字符串
    printMessage("§f===========================================================");
    printMessage("§7 ");
    printMessage("§b                       BedwarsPRO");
    printMessage("§7 ");
    printMessage("§7 ");
    printMessage("§7   Original BedwarsRel (C) 2015 Sebastian Binder (and other contributors)" );
    printMessage("§7   Original BedwarsScoreBoardAddon (C) 2015 Ram");
    printMessage("§7   Original BedwarsItemAddon (C) 2015 Ram");
    printMessage("§f  " + "版本" + ": §a" + pluginVersion);
    printMessage("§7 ");
    printMessage("§f  " + "作者" + ": §aBy JmmYm");
    printMessage("§7 ");
    printMessage("§7 ");
    printMessage("§f===========================================================");
    // ---- 结束 ----

    // holograms
    if (this.isHologramsEnabled()) {
      if (this.getServer().getPluginManager().isPluginEnabled("HologramAPI")) {
        this.holographicInteraction = new HologramAPIInteraction();
      } else if (this.getServer().getPluginManager().isPluginEnabled("HolographicDisplays")) {
        this.holographicInteraction = new HolographicDisplaysInteraction();
      }
      if (this.holographicInteraction != null) {
        this.holographicInteraction.loadHolograms();
      }
    }

    // Initialize ScoreboardAddon
    try {
      this.scoreboardAddon = new io.jmmym.bedwarspro.scoreboard.Main();
      this.scoreboardAddon.init();
      this.getLogger().info("BedwarsScoreBoardAddon initialized successfully.");
    } catch (Exception e) {
      this.getLogger().severe("Failed to initialize ScoreboardAddon: " + e.getMessage());
      e.printStackTrace();
    }

    // Initialize ItemAddon
    try {
      this.itemAddon = new io.jmmym.bedwarspro.itemaddon.Main();
      this.itemAddon.init(this);
      this.getLogger().info("BedwarsItemAddon initialized successfully.");
    } catch (Exception e) {
      this.getLogger().severe("Failed to initialize ItemAddon: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void registerBugsnag() {
    try {
      this.bugsnag = new Bugsnag("c23593c1e2f40fc0da36564af1bd00c6");
      this.bugsnag.setAppVersion(SupportData.getPluginVersion());
      this.bugsnag.setProjectPackages("io.jmmym.bedwarspro");
      this.bugsnag.setReleaseStage(SupportData.getPluginVersionType());
    } catch (Exception e) {
      this.getServer().getConsoleSender().sendMessage(
              ChatWriter.pluginMessage(ChatColor.GOLD + "Couldn't register Bugsnag."));
    }
  }

  private void registerCommands() {
    BedwarsCommandExecutor executor = new BedwarsCommandExecutor(this);

    this.commands.add(new HelpCommand(this));
    this.commands.add(new SetSpawnerCommand(this));
    this.commands.add(new AddGameCommand(this));
    this.commands.add(new EditGameCommand(this));
    this.commands.add(new StartGameCommand(this));
    this.commands.add(new StopGameCommand(this));
    this.commands.add(new SetRegionCommand(this));
    this.commands.add(new AddTeamCommand(this));
    this.commands.add(new SaveGameCommand(this));
    this.commands.add(new JoinGameCommand(this));
    this.commands.add(new SetSpawnCommand(this));
    this.commands.add(new SetLobbyCommand(this));
    this.commands.add(new LeaveGameCommand(this));
    this.commands.add(new SetTargetCommand(this));
    this.commands.add(new SetBedCommand(this));
    this.commands.add(new ReloadCommand(this));
    this.commands.add(new SetMainLobbyCommand(this));
    this.commands.add(new ListGamesCommand(this));
    this.commands.add(new RegionNameCommand(this));
    this.commands.add(new RemoveTeamCommand(this));
    this.commands.add(new RemoveGameCommand(this));
    this.commands.add(new ClearSpawnerCommand(this));
    this.commands.add(new GameTimeCommand(this));
    this.commands.add(new StatsCommand(this));
    this.commands.add(new SetMinPlayersCommand(this));
    this.commands.add(new SetGameBlockCommand(this));
    this.commands.add(new SetBuilderCommand(this));
    this.commands.add(new SetAutobalanceCommand(this));
    this.commands.add(new KickCommand(this));
    this.commands.add(new AddTeamJoinCommand(this));
    this.commands.add(new AddHoloCommand(this));
    this.commands.add(new RemoveHoloCommand(this));
    this.commands.add(new DebugPasteCommand(this));
    this.commands.add(new ItemsPasteCommand(this));
    this.commands.add(new AutoConnectCommand(this));
    this.commands.add(new AuthorCommand(this));
    this.commands.add(new BotCommand(this));
    this.getCommand("bw").setExecutor(executor);

    // Register bwbot command
    if (this.getCommand("bwbot") != null) {
      this.getCommand("bwbot").setExecutor(executor);
    }
  }

  private void registerConfigurationClasses() {
    ConfigurationSerialization.registerClass(ResourceSpawner.class, "RessourceSpawner");
    ConfigurationSerialization.registerClass(Team.class, "Team");
    ConfigurationSerialization.registerClass(PlayerStatistic.class, "PlayerStatistic");
  }

  private void registerListener() {
    new WeatherListener();
    new BlockListener();
    new PlayerListener();
    io.jmmym.bedwarspro.listener.ReturnLobbyListener.getInstance();
    if (!BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
      new Player19Listener();
    }
    new HangingListener();
    new EntityListener();
    new ServerListener();
    new SignListener();
    new ChunkListener();
    new PetListener();

    if (this.isSpigot()) {
      new PlayerSpigotListener();
    }

    SpecialItem.loadSpecials();
  }

  public void reloadLocalization() {
    this.localization = new HashMap<>();
    this.loadLocalization(this.getConfig().getString("locale"));
  }

  public void saveConfiguration() {
    File file = new File(BedwarsPRO.getInstance().getDataFolder(), "config.yml");
    try {
      file.mkdirs();

      String data = this.getYamlDump((YamlConfiguration) this.getConfig());

      FileOutputStream stream = new FileOutputStream(file);
      OutputStreamWriter writer = new OutputStreamWriter(stream, "UTF-8");

      try {
        writer.write(data);
      } finally {
        writer.close();
        stream.close();
      }
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      ex.printStackTrace();
    }
  }

  public boolean spectationEnabled() {
    if (this.getConfig().contains("spectation-enabled")
            && this.getConfig().isBoolean("spectation-enabled")) {
      return this.getConfig().getBoolean("spectation-enabled");
    }
    return true;
  }

  public void startMetricsIfEnabled() {
    if (this.metricsEnabled()) {
      new BStatsMetrics(this);
      try {
        McStatsMetrics mcStatsMetrics = new McStatsMetrics(this);
        mcStatsMetrics.start();
      } catch (Exception ex) {
        BedwarsPRO.getInstance().getBugsnag().notify(ex);
        this.getServer().getConsoleSender().sendMessage(ChatWriter
                .pluginMessage(ChatColor.RED + "Metrics are enabled, but couldn't send data!"));
      }
    }
  }

  private void startTimeListener() {
    this.timeTask = this.getServer().getScheduler().runTaskTimer(this, new Runnable() {

      @Override
      public void run() {
        for (Game g : BedwarsPRO.getInstance().getGameManager().getGames()) {
          if (g.getState() == GameState.RUNNING) {
            g.getRegion().getWorld().setTime(g.getTime());
          }
        }
      }
    }, (long) 5 * 20, (long) 5 * 20);
  }

  public boolean statisticsEnabled() {
    return this.getBooleanConfig("statistics.enabled", false);
  }

  private void stopTimeListener() {
    try {
      this.timeTask.cancel();
    } catch (Exception ex) {
      // Timer isn't running. Just ignore.
    }

    try {
      this.updateChecker.cancel();
    } catch (Exception ex) {
      // Timer isn't running. Just ignore.
    }
  }

  public boolean toMainLobby() {
    if (this.getConfig().contains("endgame.mainlobby-enabled")) {
      return this.getConfig().getBoolean("endgame.mainlobby-enabled");
    }

    return false;
  }

  // ==================== 新增的两个方法 ====================

  /**
   * 向控制台发送彩色消息（直接使用 § 颜色代码）
   */
  private void printMessage(String msg) {
    this.getServer().getConsoleSender().sendMessage(msg);
  }

  /**
   * 获取当前默认语言的本地化配置对象
   */
  private LocalizationConfig getLocaleConfig() {
    String locale = this.getConfig().getString("locale");
    return this.localization.get(locale);
  }

  /**
   * 修改 server.properties 配置
   * - 确保 spawn-animals=true
   * - 将 motd 修改为 "By JmmYm"
   */
  private void fixServerProperties() {
    try {
      // 获取服务器根目录（兼容 1.8.8）
      File serverDir = Bukkit.getServer().getWorldContainer();
      File serverProps = new File(serverDir, "server.properties");
      
      if (!serverProps.exists()) {
        getLogger().warning("server.properties not found at: " + serverProps.getAbsolutePath());
        return;
      }
      
      // 读取文件内容
      List<String> lines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new FileReader(serverProps))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      }
      
      boolean spawnAnimalsFixed = false;
      boolean motdFixed = false;
      
      // 修改配置
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.startsWith("spawn-animals=")) {
          if (!line.equals("spawn-animals=true")) {
            lines.set(i, "spawn-animals=true");
            spawnAnimalsFixed = true;
            getLogger().info("Fixed spawn-animals: set to true");
          }
        } else if (line.startsWith("motd=")) {
          if (!line.equals("motd=By JmmYm")) {
            lines.set(i, "motd=By JmmYm");
            motdFixed = true;
            getLogger().info("Fixed motd: set to 'By JmmYm'");
          }
        }
      }
      
      // 如果没有找到对应的配置项，添加它们
      boolean hasSpawnAnimals = false;
      boolean hasMotd = false;
      for (String line : lines) {
        if (line.startsWith("spawn-animals=")) hasSpawnAnimals = true;
        if (line.startsWith("motd=")) hasMotd = true;
      }
      
      if (!hasSpawnAnimals) {
        lines.add("spawn-animals=true");
        spawnAnimalsFixed = true;
        getLogger().info("Added spawn-animals=true to server.properties");
      }
      
      if (!hasMotd) {
        lines.add("motd=By JmmYm");
        motdFixed = true;
        getLogger().info("Added motd=By JmmYm to server.properties");
      }
      
      // 写回文件
      if (spawnAnimalsFixed || motdFixed) {
        try (FileWriter writer = new FileWriter(serverProps, false)) {
          for (int i = 0; i < lines.size(); i++) {
            writer.write(lines.get(i));
            if (i < lines.size() - 1) {
              writer.write("\n");
            }
          }
        }
        getLogger().info("server.properties updated successfully");
      }
    } catch (Exception e) {
      getLogger().warning("Failed to modify server.properties: " + e.getMessage());
    }
  }
}