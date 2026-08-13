package io.jmmym.bedwarspro.task;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.database.DatabaseManager;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 任务系统数据库同步（多服共享任务进度）。
 *
 * <p>使用主插件 DatabaseManager 的 MySQL 连接，表结构：
 * <pre>CREATE TABLE IF NOT EXISTS `&lt;prefix&gt;player_tasks` (
 *   uuid      varchar(36) NOT NULL,
 *   data      text,                 -- PlayerTaskState 序列化后的 YAML
 *   updatedAt bigint NOT NULL DEFAULT '0',
 *   PRIMARY KEY (uuid)
 * )</pre></p>
 *
 * <p>整个 PlayerTaskState 序列化为 YAML 字符串存入 data 字段，
 * 新增字段无需改动表结构。</p>
 */
public class TaskDatabase {

    private final DatabaseManager dbManager;
    private final String table;
    private boolean available = false;

    public TaskDatabase(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.table = dbManager.getTablePrefix() + "player_tasks";
    }

    /** 初始化表结构并测试连接。返回是否可用。 */
    public boolean initialize() {
        available = false;
        if (dbManager == null) {
            return false;
        }
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) {
                return false;
            }
            try (PreparedStatement st = conn.prepareStatement("CREATE TABLE IF NOT EXISTS `"
                    + table + "` (`uuid` varchar(36) NOT NULL, `data` text, "
                    + "`updatedAt` bigint NOT NULL DEFAULT '0', PRIMARY KEY (`uuid`)) "
                    + "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")) {
                st.executeUpdate();
            }
            available = true;
        } catch (Exception e) {
            BedwarsPRO.getInstance().getLogger()
                    .warning("[TaskManager] 任务数据库初始化失败: " + e.getMessage());
            available = false;
        }
        return available;
    }

    public boolean isAvailable() {
        return available;
    }

    /** 从数据库读取玩家任务状态，无记录或失败返回 null。 */
    public PlayerTaskState loadPlayerState(UUID uuid) {
        if (!available) {
            return null;
        }
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) {
                return null;
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT `data` FROM `" + table + "` WHERE `uuid` = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        String data = rs.getString("data");
                        if (data == null || data.isEmpty()) {
                            return null;
                        }
                        YamlConfiguration cfg =
                                YamlConfiguration.loadConfiguration(new StringReader(data));
                        return PlayerTaskState.load(uuid, cfg);
                    }
                }
            }
        } catch (Exception e) {
            BedwarsPRO.getInstance().getLogger()
                    .warning("[TaskManager] 从数据库加载玩家状态失败 " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    /** 将玩家任务状态写入数据库（upsert）。 */
    public void savePlayerState(PlayerTaskState state) {
        if (!available || state == null) {
            return;
        }
        try (Connection conn = dbManager.getConnection()) {
            if (conn == null) {
                return;
            }
            YamlConfiguration cfg = new YamlConfiguration();
            state.save(cfg);
            String data = cfg.saveToString();
            try (PreparedStatement st = conn.prepareStatement("INSERT INTO `" + table
                    + "` (`uuid`, `data`, `updatedAt`) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE `data` = VALUES(`data`), "
                    + "`updatedAt` = VALUES(`updatedAt`)")) {
                st.setString(1, state.getPlayerId().toString());
                st.setString(2, data);
                st.setLong(3, System.currentTimeMillis());
                st.executeUpdate();
            }
        } catch (Exception e) {
            BedwarsPRO.getInstance().getLogger().warning(
                    "[TaskManager] 保存玩家状态到数据库失败 " + state.getPlayerId() + ": "
                            + e.getMessage());
        }
    }
}
