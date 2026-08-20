package io.jmmym.bedwarspro;

import com.bugsnag.Bugsnag;
import com.bugsnag.Report;
import com.bugsnag.callbacks.Callback;
import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.auth.AuthManager;
import io.jmmym.bedwarspro.auth.AuthManager.Result;
import io.jmmym.bedwarspro.auth.Post;
import io.jmmym.bedwarspro.commands.*;
import io.jmmym.bedwarspro.bot.BotConfig;
import io.jmmym.bedwarspro.bot.BotManager;
import io.jmmym.bedwarspro.database.DatabaseManager;
import io.jmmym.bedwarspro.rank.RankManager;
import io.jmmym.bedwarspro.rank.RankListener;
import io.jmmym.bedwarspro.rank.RankPlaceholders;
import io.jmmym.bedwarspro.xp.XpListener;
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
  // 授权心跳任务：周期上报本服务器实例在线状态（onDisable 时取消）
  private BukkitTask authHeartbeatTask = null;
  // 远程配置同步：授权后台「插件配置」页下发的 config.yml/tasks.yml 版本号（本地已应用版本）
  private int remoteCfgVer = 0;
  // 远程配置同步：各文件已应用版本（插件相对路径 → 版本号），服务端据此只下发变更的文件；持久化到 remote-cfg-state.json
  private final java.util.Map<String, Integer> remoteFilesVer = new java.util.HashMap<String, Integer>();
  private static final String REMOTE_CFG_STATE_FILE = "remote-cfg-state.json";
  // 远程可下发文件清单（插件相对路径，与后台 REMOTE_CFG_FILES 一致）：config.yml/tasks/tasks.yml 走独立字段，其余进 files JSON
  private static final String[] REMOTE_CFG_PATHS = {
          "config.yml", "tasks/tasks.yml", "tasks/messages.yml", "api.yml",
          "shop/item_shop.yml", "shop/xp_shop.yml", "Scoreboard/config.yml", "Scoreboard/join-item.yml",
          "QuickStash/config-quickstash.yml"
  };
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
  private YamlConfiguration xpShopConfig = null;
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
  /** Bot 调试日志开关（/bwpro debug on|off），默认关闭。 */
  private volatile boolean botDebug = false;

  public boolean isBotDebug() {
    return botDebug;
  }

  public void setBotDebug(boolean botDebug) {
    this.botDebug = botDebug;
  }

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

  /**
   * 获取本插件 JAR 文件。
   * 注意：JavaPlugin.getFile() 在 1.8 为 protected，外部类（如指令处理器）无法直接调用，故在此暴露公开访问器。
   */
  public java.io.File getPluginJarFile() {
    return getFile();
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

  /** 重新初始化数据库连接（/bwpro reload 时供任务系统重连）。 */
  public void reloadDatabase() {
    this.loadDatabase();
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

  public FileConfiguration getXpShopConfig() {
    return this.xpShopConfig;
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
    // 先关闭旧连接（热重载场景）
    if (this.dbManager != null) {
      try {
        this.dbManager.close();
      } catch (Exception ignored) {
      }
      this.dbManager = null;
    }

    boolean statisticsDatabase = this.getBooleanConfig("statistics.enabled", false)
        && "database".equals(this.getStringConfig("statistics.storage", "yaml"));
    boolean taskDatabase = this.getBooleanConfig("task-database.enabled", false);

    if (!statisticsDatabase && !taskDatabase) {
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.YELLOW
          + "数据库未配置（统计与任务均未启用数据库存储），数据将保存在本地。"));
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
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.YELLOW
          + "数据库配置不完整（host/port/user/password/db），数据将保存在本地。"));
      return;
    }

    this.dbManager = new DatabaseManager(host, port, user, password, db, tablePrefix);
    try {
      this.dbManager.initialize();
    } catch (Exception e) {
      this.dbManager = null;
      String reason = e.getMessage();
      Throwable cause = e.getCause();
      while (cause != null) {
        reason = cause.getMessage();
        cause = cause.getCause();
      }
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + "数据库连接失败！数据将仅保存在本地。"));
      this.getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + "错误: " + reason));
      return;
    }

    this.getServer().getConsoleSender()
            .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "Update database ..."));

    this.getServer().getConsoleSender()
            .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "数据库连接成功，数据将同步存储。"));
    this.getServer().getConsoleSender()
            .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "Done."));
  }

  private void loadLocalization(String locale) {
    if (!this.localization.containsKey(locale)) {
      this.localization.put(locale, new LocalizationConfig(locale));
    }
  }

  public void loadShop() {
    File folder = new File(BedwarsPRO.getInstance().getDataFolder(), "shop");
    if (!folder.exists()) {
      folder.mkdirs();
    }
    // 物品商店：item_shop.yml（兼容旧版 shop.yml 自动迁移）
    File file = new File(folder, "item_shop.yml");
    if (!file.exists()) {
      File oldFile = new File(folder, "shop.yml");
      if (!oldFile.exists()) {
        oldFile = new File(BedwarsPRO.getInstance().getDataFolder(), "shop.yml");
      }
      if (oldFile.exists()) {
        oldFile.renameTo(file);
      } else {
        // create default file
        this.saveResource("shop/item_shop.yml", false);

        // wait until it's really saved
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          BedwarsPRO.getInstance().getBugsnag().notify(e);
          e.printStackTrace();
        }
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

    // 经验商店：xp_shop.yml（price 直接写所需经验值）
    File xpFile = new File(folder, "xp_shop.yml");
    if (!xpFile.exists()) {
      this.saveResource("shop/xp_shop.yml", false);

      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        BedwarsPRO.getInstance().getBugsnag().notify(e);
        e.printStackTrace();
      }
    }

    this.xpShopConfig = new YamlConfiguration();

    try {
      BufferedReader reader =
              new BufferedReader(new InputStreamReader(new FileInputStream(xpFile), "UTF-8"));
      this.xpShopConfig.load(reader);
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      this.getServer().getConsoleSender().sendMessage(
              ChatWriter.pluginMessage(ChatColor.RED + "Couldn't load xp shop! Error in parsing xp_shop.yml!"));
      e.printStackTrace();
    }
  }

  private void loadStatistics() {
    this.playerStatisticManager = new PlayerStatisticManager();
    this.playerStatisticManager.initialize();
  }

  private void startAuthHeartbeat(final String sid) {
    if (this.authHeartbeatTask != null) {
      this.authHeartbeatTask.cancel();
    }
    this.authHeartbeatTask = new BukkitRunnable() {
      @Override
      public void run() {
        try {
          // 每次心跳实时重载配置并读取 auth-check：
          // 修改 config.yml 后无需重启，下一个心跳周期（30 秒内）即上报新值，
          // 授权后台据此自动把实例移入对应的在线/离线服务器页签。
          BedwarsPRO.this.reloadConfig();
          final boolean authCheck = BedwarsPRO.this.getConfig().getBoolean("auth-check", true);
          // 后台可在“查看”中禁用本服务器实例（server_banned）：检测到后立即停服
          if (AuthManager.check(BedwarsPRO.this.getFile(), sid, authCheck, false) == Result.BANNED) {
            Bukkit.getScheduler().runTask(BedwarsPRO.this, new Runnable() {
              @Override
              public void run() {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[校验系统] 本服务器实例已被授权后台禁用，插件即将停服。");
                Bukkit.getPluginManager().disablePlugin(BedwarsPRO.this);
              }
            });
          }
          // 远程配置同步：后台「插件配置」页保存后，本周期内自动拉取并应用（需在 config.yml 开启 remote-config.enabled）
          applyRemoteConfig(authCheck, sid);
        } catch (Throwable ignored) {
          // 心跳失败静默，等待下个周期重试
        }
      }
    }.runTaskTimerAsynchronously(this, 30 * 20L, 30 * 20L);
  }

  /**
   * 拉取并应用授权后台下发的远程配置（config.yml / tasks.yml / 其它文件）。
   * 在异步心跳线程调用；写文件为纯 IO，重载配置调度回主线程执行。
   * v2 协议：按文件版本（files_ver）逐文件比对，只下发/应用变更的那一个文件；
   * 服务端版本回落（cleared）时从 .bak 备份恢复本地文件。覆盖 config.yml 时自动保留本机 auth-server-id。
   */
  private void applyRemoteConfig(final boolean authCheck, final String sid) {
    try {
      if (!getConfig().getBoolean("remote-config.enabled", true)) {
        return;
      }
      final java.io.File cfgFile = new java.io.File(getDataFolder(), "config.yml");
      final java.io.File cfgBak = new java.io.File(getDataFolder(), "config.yml.bak");
      // 任务配置位于 tasks/tasks.yml（与 TaskManager 加载路径一致）
      final java.io.File tasksDir = new java.io.File(getDataFolder(), "tasks");
      final java.io.File tasksFile = new java.io.File(tasksDir, "tasks.yml");
      final java.io.File tasksBak = new java.io.File(tasksDir, "tasks.yml.bak");

      final io.jmmym.bedwarspro.auth.ConfigSync.Result r =
              io.jmmym.bedwarspro.auth.ConfigSync.sync(getFile(), sid, authCheck, remoteCfgVer, remoteFilesVer);
      if (r == null) {
        return;
      }
      // 后台「读取本地配置到云端」：上报服务器本地配置文件内容（服务端版本不变，仅填充后台编辑器）
      if (r.readLocal) {
        final boolean uploaded =
                io.jmmym.bedwarspro.auth.ConfigSync.uploadLocal(getDataFolder(), getFile(), sid, authCheck);
        getLogger().info("[远程配置] " + (uploaded
                ? "已按后台请求将本地配置文件上报到云端。"
                : "读取本地配置上报失败，请检查授权服务器连通性。"));
      }
      // 服务端版本低于本地已应用版本（含后台清除远程配置 cleared / 版本回落为 0）：从 .bak 备份恢复本地配置
      if (r.cleared || r.version < remoteCfgVer) {
        restoreLocalConfig(cfgFile, cfgBak, tasksFile, tasksBak, r.version);
        return;
      }

      // 本次需要下发的文件（路径 → 内容）
      final java.util.Map<String, String> toWrite = new java.util.LinkedHashMap<String, String>();
      final java.util.Map<String, String> files =
              (r.files != null && !r.files.isEmpty() && !"{}".equals(r.files))
                      ? io.jmmym.bedwarspro.auth.ConfigSync.parseJsonObject(r.files)
                      : new java.util.HashMap<String, String>();
      if (!r.hasFilesVer) {
        // 旧版授权服务器（无 files_ver）：按全局版本号比较，返回哪个字段就写哪个文件
        if (r.config != null) {
          toWrite.put("config.yml", preserveAuthServerId(r.config, sid));
        }
        if (r.tasks != null) {
          toWrite.put("tasks/tasks.yml", r.tasks);
        }
        for (final java.util.Map.Entry<String, String> en : files.entrySet()) {
          toWrite.put(en.getKey(), en.getValue());
        }
      } else {
        // v2：按文件版本比对，只下发服务端版本高于本地已应用版本的文件
        for (final String path : REMOTE_CFG_PATHS) {
          final Integer sver = r.filesVer.get(path);
          if (sver == null || sver <= 0) {
            continue;
          }
          final int lver = remoteFilesVer.containsKey(path) ? remoteFilesVer.get(path) : 0;
          if (sver <= lver) {
            continue;
          }
          if ("config.yml".equals(path)) {
            if (r.config != null) {
              toWrite.put(path, preserveAuthServerId(r.config, sid));
            }
          } else if ("tasks/tasks.yml".equals(path)) {
            if (r.tasks != null) {
              toWrite.put(path, r.tasks);
            }
          } else {
            final String content = files.get(path);
            if (content != null) {
              toWrite.put(path, content);
            }
          }
        }
      }
      if (toWrite.isEmpty()) {
        return;
      }

      final boolean writeCfg = toWrite.containsKey("config.yml");
      final boolean writeTasks = toWrite.containsKey("tasks/tasks.yml");
      // 首次覆盖前备份本地原始文件，供后台「清除远程配置」时回退
      if (writeCfg && cfgFile.exists() && !cfgBak.exists()) {
        java.nio.file.Files.copy(cfgFile.toPath(), cfgBak.toPath());
      }
      if (writeTasks && tasksFile.exists() && !tasksBak.exists()) {
        if (!tasksDir.exists()) {
          tasksDir.mkdirs();
        }
        java.nio.file.Files.copy(tasksFile.toPath(), tasksBak.toPath());
      }
      final java.util.Set<String> extraWrites = new java.util.LinkedHashSet<String>();
      for (final java.util.Map.Entry<String, String> en : toWrite.entrySet()) {
        final String path = en.getKey();
        if ("config.yml".equals(path)) {
          java.nio.file.Files.write(cfgFile.toPath(), en.getValue().getBytes("UTF-8"));
        } else if ("tasks/tasks.yml".equals(path)) {
          if (!tasksDir.exists()) {
            tasksDir.mkdirs();
          }
          java.nio.file.Files.write(tasksFile.toPath(), en.getValue().getBytes("UTF-8"));
        } else {
          writeRemoteFile(path, en.getValue());
          extraWrites.add(path);
        }
      }
      final int newVer = r.version;
      Bukkit.getScheduler().runTask(this, new Runnable() {
        @Override
        public void run() {
          try {
            if (writeCfg) {
              reloadConfig();
            }
            if (writeTasks && taskManager != null) {
              taskManager.loadConfig();
            }
            reloadRemoteFiles(extraWrites);
          } catch (Throwable e) {
            getLogger().warning("[远程配置] 应用配置后重载失败: " + e.getMessage());
          }
          // 无论重载是否成功都先记录版本：避免每次心跳重复拉取并重复覆盖本地文件
          remoteCfgVer = newVer;
          for (final String p : toWrite.keySet()) {
            remoteFilesVer.put(p, r.filesVer.containsKey(p) ? r.filesVer.get(p) : newVer);
          }
          saveRemoteConfigState();
          StringBuilder sb = new StringBuilder("[远程配置] 已应用授权后台下发的配置 (v" + newVer + ")");
          for (final String p : toWrite.keySet()) {
            sb.append("，").append(p);
          }
          getLogger().info(sb.toString());
        }
      });
    } catch (Throwable e) {
      getLogger().warning("[远程配置] 同步失败: " + e.getMessage());
    }
  }

  /**
   * 写盘其它远程配置文件（相对插件数据目录），并做路径合法性校验（防路径穿越）。
   * 首次覆盖前备份本地原文件为 .bak，供后台「清除远程配置」时恢复。
   */
  private void writeRemoteFile(String relPath, String content) {
    try {
      if (relPath == null || relPath.contains("..") || !relPath.matches("[A-Za-z0-9_./\\-]+")) {
        getLogger().warning("[远程配置] 忽略非法文件路径: " + relPath);
        return;
      }
      final java.io.File f = new java.io.File(getDataFolder(), relPath);
      final java.io.File bak = new java.io.File(getDataFolder(), relPath + ".bak");
      final java.io.File parent = f.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }
      // 首次覆盖前备份本地原文件（若原本不存在则跳过备份；清除时无 .bak 的文件保留现状不误删）
      if (f.exists() && !bak.exists()) {
        java.nio.file.Files.copy(f.toPath(), bak.toPath());
      }
      java.nio.file.Files.write(f.toPath(), content.getBytes("UTF-8"));
    } catch (Throwable e) {
      getLogger().warning("[远程配置] 写入 " + relPath + " 失败: " + e.getMessage());
    }
  }

  /**
   * 热重载远程下发的其它配置文件对应模块（需在主线程调用）。
   */
  private void reloadRemoteFiles(java.util.Set<String> paths) {
    for (final String p : paths) {
      try {
        if ("tasks/messages.yml".equals(p)) {
          io.jmmym.bedwarspro.task.TaskMessages.reload();
        } else if ("api.yml".equals(p)) {
          if (taskManager != null) {
            taskManager.reload();
          }
        } else if ("shop/item_shop.yml".equals(p)) {
          loadShop();
        } else if ("shop/xp_shop.yml".equals(p)) {
          loadShop();
        } else if ("Scoreboard/config.yml".equals(p)) {
          io.jmmym.bedwarspro.worldscoreboard.WorldScoreboard.reload();
        } else if ("Scoreboard/join-item.yml".equals(p)) {
          io.jmmym.bedwarspro.joinitem.JoinItem.reload();
        } else if ("QuickStash/config-quickstash.yml".equals(p)) {
          io.jmmym.bedwarspro.quickstash.PunchToDeposit.reload();
        } else if ("rank/rank.yml".equals(p) || "rank/messages.yml".equals(p)) {
          // 排位赛配置（段位/模式名）与消息文本：合并重载，确保两者都刷新
          RankManager rm = RankManager.getInstance();
          if (rm != null) {
            rm.reload();
          }
        }
      } catch (Throwable e) {
        getLogger().warning("[远程配置] 重载 " + p + " 失败: " + e.getMessage());
      }
    }
  }

  /**
   * 后台清除远程配置（服务端版本回落为 0）后回退本地配置：
   * 用首次下发前备份的 .bak 文件恢复 config.yml / tasks.yml 及其它已下发配置文件，并重载生效。
   * 原本不存在的文件（无 .bak）保留现状，避免误删服务器本地文件。
   */
  private void restoreLocalConfig(final java.io.File cfgFile, final java.io.File cfgBak,
                                  final java.io.File tasksFile, final java.io.File tasksBak,
                                  final int serverVersion) {
    try {
      final boolean restCfg = cfgBak.exists();
      final boolean restTasks = tasksBak.exists();
      if (restCfg) {
        java.nio.file.Files.copy(cfgBak.toPath(), cfgFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      if (restTasks) {
        java.nio.file.Files.copy(tasksBak.toPath(), tasksFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      // 恢复其它已下发文件（有 .bak 的恢复；无 .bak 说明原本不存在或从未下发，保留现状）
      final java.util.Set<String> restoredOthers = new java.util.LinkedHashSet<String>();
      for (final String p : io.jmmym.bedwarspro.auth.ConfigSync.LOCAL_OTHER_FILES) {
        final java.io.File f = new java.io.File(getDataFolder(), p);
        final java.io.File bak = new java.io.File(getDataFolder(), p + ".bak");
        if (bak.exists()) {
          java.nio.file.Files.copy(bak.toPath(), f.toPath(),
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          restoredOthers.add(p);
        }
      }
      final int v = serverVersion;
      final boolean rCfg = restCfg;
      final boolean rTasks = restTasks;
      final java.util.Set<String> rOthers = restoredOthers;
      Bukkit.getScheduler().runTask(this, new Runnable() {
        @Override
        public void run() {
          try {
            if (rCfg) {
              reloadConfig();
            }
            if (rTasks && taskManager != null) {
              taskManager.loadConfig();
            }
            if (!rOthers.isEmpty()) {
              reloadRemoteFiles(rOthers);
            }
          } catch (Throwable e) {
            getLogger().warning("[远程配置] 回退后重载失败: " + e.getMessage());
          }
          // 无论重载是否成功都记录回落后的版本，避免每次心跳重复回退覆盖
          remoteCfgVer = v;
          remoteFilesVer.clear();
          saveRemoteConfigState();
          StringBuilder sb = new StringBuilder("[远程配置] 后台已清除远程配置");
          java.util.List<String> restored = new java.util.ArrayList<String>();
          if (rCfg) {
            restored.add("config.yml");
          }
          if (rTasks) {
            restored.add("tasks.yml");
          }
          restored.addAll(rOthers);
          if (!restored.isEmpty()) {
            sb.append("，已从备份恢复本地 ").append(String.join(" / ", restored));
          } else {
            sb.append("（未找到备份文件，保持当前文件）");
          }
          sb.append(" (v").append(v).append(")");
          getLogger().info(sb.toString());
        }
      });
    } catch (Throwable e) {
      getLogger().warning("[远程配置] 回退本地配置失败: " + e.getMessage());
    }
  }

  /** 持久化本地已应用的远程配置版本状态（重启后避免重复拉取/重复下发）。 */
  private void saveRemoteConfigState() {
    try {
      StringBuilder sb = new StringBuilder(128);
      sb.append("{\"ver\":\"").append(remoteCfgVer).append('"');
      for (final java.util.Map.Entry<String, Integer> e : remoteFilesVer.entrySet()) {
        if (e.getKey() == null) {
          continue;
        }
        sb.append(",\"").append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\":\"").append(e.getValue()).append('"');
      }
      sb.append('}');
      java.nio.file.Files.write(
              new java.io.File(getDataFolder(), REMOTE_CFG_STATE_FILE).toPath(),
              sb.toString().getBytes("UTF-8"));
    } catch (Throwable ignored) {
    }
  }

  /** 启动时加载持久化的远程配置版本状态（含各文件版本）。 */
  private void loadRemoteConfigState() {
    try {
      final java.io.File f = new java.io.File(getDataFolder(), REMOTE_CFG_STATE_FILE);
      if (!f.isFile()) {
        return;
      }
      final java.util.Map<String, String> map = io.jmmym.bedwarspro.auth.ConfigSync.parseJsonObject(
              new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
      final String v = map.get("ver");
      if (v != null) {
        try {
          remoteCfgVer = Integer.parseInt(v.trim());
        } catch (NumberFormatException ignored) {
        }
      }
      for (final String p : REMOTE_CFG_PATHS) {
        final String sv = map.get(p);
        if (sv != null) {
          try {
            remoteFilesVer.put(p, Integer.parseInt(sv.trim()));
          } catch (NumberFormatException ignored) {
          }
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /** 远程 config.yml 中的 auth-server-id 一律以本机为准（整行替换；缺失则追加），防止实例标识变化。 */
  private String preserveAuthServerId(String yaml, String id) {
    String out = yaml.replaceAll("(?m)^auth-server-id:\\s*\\S+[^\r\n]*", "auth-server-id: " + id);
    if (!java.util.regex.Pattern.compile("(?m)^auth-server-id:").matcher(out).find()) {
      out = out + "\nauth-server-id: " + id + "\n";
    }
    return out;
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
    // 停止授权心跳：服务器关闭后不再上报在线状态
    if (this.authHeartbeatTask != null) {
      this.authHeartbeatTask.cancel();
      this.authHeartbeatTask = null;
    }
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
    // 注销 Corpse 包过滤器（ProtocolLib 监听器需显式移除，避免重载泄漏）
    io.jmmym.bedwarspro.bot.CorpsePacketFilter.shutdown();

    // 保存每日任务系统状态
    if (this.taskManager != null) {
      try {
        this.taskManager.saveAll();
      } catch (Exception e) {
        this.getLogger().warning("保存每日任务状态失败: " + e.getMessage());
      }
    }

    // 保存排位赛数据并停止匹配队列调度
    if (RankManager.getInstance() != null) {
      try {
        RankManager.getInstance().shutdown();
      } catch (Exception e) {
        this.getLogger().warning("关闭排位赛系统失败: " + e.getMessage());
      }
    }

    // 保存快捷存入玩家开关状态
    if (io.jmmym.bedwarspro.quickstash.PunchToDeposit.isInitialized()) {
      try {
        io.jmmym.bedwarspro.quickstash.PunchToDeposit.shutdown();
      } catch (Exception e) {
        this.getLogger().warning("保存快捷存入状态失败: " + e.getMessage());
      }
    }

    // 关闭世界计分板（清除已显示的计分板）
    if (io.jmmym.bedwarspro.worldscoreboard.WorldScoreboard.getInstance() != null) {
      try {
        io.jmmym.bedwarspro.worldscoreboard.WorldScoreboard.shutdown();
      } catch (Exception e) {
        this.getLogger().warning("关闭世界计分板失败: " + e.getMessage());
      }
    }

    // 关闭加入物品（清理冷却记录）
    if (io.jmmym.bedwarspro.joinitem.JoinItem.getInstance() != null) {
      try {
        io.jmmym.bedwarspro.joinitem.JoinItem.shutdown();
      } catch (Exception e) {
        this.getLogger().warning("关闭加入物品失败: " + e.getMessage());
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

    // 关闭数据库连接池（必须在 saveAll 之后）
    if (this.dbManager != null) {
      try {
        this.dbManager.close();
      } catch (Exception ignored) {
      }
      this.dbManager = null;
    }
  }

  /**
   * 加载服务器实例唯一标识（auth-server-id）。
   * 持久化位置：config.yml 中间区域的隐蔽位置（用户要求，不再使用独立文件）。
   * 读取优先级：config.yml 中已有值 → 生成新 ID 并写回 config.yml。
   * 注意：删除 config.yml 后 ID 会重新生成（后台将视为新实例）。
   */
  private String loadAuthServerId() {
    String id = getConfig().getString("auth-server-id");
    if (id == null || id.isEmpty()) {
      id = UUID.randomUUID().toString().replace("-", "");
      getConfig().set("auth-server-id", id);
      saveConfig(); // 立即落盘，避免后续 loadConfigInUTF() 从磁盘重载时丢失
    }
    return id;
  }

  @Override
  public void onEnable() {
    BedwarsPRO.instance = this;
    // ---- 1. 保存默认配置文件（确保 config.yml 存在） ----
    saveDefaultConfig(); // 这一步会从 JAR 中复制 config.yml 到插件目录
    String authServerId = loadAuthServerId();
    // 调试开关：备用授权域名无 SSL 证书时跳过证书校验（仅调试用，默认 false）。
    Post.IGNORE_SSL = getConfig().getBoolean("ignore-ssl-errors", false);
    boolean authCheckEnabled = getConfig().getBoolean("auth-check", true);
    if (authCheckEnabled) {
      final Result authResult = AuthManager.check(this.getFile(), authServerId, authCheckEnabled, true);
      // 仅对「确定未授权 / 被禁用」停服；网络异常（UNREACHABLE）直接放行启动，
      // 由下方每 30 秒心跳持续重试连接——恢复连接后若发现未授权或被禁用仍会停服。
      if (authResult == Result.BANNED || authResult == Result.NOT_LICENSED) {
        // 彩色控制台输出
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "==========================================");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
        if (authResult == Result.BANNED) {
          Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  本服务器实例已被授权后台禁用！插件将停止运行。");
          Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  如需恢复，请到授权后台找到本授权码并解除该 UUID 的禁用。");
        } else {
          Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  授权验证失败！插件将停止加载。");
          Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  请将本 插件 的 授权码 添加到授权白名单后再重启服务器。");
          Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  上方日志中已输出本插件的 授权码，可在授权后台录入。");
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "==========================================");
        getServer().getPluginManager().disablePlugin(this);
        return;
      }
      if (authResult == Result.UNREACHABLE) {
        // 网络异常放行：插件正常启动，由每 30 秒心跳持续重试连接。
        getLogger().warning("[校验系统] 暂时无法连接授权服务器（网络异常），已临时放行插件启动；"
            + "将在每 30 秒心跳中持续尝试重连，若恢复连接后发现未授权或已被禁用将自动停止运行。");
      } else {
        getLogger().info("授权验证通过！");
      }
    } else {
      // 开关关闭（auth-check: false）：不强制授权验证，但仍受后台软管控。
      // 能连上授权服务器时上报状态并接受禁用指令；连不上则静默忽略，插件正常启动。
      getLogger().info("授权验证已关闭。");
    }
    // 无论开关状态都启动心跳：严格模式下心跳拒绝（server_banned）即停服；
    // 软管控模式下同上——后台禁用（BANNED）仍停服，其余结果（未授权/网络异常）一律忽略。
    loadRemoteConfigState();
    startAuthHeartbeat(authServerId);
    // 启动时自动检测一次版本：后台 update/ 目录有更高版本时提示管理员执行 /bwpro update
    io.jmmym.bedwarspro.auth.UpdateFlow.startupCheck(BedwarsPRO.this);
    
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
    // 无条件注册 BungeeCord 出站通道：供加入物品（JoinItem）在本地没有 hub 命令时，
    // 通过代理直接跳转到 hubserver 服务器（是否连接代理取决于服务器网络环境，与 bungeecord.enabled 无关）
    this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

    this.loadStatistics();
    // 打印统计数据实际存储位置（数据库或本地文件），便于管理员确认
    if (this.playerStatisticManager != null
        && this.playerStatisticManager.getEffectiveStorageType() == StorageType.DATABASE) {
      this.getLogger().info("[BedwarsPRO] 统计数据库连接成功，玩家统计将同步存储。");
    } else {
      this.getLogger().info("[BedwarsPRO] 数据库未配置或连接失败，玩家统计将仅保存在本地！");
    }
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
      // 启动 Corpse 尸体 Tab 周期清理 + bot 保位任务：
      // 尸体 show 时用随机 UUID 发 ADD_PLAYER，Corpse 的 despawn 从不发 REMOVE_PLAYER，
      // 导致 Tab 里同名条目永久累积（bot 死几次 Tab 就多几个名字）；周期任务每 5 tick
      // 移除 bot 尸体、给真人的尸体补发 REMOVE_PLAYER，并确保 bot 在玩家列表/世界
      // 实体列表（修复 /tp、/kill 找不到实体）。
      io.jmmym.bedwarspro.bot.BukkitFakePlayer.startCorpseTabCleanupTask();
      // 网络层包过滤器：Corpse 是独立插件的类，我们的 Class.forName 反射跨插件
      // classloader 加载不到（实测 jar 内无 unldenis 类），清理代码只能静默失败。
      // 这里直接从 ProtocolLib 拦截 Corpse 发给客户端的尸体包（随机 UUID 的
      // ADD_PLAYER + NamedEntitySpawn），让尸体对客户端从未出现——遗体不残留、
      // Tab 不再累积同名条目。真实假人的固定 UUID 包不受影响。
      io.jmmym.bedwarspro.bot.CorpsePacketFilter.init(this);
      this.getLogger().info("Bot系统初始化完成。");
    } catch (Exception e) {
      this.getLogger().severe("Bot系统初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- 快捷存入（QuickStash）模块初始化 ----
    try {
      io.jmmym.bedwarspro.quickstash.PunchToDeposit.init(this);
      this.getLogger().info("快捷存储初始化完成。");
    } catch (Exception e) {
      this.getLogger().severe("快捷存储初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- 世界计分板（WorldScoreboard）模块初始化 ----
    try {
      io.jmmym.bedwarspro.worldscoreboard.WorldScoreboard.init(this);
    } catch (Exception e) {
      this.getLogger().severe("世界计分板初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- 加入物品（JoinItem）模块初始化 ----
    try {
      io.jmmym.bedwarspro.joinitem.JoinItem.init(this);
    } catch (Exception e) {
      this.getLogger().severe("加入物品初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- 排位赛系统初始化 ----
    try {
      RankManager rm = RankManager.getInstance();
      if (rm == null) {
        rm = new RankManager();
      }
      rm.init(this);
      new RankListener();
      // 注册排位赛占位符（PlaceholderAPI 可用时）
      if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
        try {
          new RankPlaceholders().register();
        } catch (Exception ex) {
          this.getLogger().warning("排位赛占位符注册失败: " + ex.getMessage());
        }
      }
      this.getLogger().info("[BedwarsPRO] 排位赛系统初始化完成。");
    } catch (Exception e) {
      this.getLogger().severe("[BedwarsPRO] 排位赛系统初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- 经验起床系统初始化 ----
    try {
      new XpListener();
      this.getLogger().info("[BedwarsPRO] 经验起床系统初始化完成。");
    } catch (Exception e) {
      this.getLogger().severe("[BedwarsPRO] 经验起床系统初始化失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ---- 启动生物清理任务 ----
    try {
      io.jmmym.bedwarspro.listener.EntityListener.startMobClearTask();
      this.getLogger().info("[BedwarsPRO] 生物清理任务已启动。");
    } catch (Exception e) {
      this.getLogger().severe("[BedwarsPRO] 生物清理任务启动失败: " + e.getMessage());
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
    this.commands.add(new LeaderboardCommand(this));
    this.commands.add(new RankLeaderboardCommand(this));
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

    new RandomTickZeroListener();
    new WorldProtectionListener();

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
    // enabled 为 true 时启用；或配置了数据库存储（storage: database）时也视为启用——
    // 否则 /bw stats 有显示但游戏内统计（击杀/死亡/获胜）永远不会被记录，数据恒为 0
    return this.getBooleanConfig("statistics.enabled", false)
        || this.getStatisticStorageType() == StorageType.DATABASE;
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