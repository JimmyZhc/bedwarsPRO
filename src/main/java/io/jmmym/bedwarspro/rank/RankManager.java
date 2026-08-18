package io.jmmym.bedwarspro.rank;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 排位赛核心管理器（单例）。
 *
 * <p>负责：排位地图配置（rank/rank.yml 的 ranked-games）、消息（rank/messages.yml）加载、
 * 排位匹配队列调度、玩家排位数据的读写入口。所有排位相关模块均通过本类协作。</p>
 */
public class RankManager {

  private static final RankManager INSTANCE = new RankManager();

  private BedwarsPRO plugin = null;
  private RankStorage storage = null;
  private RankMatchQueue rankedQueue = null;
  private File configFile = null;
  private YamlConfiguration config = null;
  /** 排位地图名（由 rank.yml 的 ranked-games 列表持久化，GUI /bwpro mapgui 可切换）。 */
  private final Set<String> rankedGames = new HashSet<>();

  public static RankManager getInstance() {
    return RankManager.INSTANCE;
  }

  /** 初始化（插件启用时调用一次）。 */
  public void init(BedwarsPRO plugin) {
    this.plugin = plugin;
    File folder = new File(plugin.getDataFolder(), "rank");
    if (!folder.exists()) {
      folder.mkdirs();
    }
    this.configFile = new File(folder, "rank.yml");
    if (!this.configFile.exists()) {
      plugin.saveResource("rank/rank.yml", false);
    }
    this.loadConfig();

    RankMessages.init(plugin);
    this.storage = new RankStorage();
    this.rankedQueue = new RankMatchQueue();
    this.rankedQueue.startTicker();

    // 启动提示：与每日任务/玩家统计一致，打印排位数据存储状态
    int pending = this.storage.getPendingCount();
    if (pending > 0) {
      plugin.getLogger().info("[排位赛] 有 " + pending
          + " 名玩家的排位数据尚未上传数据库，将在后台自动重试上传。");
    } else if (this.storage.isDbAvailable()) {
      plugin.getLogger().info("[排位赛] 数据库连接正常，排位数据将同步存储至数据库。");
    } else {
      plugin.getLogger().info("[排位赛] 数据库未配置或连接失败，排位数据将仅保存在本地！");
    }
    // 重试上次启动未上传成功的排位数据（失败保留待下次再试）
    this.storage.retryPendingUploads();
    // 启动时异步确保数据库表存在并补齐 updated_at 列（网站端排行榜显示更新时间）
    this.storage.initTableAsync();

    plugin.getLogger().info("[排位赛] 排位系统初始化完成，排位图: " + this.rankedGames);
  }

  /** 插件关闭时调用：停止队列调度并保存全部数据。 */
  public void shutdown() {
    if (this.rankedQueue != null) {
      this.rankedQueue.stopTicker();
    }
    if (this.storage != null) {
      this.storage.saveAll();
    }
  }

  /** 重新加载配置与消息文件（/bwpro rankreload）。 */
  public void reload() {
    this.loadConfig();
    RankMessages.load();
    if (this.storage != null) {
      this.storage.reload();
    }
  }

  private void loadConfig() {
    if (this.configFile == null) {
      return;
    }
    this.config = YamlConfiguration.loadConfiguration(this.configFile);
    // 读取自定义名称（段位名称），未配置的保持默认
    RankTier.loadNames(this.config);
    // 读取排位地图配置（ranked-games 列表）
    this.rankedGames.clear();
    if (this.config.isList("ranked-games")) {
      this.rankedGames.addAll(this.config.getStringList("ranked-games"));
    }
  }

  // ===== 数据入口 =====

  public RankStorage getStorage() {
    return this.storage;
  }

  public RankMatchQueue getRankedQueue() {
    return this.rankedQueue;
  }

  /** 获取玩家的排位数据（不存在则创建默认数据）。 */
  public RankPlayer getPlayer(UUID uuid, String name) {
    return this.storage.load(uuid, name);
  }

  // ===== 配置读取 =====

  public int getMatchPlayers() {
    return this.getInt("match.players", 16);
  }

  /** 当前配置为排位图的地图名（副本，用于 GUI 展示）。 */
  public Set<String> getRankedGames() {
    return new HashSet<>(this.rankedGames);
  }

  /**
   * 设置某地图是否为排位图（持久化到 rank.yml 的 ranked-games 列表并立即生效）。
   * 切换后：排位队列将只在新排位图中选图；原排位图中未开局的等待玩家会被队列
   * 调度器在下个周期自动移出。
   */
  public void setRankedGame(String gameName, boolean ranked) {
    if (gameName == null || gameName.isEmpty()) {
      return;
    }
    if (ranked) {
      this.rankedGames.add(gameName);
    } else {
      this.rankedGames.remove(gameName);
    }
    if (this.config != null) {
      this.config.set("ranked-games", new ArrayList<>(this.rankedGames));
      this.saveConfig();
    }
  }

  /** 该地图是否为排位图（由 rank.yml 的 ranked-games 配置决定）。 */
  public boolean isRankedGame(String gameName) {
    return gameName != null && this.rankedGames.contains(gameName);
  }

  public int getMatchThreshold() {
    return this.getInt("elo.threshold", 500);
  }

  public int getKFactorNew() {
    return this.getInt("elo.k-factor-new", 40);
  }

  public int getKFactorLow() {
    return this.getInt("elo.k-factor-low", 25);
  }

  public int getKFactorNormal() {
    return this.getInt("elo.k-factor-normal", 20);
  }

  public int getKFactorHigh() {
    return this.getInt("elo.k-factor-high", 16);
  }

  /** 高分段 K 因子生效的 ELO 阈值（ELO &gt; 该值）。 */
  public int getKFactorHighEloThreshold() {
    return this.getInt("elo.k-factor-high-elo", 1500);
  }

  /** 新玩家 K 因子生效的场次数（前 N 场）。 */
  public int getPlacementGames() {
    return this.getInt("elo.placement-games", 10);
  }

  /** 定级赛场次（前 N 场视为定级赛）。 */
  public int getPlacementMatches() {
    return this.getInt("elo.placement-matches", 5);
  }

  public int getMinElo() {
    return this.getInt("elo.min-elo", 0);
  }

  /** 队列人数不足提示等待时间（秒）。 */
  public int getLowWaitTimeout() {
    return this.getInt("queue.low-wait-timeout", 180);
  }

  public int getInt(String path, int def) {
    if (this.config == null || !this.config.contains(path)) {
      return def;
    }
    return this.config.getInt(path, def);
  }

  public boolean getBoolean(String path, boolean def) {
    if (this.config == null || !this.config.contains(path)) {
      return def;
    }
    return this.config.getBoolean(path, def);
  }

  public String getString(String path, String def) {
    if (this.config == null || !this.config.contains(path)) {
      return def;
    }
    return this.config.getString(path, def);
  }

  /** 保存配置（备用，通常配置由管理员直接编辑文件）。 */
  public void saveConfig() {
    if (this.config == null || this.configFile == null) {
      return;
    }
    try {
      this.config.save(this.configFile);
    } catch (IOException e) {
      this.plugin.getBugsnag().notify(e);
    }
  }
}
