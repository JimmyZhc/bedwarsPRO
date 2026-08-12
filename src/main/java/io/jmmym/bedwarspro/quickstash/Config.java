package io.jmmym.bedwarspro.quickstash;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.utils.Utils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 快捷存入（QuickStash）配置管理。
 *
 * <p>配置文件位置：plugins/BedwarsPRO/QuickStash/config-quickstash.yml
 * <ul>
 *   <li>enabled — 模块总开关</li>
 *   <li>world-mode / worlds — 世界隔离（whitelist=仅指定世界生效，blacklist=排除指定世界）</li>
 *   <li>blocks — 触发存入的方块类型</li>
 *   <li>blacklist / blacklist-tools — 禁止存入的物品黑名单</li>
 *   <li>messages — 聊天消息</li>
 * </ul></p>
 */
public class Config {

    private final BedwarsPRO plugin;
    private final File configFile;
    private YamlConfiguration cfg;

    private boolean enabled = true;
    /** true=白名单模式（仅 worlds 列表生效），false=黑名单模式（worlds 列表不生效）。 */
    private boolean worldWhitelist = true;
    private final List<String> worlds = new ArrayList<>();
    private final List<Material> targetBlocks = new ArrayList<>();
    private final List<String> blacklist = new ArrayList<>();
    private boolean blacklistTools = true;
    private String prefix = "&8[&6快捷存入&8] &r";

    public Config(BedwarsPRO plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "QuickStash/config-quickstash.yml");
    }

    /** 加载/重新加载配置。 */
    public void load() {
        ensureFile();
        cfg = YamlConfiguration.loadConfiguration(configFile);
        // 与 JAR 内默认值合并，缺失 key 补默认并写回（保留用户修改）
        InputStream def = plugin.getResource("QuickStash/config-quickstash.yml");
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

        enabled = cfg.getBoolean("enabled", true);
        worldWhitelist = !"blacklist".equalsIgnoreCase(cfg.getString("world-mode", "whitelist"));
        worlds.clear();
        worlds.addAll(cfg.getStringList("worlds"));

        targetBlocks.clear();
        for (String s : cfg.getStringList("blocks")) {
            Material m = Utils.parseMaterial(s);
            if (m != null && !targetBlocks.contains(m)) {
                targetBlocks.add(m);
            }
        }
        if (targetBlocks.isEmpty()) {
            targetBlocks.add(Material.CHEST);
            targetBlocks.add(Material.TRAPPED_CHEST);
            targetBlocks.add(Material.ENDER_CHEST);
        }

        blacklist.clear();
        blacklist.addAll(cfg.getStringList("blacklist"));
        blacklistTools = cfg.getBoolean("blacklist-tools", true);
        prefix = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("messages.prefix", "&8[&6快捷存入&8] &r"));
    }

    private void ensureFile() {
        if (configFile.exists()) {
            return;
        }
        File dir = configFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        InputStream in = plugin.getResource("QuickStash/config-quickstash.yml");
        if (in != null) {
            try {
                java.nio.file.Files.copy(in, configFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("[QuickStash] 生成配置文件失败: " + e.getMessage());
            }
        } else {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[QuickStash] 创建配置文件失败: " + e.getMessage());
            }
        }
    }

    // ==================== 判断 ====================

    public boolean isEnabled() {
        return enabled;
    }

    /** 世界隔离：白名单模式=仅列表内生效，黑名单模式=排除列表。 */
    public boolean isWorldEnabled(String worldName) {
        if (!enabled || worldName == null) {
            return false;
        }
        boolean contains = worlds.contains(worldName);
        return worldWhitelist ? contains : !contains;
    }

    public boolean isTargetBlock(Material type) {
        return targetBlocks.contains(type);
    }

    /** 判断物品是否被禁止存入（黑名单或工具/武器类）。 */
    public boolean isBlacklisted(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }
        Material type = item.getType();
        for (String b : blacklist) {
            Material m = Utils.parseMaterial(b);
            if (m == type || b.equalsIgnoreCase(type.name())) {
                return true;
            }
        }
        if (blacklistTools) {
            String name = type.name();
            // 剑 / 斧 / 镐 / 锹(1.8=SPADE, 1.13+=SHOVEL) / 锄 / 弓 / 钓鱼竿
            if (name.endsWith("_SWORD") || name.endsWith("_AXE")
                    || name.endsWith("_PICKAXE") || name.endsWith("_SPADE")
                    || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                    || name.equals("BOW") || name.equals("FISHING_ROD")) {
                return true;
            }
        }
        return false;
    }

    // ==================== 消息 ====================

    /** 获取处理后的消息（前缀 + 颜色 + 占位符）。 */
    public String getMsg(String key, Object... replacements) {
        String raw = cfg == null ? key : cfg.getString("messages." + key, key);
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            map.put(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        return prefix + replacePlaceholders(ChatColor.translateAlternateColorCodes('&', raw), map);
    }

    /** 向发送者发送一条快捷存入消息。 */
    public void msg(CommandSender sender, String key, Object... replacements) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(getMsg(key, replacements));
    }

    private static String replacePlaceholders(String template, Map<String, String> vars) {
        if (template == null || vars.isEmpty()) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length()) {
                int end = template.indexOf('}', i + 1);
                if (end > i + 1) {
                    String key = template.substring(i + 1, end);
                    String val = vars.get(key);
                    if (val != null) {
                        sb.append(val);
                        i = end + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
