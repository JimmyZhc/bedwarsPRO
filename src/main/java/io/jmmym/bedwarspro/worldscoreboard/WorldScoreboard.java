package io.jmmym.bedwarspro.worldscoreboard;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.scoreboard.utils.PlaceholderAPIUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * 世界计分板（WorldScoreboard）模块主类。
 *
 * <p>功能：
 * <ul>
 *   <li>仅在「一端多图」（非 BungeeCord，bungeecord.enabled=false）且功能开启时生效</li>
 *   <li>玩家加入服务器 / 切换世界时自动检测所在世界，按黑白名单规则显示或移除计分板</li>
 *   <li>定时刷新，实时更新 PlaceholderAPI 占位符（金币、经验等动态数值）</li>
 *   <li>起床战争游戏内不显示世界计分板，避免与游戏计分板冲突</li>
 * </ul></p>
 */
public class WorldScoreboard {

    private static final String OBJECTIVE = "world-sb";
    private static final int MAX_LINES = 15;
    private static final int MAX_LINE_LENGTH = 40;
    private static final int MAX_TITLE_LENGTH = 32;

    private static WorldScoreboard instance;

    private final BedwarsPRO plugin;
    private Config config;
    private BukkitTask task;
    /** 已显示世界计分板的玩家。 */
    private final Set<UUID> shown = new HashSet<>();

    private WorldScoreboard(BedwarsPRO plugin) {
        this.plugin = plugin;
    }

    public static WorldScoreboard getInstance() {
        return instance;
    }

    public static Config getConfig() {
        return instance == null ? null : instance.config;
    }

    /** 插件启用时调用。 */
    public static void init(BedwarsPRO plugin) {
        if (instance == null) {
            instance = new WorldScoreboard(plugin);
        }
        instance.enable();
    }

    /** 插件禁用时调用。 */
    public static void shutdown() {
        if (instance != null) {
            instance.disable();
            instance = null;
        }
    }

    /** 重载世界计分板配置（供 /bwpro reload 调用）。 */
    public static void reload() {
        if (instance != null) {
            instance.config.load();
            instance.startTask();
            for (Player p : Bukkit.getOnlinePlayers()) {
                instance.apply(p);
            }
        }
    }

    // ==================== 生命周期 ====================

    private void enable() {
        config = new Config(plugin);
        config.load();
        Bukkit.getPluginManager().registerEvents(new WorldScoreboardListener(), plugin);
        startTask();
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);
        }
        if (isActive()) {
            plugin.getLogger().info("[WorldScoreboard] 世界计分板已启用（一端多图模式）。");
        } else {
            plugin.getLogger().info("[WorldScoreboard] 世界计分板未启用（需在配置中开启，且处于一端多图/非 BungeeCord 模式）。");
        }
    }

    private void disable() {
        stopTask();
        for (Player p : Bukkit.getOnlinePlayers()) {
            remove(p);
        }
        shown.clear();
    }

    private void startTask() {
        stopTask();
        if (!isActive()) {
            return;
        }
        long interval = Math.max(1, config.getUpdateInterval());
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                apply(p);
            }
        }, interval, interval);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ==================== 判断 ====================

    /** 功能是否真正生效：配置开启且处于一端多图（非 BungeeCord）模式。 */
    public boolean isActive() {
        return config != null && config.isEnabled() && !plugin.isBungee();
    }

    private boolean inGame(Player player) {
        return plugin.getGameManager() != null
                && plugin.getGameManager().getGameOfPlayer(player) != null;
    }

    // ==================== 应用 ====================

    /**
     * 对玩家应用计分板：符合条件则显示/更新，否则移除。
     * 起床战争游戏内不做任何操作（由游戏计分板接管）。
     */
    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!isActive()) {
            remove(player);
            return;
        }
        if (inGame(player)) {
            return;
        }
        if (config.isWorldEnabled(player.getWorld().getName())) {
            update(player);
        } else {
            remove(player);
        }
    }

    /** 移除玩家世界计分板（仅当确实显示过时）。 */
    public void remove(Player player) {
        if (player == null) {
            return;
        }
        if (shown.remove(player.getUniqueId())) {
            if (player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }

    private void update(Player player) {
        String title = parse(player, config.getTitle());
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }
        List<String> elements = prepareLines(player, config.getLines());

        Scoreboard sb = player.getScoreboard();
        if (sb == null || sb == Bukkit.getScoreboardManager().getMainScoreboard()
                || sb.getObjective(OBJECTIVE) == null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            sb = player.getScoreboard();
        }
        Objective obj;
        try {
            obj = sb.getObjective(OBJECTIVE);
            if (obj == null) {
                obj = sb.registerNewObjective(OBJECTIVE, "dummy");
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
        } catch (IllegalStateException e) {
            // objective 冲突时重建计分板
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            sb = player.getScoreboard();
            obj = sb.registerNewObjective(OBJECTIVE, "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.setDisplayName(title);

        // 配置第一行显示在最上方（score 最大）
        int score = elements.size();
        for (String entry : elements) {
            if (obj.getScore(entry).getScore() != score) {
                obj.getScore(entry).setScore(score);
            }
            score--;
        }
        // 清除不再使用的旧条目
        for (String entry : sb.getEntries()) {
            if (!elements.contains(entry)) {
                sb.resetScores(entry);
            }
        }
        shown.add(player.getUniqueId());
    }

    // ==================== 文本处理 ====================

    /** 解析占位符（PlaceholderAPI）并转换 & 颜色代码。 */
    private String parse(Player player, String text) {
        if (text == null) {
            return "";
        }
        String t = PlaceholderAPIUtil.setPlaceholders(player, text);
        return ChatColor.translateAlternateColorCodes('&', t);
    }

    /**
     * 处理配置行：解析占位符/颜色、空行转空格、处理重复行、限制行数与长度。
     */
    private List<String> prepareLines(Player player, List<String> rawLines) {
        List<String> result = new ArrayList<>();
        int count = 0;
        for (String line : rawLines) {
            if (count >= MAX_LINES) {
                break;
            }
            String l = parse(player, line == null ? "" : line);
            if (l.isEmpty()) {
                l = " ";
            }
            while (result.contains(l)) {
                l += (l.length() < MAX_LINE_LENGTH) ? "§r" : " ";
            }
            if (l.length() > MAX_LINE_LENGTH) {
                l = l.substring(0, MAX_LINE_LENGTH);
            }
            result.add(l);
            count++;
        }
        return result;
    }
}
