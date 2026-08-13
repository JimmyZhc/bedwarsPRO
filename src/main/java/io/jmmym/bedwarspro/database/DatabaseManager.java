package io.jmmym.bedwarspro.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.TimeZone;
import lombok.Getter;

public class DatabaseManager {

  @Getter
  private String tablePrefix = "bw_";
  private String database = null;
  private HikariDataSource dataSource = null;
  private String host = null;
  private String password = null;
  private int port = 3306;
  private String user = null;

  public DatabaseManager(String host, int port, String user, String password, String database,
      String tablePrefix) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.database = database;
    this.tablePrefix = tablePrefix;
  }


  public void initialize() {
    HikariConfig config = new HikariConfig();
    // useSSL=false: 兼容无 SSL 证书的数据库
    // allowPublicKeyRetrieval=true: MySQL 8 caching_sha2_password 必需
    // useUnicode/characterEncoding=utf8: 任务名称等中文不乱码
    config.setJdbcUrl("jdbc:mysql://" + this.host + ":" + String.valueOf(this.port) + "/"
        + this.database + "?autoReconnect=true&useSSL=false"
        + "&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8"
        + "&serverTimezone=" + TimeZone.getDefault().getID());
    config.setUsername(this.user);
    config.setPassword(this.password);
    // 显式指定事务隔离级别：MySQL 8 移除了 @@tx_isolation 变量，
    // 老驱动读取默认隔离级别会返回 -1，导致 "Unsupported transaction isolation level '-1'"
    config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
    config.setConnectionTimeout(5000L);
    config.setValidationTimeout(3000L);
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    this.dataSource = new HikariDataSource(config);
  }

  public Connection getConnection() {
    try {
      return this.dataSource.getConnection();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /** 关闭连接池（插件卸载/重载时调用）。 */
  public void close() {
    if (this.dataSource != null) {
      this.dataSource.close();
      this.dataSource = null;
    }
  }

  public String getCreateTableSql() {
    return "CREATE TABLE IF NOT EXISTS `" + this.getTablePrefix()
        + "stats_players` (`kills` int(11) NOT NULL DEFAULT '0', `wins` int(11) NOT NULL DEFAULT '0', `score` int(11) NOT NULL DEFAULT '0', `loses` int(11) NOT NULL DEFAULT '0', `name` varchar(255) NOT NULL, `destroyedBeds` int(11) NOT NULL DEFAULT '0', `uuid` varchar(255) NOT NULL, `deaths` int(11) NOT NULL DEFAULT '0', PRIMARY KEY (`uuid`))";
  }

  public String getReadObjectSql() {
    return "SELECT * FROM " + this.getTablePrefix()
        + "stats_players WHERE uuid = ? LIMIT 1";
  }

  public String getWriteObjectSql() {
    return "INSERT INTO " + this.getTablePrefix()
        + "stats_players(uuid, name, deaths, destroyedBeds, kills, loses, score, wins) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE uuid=VALUES(uuid),name=VALUES(name),deaths=deaths+VALUES(deaths),destroyedBeds=destroyedBeds+VALUES(destroyedBeds),kills=kills+VALUES(kills),loses=loses+VALUES(loses),score=score+VALUES(score),wins=wins+VALUES(wins)";
  }
}
