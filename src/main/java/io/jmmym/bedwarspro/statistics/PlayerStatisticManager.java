package io.jmmym.bedwarspro.statistics;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.database.DatabaseManager;
import io.jmmym.bedwarspro.events.BedwarsSavePlayerStatisticEvent;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class PlayerStatisticManager {

  private File databaseFile = null;
  private FileConfiguration fileDatabase = null;
  private Map<UUID, PlayerStatistic> playerStatistic = null;

  public PlayerStatisticManager() {
    this.playerStatistic = new HashMap<>();
    this.fileDatabase = null;
  }

  public List<String> createStatisticLines(PlayerStatistic playerStatistic, boolean withPrefix,
      ChatColor nameColor,
      ChatColor valueColor) {
    return this.createStatisticLines(playerStatistic, withPrefix, nameColor.toString(),
        valueColor.toString());
  }

  public List<String> createStatisticLines(PlayerStatistic playerStatistic, boolean withPrefix,
      String nameColor,
      String valueColor) {
    List<String> lines = new ArrayList<>();

    lines.add(this.getStatisticLine("name", playerStatistic.getName(), null, withPrefix, nameColor,
        valueColor));
    lines.add(this.getStatisticLine("kills",
        playerStatistic.getKills() + playerStatistic.getCurrentKills(),
        playerStatistic.getCurrentKills(), withPrefix, nameColor,
        valueColor));
    lines.add(this.getStatisticLine("deaths",
        playerStatistic.getDeaths() + playerStatistic.getCurrentDeaths(),
        playerStatistic.getCurrentDeaths(), withPrefix, nameColor,
        valueColor));
    Double kdDifference = playerStatistic.getCurrentKD() - playerStatistic.getKD();
    DecimalFormat df = new DecimalFormat("#.##");
    kdDifference = Double.valueOf(df.format(kdDifference));
    lines.add(
        this.getStatisticLine("kd", playerStatistic.getCurrentKD(), kdDifference, withPrefix,
            nameColor,
            valueColor));
    lines.add(
        this.getStatisticLine("wins", playerStatistic.getWins() + playerStatistic.getCurrentWins(),
            playerStatistic.getCurrentWins(), withPrefix, nameColor,
            valueColor));
    lines.add(this.getStatisticLine("loses",
        playerStatistic.getLoses() + playerStatistic.getCurrentLoses(),
        playerStatistic.getCurrentLoses(), withPrefix, nameColor,
        valueColor));
    lines.add(this.getStatisticLine("games",
        playerStatistic.getGames() + playerStatistic.getCurrentGames(),
        playerStatistic.getCurrentGames(), withPrefix, nameColor,
        valueColor));
    lines.add(this.getStatisticLine("destroyedBeds",
        playerStatistic.getDestroyedBeds() + playerStatistic.getCurrentDestroyedBeds(),
        playerStatistic.getCurrentDestroyedBeds(), withPrefix, nameColor,
        valueColor));
    lines.add(this.getStatisticLine("score",
        playerStatistic.getScore() + playerStatistic.getCurrentScore(),
        playerStatistic.getCurrentScore(), withPrefix, nameColor,
        valueColor));

    return lines;
  }

  private String getComparisonString(Double value) {
    if (value > 0) {
      return ChatColor.GREEN + "+" + value;
    } else if (value < 0) {
      return ChatColor.RED + String.valueOf(value);
    } else {
      return String.valueOf(value);
    }
  }

  private String getComparisonString(Integer value) {
    if (value > 0) {
      return ChatColor.GREEN + "+" + value;
    } else if (value < 0) {
      return ChatColor.RED + String.valueOf(value);
    } else {
      return String.valueOf(value);
    }
  }

  public PlayerStatistic getStatistic(OfflinePlayer player) {
    if (player == null) {
      return null;
    }

    if (!this.playerStatistic.containsKey(player.getUniqueId())) {
      return this.loadStatistic(player.getUniqueId());
    }

    return this.playerStatistic.get(player.getUniqueId());
  }

  private String getStatisticLine(String name, Object value1, Object value2, Boolean withPrefix,
      String nameColor,
      String valueColor) {
    String line;
    if (value2 != null && value2 instanceof Integer && (int) value2 != 0) {
      line = nameColor + BedwarsPRO._l("stats." + name) + ": "
          + valueColor + value1 + " " + nameColor + "(" + this.getComparisonString((int) value2)
          + nameColor + ")";
    } else if (value2 != null && value2 instanceof Double && (double) value2 != 0.00) {
      line = nameColor + BedwarsPRO._l("stats." + name) + ": "
          + valueColor + value1 + " " + nameColor + "(" + this.getComparisonString((double) value2)
          + nameColor + ")";
    } else {
      line = nameColor + BedwarsPRO._l("stats." + name) + ": "
          + valueColor + value1;
    }
    if (withPrefix) {
      line = ChatWriter.pluginMessage(line);
    }
    return line;
  }

  /**
   * 实际生效的存储类型：配置为 DATABASE 但数据库连接不可用时，
   * 自动降级为本地文件（YAML），避免统计数据无法写入/读取导致全部为 0。
   */
  public StorageType getEffectiveStorageType() {
    StorageType configured = BedwarsPRO.getInstance().getStatisticStorageType();
    if (configured == StorageType.DATABASE
        && BedwarsPRO.getInstance().getDatabaseManager() == null) {
      return StorageType.YAML;
    }
    return configured;
  }

  public void initialize() {
    StorageType storage = getEffectiveStorageType();
    if (storage == StorageType.YAML) {
      if (!BedwarsPRO.getInstance().getBooleanConfig("statistics.enabled", false)) {
        return;
      }
      File file = new File(
          BedwarsPRO.getInstance().getDataFolder() + "/database/bw_stats_players.yml");
      this.loadYml(file);
    }
    if (storage == StorageType.DATABASE) {
      // 插件启用时自动检查并创建统计数据表
      // （不依赖 statistics.enabled 开关：只要存储类型为 DATABASE 就确保表存在，
      //   否则 /bw stats 读取时会报 Table doesn't exist）
      this.ensureTableExists();
    }
  }

  /**
   * 确保统计数据表存在。
   *
   * <p>使用 CREATE TABLE IF NOT EXISTS，表已存在时自动跳过；
   * 若数据库未配置或连接失败（FILE/YAML 模式）则静默忽略，不影响插件运行。</p>
   */
  public void ensureTableExists() {
    DatabaseManager db = BedwarsPRO.getInstance().getDatabaseManager();
    if (db == null) {
      // 数据库未启用/未配置：FILE 模式，忽略数据库相关错误
      return;
    }
    try (Connection connection = db.getConnection()) {
      if (connection == null) {
        BedwarsPRO.getInstance().getLogger().warning(
            "[BedwarsPRO] 统计数据表创建失败: 无法获取数据库连接，已跳过建表。");
        return;
      }
      connection.setAutoCommit(false);
      try (PreparedStatement preparedStatement =
          connection.prepareStatement(db.getCreateTableSql())) {
        preparedStatement.executeUpdate();
      }
      connection.commit();
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getLogger().warning(
          "[BedwarsPRO] 统计数据表创建失败: " + ex.getMessage());
      ex.printStackTrace();
    }
  }

  private PlayerStatistic loadDatabaseStatistic(UUID uuid) {
    if (this.playerStatistic.containsKey(uuid)) {
      return this.playerStatistic.get(uuid);
    }
    // 读取前确保统计表存在（缺失时自动创建，避免 Table doesn't exist）
    this.ensureTableExists();
    HashMap<String, Object> deserialize = new HashMap<>();

    DatabaseManager db = BedwarsPRO.getInstance().getDatabaseManager();
    if (db != null) {
      try (Connection connection = db.getConnection()) {
        if (connection == null) {
          // 数据库不可用（FILE 模式/连接失败），返回空统计，忽略数据库错误
          PlayerStatistic empty = new PlayerStatistic(uuid);
          this.playerStatistic.put(uuid, empty);
          return empty;
        }
        try (PreparedStatement preparedStatement = connection
            .prepareStatement(db.getReadObjectSql())) {
          preparedStatement.setString(1, uuid.toString());
          try (ResultSet resultSet = preparedStatement.executeQuery()) {
            ResultSetMetaData meta = resultSet.getMetaData();
            while (resultSet.next()) {
              for (int i = 1; i <= meta.getColumnCount(); i++) {
                String key = meta.getColumnName(i);
                Object value = resultSet.getObject(key);
                deserialize.put(key, value);
              }
            }
          }
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    PlayerStatistic playerStatistic;

    if (deserialize.isEmpty()) {
      playerStatistic = new PlayerStatistic(uuid);
    } else {
      playerStatistic = new PlayerStatistic(deserialize);
    }
    Player player = BedwarsPRO.getInstance().getServer().getPlayer(uuid);
    if (player != null && !playerStatistic.getName().equals(player.getName())) {
      playerStatistic.setName(player.getName());
    }

    this.playerStatistic.put(playerStatistic.getId(), playerStatistic);
    return playerStatistic;
  }

  public PlayerStatistic loadStatistic(UUID uuid) {
    if (getEffectiveStorageType() == StorageType.YAML) {
      return this.loadYamlStatistic(uuid);
    } else {
      return this.loadDatabaseStatistic(uuid);
    }
  }

  private PlayerStatistic loadYamlStatistic(UUID uuid) {
    // 懒加载：initialize 未初始化（如数据库降级场景）时自动加载本地文件
    if (this.fileDatabase == null) {
      File file = new File(
          BedwarsPRO.getInstance().getDataFolder() + "/database/bw_stats_players.yml");
      this.loadYml(file);
    }
    if (!this.fileDatabase.contains("data." + uuid.toString())) {
      PlayerStatistic playerStatistic = new PlayerStatistic(uuid);
      this.playerStatistic.put(uuid, playerStatistic);
      return playerStatistic;
    }

    HashMap<String, Object> deserialize = new HashMap<>();
    deserialize.putAll(
        this.fileDatabase.getConfigurationSection("data." + uuid.toString()).getValues(false));
    PlayerStatistic playerStatistic = new PlayerStatistic(deserialize);
    playerStatistic.setId(uuid);
    Player player = BedwarsPRO.getInstance().getServer().getPlayer(uuid);
    if (player != null && !playerStatistic.getName().equals(player.getName())) {
      playerStatistic.setName(player.getName());
    }
    this.playerStatistic.put(uuid, playerStatistic);
    return playerStatistic;
  }

  private void loadYml(File ymlFile) {
    try {
      BedwarsPRO.getInstance().getServer().getConsoleSender().sendMessage(
          ChatWriter.pluginMessage(ChatColor.GREEN + "Loading statistics from YAML-File ..."));

      YamlConfiguration config = null;
      Map<OfflinePlayer, PlayerStatistic> map = new HashMap<>();

      this.databaseFile = ymlFile;

      if (!ymlFile.exists()) {
        ymlFile.getParentFile().mkdirs();
        ymlFile.createNewFile();

        config = new YamlConfiguration();
        config.createSection("data");
        config.save(ymlFile);
      } else {
        config = YamlConfiguration.loadConfiguration(ymlFile);
      }

      this.fileDatabase = config;

    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      ex.printStackTrace();
    }

    BedwarsPRO.getInstance().getServer().getConsoleSender()
        .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "Done!"));
  }

  private void storeDatabaseStatistic(PlayerStatistic playerStatistic) {
    // 写入前确保统计表存在（缺失时自动创建，避免 Table doesn't exist）
    this.ensureTableExists();
    DatabaseManager db = BedwarsPRO.getInstance().getDatabaseManager();
    if (db == null) {
      // 数据库未启用（FILE 模式）：忽略数据库相关错误
      return;
    }
    try (Connection connection = db.getConnection()) {
      if (connection == null) {
        // 数据库不可用，忽略保存（FILE 模式/连接失败）
        return;
      }
      connection.setAutoCommit(false);

      try (PreparedStatement preparedStatement = connection
          .prepareStatement(db.getWriteObjectSql())) {
        preparedStatement.setString(1, playerStatistic.getId().toString());
        preparedStatement.setString(2, playerStatistic.getName());
        preparedStatement.setInt(3, playerStatistic.getCurrentDeaths());
        preparedStatement.setInt(4, playerStatistic.getCurrentDestroyedBeds());
        preparedStatement.setInt(5, playerStatistic.getCurrentKills());
        preparedStatement.setInt(6, playerStatistic.getCurrentLoses());
        preparedStatement.setInt(7, playerStatistic.getCurrentScore());
        preparedStatement.setInt(8, playerStatistic.getCurrentWins());
        preparedStatement.executeUpdate();
      }
      connection.commit();
      playerStatistic.addCurrentValues();
    } catch (SQLException e) {
      e.printStackTrace();
    }

  }

  public void storeStatistic(PlayerStatistic statistic) {
    BedwarsSavePlayerStatisticEvent savePlayerStatisticEvent =
        new BedwarsSavePlayerStatisticEvent(statistic);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(savePlayerStatisticEvent);

    if (savePlayerStatisticEvent.isCancelled()) {
      return;
    }

    if (getEffectiveStorageType() == StorageType.YAML) {
      this.storeYamlStatistic(statistic);
    } else {
      this.storeDatabaseStatistic(statistic);
    }
  }

  private synchronized void storeYamlStatistic(PlayerStatistic statistic) {
    if (this.fileDatabase == null) {
      File file = new File(
          BedwarsPRO.getInstance().getDataFolder() + "/database/bw_stats_players.yml");
      this.loadYml(file);
      if (this.fileDatabase == null) {
        BedwarsPRO.getInstance().getServer().getConsoleSender().sendMessage(
            ChatWriter.pluginMessage(ChatColor.RED
                + "Statistics database not initialized, cannot store statistic"));
        return;
      }
    }
    statistic.addCurrentValues();
    this.fileDatabase.set("data." + statistic.getId().toString(), statistic.serialize());
    try {
      this.fileDatabase.save(this.databaseFile);
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      BedwarsPRO.getInstance().getServer().getConsoleSender().sendMessage(ChatWriter.pluginMessage(
          ChatColor.RED + "Couldn't store statistic data for player with uuid: " + statistic.getId()
              .toString()));
    }
  }

  /**
   * 获取全服起床统计排行榜（按指定字段降序）。
   *
   * <p>数据源与统计存储一致：YAML 模式遍历本地文件，数据库模式按字段 SQL 排序；
   * KD 无法在 SQL 中直接排序，统一全量读取后内存计算。</p>
   *
   * @param field 排序字段：kills / deaths / wins / loses / beds / score / kd
   * @param limit 返回条数上限（至少 1）
   */
  public List<PlayerStatistic> getStatisticsLeaderboard(String field, int limit) {
    String sortField = this.normalizeLeaderboardField(field);
    List<PlayerStatistic> result = new ArrayList<>();
    boolean needInMemorySort = false;

    if (getEffectiveStorageType() == StorageType.YAML) {
      needInMemorySort = true;
      if (this.fileDatabase == null) {
        File file = new File(
            BedwarsPRO.getInstance().getDataFolder() + "/database/bw_stats_players.yml");
        this.loadYml(file);
      }
      if (this.fileDatabase != null) {
        org.bukkit.configuration.ConfigurationSection section =
            this.fileDatabase.getConfigurationSection("data");
        if (section != null) {
          for (String key : section.getKeys(false)) {
            try {
              PlayerStatistic ps = this.loadYamlStatistic(UUID.fromString(key));
              if (ps != null) {
                result.add(ps);
              }
            } catch (IllegalArgumentException ignored) {
              // 非法 uuid key，跳过
            }
          }
        }
      }
    } else {
      DatabaseManager db = BedwarsPRO.getInstance().getDatabaseManager();
      if (db != null) {
        try (Connection connection = db.getConnection()) {
          if (connection != null) {
            String sql;
            if ("kd".equals(sortField)) {
              // KD 需全量读取后在内存中计算排序
              needInMemorySort = true;
              sql = "SELECT uuid, name, kills, deaths, wins, loses, score, destroyedBeds FROM "
                  + db.getTablePrefix() + "stats_players";
            } else {
              sql = "SELECT uuid, name, kills, deaths, wins, loses, score, destroyedBeds FROM "
                  + db.getTablePrefix() + "stats_players ORDER BY " + sortField + " DESC LIMIT ?";
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
              if (!"kd".equals(sortField)) {
                preparedStatement.setInt(1, Math.max(1, limit));
              }
              try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                  PlayerStatistic ps = this.statisticFromResultSet(resultSet);
                  if (ps != null) {
                    result.add(ps);
                  }
                }
              }
            }
          }
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }

    if (needInMemorySort) {
      result.sort(this.leaderboardComparator(sortField));
      if (result.size() > limit) {
        result = new ArrayList<>(result.subList(0, Math.max(1, limit)));
      }
    }
    return result;
  }

  /** 校验并规范化排行榜排序字段（白名单，防止 SQL 注入）。 */
  private String normalizeLeaderboardField(String field) {
    if (field == null) {
      return "kills";
    }
    switch (field.toLowerCase().replace("-", "")) {
      case "death":
      case "deaths":
        return "deaths";
      case "win":
      case "wins":
        return "wins";
      case "lose":
      case "loss":
      case "loses":
      case "losses":
        return "loses";
      case "bed":
      case "beds":
      case "destroyedbeds":
        return "destroyedBeds";
      case "score":
      case "points":
        return "score";
      case "kd":
      case "k/d":
      case "killdeath":
        return "kd";
      default:
        return "kills";
    }
  }

  /** 从数据库结果集构造统计对象（字段白名单固定列，避免依赖表结构顺序）。 */
  private PlayerStatistic statisticFromResultSet(ResultSet resultSet) throws SQLException {
    String uuidStr = resultSet.getString("uuid");
    if (uuidStr == null || uuidStr.isEmpty()) {
      return null;
    }
    try {
      UUID.fromString(uuidStr);
    } catch (IllegalArgumentException e) {
      return null;
    }
    HashMap<String, Object> map = new HashMap<>();
    map.put("uuid", uuidStr);
    map.put("name", resultSet.getString("name"));
    map.put("kills", resultSet.getInt("kills"));
    map.put("deaths", resultSet.getInt("deaths"));
    map.put("wins", resultSet.getInt("wins"));
    map.put("loses", resultSet.getInt("loses"));
    map.put("score", resultSet.getInt("score"));
    map.put("destroyedBeds", resultSet.getInt("destroyedBeds"));
    return new PlayerStatistic(map);
  }

  /** 排行榜降序比较器（KD 用双击数，其余用整型字段）。 */
  private java.util.Comparator<PlayerStatistic> leaderboardComparator(String sortField) {
    switch (sortField) {
      case "deaths":
        return java.util.Comparator.comparingInt(PlayerStatistic::getDeaths);
      case "wins":
        return java.util.Comparator.comparingInt(PlayerStatistic::getWins);
      case "loses":
        return java.util.Comparator.comparingInt(PlayerStatistic::getLoses);
      case "destroyedBeds":
        return java.util.Comparator.comparingInt(PlayerStatistic::getDestroyedBeds);
      case "score":
        return java.util.Comparator.comparingInt(PlayerStatistic::getScore);
      case "kd":
        return java.util.Comparator.comparingDouble(PlayerStatistic::getKD);
      default:
        return java.util.Comparator.comparingInt(PlayerStatistic::getKills);
    }
  }

  public void unloadStatistic(OfflinePlayer player) {
    if (getEffectiveStorageType() != StorageType.YAML) {
      this.playerStatistic.remove(player.getUniqueId());
    }
  }

  /**
   * 清除玩家全部统计（本地 YAML 或数据库），同时移除内存缓存。
   * 供 /bwpro clearstats 指令调用，执行后玩家统计将归零。
   */
  public void resetStatistic(OfflinePlayer player) {
    if (player == null || player.getUniqueId() == null) {
      return;
    }
    UUID uuid = player.getUniqueId();
    // 先移除内存缓存，避免清除后仍显示旧数据
    this.playerStatistic.remove(uuid);
    if (getEffectiveStorageType() == StorageType.YAML) {
      if (this.fileDatabase == null) {
        File file = new File(
            BedwarsPRO.getInstance().getDataFolder() + "/database/bw_stats_players.yml");
        this.loadYml(file);
      }
      if (this.fileDatabase != null) {
        this.fileDatabase.set("data." + uuid.toString(), null);
        try {
          this.fileDatabase.save(this.databaseFile);
        } catch (Exception ex) {
          BedwarsPRO.getInstance().getBugsnag().notify(ex);
          ex.printStackTrace();
        }
      }
    } else {
      DatabaseManager db = BedwarsPRO.getInstance().getDatabaseManager();
      if (db != null) {
        try (Connection connection = db.getConnection()) {
          if (connection == null) {
            // 数据库不可用，忽略清除（FILE 模式/连接失败）
            return;
          }
          try (PreparedStatement ps = connection
              .prepareStatement("DELETE FROM " + db.getTablePrefix()
                  + "stats_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
          }
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }
  }


}
