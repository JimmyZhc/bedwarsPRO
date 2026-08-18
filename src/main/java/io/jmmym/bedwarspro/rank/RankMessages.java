package io.jmmym.bedwarspro.rank;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 排位赛消息管理。从 rank/messages.yml 加载所有聊天提示文本（语言文件可自由配置）。
 *
 * <p>用法：
 * <pre>
 *   RankMessages.msg(player, "queue.joined", "x", 4, "y", 16);
 *   RankMessages.msg(sender, "cmd.no-permission");
 * </pre>
 * 占位符使用 {key} 形式，颜色代码支持 &amp;。
 */
public class RankMessages {

  private static YamlConfiguration config;
  private static File configFile;

  private RankMessages() {
  }

  /** 初始化（插件启用时调用一次）。 */
  public static void init(BedwarsPRO plugin) {
    File folder = new File(plugin.getDataFolder(), "rank");
    if (!folder.exists()) {
      folder.mkdirs();
    }
    configFile = new File(folder, "messages.yml");
    if (!configFile.exists()) {
      plugin.saveResource("rank/messages.yml", false);
    }
    load();
  }

  /** 从文件重新加载（/bwpro rankreload 时调用）。 */
  public static void load() {
    if (configFile == null) {
      return;
    }
    YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configFile);
    // 与 JAR 内默认值合并（用户未配置的 key 使用默认值）
    InputStream defStream = BedwarsPRO.getInstance().getResource("rank/messages.yml");
    if (defStream != null) {
      YamlConfiguration defaults =
          YamlConfiguration.loadConfiguration(new InputStreamReader(defStream));
      for (String key : defaults.getKeys(true)) {
        if (!loaded.contains(key)) {
          loaded.set(key, defaults.get(key));
        }
      }
      // 兼容旧版：queue.joined 曾使用 {mode} 占位符（全服模式已移除，代码不再提供该占位符）。
      // 若旧配置文件仍残留 {mode}，恢复为新版默认文案并写回，避免原样显示 {mode}
      String joined = loaded.getString("queue.joined");
      if (joined != null && joined.contains("{mode}")) {
        loaded.set("queue.joined", defaults.get("queue.joined"));
        try {
          loaded.save(configFile);
        } catch (java.io.IOException ignore) {
          // 保存失败不影响本次加载
        }
      }
    }
    config = loaded;
  }

  /** 获取原始消息文本（未处理颜色与占位符）。 */
  public static String getRaw(String key) {
    if (config == null) {
      return key;
    }
    String val = config.getString(key);
    return val == null ? key : val;
  }

  /**
   * 获取处理后的消息（颜色代码 + 占位符替换）。
   *
   * @param key          消息 key
   * @param replacements 交替的 key-value 对（如 "x", 4, "y", 16）
   */
  public static String get(String key, Object... replacements) {
    String raw = getRaw(key);
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i + 1 < replacements.length; i += 2) {
      map.put(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
    }
    return replaceColors(replacePlaceholders(raw, map));
  }

  /** 向玩家发送一条消息。 */
  public static void msg(Player player, String key, Object... replacements) {
    if (player == null) {
      return;
    }
    player.sendMessage(get(key, replacements));
  }

  /** 向命令发送者（或玩家）发送消息。 */
  public static void msg(org.bukkit.command.CommandSender sender, String key,
      Object... replacements) {
    if (sender == null) {
      return;
    }
    sender.sendMessage(get(key, replacements));
  }

  /**
   * 获取处理后的多行文本（用于物品 Lore 等）。
   *
   * @param key          消息 key（配置中应为字符串列表）
   * @param replacements 交替的 key-value 对
   */
  public static List<String> getList(String key, Object... replacements) {
    List<String> result = new ArrayList<>();
    if (config == null) {
      return result;
    }
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i + 1 < replacements.length; i += 2) {
      map.put(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
    }
    for (String line : config.getStringList(key)) {
      result.add(replaceColors(replacePlaceholders(line, map)));
    }
    return result;
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
