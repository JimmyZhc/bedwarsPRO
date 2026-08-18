package io.jmmym.bedwarspro.game;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.bot.BotManager;
import io.jmmym.bedwarspro.bot.BotPlayer;
import io.jmmym.bedwarspro.bot.BotTaskRunner;
import io.jmmym.bedwarspro.events.BedwarsGameStartEvent;
import io.jmmym.bedwarspro.events.BedwarsGameStartedEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerJoinEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerJoinedEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerLeaveEvent;
import io.jmmym.bedwarspro.events.BedwarsSaveGameEvent;
import io.jmmym.bedwarspro.events.BedwarsTargetBlockDestroyedEvent;
import io.jmmym.bedwarspro.shop.NewItemShop;
import io.jmmym.bedwarspro.shop.Specials.SpecialItem;
import io.jmmym.bedwarspro.statistics.PlayerStatistic;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import io.jmmym.bedwarspro.villager.MerchantCategory;
import io.jmmym.bedwarspro.villager.MerchantCategoryComparator;
import io.jmmym.bedwarspro.xp.XpManager;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.material.Bed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

@Data
public class Game {

  private List<BotPlayer> bots = null;
  private boolean autobalance = false;
  private String builder = null;
  private YamlConfiguration config = null;
  private GameCycle cycle = null;
  private List<Player> freePlayers = null;
  private GameLobbyCountdown gameLobbyCountdown = null;
  private Location hologramLocation = null;
  private boolean isOver = false;
  private boolean isStopping = false;
  private HashMap<Location, GameJoinSign> joinSigns = null;
  private int length = 0;
  private Location lobby = null;
  private Location loc1 = null;
  private Location loc2 = null;
  private Location mainLobby = null;
  private int minPlayers = 0;
  private String name = null;
  // 经验模式开关：null = 未单独设置（跟随全局配置），true/false = 强制经验/物品模式
  private Boolean xpMode = null;
  // Itemshops
  private HashMap<Player, NewItemShop> newItemShops = null;
  private List<MerchantCategory> orderedShopCategories = null;
  private List<MerchantCategory> orderedXpShopCategories = null;
  private Map<Player, Player> playerDamages = null;
  private Map<Player, PlayerSettings> playerSettings = null;
  private HashMap<Player, PlayerStorage> playerStorages = null;
  private List<Team> playingTeams = null;
  private int record = 0;
  private List<String> recordHolders = null;
  private Region region = null;
  private String regionName = null;
  private List<ResourceSpawner> resourceSpawners = null;
  private Map<Player, RespawnProtectionRunnable> respawnProtections = null;
  private List<BukkitTask> runningTasks = null;
  private Scoreboard scoreboard = null;
  private HashMap<Material, MerchantCategory> shopCategories = null;
  private HashMap<Material, MerchantCategory> xpShopCategories = null;
  private List<SpecialItem> specialItems = null;
  private GameState state = null;
  private Material targetMaterial = null;
  private HashMap<String, Team> teams = null;
  private int time = 1000;
  private int timeLeft = 0;

  // 经验模式事件：对局进行到第6分钟触发一次，全员最大血量+5颗心（永久生效，死亡/重生不影响）
  public static final double XP_HEALTH_EVENT_MAX_HEALTH = 30.0;
  private boolean xpHealthEventTriggered = false;

  public Game(String name) {
    super();

    this.name = name;
    this.runningTasks = new ArrayList<BukkitTask>();
    this.bots = new ArrayList<BotPlayer>();

    this.freePlayers = new ArrayList<Player>();
    this.resourceSpawners = new ArrayList<ResourceSpawner>();
    this.teams = new HashMap<String, Team>();
    this.playingTeams = new ArrayList<Team>();

    this.playerStorages = new HashMap<Player, PlayerStorage>();
    this.state = GameState.STOPPED;
    this.scoreboard = BedwarsPRO.getInstance().getScoreboardManager().getNewScoreboard();

    this.gameLobbyCountdown = null;
    this.joinSigns = new HashMap<Location, GameJoinSign>();
    this.timeLeft = BedwarsPRO.getInstance().getMaxLength();
    this.isOver = false;
    this.newItemShops = new HashMap<Player, NewItemShop>();
    this.respawnProtections = new HashMap<Player, RespawnProtectionRunnable>();
    this.playerDamages = new HashMap<Player, Player>();
    this.specialItems = new ArrayList<SpecialItem>();

    this.record = BedwarsPRO.getInstance().getMaxLength();
    this.length = BedwarsPRO.getInstance().getMaxLength();
    this.recordHolders = new ArrayList<String>();

    this.playerSettings = new HashMap<Player, PlayerSettings>();

    this.autobalance = BedwarsPRO.getInstance().getBooleanConfig("global-autobalance", false);

    if (BedwarsPRO.getInstance().isBungee()) {
      this.cycle = new BungeeGameCycle(this);
    } else {
      this.cycle = new SingleGameCycle(this);
    }
  }

  /*
   * STATIC
   */

  public static String bedExistString() {
    return "\u2714";
  }

  public static String bedLostString() {
    return "\u2718";
  }

  public static String getPlayerWithTeamString(Player player, Team team, ChatColor before) {
    if (BedwarsPRO.getInstance().getBooleanConfig("teamname-in-chat", true)) {
      return player.getDisplayName() + before + " (" + team.getChatColor() + team.getDisplayName()
          + before + ")";
    }

    return player.getDisplayName() + before;
  }

  public static String getPlayerWithTeamString(Player player, Team team, ChatColor before,
      String playerAdding) {
    if (BedwarsPRO.getInstance().getBooleanConfig("teamname-in-chat", true)) {
      return player.getDisplayName() + before + playerAdding + before + " (" + team.getChatColor()
          + team.getDisplayName() + before + ")";
    }

    return player.getDisplayName() + before + playerAdding + before;
  }

  /*
   * PUBLIC
   */

  public void addJoinSign(Location signLocation) {
    if (this.joinSigns.containsKey(signLocation)) {
      this.joinSigns.remove(signLocation);
    }

    this.joinSigns.put(signLocation, new GameJoinSign(this, signLocation));
    this.updateSignConfig();
  }

  public void addPlayerSettings(Player player) {
    this.playerSettings.put(player, new PlayerSettings(player));
  }

  public PlayerStorage addPlayerStorage(Player p) {
    PlayerStorage storage = new PlayerStorage(p);
    this.playerStorages.put(p, storage);

    return storage;
  }

  public RespawnProtectionRunnable addProtection(Player player) {
    RespawnProtectionRunnable rpr =
        new RespawnProtectionRunnable(this, player,
            BedwarsPRO.getInstance().getRespawnProtectionTime());
    this.respawnProtections.put(player, rpr);

    return rpr;
  }

  public void addRecordHolder(String holder) {
    this.recordHolders.add(holder);
  }

  public void addResourceSpawner(ResourceSpawner rs) {
    this.resourceSpawners.add(rs);
  }

  public void addRunningTask(BukkitTask task) {
    this.runningTasks.add(task);
  }

  public void addSpecialItem(SpecialItem item) {
    this.specialItems.add(item);
  }

  public boolean addBot(BotPlayer bot) {
    BotManager botManager = BedwarsPRO.getInstance().getBotManager();
    int maxBots = BedwarsPRO.getInstance().getBotConfig().getMaxBotsPerGame();
    if (this.bots.size() >= maxBots) {
      return false;
    }
    this.bots.add(bot);
    BotTaskRunner runner = botManager.getOrCreateTaskRunner(this);
    runner.addBot(bot.getBukkitPlayer());
    return true;
  }

  public void removeBot(BotPlayer bot) {
    this.bots.remove(bot);
    BotManager botManager = BedwarsPRO.getInstance().getBotManager();
    BotTaskRunner runner = botManager.getTaskRunner(this);
    if (runner != null) {
      runner.removeBot(bot.getBukkitPlayer());
    }
  }

  public List<BotPlayer> getBots() {
    return this.bots;
  }

  public int getBotCount() {
    return this.bots.size();
  }

  public void addTeam(String name, TeamColor color, int maxPlayers) {
    org.bukkit.scoreboard.Team newTeam = this.scoreboard.registerNewTeam(name);
    newTeam.setDisplayName(name);
    newTeam.setPrefix(color.getChatColor().toString());

    Team theTeam = new Team(name, color, maxPlayers, newTeam);
    theTeam.setGame(this);
    this.teams.put(name, theTeam);
  }

  public void addTeam(Team team) {
    org.bukkit.scoreboard.Team newTeam = this.scoreboard.registerNewTeam(team.getName());
    newTeam.setDisplayName(team.getName());
    newTeam.setPrefix(team.getChatColor().toString());

    team.setScoreboardTeam(newTeam);
    team.setGame(this);

    this.teams.put(team.getName(), team);
  }

  public void broadcastSound(Sound sound, float volume, float pitch) {
    for (Player p : this.getPlayers()) {
      if (p.isOnline()) {
        p.playSound(p.getLocation(), sound, volume, pitch);
      }
    }
  }

  public void broadcastSound(Sound sound, float volume, float pitch, List<Player> players) {
    for (Player p : players) {
      if (p.isOnline()) {
        p.playSound(p.getLocation(), sound, volume, pitch);
      }
    }
  }

  public GameCheckCode checkGame() {
    if (this.loc1 == null || this.loc2 == null) {
      return GameCheckCode.LOC_NOT_SET_ERROR;
    }

    if (this.teams == null || this.teams.size() <= 1) {
      return GameCheckCode.TEAM_SIZE_LOW_ERROR;
    }

    GameCheckCode teamCheck = this.checkTeams();
    if (teamCheck != GameCheckCode.OK) {
      return teamCheck;
    }

    if (this.getRessourceSpawner().size() == 0) {
      return GameCheckCode.NO_RES_SPAWNER_ERROR;
    }

    if (this.lobby == null) {
      return GameCheckCode.NO_LOBBY_SET;
    }

    if (BedwarsPRO.getInstance().toMainLobby() && this.mainLobby == null) {
      return GameCheckCode.NO_MAIN_LOBBY_SET;
    }

    return GameCheckCode.OK;
  }

  private GameCheckCode checkTeams() {
    for (Team t : this.teams.values()) {
      if (t.getSpawnLocation() == null) {
        return GameCheckCode.TEAMS_WITHOUT_SPAWNS;
      }

      Material targetMaterial = this.getTargetMaterial();

      if (targetMaterial.equals(Material.BED_BLOCK)) {
        if ((t.getHeadTarget() == null || t.getFeetTarget() == null)
            || (!Utils.isBedBlock(t.getHeadTarget()) || !Utils.isBedBlock(t.getFeetTarget()))) {
          return GameCheckCode.TEAM_NO_WRONG_BED;
        }
      } else {
        if (t.getHeadTarget() == null) {
          return GameCheckCode.TEAM_NO_WRONG_TARGET;
        }

        if (!t.getHeadTarget().getType().equals(targetMaterial)) {
          return GameCheckCode.TEAM_NO_WRONG_TARGET;
        }
      }

    }

    return GameCheckCode.OK;
  }

  private void cleanUsersInventory() {
    for (PlayerStorage storage : this.playerStorages.values()) {
      storage.clean();
    }
  }

  public void clearProtections() {
    for (RespawnProtectionRunnable protection : this.respawnProtections.values()) {
      try {
        protection.cancel();
      } catch (Exception ex) {
        BedwarsPRO.getInstance().getBugsnag().notify(ex);
        // isn't running, ignore
      }
    }

    this.respawnProtections.clear();
  }

  private void createGameConfig(File config) {
    YamlConfiguration yml = new YamlConfiguration();

    yml.set("name", this.name);
    yml.set("world", this.getRegion().getWorld().getName());
    yml.set("loc1", Utils.locationSerialize(this.loc1));
    yml.set("loc2", Utils.locationSerialize(this.loc2));
    yml.set("lobby", Utils.locationSerialize(this.lobby));
    yml.set("minplayers", this.getMinPlayers());

    if (BedwarsPRO.getInstance().getBooleanConfig("store-game-records", true)) {
      yml.set("record", this.record);

      if (BedwarsPRO.getInstance().getBooleanConfig("store-game-records-holder", true)) {
        yml.set("record-holders", this.recordHolders);
      }
    }

    if (this.regionName == null) {
      this.regionName = this.region.getName();
    }

    yml.set("regionname", this.regionName);
    yml.set("time", this.time);

    yml.set("targetmaterial", this.getTargetMaterial().name());
    yml.set("builder", this.builder);

    if (this.hologramLocation != null) {
      yml.set("hololoc", Utils.locationSerialize(this.hologramLocation));
    }

    if (this.mainLobby != null) {
      yml.set("mainlobby", Utils.locationSerialize(this.mainLobby));
    }

    yml.set("autobalance", this.autobalance);

    if (this.xpMode != null) {
      yml.set("xp-mode", this.xpMode);
    }

    yml.set("spawner", this.resourceSpawners);
    yml.createSection("teams", this.teams);

    try {
      yml.save(config);
      this.config = yml;
    } catch (IOException e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      BedwarsPRO.getInstance().getLogger().info(ChatWriter.pluginMessage(e.getMessage()));
    }
  }

  private void displayMapInfo() {
    for (Player player : this.getPlayers()) {
      this.displayMapInfo(player);
    }
  }

  private void displayMapInfo(Player player) {
    try {
      Class<?> clazz = Class.forName("io.jmmym.bedwarspro.com."
          + BedwarsPRO.getInstance().getCurrentVersion().toLowerCase() + ".Title");
      Method showTitle = clazz.getMethod("showTitle", Player.class, String.class, double.class,
          double.class, double.class);
      double titleFadeIn = BedwarsPRO.getInstance().getConfig()
          .getDouble("titles.map.title-fade-in");
      double titleStay = BedwarsPRO.getInstance().getConfig().getDouble("titles.map.title-stay");
      double titleFadeOut = BedwarsPRO.getInstance().getConfig()
          .getDouble("titles.map.title-fade-out");

      showTitle.invoke(null, player, this.getRegion().getName(), titleFadeIn, titleStay,
          titleFadeOut);

      if (this.builder != null) {
        Method showSubTitle = clazz.getMethod("showSubTitle", Player.class, String.class,
            double.class, double.class, double.class);
        double subtitleFadeIn =
            BedwarsPRO.getInstance().getConfig().getDouble("titles.map.subtitle-fade-in");
        double subtitleStay = BedwarsPRO.getInstance().getConfig()
            .getDouble("titles.map.subtitle-stay");
        double subtitleFadeOut =
            BedwarsPRO.getInstance().getConfig().getDouble("titles.map.subtitle-fade-out");

        showSubTitle.invoke(null, player,
            BedwarsPRO._l(player, "ingame.title.map-builder",
                ImmutableMap.of("builder",
                    ChatColor.translateAlternateColorCodes('&', this.builder))),
            subtitleFadeIn, subtitleStay, subtitleFadeOut);
      }

    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      ex.printStackTrace();
    }
  }

  private void displayRecord() {
    for (Player player : this.getPlayers()) {
      this.displayRecord(player);
    }
  }

  private void displayRecord(Player player) {
    boolean displayHolders = BedwarsPRO
        .getInstance().getBooleanConfig("store-game-records-holder", true);

    if (displayHolders && this.getRecordHolders().size() > 0) {
      StringBuilder holders = new StringBuilder();

      for (String holder : this.recordHolders) {
        if (holders.length() == 0) {
          holders.append(ChatColor.WHITE + holder);
        } else {
          holders.append(ChatColor.GOLD + ", " + ChatColor.WHITE + holder);
        }
      }

      player
          .sendMessage(ChatWriter.pluginMessage(BedwarsPRO._l(player, "ingame.record-with-holders",
              ImmutableMap
                  .of("record", this.getFormattedRecord(), "holders", holders.toString()))));
    } else {
      player.sendMessage(ChatWriter.pluginMessage(
          BedwarsPRO
              ._l(player, "ingame.record", ImmutableMap.of("record", this.getFormattedRecord()))));
    }
  }

  private void dropTargetBlock(Block targetBlock) {
    if (targetBlock.getType().equals(Material.BED_BLOCK)) {
      Block bedHead;
      Block bedFeet;
      Bed bedBlock = (Bed) targetBlock.getState().getData();

      if (!bedBlock.isHeadOfBed()) {
        bedFeet = targetBlock;
        bedHead = Utils.getBedNeighbor(bedFeet);
      } else {
        bedHead = targetBlock;
        bedFeet = Utils.getBedNeighbor(bedHead);
      }

      if (!BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_12")) {
        bedFeet.setType(Material.AIR);
      } else {
        bedHead.setType(Material.AIR);
      }
    } else {
      targetBlock.setType(Material.AIR);
    }
  }

  private String formatLobbyScoreboardString(String str) {
    String finalStr = str;

    finalStr = finalStr.replace("$regionname$", this.region.getName());
    finalStr = finalStr.replace("$gamename$", this.name);
    finalStr = finalStr.replace("$players$", String.valueOf(this.getPlayerAmount()));
    finalStr = finalStr.replace("$maxplayers$", String.valueOf(this.getMaxPlayers()));

    return ChatColor.translateAlternateColorCodes('&', finalStr);
  }

  private String formatScoreboardTeam(Team team, boolean destroyed) {
    String format = null;

    if (team == null) {
      return "";
    }

    if (destroyed) {
      format = BedwarsPRO.getInstance().getStringConfig("scoreboard.format-bed-destroyed",
          "&c$status$ $team$");
    } else {
      format =
          BedwarsPRO
              .getInstance().getStringConfig("scoreboard.format-bed-alive", "&a$status$ $team$");
    }

    format = format.replace("$status$", (destroyed) ? Game.bedLostString() : Game.bedExistString());
    format = format.replace("$team$", team.getChatColor() + team.getName());

    return ChatColor.translateAlternateColorCodes('&', format);
  }

  private String formatScoreboardTitle() {
    String format =
        BedwarsPRO.getInstance()
            .getStringConfig("scoreboard.format-title", "&e$region$&f - $time$");

    // replaces
    format = format.replace("$region$", this.getRegion().getName());
    format = format.replace("$game$", this.name);
    format = format.replace("$time$", this.getFormattedTimeLeft());

    return ChatColor.translateAlternateColorCodes('&', format);
  }

  public int getCurrentPlayerAmount() {
    int amount = 0;
    for (Team t : this.teams.values()) {
      amount += t.getPlayers().size();
    }

    return amount;
  }

  public String getFormattedRecord() {
    return Utils.getFormattedTime(this.record);
  }

  private String getFormattedTimeLeft() {
    int min = 0;
    int sec = 0;
    String minStr = "";
    String secStr = "";

    min = (int) Math.floor(this.timeLeft / 60);
    sec = this.timeLeft % 60;

    minStr = (min < 10) ? "0" + String.valueOf(min) : String.valueOf(min);
    secStr = (sec < 10) ? "0" + String.valueOf(sec) : String.valueOf(sec);

    return minStr + ":" + secStr;
  }

  public List<Player> getFreePlayersClone() {
    List<Player> players = new ArrayList<Player>();
    if (this.freePlayers.size() > 0) {
      players.addAll(this.freePlayers);
    }

    return players;
  }

  public HashMap<Material, MerchantCategory> getItemShopCategories() {
    return this.shopCategories;
  }

  public void setItemShopCategories(HashMap<Material, MerchantCategory> cats) {
    this.shopCategories = cats;
  }

  public GameLobbyCountdown getLobbyCountdown() {
    return this.gameLobbyCountdown;
  }

  public void setLobbyCountdown(GameLobbyCountdown glc) {
    this.gameLobbyCountdown = glc;
  }

  private Team getLowestTeam() {
    Team lowest = null;
    for (Team team : this.teams.values()) {
      if (lowest == null) {
        lowest = team;
        continue;
      }

      if (team.getPlayers().size() < lowest.getPlayers().size()) {
        lowest = team;
      }
    }

    return lowest;
  }

  public int getMaxPlayers() {
    int max = 0;
    for (Team t : this.teams.values()) {
      max += t.getMaxPlayers();
    }

    return max;
  }

  public NewItemShop getNewItemShop(Player player) {
    return this.newItemShops.get(player);
  }

  public List<Player> getNonVipPlayers() {
    List<Player> players = this.getPlayers();

    Iterator<Player> playerIterator = players.iterator();
    while (playerIterator.hasNext()) {
      Player player = playerIterator.next();
      if (player.hasPermission("bw.vip.joinfull") || player.hasPermission("bw.vip.forcestart")
          || player.hasPermission("bw.vip")) {
        playerIterator.remove();
      }
    }

    return players;
  }

  /** 当前模式对应的商店分类：经验模式用 xp_shop.yml，物品模式用 item_shop.yml */
  public List<MerchantCategory> getOrderedItemShopCategories() {
    if (this.isXpMode()) {
      return this.orderedXpShopCategories;
    }
    return this.orderedShopCategories;
  }

  /** 当前对局是否为经验起床模式（对局单独设置优先，其次 config: xp-bedwars） */
  public boolean isXpMode() {
    return XpManager.isXpMode(this);
  }

  /** 设置经验模式开关并立即保存到 game.yml（bwsba 编辑 GUI 使用） */
  public void setXpModeAndSave(boolean value) {
    this.xpMode = value;
    if (this.config != null) {
      this.config.set("xp-mode", value);
      File gameConfig = new File(BedwarsPRO.getInstance().getDataFolder() + "/"
          + GameManager.gamesPath + "/" + this.name + "/game.yml");
      if (gameConfig.exists()) {
        try {
          this.config.save(gameConfig);
        } catch (IOException e) {
          BedwarsPRO.getInstance().getBugsnag().notify(e);
          e.printStackTrace();
        }
      }
    }
  }

  public int getPlayerAmount() {
    return this.getPlayers().size();
  }

  public Player getPlayerDamager(Player p) {
    return this.playerDamages.get(p);
  }

  public PlayerSettings getPlayerSettings(Player player) {
    return this.playerSettings.get(player);
  }

  public PlayerStorage getPlayerStorage(Player p) {
    return this.playerStorages.get(p);
  }

  public Team getPlayerTeam(Player p) {
    for (Team team : this.getTeams().values()) {
      if (team.isInTeam(p)) {
        return team;
      }
    }

    return null;
  }

  public Location getPlayerTeleportLocation(Player player) {
    if (this.isSpectator(player)
        && !(this.getCycle() instanceof BungeeGameCycle && this.getCycle().isEndGameRunning()
        && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {
      return ((Team) this.teams.values().toArray()[Utils.randInt(0, this.teams.size() - 1)])
          .getSpawnLocation();
    }

    if (this.getPlayerTeam(player) != null
        && !(this.getCycle() instanceof BungeeGameCycle && this.getCycle().isEndGameRunning()
        && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {
      return this.getPlayerTeam(player).getSpawnLocation();
    }

    return this.getLobby();
  }

  public ArrayList<Player> getPlayers() {
    ArrayList<Player> players = new ArrayList<>();

    players.addAll(this.freePlayers);

    for (Team team : this.teams.values()) {
      players.addAll(team.getPlayers());
    }

    return players;
  }

  public List<ResourceSpawner> getRessourceSpawner() {
    return this.resourceSpawners;
  }

  public HashMap<Location, GameJoinSign> getSigns() {
    return this.joinSigns;
  }

  public List<SpecialItem> getSpecialItems() {
    return this.specialItems;
  }

  public Material getTargetMaterial() {
    if (this.targetMaterial == null) {
      return Utils.getMaterialByConfig("game-block", Material.BED_BLOCK);
    }

    return this.targetMaterial;
  }

  public Team getTeam(String name) {
    return this.teams.get(name);
  }

  public Team getTeamByDyeColor(DyeColor color) {
    for (Team t : this.teams.values()) {
      if (t.getColor().getDyeColor().equals(color)) {
        return t;
      }
    }

    return null;
  }

  public Team getTeamOfBed(Block bed) {
    for (Team team : this.getTeams().values()) {
      if (team.getFeetTarget() == null) {
        if (team.getHeadTarget().equals(bed)) {
          return team;
        }
      } else {
        if (team.getHeadTarget().equals(bed) || team.getFeetTarget().equals(bed)) {
          return team;
        }
      }
    }

    return null;
  }

  public Team getTeamOfEnderChest(Block chest) {
    for (Team team : this.teams.values()) {
      if (team.getChests().contains(chest)) {
        return team;
      }
    }

    return null;
  }

  public ArrayList<Player> getTeamPlayers() {
    ArrayList<Player> players = new ArrayList<>();

    for (Team team : this.teams.values()) {
      players.addAll(team.getPlayers());
    }

    return players;
  }

  public HashMap<String, Team> getTeams() {
    return this.teams;
  }

  public boolean handleDestroyTargetMaterial(Player p, Block block) {
    Team team = this.getPlayerTeam(p);
    if (team == null) {
      return false;
    }

    Team bedDestroyTeam = null;
    Block bedBlock = team.getHeadTarget();

    if (block.getType().equals(Material.BED_BLOCK)) {
      Block breakBlock = block;
      Block neighbor = null;
      Bed breakBed = (Bed) breakBlock.getState().getData();

      if (!breakBed.isHeadOfBed()) {
        neighbor = breakBlock;
        breakBlock = Utils.getBedNeighbor(neighbor);
      } else {
        neighbor = Utils.getBedNeighbor(breakBlock);
      }

      if (bedBlock.equals(breakBlock)) {
        p.sendMessage(
            ChatWriter
                .pluginMessage(ChatColor.RED + BedwarsPRO._l(p, "ingame.blocks.ownbeddestroy")));
        return false;
      }

      bedDestroyTeam = this.getTeamOfBed(breakBlock);
      if (bedDestroyTeam == null) {
        return false;
      }
      this.dropTargetBlock(block);
    } else {
      if (bedBlock.equals(block)) {
        p.sendMessage(
            ChatWriter
                .pluginMessage(ChatColor.RED + BedwarsPRO._l(p, "ingame.blocks.ownbeddestroy")));
        return false;
      }

      bedDestroyTeam = this.getTeamOfBed(block);
      if (bedDestroyTeam == null) {
        return false;
      }

      this.dropTargetBlock(block);
    }

    // set statistics
    if (BedwarsPRO.getInstance().statisticsEnabled()) {
      PlayerStatistic statistic = BedwarsPRO.getInstance().getPlayerStatisticManager()
          .getStatistic(p);
      statistic.setCurrentDestroyedBeds(statistic.getCurrentDestroyedBeds() + 1);
      statistic.setCurrentScore(statistic.getCurrentScore() + BedwarsPRO.getInstance()
          .getIntConfig("statistics.scores.bed-destroy", 25));
    }

    // reward when destroy bed
    if (BedwarsPRO.getInstance().getBooleanConfig("rewards.enabled", false)) {
      List<String> commands =
          BedwarsPRO.getInstance().getConfig().getStringList("rewards.player-destroy-bed");
      BedwarsPRO.getInstance()
          .dispatchRewardCommands(commands, ImmutableMap.of("{player}", p.getName(),
              "{score}",
              String.valueOf(
                  BedwarsPRO.getInstance().getIntConfig("statistics.scores.bed-destroy", 25))));
    }

    BedwarsTargetBlockDestroyedEvent targetBlockDestroyedEvent =
        new BedwarsTargetBlockDestroyedEvent(this, p, bedDestroyTeam);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(targetBlockDestroyedEvent);

    for (Player aPlayer : this.getPlayers()) {
      if (aPlayer.isOnline()) {
        aPlayer.sendMessage(
            ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                ._l(aPlayer, "ingame.blocks.beddestroyed",
                    ImmutableMap.of("team",
                        bedDestroyTeam.getChatColor() + bedDestroyTeam.getName() + ChatColor.RED,
                        "player",
                        Game.getPlayerWithTeamString(p, team, ChatColor.RED)))));
      }
    }

    this.broadcastSound(
        Sound.valueOf(
            BedwarsPRO.getInstance().getStringConfig("bed-sound", "ENDERDRAGON_GROWL")
                .toUpperCase()),
        30.0F, 10.0F);
    this.updateScoreboard();
    return true;
  }

  public boolean hasEnoughPlayers() {
    return this.getPlayers().size() >= this.getMinPlayers();
  }

  public boolean hasEnoughTeams() {
    int teamsWithPlayers = 0;
    for (Team team : this.getTeams().values()) {
      if (team.getPlayers().size() > 0) {
        teamsWithPlayers++;
      }
    }

    return (teamsWithPlayers > 1 || (teamsWithPlayers == 1 && this.getFreePlayers().size() >= 1)
        || (teamsWithPlayers == 0 && this.getFreePlayers().size() >= 2));
  }

  public boolean isAutobalanceEnabled() {
    if (BedwarsPRO.getInstance().getBooleanConfig("global-autobalance", false)) {
      return true;
    }

    return this.autobalance;
  }

  /*
   * GETTER / SETTER
   */

  public boolean isFull() {
    return (this.getMaxPlayers() <= this.getPlayerAmount());
  }

  public boolean isInGame(Player p) {
    for (Team t : this.teams.values()) {
      if (t.isInTeam(p)) {
        return true;
      }
    }

    return this.freePlayers.contains(p);
  }

  public Team isOver() {
    if (this.isOver || this.state != GameState.RUNNING) {
      return null;
    }

    ArrayList<Player> players = this.getTeamPlayers();
    ArrayList<Team> teams = new ArrayList<>();

    if (players.size() == 0 || players.isEmpty()) {
      return null;
    }

    for (Player player : players) {
      Team playerTeam = this.getPlayerTeam(player);
      if (teams.contains(playerTeam)) {
        continue;
      }

      if (!player.isDead()) {
        teams.add(playerTeam);
      } else if (!playerTeam.isDead(this)) {
        teams.add(playerTeam);
      }
    }

    if (teams.size() == 1) {
      return teams.get(0);
    } else {
      return null;
    }
  }

  public boolean isOverSet() {
    return this.isOver;
  }

  public boolean isProtected(Player player) {
    return (this.respawnProtections.containsKey(player) && this.getState() == GameState.RUNNING);
  }

  public boolean isSpectator(Player player) {
    return (this.getState() == GameState.RUNNING && this.freePlayers.contains(player));
  }

  public boolean isStartable() {
    return (this.hasEnoughPlayers() && this.hasEnoughTeams());
  }

  public void kickAllPlayers() {
    for (Player p : this.getPlayers()) {
      this.playerLeave(p, false);
    }
  }

  public void loadItemShopCategories() {
    this.shopCategories = MerchantCategory.loadCategories(BedwarsPRO.getInstance().getShopConfig());
    this.xpShopCategories = MerchantCategory.loadCategories(BedwarsPRO.getInstance().getXpShopConfig());
    this.orderedShopCategories = this.loadOrderedItemShopCategories();
    this.orderedXpShopCategories = this.loadOrderedXpShopCategories();
  }

  private List<MerchantCategory> loadOrderedItemShopCategories() {
    List<MerchantCategory> list = new ArrayList<MerchantCategory>(this.shopCategories.values());
    Collections.sort(list, new MerchantCategoryComparator());
    return list;
  }

  private List<MerchantCategory> loadOrderedXpShopCategories() {
    List<MerchantCategory> list =
            this.xpShopCategories == null
                    ? new ArrayList<MerchantCategory>()
                    : new ArrayList<MerchantCategory>(this.xpShopCategories.values());
    Collections.sort(list, new MerchantCategoryComparator());
    return list;
  }

  private void makeTeamsReady() {
    this.playingTeams.clear();

    for (Team team : this.teams.values()) {
      team.getScoreboardTeam()
          .setAllowFriendlyFire(BedwarsPRO.getInstance().getConfig().getBoolean("friendlyfire"));
      if (team.getPlayers().size() == 0) {
        this.dropTargetBlock(team.getHeadTarget());
      } else {
        this.playingTeams.add(team);
      }
    }

    this.updateScoreboard();
  }

  private void moveFreePlayersToTeam() {
    for (Player player : this.freePlayers) {
      Team lowest = this.getLowestTeam();
      lowest.addPlayer(player);
    }

    this.freePlayers = new ArrayList<Player>();
    this.updateScoreboard();
  }

  public void nonFreePlayer(Player p) {
    if (this.freePlayers.contains(p)) {
      this.freePlayers.remove(p);
    }
  }

  public NewItemShop openNewItemShop(Player player) {
    NewItemShop newShop = new NewItemShop(this.getOrderedItemShopCategories());
    this.newItemShops.put(player, newShop);

    return newShop;
  }

  public void openSpectatorCompass(Player player) {
    if (!this.isSpectator(player)) {
      return;
    }

    int teamplayers = this.getTeamPlayers().size();
    int nom = (teamplayers % 9 == 0) ? 9 : (teamplayers % 9);
    int size = teamplayers + (9 - nom);
    Inventory compass = Bukkit
        .createInventory(null, size, BedwarsPRO._l(player, "ingame.spectator"));
    for (Team t : this.getTeams().values()) {
      for (Player p : t.getPlayers()) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setDisplayName(t.getChatColor() + p.getDisplayName());
        meta.setLore(Arrays.asList(t.getChatColor() + t.getDisplayName()));
        meta.setOwner(p.getName());
        head.setItemMeta(meta);

        compass.addItem(head);
      }
    }

    player.openInventory(compass);
  }

  public void playerJoinTeam(Player player, Team team) {
    if (team.getPlayers().size() >= team.getMaxPlayers()) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.teamfull")));
      return;
    }

    if (team.addPlayer(player)) {
      this.nonFreePlayer(player);

      // Team color chestplate
      ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
      LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
      meta.setColor(team.getColor().getColor());
      meta.setDisplayName(team.getChatColor() + team.getDisplayName());
      chestplate.setItemMeta(meta);

      player.getInventory().setItem(7, chestplate);
      player.updateInventory();
    } else {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.teamfull")));
      return;
    }

    this.updateScoreboard();

    if (this.isStartable() && this.getLobbyCountdown() == null) {
      GameLobbyCountdown lobbyCountdown = new GameLobbyCountdown(this);
      lobbyCountdown.runTaskTimer(BedwarsPRO.getInstance(), 20L, 20L);
      this.setLobbyCountdown(lobbyCountdown);
    }

    player
        .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO
            ._l(player, "lobby.teamjoined",
                ImmutableMap.of("team", team.getDisplayName() + ChatColor.GREEN))));
  }

  public boolean playerJoins(final Player p) {

    if (this.state == GameState.STOPPED
        || (this.state == GameState.RUNNING && !BedwarsPRO.getInstance().spectationEnabled())) {
      if (this.cycle instanceof BungeeGameCycle) {
        ((BungeeGameCycle) this.cycle).sendBungeeMessage(p,
            ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(p, "errors.cantjoingame")));
      } else {
        p.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
            ._l(p, "errors.cantjoingame")));
      }
      return false;
    }

    if (!this.cycle.onPlayerJoins(p)) {
      return false;
    }

    BedwarsPlayerJoinEvent joiningEvent = new BedwarsPlayerJoinEvent(this, p);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(joiningEvent);

    if (joiningEvent.isCancelled()) {
      if (joiningEvent.getKickOnCancel()) {
        new BukkitRunnable() {
          @Override
          public void run() {
            if (Game.this.getCycle() instanceof BungeeGameCycle) {
              ((BungeeGameCycle) Game.this.getCycle())
                  .bungeeSendToServer(BedwarsPRO.getInstance().getBungeeHub(), p, true);
            }
          }
        }.runTaskLater(BedwarsPRO.getInstance(), 5L);
      }
      return false;
    }

    BedwarsPRO.getInstance().getGameManager().addGamePlayer(p, this);
    if (BedwarsPRO.getInstance().statisticsEnabled()) {
      // load statistics
      BedwarsPRO.getInstance().getPlayerStatisticManager().getStatistic(p);
    }

    // add damager and set it to null
    this.playerDamages.put(p, null);

    // add player settings
    this.addPlayerSettings(p);

    new BukkitRunnable() {

      @Override
      public void run() {
        for (Player playerInGame : Game.this.getPlayers()) {
          playerInGame.hidePlayer(p);
          p.hidePlayer(playerInGame);
        }
      }

    }.runTaskLater(BedwarsPRO.getInstance(), 5L);

    if (this.state == GameState.RUNNING) {
      this.toSpectator(p);
      this.displayMapInfo(p);
    } else {

      PlayerStorage storage = this.addPlayerStorage(p);
      storage.store();
      storage.clean();

      if (!BedwarsPRO.getInstance().isBungee()) {
        final Location location = this.getPlayerTeleportLocation(p);
        if (!p.getLocation().equals(location)) {
          this.getPlayerSettings(p).setTeleporting(true);
          if (BedwarsPRO.getInstance().isBungee()) {
            new BukkitRunnable() {

              @Override
              public void run() {
                p.teleport(location);
              }

            }.runTaskLater(BedwarsPRO.getInstance(), 10L);
          } else {
            p.teleport(location);
          }
        }
      }

      storage.loadLobbyInventory(this);

      new BukkitRunnable() {

        @Override
        public void run() {
          Game.this.setPlayerGameMode(p);
          Game.this.setPlayerVisibility(p);
        }

      }.runTaskLater(BedwarsPRO.getInstance(), 15L);

      for (Player aPlayer : this.getPlayers()) {
        if (aPlayer.isOnline()) {
          aPlayer.sendMessage(
              ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(aPlayer, "lobby.playerjoin",
                  ImmutableMap.of("player", p.getDisplayName() + ChatColor.GREEN))));
        }
      }

      if (!this.isAutobalanceEnabled()) {
        if (!this.freePlayers.contains(p)) {
          this.freePlayers.add(p);
        }
      } else {
        Team team = this.getLowestTeam();
        team.addPlayer(p);
      }

      if (BedwarsPRO.getInstance().getBooleanConfig("store-game-records", true)) {
        this.displayRecord(p);
      }

      if (this.isStartable()) {
        if (this.gameLobbyCountdown == null) {
          this.gameLobbyCountdown = new GameLobbyCountdown(this);
          this.gameLobbyCountdown.runTaskTimer(BedwarsPRO.getInstance(), 20L, 20L);
        }
      } else {
        if (!this.hasEnoughPlayers()) {
          int playersNeeded = this.getMinPlayers() - this.getPlayerAmount();
          for (Player aPlayer : this.getPlayers()) {
            if (aPlayer.isOnline()) {
              aPlayer.sendMessage(ChatWriter
                  .pluginMessage(
                      ChatColor.GREEN + BedwarsPRO._l(aPlayer, "lobby.moreplayersneeded", "count",
                          ImmutableMap.of("count", String.valueOf(playersNeeded)))));
            }
          }
        } else if (!this.hasEnoughTeams()) {
          for (Player aPlayer : this.getPlayers()) {
            if (aPlayer.isOnline()) {
              aPlayer.sendMessage(ChatWriter
                  .pluginMessage(ChatColor.RED + BedwarsPRO._l(aPlayer, "lobby.moreteamsneeded")));
            }
          }
        }
      }
    }

    BedwarsPlayerJoinedEvent joinEvent = new BedwarsPlayerJoinedEvent(this, null, p);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(joinEvent);

    this.updateScoreboard();
    this.updateSigns();
    return true;

  }

  public boolean playerLeave(Player p, boolean kicked) {
    PlayerSettings settings = this.getPlayerSettings(p);
    if (settings != null) {
      settings.setTeleporting(true);
    }
    Team team = this.getPlayerTeam(p);

    BedwarsPlayerLeaveEvent leaveEvent = new BedwarsPlayerLeaveEvent(this, p, team);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(leaveEvent);

    PlayerStatistic statistic = null;
    if (BedwarsPRO.getInstance().statisticsEnabled()) {
      statistic = BedwarsPRO.getInstance().getPlayerStatisticManager().getStatistic(p);
    }

    if (this.isSpectator(p)) {
      if (!this.getCycle().isEndGameRunning()) {
        for (Player player : this.getPlayers()) {
          if (player.equals(p)) {
            continue;
          }

          player.showPlayer(p);
          p.showPlayer(player);
        }
      }
    } else {
      if (this.state == GameState.RUNNING && !this.getCycle().isEndGameRunning()) {
        if (!team.isDead(this) && !p.isDead() && BedwarsPRO.getInstance().statisticsEnabled()
            && BedwarsPRO.getInstance().getBooleanConfig("statistics.player-leave-kills", false)) {
          statistic.setCurrentDeaths(statistic.getCurrentDeaths() + 1);
          statistic.setCurrentScore(statistic.getCurrentScore() + BedwarsPRO.getInstance()
              .getIntConfig("statistics.scores.die", 0));
          if (this.getPlayerDamager(p) != null) {
            PlayerStatistic killerPlayer = BedwarsPRO.getInstance().getPlayerStatisticManager()
                .getStatistic(this.getPlayerDamager(p));
            killerPlayer.setCurrentKills(killerPlayer.getCurrentKills() + 1);
            killerPlayer.setCurrentScore(killerPlayer.getCurrentScore() + BedwarsPRO.getInstance()
                .getIntConfig("statistics.scores.kill", 10));
          }
          statistic.setCurrentLoses(statistic.getCurrentLoses() + 1);
          statistic.setCurrentScore(statistic.getCurrentScore() + BedwarsPRO.getInstance()
              .getIntConfig("statistics.scores.lose", 0));
        }
      }
    }

    if (this.isProtected(p)) {
      this.removeProtection(p);
    }

    this.playerDamages.remove(p);
    if (team != null && BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p) != null
        && !BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p).isSpectator(p)) {
      if (kicked) {
        for (Player aPlayer : this.getPlayers()) {
          if (aPlayer.isOnline()) {
            aPlayer.sendMessage(
                ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                    ._l(aPlayer, "ingame.player.kicked", ImmutableMap.of("player",
                        Game.getPlayerWithTeamString(p, team, ChatColor.RED) + ChatColor.RED))));
          }
        }
      } else {
        for (Player aPlayer : this.getPlayers()) {
          if (aPlayer.isOnline()) {
            aPlayer.sendMessage(
                ChatWriter.pluginMessage(
                    ChatColor.RED + BedwarsPRO
                        ._l(aPlayer, "ingame.player.left", ImmutableMap.of("player",
                            Game.getPlayerWithTeamString(p, team, ChatColor.RED)
                                + ChatColor.RED))));
          }
        }
      }
    }
    // 无论是否观战者，离开时都从队伍计分板 entries 移除，防止人数残留
    if (team != null) {
      team.removePlayer(p);
    }

    BedwarsPRO.getInstance().getGameManager().removeGamePlayer(p);

    if (this.freePlayers.contains(p)) {
      this.freePlayers.remove(p);
    }

    if (BedwarsPRO.getInstance().isBungee()) {
      this.cycle.onPlayerLeave(p);
    }

    if (BedwarsPRO.getInstance().statisticsEnabled()) {

      if (BedwarsPRO.getInstance().isHologramsEnabled()
          && BedwarsPRO.getInstance().getHolographicInteractor() != null && BedwarsPRO.getInstance()
          .getHolographicInteractor().getType().equalsIgnoreCase("HolographicDisplays")) {
        BedwarsPRO.getInstance().getHolographicInteractor().updateHolograms(p);
      }

      if (BedwarsPRO.getInstance().getBooleanConfig("statistics.show-on-game-end", true)) {
        BedwarsPRO.getInstance().getServer().dispatchCommand(p, "bw stats");
      }
      BedwarsPRO.getInstance().getPlayerStatisticManager().storeStatistic(statistic);

      BedwarsPRO.getInstance().getPlayerStatisticManager().unloadStatistic(p);
    }

    PlayerStorage storage = this.playerStorages.get(p);
    if (storage != null) {
      storage.clean();
      storage.restore();
    }

    // 补发加入物品：游戏结束回大厅的路径是先传送到大厅再 playerLeave，
    // 传送时玩家仍在游戏中导致世界切换事件里的 apply 被跳过，且同世界再传送不会触发事件。
    // 此时玩家已从游戏中移除（removeGamePlayer），主动补发快捷物品（apply 幂等，已持有则跳过）
    io.jmmym.bedwarspro.joinitem.JoinItem.getInstance().apply(p);

    this.playerSettings.remove(p);
    this.updateScoreboard();

    try {
      p.setScoreboard(BedwarsPRO.getInstance().getScoreboardManager().getMainScoreboard());
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
    }

    this.removeNewItemShop(p);

    if (!BedwarsPRO.getInstance().isBungee() && p.isOnline()) {
      if (kicked) {
        p.sendMessage(
            ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(p, "ingame.player.waskicked")));
      } else {
        p.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(p, "success.left")));
      }
    }

    // 排位等待大厅：任何路径离开（右键粘液球/命令/断线/踢出）统一退出排位匹配队列，
    // 与 /bw leave 行为一致，避免玩家离开等待房后仍留在队列中被下次匹配带上。
    // 此时玩家已被 removeGamePlayer 移出游戏，RankMatchQueue.removePlayer 内部
    // 的 playerLeave 分支不会再触发（getGameOfPlayer 为 null），无递归风险；
    // removePlayer 幂等，未在队列时无副作用。
    if (io.jmmym.bedwarspro.rank.RankManager.getInstance() != null
        && this.state != GameState.RUNNING
        && io.jmmym.bedwarspro.rank.RankManager.getInstance().isRankedGame(this.name)) {
      boolean wasQueued = io.jmmym.bedwarspro.rank.RankManager.getInstance().getRankedQueue()
          .removePlayer(p);
      if (wasQueued && !kicked && p.isOnline()) {
        p.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "你已退出排位匹配队列！"));
      }
    }

    if (!BedwarsPRO.getInstance().isBungee()) {
      this.cycle.onPlayerLeave(p);
    }

    this.updateSigns();
    this.playerStorages.remove(p);
    return true;
  }

  public void removeJoinSign(Location location) {
    this.joinSigns.remove(location);
    this.updateSignConfig();
  }

  public void removeNewItemShop(Player player) {
    if (!this.newItemShops.containsKey(player)) {
      return;
    }

    this.newItemShops.remove(player);
  }

  public void removePlayerSettings(Player player) {
    this.playerSettings.remove(player);
  }

  public void removeProtection(Player player) {
    RespawnProtectionRunnable rpr = this.respawnProtections.get(player);
    if (rpr == null) {
      return;
    }

    try {
      rpr.cancel();
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      // isn't running, ignore
    }

    this.respawnProtections.remove(player);
  }

  public void removeRunningTask(BukkitTask task) {
    this.runningTasks.remove(task);
  }

  public void removeRunningTask(BukkitRunnable bukkitRunnable) {
    this.runningTasks.remove(bukkitRunnable);
  }

  public void removeSpecialItem(SpecialItem item) {
    this.specialItems.remove(item);
  }

  public void removeTeam(Team team) {
    this.teams.remove(team.getName());
    this.updateSigns();
  }

  public void resetRegion() {
    if (this.region == null) {
      return;
    }

    this.region.reset(this);
  }

  public void resetScoreboard() {
    this.timeLeft = BedwarsPRO.getInstance().getMaxLength();
    this.length = this.timeLeft;
    this.scoreboard.clearSlot(DisplaySlot.SIDEBAR);
  }

  public boolean run(CommandSender sender) {
    if (this.state != GameState.STOPPED) {
      sender
          .sendMessage(
              ChatWriter
                  .pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.cantstartagain")));
      return false;
    }

    GameCheckCode gcc = this.checkGame();
    if (gcc != GameCheckCode.OK) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + gcc.getCodeMessage()));
      return false;
    }

    if (sender instanceof Player) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(sender, "success.gamerun")));
    }

    this.isStopping = false;
    this.state = GameState.WAITING;
    this.updateSigns();
    return true;
  }

  public boolean saveGame(CommandSender sender, boolean direct) {
    BedwarsSaveGameEvent saveEvent = new BedwarsSaveGameEvent(this, sender);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(saveEvent);

    if (saveEvent.isCancelled()) {
      return true;
    }

    GameCheckCode check = this.checkGame();

    if (check != GameCheckCode.OK) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + check.getCodeMessage()));
      return false;
    }

    File gameConfig = new File(
        BedwarsPRO.getInstance().getDataFolder() + "/" + GameManager.gamesPath
            + "/" + this.name + "/game.yml");
    gameConfig.mkdirs();

    if (gameConfig.exists()) {
      gameConfig.delete();
    }

    this.saveRegion(direct);
    this.createGameConfig(gameConfig);

    return true;
  }

  public void saveRecord() {
    File gameConfig = new File(
        BedwarsPRO.getInstance().getDataFolder() + "/" + GameManager.gamesPath
            + "/" + this.name + "/game.yml");

    if (!gameConfig.exists()) {
      return;
    }

    this.config.set("record", this.record);
    if (BedwarsPRO.getInstance().getBooleanConfig("store-game-records-holder", true)) {
      this.config.set("record-holders", this.recordHolders);
    }

    try {
      this.config.save(gameConfig);
    } catch (IOException e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      e.printStackTrace();
    }
  }

  private void saveRegion(boolean direct) {
    if (this.region == null || direct) {
      if (this.regionName == null) {
        this.regionName = this.loc1.getWorld().getName();
      }

      this.region = new Region(this.loc1, this.loc2, this.regionName);
    }

    // nametag the villager
    this.region.setVillagerNametag();

    this.updateSigns();
  }

  public void setGameLobbyCountdown(GameLobbyCountdown countdown) {
    this.gameLobbyCountdown = countdown;
  }

  public void setLobby(Player sender) {
    Location lobby = sender.getLocation();

    if (this.region != null && this.region.getWorld().equals(lobby.getWorld())) {
      sender.sendMessage(
          ChatWriter
              .pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.lobbyongameworld")));
      return;
    }

    this.lobby = lobby;
    sender.sendMessage(
        ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(sender, "success.lobbyset")));
  }

  public void setLobby(Location lobby) {
    if (this.region != null) {
      if (this.region.getWorld().equals(lobby.getWorld())) {
        BedwarsPRO.getInstance().getServer().getConsoleSender().sendMessage(
            ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                ._l(BedwarsPRO.getInstance().getServer().getConsoleSender(),
                    "errors.lobbyongameworld")));
        return;
      }
    }

    this.lobby = lobby;
  }

  /*
   * PRIVATE
   */

  public void setLoc(Location loc, String type) {
    if (type.equalsIgnoreCase("loc1")) {
      this.loc1 = loc;
    } else {
      this.loc2 = loc;
    }
  }

  public void setMinPlayers(int players) {
    int max = this.getMaxPlayers();
    int minPlayers = players;

    if (max < players && max > 0) {
      minPlayers = max;
    }

    this.minPlayers = minPlayers;
  }

  public void setPlayerDamager(Player p, Player damager) {
    this.playerDamages.remove(p);
    this.playerDamages.put(p, damager);
  }

  public void setPlayerGameMode(Player player) {
    if (this.isSpectator(player)
        && !(this.getCycle() instanceof BungeeGameCycle && this.getCycle().isEndGameRunning()
        && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {

      player.setAllowFlight(true);
      player.setFlying(true);
      player.setGameMode(GameMode.SPECTATOR);

    } else {
      if (this.getState().equals(GameState.RUNNING)) {
        if (player.isOp() || player.hasPermission("bw.setup")) {
          if (player.getGameMode() == GameMode.CREATIVE) {
            player.setAllowFlight(true);
            player.setFlying(player.isFlying());
          } else {
            player.setGameMode(GameMode.SURVIVAL);
          }
        } else {
          player.setGameMode(GameMode.SURVIVAL);
        }
      } else if (this.getState().equals(GameState.WAITING)) {
        Integer gameMode = BedwarsPRO.getInstance().getIntConfig("lobby-gamemode", 0);
        if (gameMode == 0) {
          player.setGameMode(GameMode.SURVIVAL);
        } else if (gameMode == 1) {
          player.setGameMode(GameMode.CREATIVE);
        } else if (gameMode == 2) {
          player.setGameMode(GameMode.ADVENTURE);
        } else if (gameMode == 3) {
          player.setGameMode(GameMode.SPECTATOR);
        }
      }
    }
  }

  public void setPlayerVisibility(Player player) {
    ArrayList<Player> players = new ArrayList<Player>();
    players.addAll(this.getPlayers());

    if (this.state == GameState.RUNNING
        && !(this.getCycle() instanceof BungeeGameCycle && this.getCycle().isEndGameRunning()
        && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {
      if (this.isSpectator(player)) {
        if (player.getGameMode().equals(GameMode.SURVIVAL)) {
          for (Player playerInGame : players) {
            playerInGame.hidePlayer(player);
            player.showPlayer(playerInGame);
          }
        } else {
          for (Player teamPlayer : this.getTeamPlayers()) {
            teamPlayer.hidePlayer(player);
            player.showPlayer(teamPlayer);
          }
          for (Player freePlayer : this.getFreePlayers()) {
            freePlayer.showPlayer(player);
            player.showPlayer(freePlayer);
          }
        }
      } else {
        for (Player playerInGame : players) {
          playerInGame.showPlayer(player);
          player.showPlayer(playerInGame);
        }
      }
    } else {
      for (Player playerInGame : players) {
        if (!playerInGame.equals(player)) {
          playerInGame.showPlayer(player);
          player.showPlayer(playerInGame);
        }
      }
    }

  }

  public void setScoreboard(Scoreboard sb) {
    this.scoreboard = sb;
  }

  public void setState(GameState state) {
    this.state = state;
    this.updateSigns();
  }

  public boolean start(CommandSender sender) {
    if (this.state != GameState.WAITING) {
      sender.sendMessage(
          ChatWriter
              .pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.startoutofwaiting")));
      return false;
    }

    BedwarsGameStartEvent startEvent = new BedwarsGameStartEvent(this);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(startEvent);

    if (startEvent.isCancelled()) {
      return false;
    }

    this.isOver = false;

    // 立即取消大厅倒计时，防止与强制开始产生竞态
    if (this.gameLobbyCountdown != null) {
      this.gameLobbyCountdown.cancel();
      this.gameLobbyCountdown = null;
    }

    // load shop categories again (if shop was changed)
    this.loadItemShopCategories();

    this.runningTasks.clear();
    this.cleanUsersInventory();
    this.clearProtections();
    this.moveFreePlayersToTeam();
    this.makeTeamsReady();

    this.cycle.onGameStart();
    this.startResourceSpawners();

    // Update world time before game starts
    this.getRegion().getWorld().setTime(this.time);

    this.teleportPlayersToTeamSpawn();
    for (Player player : this.getPlayers()) {
      player.setMaxHealth(20.0);
      player.setHealth(20.0);
    }

    this.state = GameState.RUNNING;

    for (Player player : this.getPlayers()) {
      this.setPlayerGameMode(player);
      this.setPlayerVisibility(player);
    }

    this.startActionBarRunnable();
    this.updateScoreboard();

    if (BedwarsPRO.getInstance().getBooleanConfig("store-game-records", true)) {
      this.displayRecord();
    }

    this.startTimerCountdown();

    // 每5秒强制检查游戏是否应该结束（安全网）
    this.addRunningTask(new BukkitRunnable() {
      @Override
      public void run() {
        if (Game.this.state == GameState.RUNNING && !Game.this.isOver) {
          Game.this.getCycle().checkGameOver();
        }
      }
    }.runTaskTimer(BedwarsPRO.getInstance(), 100L, 100L));

    // Start Bot AI task runner
    if (BedwarsPRO.getInstance().getBotConfig().isEnabled() && !this.bots.isEmpty()) {
      BotTaskRunner botRunner = BedwarsPRO.getInstance().getBotManager().getOrCreateTaskRunner(this);
      org.bukkit.scheduler.BukkitTask task = botRunner.start();
      if (task != null) {
        this.addRunningTask(task);
      }
    }

    if (BedwarsPRO.getInstance().getBooleanConfig("titles.map.enabled", false)) {
      this.displayMapInfo();
    }

    this.updateSigns();

    BedwarsGameStartedEvent startedEvent = new BedwarsGameStartedEvent(this);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(startedEvent);

    return true;
  }

  private void startActionBarRunnable() {
    if (BedwarsPRO.getInstance().getBooleanConfig("show-team-in-actionbar", false)) {
      try {
        Class<?> clazz = Class.forName("io.jmmym.bedwarspro.com."
            + BedwarsPRO.getInstance().getCurrentVersion().toLowerCase() + ".ActionBar");
        final Method sendActionBar =
            clazz.getDeclaredMethod("sendActionBar", Player.class, String.class);

        BukkitTask task = new BukkitRunnable() {

          @Override
          public void run() {
            for (Team team : Game.this.getTeams().values()) {
              for (Player player : team.getPlayers()) {
                try {
                  sendActionBar.invoke(null, player,
                      team.getChatColor() + BedwarsPRO._l(player, "ingame.team") + " " + team
                          .getDisplayName());
                } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                  BedwarsPRO.getInstance().getBugsnag().notify(e);
                  e.printStackTrace();
                }
              }
            }
          }
        }.runTaskTimer(BedwarsPRO.getInstance(), 0L, 20L);
        this.addRunningTask(task);
      } catch (Exception ex) {
        BedwarsPRO.getInstance().getBugsnag().notify(ex);
        ex.printStackTrace();
      }
    }
  }

  private void startResourceSpawners() {
    for (ResourceSpawner rs : this.getRessourceSpawner()) {
      rs.setGame(this);
      this.runningTasks.add(BedwarsPRO.getInstance().getServer().getScheduler().runTaskTimer(
          BedwarsPRO.getInstance(), rs, Math.round((((double) rs.getInterval()) / 1000.0) * 20.0),
          Math.round((((double) rs.getInterval()) / 1000.0) * 20.0)));
    }
  }

  private void startTimerCountdown() {
    this.timeLeft = BedwarsPRO.getInstance().getMaxLength();
    this.length = BedwarsPRO.getInstance().getMaxLength();
    BukkitRunnable task = new BukkitRunnable() {

      @Override
      public void run() {
        Game.this.updateScoreboardTimer();
        if (Game.this.timeLeft == 0) {
          Game.this.isOver = true;
          Game.this.getCycle().checkGameOver();
          this.cancel();
          return;
        }

        // 经验模式事件：对局进行到第6分钟（timeLeft 降到 总时长-360 秒）触发一次，
        // 全员最大血量+5颗心，永久生效（死亡/重生不重置）
        if (!Game.this.xpHealthEventTriggered && XpManager.isXpMode(Game.this)
            && Game.this.timeLeft <= BedwarsPRO.getInstance().getMaxLength() - 360) {
          Game.this.xpHealthEventTriggered = true;
          Game.this.triggerXpHealthEvent();
        }

        Game.this.timeLeft--;
      }
    };

    this.runningTasks.add(task.runTaskTimer(BedwarsPRO.getInstance(), 0L, 20L));
  }

  /**
   * 经验模式血量事件：全体存活玩家最大血量上限+5颗心（20 -> 30），
   * 死亡/重生后依然保持，仅触发一次。
   */
  private void triggerXpHealthEvent() {
    for (Player player : this.getPlayers()) {
      if (player.isOnline() && !this.isSpectator(player)) {
        double diff = XP_HEALTH_EVENT_MAX_HEALTH - player.getMaxHealth();
        player.setMaxHealth(XP_HEALTH_EVENT_MAX_HEALTH);
        if (diff > 0) {
          double nhealth = player.getHealth() + diff;
          player.setHealth(nhealth > XP_HEALTH_EVENT_MAX_HEALTH ? XP_HEALTH_EVENT_MAX_HEALTH : nhealth);
        }
        io.jmmym.bedwarspro.itemaddon.utils.Utils.sendTitle(player, 10, 50, 10,
            "§e最大血量提升5颗心！", "§7全体玩家血量上限永久提升");
        player.sendMessage("§6[事件] §e全体玩家最大血量上限提升5颗心！");
      }
    }
  }

  public boolean stop() {
    if (this.state == GameState.STOPPED) {
      return false;
    }

    this.isStopping = true;

    this.stopWorkers();
    this.clearProtections();

    try {
      this.kickAllPlayers();
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      e.printStackTrace();
    }
    this.resetRegion();
    this.state = GameState.STOPPED;
    this.updateSigns();

    this.isStopping = false;
    return true;
  }

  public void stopWorkers() {
    for (BukkitTask task : this.runningTasks) {
      try {
        task.cancel();
      } catch (Exception ex) {
        BedwarsPRO.getInstance().getBugsnag().notify(ex);
        // already cancelled
      }
    }

    this.runningTasks.clear();
  }

  private void teleportPlayersToTeamSpawn() {
    for (Team team : this.teams.values()) {
      for (Player player : team.getPlayers()) {
        this.getPlayerSettings(player).setTeleporting(true);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0.0F);
        player.teleport(team.getSpawnLocation());
        if (this.getPlayerStorage(player) != null) {
          this.getPlayerStorage(player).clean();
        }
      }
    }
  }

  public void toSpectator(Player player) {
    final Player p = player;

    // 观战前先从原队伍移出，避免玩家名残留在队伍计分板 entries 中
    Team oldTeam = this.getPlayerTeam(player);
    if (oldTeam != null) {
      oldTeam.removePlayer(player);
    }

    if (!this.freePlayers.contains(player)) {
      this.freePlayers.add(player);
    }

    PlayerStorage storage = this.getPlayerStorage(player);
    if (storage != null) {
      storage.clean();
    } else {
      storage = this.addPlayerStorage(player);
      storage.store();
      storage.clean();
    }

    final Location location = this.getPlayerTeleportLocation(p);

    if (!p.getLocation().getWorld().equals(location.getWorld())) {
      this.getPlayerSettings(p).setTeleporting(true);
      if (BedwarsPRO.getInstance().isBungee()) {
        new BukkitRunnable() {

          @Override
          public void run() {
            p.teleport(location);
          }

        }.runTaskLater(BedwarsPRO.getInstance(), 10L);

      } else {
        p.teleport(location);
      }
    }

    new BukkitRunnable() {

      @Override
      public void run() {
        Game.this.setPlayerGameMode(p);
        Game.this.setPlayerVisibility(p);
      }

    }.runTaskLater(BedwarsPRO.getInstance(), 15L);

    // Leave game (Slimeball)
    ItemStack leaveGame = new ItemStack(Material.SLIME_BALL, 1);
    ItemMeta im = leaveGame.getItemMeta();
    im.setDisplayName(BedwarsPRO._l(player, "lobby.leavegame"));
    leaveGame.setItemMeta(im);
    p.getInventory().setItem(8, leaveGame);

    if (this.getCycle() instanceof BungeeGameCycle && this.getCycle().isEndGameRunning()
        && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true)) {
      p.updateInventory();
      return;
    }

    // Teleport to player (Compass)
    ItemStack teleportPlayer = new ItemStack(Material.COMPASS, 1);
    im = teleportPlayer.getItemMeta();
    im.setDisplayName(BedwarsPRO._l(p, "ingame.spectate"));
    teleportPlayer.setItemMeta(im);
    p.getInventory().setItem(0, teleportPlayer);

    p.updateInventory();
    this.updateScoreboard();

  }

  private void updateLobbyScoreboard() {
    this.scoreboard.clearSlot(DisplaySlot.SIDEBAR);

    Objective obj = this.scoreboard.getObjective("lobby");
    if (obj != null) {
      obj.unregister();
    }

    obj = this.scoreboard.registerNewObjective("lobby", "dummy");
    obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    obj.setDisplayName(this.formatLobbyScoreboardString(
        BedwarsPRO.getInstance().getStringConfig("lobby-scoreboard.title", "&eBEDWARS")));

    List<String> rows = BedwarsPRO.getInstance().getConfig()
        .getStringList("lobby-scoreboard.content");
    int rowMax = rows.size();
    if (rows == null || rows.isEmpty()) {
      return;
    }

    for (String row : rows) {
      if (row.trim().equals("")) {
        for (int i = 0; i <= rowMax; i++) {
          row = row + " ";
        }
      }

      Score score = obj.getScore(this.formatLobbyScoreboardString(row));
      score.setScore(rowMax);
      rowMax--;
    }

    for (Player player : this.getPlayers()) {
      player.setScoreboard(this.scoreboard);
    }
  }

  public void updateScoreboard() {
    return;
  }

  private void updateScoreboardTimer() {
    return;
  }

  private void updateSignConfig() {
    try {
      File config = new File(
          BedwarsPRO.getInstance().getDataFolder() + "/" + GameManager.gamesPath + "/"
              + this.name + "/sign.yml");

      YamlConfiguration cfg = new YamlConfiguration();
      if (config.exists()) {
        cfg = YamlConfiguration.loadConfiguration(config);
      }

      List<Map<String, Object>> locList = new ArrayList<Map<String, Object>>();
      for (Location loc : this.joinSigns.keySet()) {
        locList.add(Utils.locationSerialize(loc));
      }

      cfg.set("signs", locList);
      cfg.save(config);
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      BedwarsPRO.getInstance().getServer().getConsoleSender()
          .sendMessage(ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
              ._l(BedwarsPRO.getInstance().getServer().getConsoleSender(), "errors.savesign")));
    }
  }

  public void updateSigns() {
    boolean removedItem = false;

    Iterator<GameJoinSign> iterator = Game.this.joinSigns.values().iterator();
    while (iterator.hasNext()) {
      GameJoinSign sign = iterator.next();

      Chunk signChunk = sign.getSign().getLocation().getChunk();
      if (!signChunk.isLoaded()) {
        signChunk.load(true);
      }

      if (sign.getSign() == null) {
        iterator.remove();
        removedItem = true;
        continue;
      }

      Block signBlock = sign.getSign().getLocation().getBlock();
      if (!(signBlock.getState() instanceof Sign)) {
        iterator.remove();
        removedItem = true;
        continue;
      }
      sign.updateSign();
    }

    if (removedItem) {
      Game.this.updateSignConfig();
    }
  }
}
