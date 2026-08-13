package io.jmmym.bedwarspro.worldscoreboard;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 世界计分板（WorldScoreboard）配置管理。
 *
 * <p>配置文件位置：plugins/BedwarsPRO/Scoreboard/config.yml
 * <ul>
 *   <li>enabled — 功能总开关（默认关闭）</li>
 *   <li>world-mode / worlds — 世界隔离（whitelist=仅指定世界显示，blacklist=排除指定世界）</li>
 *   <li>update-interval — 占位符刷新间隔（tick）</li>
 *   <li>title / lines — 计分板标题与内容（支持 & 颜色与 PlaceholderAPI）</li>
 * </ul></p>
 */
public class Config {

    private final BedwarsPRO plugin;
    private final File configFile;
    private YamlConfiguration cfg;

    private boolean enabled = false;
    /** true=白名单模式（仅 worlds 列表生效），false=黑名单模式（worlds 列表不生效）。 */
    private boolean whitelist = true;
    private final List<String> worlds = new ArrayList<>();
    private int updateInterval = 20;
    private String title = "&6&l起床战争";
    private final List<String> lines = new ArrayList<>();

    public Config(BedwarsPRO plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "Scoreboard/config.yml");
    }

    /** 加载/重新加载配置。 */
    public void load() {
        ensureFile();
        cfg = YamlConfiguration.loadConfiguration(configFile);
        // 与 JAR 内默认值合并，缺失 key 补默认并写回（保留用户修改）
        InputStream def = plugin.getResource("Scoreboard/config.yml");
        if (def != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(def));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!cfg.contains(key)) {
                    cfg.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    cfg.save(configFile);
                } catch (IOException ignored) {
                }
            }
        }

        enabled = cfg.getBoolean("enabled", false);
        whitelist = !"blacklist".equalsIgnoreCase(cfg.getString("world-mode", "whitelist"));
        worlds.clear();
        worlds.addAll(cfg.getStringList("worlds"));
        updateInterval = Math.max(1, cfg.getInt("update-interval", 20));
        title = cfg.getString("title", "&6&l起床战争");
        lines.clear();
        lines.addAll(cfg.getStringList("lines"));
    }

    private void ensureFile() {
        if (configFile.exists()) {
            return;
        }
        File dir = configFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        InputStream in = plugin.getResource("WorldScoreboard/config.yml");
        if (in != null) {
            try {
                java.nio.file.Files.copy(in, configFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("[WorldScoreboard] 生成配置文件失败: " + e.getMessage());
            }
        } else {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[WorldScoreboard] 创建配置文件失败: " + e.getMessage());
            }
        }
    }

    // ==================== 判断 ====================

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getLines() {
        return lines;
    }

    /** 世界隔离：白名单模式=仅列表内生效，黑名单模式=排除列表。 */
    public boolean isWorldEnabled(String worldName) {
        if (worldName == null) {
            return false;
        }
        boolean contains = worlds.contains(worldName);
        return whitelist ? contains : !contains;
    }
}
