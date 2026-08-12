package io.jmmym.bedwarspro.joinitem;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 快捷物品（JoinItem）配置管理。
 *
 * <p>配置文件位置：plugins/BedwarsPRO/Scoreboard/join-item.yml
 * <ul>
 *   <li>enabled — 功能总开关（默认关闭）</li>
 *   <li>world-mode / worlds — 世界隔离（whitelist=仅指定世界发放，blacklist=排除指定世界）</li>
 *   <li>items — 物品列表，每项含 material / name / lore / slot / command / cooldown</li>
 * </ul>
 * 兼容旧版单物品结构：若配置中没有 items 列表，则按旧的
 * material/name/lore/slot/command/cooldown 顶层字段构造单个物品。</p>
 */
public class JoinItemConfig {

    /** 单个快捷物品配置。 */
    public static class ItemEntry {
        public String material = "NETHER_STAR";
        public String name = "";
        public final List<String> lore = new ArrayList<>();
        public int slot = 0;
        public String command = "";
        public int cooldown = 20;
    }

    private final BedwarsPRO plugin;
    private final File configFile;
    private YamlConfiguration cfg;

    private boolean enabled = false;
    /** true=白名单模式，false=黑名单模式。 */
    private boolean whitelist = true;
    private final List<String> worlds = new ArrayList<>();
    private final List<ItemEntry> items = new ArrayList<>();

    public JoinItemConfig(BedwarsPRO plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "Scoreboard/join-item.yml");
    }

    /** 加载/重新加载配置。 */
    public void load() {
        ensureFile();
        cfg = YamlConfiguration.loadConfiguration(configFile);
        // 与 JAR 内默认值合并，缺失 key 补默认并写回（保留用户修改）
        InputStream def = plugin.getResource("Scoreboard/join-item.yml");
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

        items.clear();
        List<Map<?, ?>> list = cfg.getMapList("items");
        if (!list.isEmpty()) {
            for (Map<?, ?> map : list) {
                ItemEntry e = new ItemEntry();
                if (map.get("material") != null) {
                    e.material = String.valueOf(map.get("material"));
                }
                if (map.get("name") != null) {
                    e.name = String.valueOf(map.get("name"));
                }
                if (map.get("lore") instanceof List) {
                    for (Object o : (List<?>) map.get("lore")) {
                        e.lore.add(String.valueOf(o));
                    }
                }
                if (map.get("slot") instanceof Number) {
                    e.slot = clampSlot(((Number) map.get("slot")).intValue());
                }
                if (map.get("command") != null) {
                    e.command = String.valueOf(map.get("command"));
                }
                if (map.get("cooldown") instanceof Number) {
                    e.cooldown = Math.max(0, ((Number) map.get("cooldown")).intValue());
                }
                items.add(e);
            }
        } else {
            // 兼容旧版单物品结构
            ItemEntry e = new ItemEntry();
            e.material = cfg.getString("material", "NETHER_STAR");
            e.name = cfg.getString("name", "");
            e.lore.addAll(cfg.getStringList("lore"));
            e.slot = clampSlot(cfg.getInt("slot", 0));
            e.command = cfg.getString("command", "");
            e.cooldown = Math.max(0, cfg.getInt("cooldown", 5));
            items.add(e);
        }
    }

    private static int clampSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private void ensureFile() {
        if (configFile.exists()) {
            return;
        }
        File dir = configFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        InputStream in = plugin.getResource("Scoreboard/join-item.yml");
        if (in != null) {
            try {
                java.nio.file.Files.copy(in, configFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("[JoinItem] 生成配置文件失败: " + e.getMessage());
            }
        } else {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[JoinItem] 创建配置文件失败: " + e.getMessage());
            }
        }
    }

    // ==================== 读取 ====================

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public List<ItemEntry> getItems() {
        return items;
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
