package io.jmmym.bedwarspro.task;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 任务系统消息管理。从 messages.yml 加载所有聊天提示文本，
 * 支持 & 颜色代码和 {key} 占位符替换，实现完全自定义。
 *
 * <p>用法:
 * <pre>
 *   TaskMessages.msg(player, "daily-complete", "task", "猎手");
 *   TaskMessages.msg(player, "reward-line", "exp", 20, "coins", 2);
 * </pre>
 */
public class TaskMessages {

    private static YamlConfiguration config;
    private static File configFile;

    /** 初始化（插件启用时调用一次）。 */
    public static void init(BedwarsPRO plugin) {
        configFile = new File(plugin.getDataFolder(), "tasks/messages.yml");
        if (!configFile.exists()) {
            plugin.saveResource("messages.yml", true);
            // 移动到 tasks 子文件夹
            File destDir = new File(plugin.getDataFolder(), "tasks");
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            File defaultFile = new File(plugin.getDataFolder(), "messages.yml");
            if (defaultFile.exists() && !configFile.exists()) {
                defaultFile.renameTo(configFile);
            }
        }
        load();
    }

    /** 从文件重新加载（/bwpro task reload 时调用）。 */
    public static void load() {
        if (configFile == null) {
            return;
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        // 与 resource 内默认值合并（用户未配置的 key 使用默认）
        InputStream defStream = BedwarsPRO.getInstance().getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream));
            for (String key : defaults.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaults.get(key));
                }
            }
        }
    }

    /** 重载并保存默认值（保留用户修改）。 */
    public static void reload() {
        if (configFile == null) {
            return;
        }
        // 读取现有
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
        // 读取默认
        InputStream defStream = BedwarsPRO.getInstance().getResource("messages.yml");
        if (defStream == null) {
            config = existing;
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defStream));
        // 合并：用户已有 key 保留，缺失的补默认
        for (String key : defaults.getKeys(true)) {
            if (!existing.contains(key)) {
                existing.set(key, defaults.get(key));
            }
        }
        config = existing;
        // 保存合并结果
        try {
            config.save(configFile);
        } catch (IOException ignored) {
        }
    }

    /**
     * 获取原始消息文本（未经颜色和占位符处理）。
     */
    public static String getRaw(String key) {
        if (config == null) {
            return key;
        }
        String val = config.getString(key);
        return val == null ? key : val;
    }

    /**
     * 获取处理后的消息（颜色代码 + 占位符替换）。
     * @param key 消息 key
     * @param replacements 交替的 key-value 对（如 "task", "猎手", "exp", 20）
     */
    public static String get(String key, Object... replacements) {
        String raw = getRaw(key);
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            map.put(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        return replaceColors(replacePlaceholders(raw, map));
    }

    /**
     * 向玩家发送一条消息。
     */
    public static void msg(Player player, String key, Object... replacements) {
        if (player == null) {
            return;
        }
        player.sendMessage(get(key, replacements));
    }

    /**
     * 向命令发送者（或玩家）发送消息。
     */
    public static void msg(org.bukkit.command.CommandSender sender, String key,
                            Object... replacements) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(get(key, replacements));
    }

    /**
     * 构建一行奖励文本（经验+栖云币），可嵌入到其他消息中。
     */
    public static String rewardLine(int exp, int coins) {
        StringBuilder sb = new StringBuilder();
        if (exp > 0) {
            sb.append(get("gui-exp", "exp", exp));
        }
        if (coins > 0) {
            if (exp > 0) {
                sb.append(ChatColor.GRAY).append("  ");
            }
            sb.append(get("gui-coins", "coins", coins));
        }
        if (sb.length() == 0) {
            sb.append(get("gui-no-reward"));
        }
        return sb.toString();
    }

    // ===== 内部工具 =====

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

    private static String replaceColors(String text) {
        if (text == null) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
