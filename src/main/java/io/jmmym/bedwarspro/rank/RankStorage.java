package io.jmmym.bedwarspro.rank;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 排位数据存储。
 *
 * <p>以本地 YAML（rank/players.yml）为主存储；当服务器连接数据库时，
 * 每次保存自动把该玩家信息同步写入数据库表 bw_rank_players（自动建表）。
 * 上传失败时该玩家数据会写入待上传队列（rank/pending-upload.yml），
 * 下次启动时自动重试，保证数据最终入库。读取一律以本地 YAML 为准，
 * 保证无数据库环境下功能完整。</p>
 */
public class RankStorage {

  private final File dataFile;
  private YamlConfiguration data;
  private final Map<UUID, RankPlayer> cache = new HashMap<>();

  /** 待上传队列文件：数据库不可用时暂存玩家快照，下次启动自动重试上传。 */
  private final File pendingFile;
  private YamlConfiguration pending;

  public RankStorage() {
    File folder = new File(BedwarsPRO.getInstance().getDataFolder(), "rank");
    if (!folder.exists()) {
      folder.mkdirs();
    }
    this.dataFile = new File(folder, "players.yml");
    if (!this.dataFile.exists()) {
      try {
        this.dataFile.createNewFile();
      } catch (IOException e) {
        BedwarsPRO.getInstance().getBugsnag().notify(e);
      }
    }
    this.pendingFile = new File(folder, "pending-upload.yml");
    if (!this.pendingFile.exists()) {
      try {
        this.pendingFile.createNewFile();
      } catch (IOException e) {
        BedwarsPRO.getInstance().getBugsnag().notify(e);
      }
    }
    this.reload();
    this.pending = YamlConfiguration.loadConfiguration(this.pendingFile);
  }

  /** 从磁盘重载 YAML（reload 命令使用）。 */
  public void reload() {
    this.data = YamlConfiguration.loadConfiguration(this.dataFile);
  }

  /** 数据库是否可用（连接池已初始化）。 */
  public boolean isDbAvailable() {
    return BedwarsPRO.getInstance().getDatabaseManager() != null;
  }

  /** 待上传队列中的玩家数量（启动时用于提示）。 */
  public int getPendingCount() {
    return this.pending == null ? 0 : this.pending.getKeys(false).size();
  }

  /** 获取已缓存的玩家数据（未加载返回 null）。 */
  public RankPlayer getLoaded(UUID uuid) {
    return this.cache.get(uuid);
  }

  /**
   * 获取全服排位 ELO 排行榜（按 ELO 降序）。
   *
   * <p>以本地 YAML 为准（数据库仅作同步副本），无数据库环境同样可用。</p>
   *
   * @param limit 返回条数上限（至少 1）
   */
  public List<RankPlayer> getEloLeaderboard(int limit) {
    List<RankPlayer> result = new ArrayList<>();
    for (String key : this.data.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(key);
        RankPlayer p = this.load(uuid, this.data.getString(key + ".name", ""));
        if (p != null) {
          result.add(p);
        }
      } catch (IllegalArgumentException ignored) {
        // 非法 uuid key，跳过
      }
    }
    result.sort(Comparator.comparingInt(RankPlayer::getElo).reversed());
    if (result.size() > limit) {
      result = new ArrayList<>(result.subList(0, Math.max(1, limit)));
    }
    return result;
  }

  /** 加载玩家数据（优先缓存，其次本地 YAML；本地无该玩家时跨服兜底查数据库；都不存在则新建默认数据）。 */
  public RankPlayer load(UUID uuid, String name) {
    RankPlayer cached = this.cache.get(uuid);
    if (cached != null) {
      return cached;
    }

    RankPlayer p = new RankPlayer(uuid, name);
    String path = uuid.toString();
    if (this.data.contains(path)) {
      p.setName(this.data.getString(path + ".name", name));
      p.setElo(this.data.getInt(path + ".elo", 0));
      p.setGamesPlayed(this.data.getInt(path + ".games", 0));
      p.setWins(this.data.getInt(path + ".wins", 0));
      p.setSecondCount(this.data.getInt(path + ".second", 0));
      p.setThirdCount(this.data.getInt(path + ".third", 0));
      p.setFourthCount(this.data.getInt(path + ".fourth", 0));
      p.setHighestElo(this.data.getInt(path + ".highest-elo", p.getElo()));
      p.setKills(this.data.getInt(path + ".kills", 0));
      p.setBedsDestroyed(this.data.getInt(path + ".beds", 0));
      p.setWinStreak(this.data.getInt(path + ".win-streak", 0));
      p.setLoseStreak(this.data.getInt(path + ".lose-streak", 0));
    } else {
      // 本地无该玩家数据：跨服玩家首次在本服出现，从共享数据库兜底读取
      // （A 服结算写入数据库后，玩家转到 B 服应能读到同一份 ELO）
      RankPlayer db = this.loadFromDb(uuid);
      if (db != null) {
        p = db;
        // 落盘到本服本地，后续读取无需再查库
        this.writeLocal(p);
      }
    }

    this.cache.put(uuid, p);
    return p;
  }

  /** 保存玩家数据：写本地 YAML，数据库可用时自动异步同步。 */
  public void save(RankPlayer p) {
    if (p == null || p.getUuid() == null) {
      return;
    }
    this.writeLocal(p);

    if (this.isDbAvailable()) {
      this.syncToDbAsync(p);
    } else {
      // 数据库不可用：保存到待上传队列，等下次启动重试
      this.writePending(p);
    }
  }

  /** 把玩家数据写入本地 YAML（主存储）。 */
  private void writeLocal(RankPlayer p) {
    if (p == null || p.getUuid() == null) {
      return;
    }
    String path = p.getUuid().toString();
    this.data.set(path + ".name", p.getName());
    this.data.set(path + ".elo", p.getElo());
    this.data.set(path + ".games", p.getGamesPlayed());
    this.data.set(path + ".wins", p.getWins());
    this.data.set(path + ".second", p.getSecondCount());
    this.data.set(path + ".third", p.getThirdCount());
    this.data.set(path + ".fourth", p.getFourthCount());
    this.data.set(path + ".highest-elo", p.getHighestElo());
    this.data.set(path + ".kills", p.getKills());
    this.data.set(path + ".beds", p.getBedsDestroyed());
    this.data.set(path + ".win-streak", p.getWinStreak());
    this.data.set(path + ".lose-streak", p.getLoseStreak());
    this.saveFile();
  }

  /** 保存全部缓存数据（插件关闭时调用）。 */
  public void saveAll() {
    if (this.cache.isEmpty()) {
      return;
    }
    this.saveFile();
    if (!this.isDbAvailable()) {
      // 数据库不可用：全部进入待上传队列（同步写入，保证关闭前落盘）
      for (RankPlayer p : this.cache.values()) {
        this.writePending(p);
      }
    } else {
      for (RankPlayer p : this.cache.values()) {
        this.syncToDbAsync(p);
      }
    }
  }

  private void saveFile() {
    try {
      this.data.save(this.dataFile);
    } catch (IOException e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
    }
  }

  // ==================== 待上传队列 ====================

  /** 把玩家数据快照写入待上传队列文件。 */
  private void writePending(RankPlayer p) {
    if (p == null || p.getUuid() == null) {
      return;
    }
    String path = p.getUuid().toString();
    this.pending.set(path + ".name", p.getName());
    this.pending.set(path + ".elo", p.getElo());
    this.pending.set(path + ".games", p.getGamesPlayed());
    this.pending.set(path + ".wins", p.getWins());
    this.pending.set(path + ".second", p.getSecondCount());
    this.pending.set(path + ".third", p.getThirdCount());
    this.pending.set(path + ".fourth", p.getFourthCount());
    this.pending.set(path + ".highest-elo", p.getHighestElo());
    this.pending.set(path + ".kills", p.getKills());
    this.pending.set(path + ".beds", p.getBedsDestroyed());
    this.pending.set(path + ".win-streak", p.getWinStreak());
    this.pending.set(path + ".lose-streak", p.getLoseStreak());
    this.pending.set(path + ".queued", System.currentTimeMillis());
    savePending();
  }

  private void savePending() {
    try {
      this.pending.save(this.pendingFile);
    } catch (IOException e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
    }
  }

  /** 从待上传队列反序列化玩家快照。 */
  private RankPlayer deserializePending(UUID uuid) {
    String path = uuid.toString();
    if (!this.pending.contains(path)) {
      return null;
    }
    RankPlayer p = new RankPlayer(uuid, this.pending.getString(path + ".name", ""));
    p.setElo(this.pending.getInt(path + ".elo", 0));
    p.setGamesPlayed(this.pending.getInt(path + ".games", 0));
    p.setWins(this.pending.getInt(path + ".wins", 0));
    p.setSecondCount(this.pending.getInt(path + ".second", 0));
    p.setThirdCount(this.pending.getInt(path + ".third", 0));
    p.setFourthCount(this.pending.getInt(path + ".fourth", 0));
    p.setHighestElo(this.pending.getInt(path + ".highest-elo", 0));
    p.setKills(this.pending.getInt(path + ".kills", 0));
    p.setBedsDestroyed(this.pending.getInt(path + ".beds", 0));
    p.setWinStreak(this.pending.getInt(path + ".win-streak", 0));
    p.setLoseStreak(this.pending.getInt(path + ".lose-streak", 0));
    return p;
  }

  /**
   * 启动时重试上传待上传队列中的玩家数据。
   * 上传成功的条目从队列移除；失败的保留，待下次启动继续重试。
   */
  public void retryPendingUploads() {
    final Set<String> keys = new HashSet<>(this.pending.getKeys(false));
    if (keys.isEmpty()) {
      return;
    }
    final int count = keys.size();
    Bukkit.getScheduler().runTaskAsynchronously(BedwarsPRO.getInstance(), new Runnable() {
      @Override
      public void run() {
        int success = 0;
        for (String key : keys) {
          UUID uuid;
          try {
            uuid = UUID.fromString(key);
          } catch (IllegalArgumentException e) {
            pending.set(key, null);
            continue;
          }
          RankPlayer p = deserializePending(uuid);
          if (p == null) {
            pending.set(key, null);
            continue;
          }
          if (writeToDb(p)) {
            pending.set(key, null);
            success++;
          }
        }
        savePending();
        if (success == count) {
          BedwarsPRO.getInstance().getLogger().info(
              "[排位赛] 待上传的排位数据已全部上传成功（" + count + " 名玩家）。");
        } else if (success > 0) {
          BedwarsPRO.getInstance().getLogger().warning(
              "[排位赛] 排位数据部分上传成功（" + success + "/" + count
                  + "），其余已保留待下次启动重试。");
        } else {
          BedwarsPRO.getInstance().getLogger().warning(
              "[排位赛] 排位数据上传失败（数据库仍不可用），已保留待下次启动重试。");
        }
      }
    });
  }

  // ==================== 数据库读写 ====================

  /**
   * 从数据库读取单个玩家数据（跨服共享）。
   *
   * <p>各服务器连接同一数据库时，A 服结算写入的 ELO 等数据可在此被 B 服读取，
   * 实现跨服数据一致。数据库不可用或查无记录时返回 null（调用方回退本地默认值）。</p>
   */
  private RankPlayer loadFromDb(UUID uuid) {
    if (uuid == null || !this.isDbAvailable()) {
      return null;
    }
    Connection conn = null;
    try {
      conn = BedwarsPRO.getInstance().getDatabaseManager().getConnection();
      if (conn == null) {
        return null;
      }
      String prefix = BedwarsPRO.getInstance().getDatabaseManager().getTablePrefix();
      PreparedStatement ps = conn.prepareStatement(
          "SELECT `name`, `elo`, `games`, `wins`, `second_count`, `third_count`, "
          + "`fourth_count`, `highest_elo`, `kills`, `beds`, `win_streak`, `lose_streak` "
          + "FROM `" + prefix + "rank_players` WHERE `uuid` = ?");
      ps.setString(1, uuid.toString());
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        RankPlayer p = new RankPlayer(uuid, rs.getString("name"));
        p.setElo(rs.getInt("elo"));
        p.setGamesPlayed(rs.getInt("games"));
        p.setWins(rs.getInt("wins"));
        p.setSecondCount(rs.getInt("second_count"));
        p.setThirdCount(rs.getInt("third_count"));
        p.setFourthCount(rs.getInt("fourth_count"));
        p.setHighestElo(rs.getInt("highest_elo"));
        p.setKills(rs.getInt("kills"));
        p.setBedsDestroyed(rs.getInt("beds"));
        p.setWinStreak(rs.getInt("win_streak"));
        p.setLoseStreak(rs.getInt("lose_streak"));
        rs.close();
        ps.close();
        return p;
      }
      rs.close();
      ps.close();
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception ignored) {
        }
      }
    }
    return null;
  }

  /** 确保建表 SQL（含 updated_at 列）。 */
  private String createTableSql(String prefix) {
    return "CREATE TABLE IF NOT EXISTS `" + prefix + "rank_players` ("
        + "`uuid` varchar(36) NOT NULL,"
        + "`name` varchar(32) NOT NULL,"
        + "`elo` int(11) NOT NULL DEFAULT '0',"
        + "`games` int(11) NOT NULL DEFAULT '0',"
        + "`wins` int(11) NOT NULL DEFAULT '0',"
        + "`second_count` int(11) NOT NULL DEFAULT '0',"
        + "`third_count` int(11) NOT NULL DEFAULT '0',"
        + "`fourth_count` int(11) NOT NULL DEFAULT '0',"
        + "`highest_elo` int(11) NOT NULL DEFAULT '0',"
        + "`kills` int(11) NOT NULL DEFAULT '0',"
        + "`beds` int(11) NOT NULL DEFAULT '0',"
        + "`win_streak` int(11) NOT NULL DEFAULT '0',"
        + "`lose_streak` int(11) NOT NULL DEFAULT '0',"
        + "`updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
        + "PRIMARY KEY (`uuid`))";
  }

  /**
   * 启动时异步确保数据库表存在并补齐 updated_at 列（兼容旧版插件建的表），
   * 使网站端排行榜能直接显示真实更新时间，无需等待对局结算触发。
   */
  public void initTableAsync() {
    if (!this.isDbAvailable()) {
      return;
    }
    Bukkit.getScheduler().runTaskAsynchronously(BedwarsPRO.getInstance(), new Runnable() {
      @Override
      public void run() {
        Connection conn = null;
        try {
          conn = BedwarsPRO.getInstance().getDatabaseManager().getConnection();
          if (conn == null) {
            return;
          }
          String prefix = BedwarsPRO.getInstance().getDatabaseManager().getTablePrefix();
          PreparedStatement create = conn.prepareStatement(createTableSql(prefix));
          create.execute();
          create.close();
          ensureUpdatedAtColumn(conn, prefix + "rank_players");
        } catch (Exception e) {
          BedwarsPRO.getInstance().getBugsnag().notify(e);
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (Exception ignored) {
            }
          }
        }
      }
    });
  }

  /** 异步把单个玩家数据写入数据库，失败时进入待上传队列。 */
  private void syncToDbAsync(final RankPlayer p) {
    Bukkit.getScheduler().runTaskAsynchronously(BedwarsPRO.getInstance(), new Runnable() {
      @Override
      public void run() {
        if (!writeToDb(p)) {
          // 上传失败：保存到本地待上传队列，等下次启动重试
          writePending(p);
        }
      }
    });
  }

  /** 同步把单个玩家数据写入数据库（自动建表 + UPSERT + updated_at）。 */
  private boolean writeToDb(RankPlayer p) {
    if (p == null || p.getUuid() == null) {
      return true;
    }
    Connection conn = null;
    try {
      conn = BedwarsPRO.getInstance().getDatabaseManager().getConnection();
      if (conn == null) {
        return false;
      }
      String prefix = BedwarsPRO.getInstance().getDatabaseManager().getTablePrefix();
      PreparedStatement create = conn.prepareStatement(createTableSql(prefix));
      create.execute();
      create.close();

      // 兼容旧表：若缺少 updated_at 列则补充（网站端排行榜展示更新时间）
      if (!this.ensureUpdatedAtColumn(conn, prefix + "rank_players")) {
        return false;
      }

      PreparedStatement ps = conn.prepareStatement("INSERT INTO `" + prefix
          + "rank_players`(`uuid`, `name`, `elo`, `games`, `wins`, `second_count`, "
          + "`third_count`, `fourth_count`, `highest_elo`, `kills`, `beds`, `win_streak`, "
          + "`lose_streak`, `updated_at`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
          + "ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `elo`=VALUES(`elo`), "
          + "`games`=VALUES(`games`), `wins`=VALUES(`wins`), "
          + "`second_count`=VALUES(`second_count`), `third_count`=VALUES(`third_count`), "
          + "`fourth_count`=VALUES(`fourth_count`), `highest_elo`=VALUES(`highest_elo`), "
          + "`kills`=VALUES(`kills`), `beds`=VALUES(`beds`), "
          + "`win_streak`=VALUES(`win_streak`), `lose_streak`=VALUES(`lose_streak`), "
          + "`updated_at`=NOW()");
      ps.setString(1, p.getUuid().toString());
      ps.setString(2, p.getName());
      ps.setInt(3, p.getElo());
      ps.setInt(4, p.getGamesPlayed());
      ps.setInt(5, p.getWins());
      ps.setInt(6, p.getSecondCount());
      ps.setInt(7, p.getThirdCount());
      ps.setInt(8, p.getFourthCount());
      ps.setInt(9, p.getHighestElo());
      ps.setInt(10, p.getKills());
      ps.setInt(11, p.getBedsDestroyed());
      ps.setInt(12, p.getWinStreak());
      ps.setInt(13, p.getLoseStreak());
      ps.execute();
      ps.close();
      return true;
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      return false;
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  /** 检测并补充 updated_at 列（兼容旧版本建立的表）。 */
  private boolean ensureUpdatedAtColumn(Connection conn, String table) {
    try {
      PreparedStatement check = conn.prepareStatement(
          "SELECT COUNT(*) FROM information_schema.COLUMNS "
              + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'updated_at'");
      check.setString(1, table);
      ResultSet rs = check.executeQuery();
      boolean exists = rs.next() && rs.getInt(1) > 0;
      rs.close();
      check.close();
      if (!exists) {
        PreparedStatement alter = conn.prepareStatement("ALTER TABLE `" + table
            + "` ADD COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP "
            + "ON UPDATE CURRENT_TIMESTAMP");
        alter.execute();
        alter.close();
      }
      return true;
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      return false;
    }
  }
}
