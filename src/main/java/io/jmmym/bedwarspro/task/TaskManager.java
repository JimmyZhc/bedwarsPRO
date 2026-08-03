package io.jmmym.bedwarspro.task;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 任务系统核心管理器：每日任务 + 每周任务 + 限时任务发布 + 配置热重载。
 *
 * <p>持久化目录：plugins/BedwarsPRO/tasks/
 * <ul>
 *   <li>tasks.yml      — 任务池与开关配置</li>
 *   <li>api.yml        — 经验/栖云币奖励 HTTP API 配置（url + api-key）</li>
 *   <li>state.yml      — 当日/周选中任务、限时任务、配置标志快照</li>
 *   <li>players/&lt;uuid&gt;.yml — 单个玩家的每日+每周进度</li>
 * </ul></p>
 */
public class TaskManager {

    private static TaskManager instance;

    private final BedwarsPRO plugin;
    private final File tasksDir;
    private final File playersDir;
    private final File stateFile;

    // ===== 奖励 API 配置 =====
    private String apiUrl = "";
    private String apiKey = "";

    // ===== 任务池 =====
    private final List<Task> dailyTaskPool = new ArrayList<>();
    private final List<Task> weeklyTaskPool = new ArrayList<>();

    // ===== 当日选中的任务 =====
    private final List<Task> dailyTasks = new ArrayList<>();
    private final List<String> dailyTaskNames = new ArrayList<>();

    // ===== 本周选中的任务（含随机 target） =====
    private final List<Task> weeklyTasks = new ArrayList<>();
    private final List<String> weeklyTaskNames = new ArrayList<>();
    private final List<Integer> weeklyTaskTargets = new ArrayList<>();

    // ===== 限时任务（原特殊任务）=====
    private final List<Task> specialTasks = new ArrayList<>();
    /** 限时任务发布时间戳（毫秒），key=任务名。用于到期自动移除。 */
    private final Map<String, Long> specialTaskPublishTime = new HashMap<>();

    // ===== 配置 =====
    private boolean dailyEnabled = true;
    private boolean weeklyEnabled = false;
    private boolean randomAssign = true;
    private boolean weeklyRandomAssign = false;
    private int dailyCount = 3;
    private int weeklyCount = 3;
    private int timedDurationMinutes = 60;
    private long timeMultiplier = 1L;

    // ===== 对局内行为奖励 =====
    private int ingameNormalKillExp = 1;
    private int ingameNormalKillCoins = 3;
    private int ingameFinalKillExp = 3;
    private int ingameFinalKillCoins = 1;
    private int ingameDestroyBedExp = 5;
    private int ingameDestroyBedCoins = 1;
    private int ingameWinExp = 6;
    private int ingameWinCoins = 2;

    // ===== 追杀令奖励 =====
    private int bountyRewardExp = 15;
    private int bountyRewardCoins = 2;

    // ===== 持久化标志 =====
    private long currentDay = 0L;
    private long currentWeek = 0L;
    /** 上次保存时的 randomAssign 值，用于检测配置变化后重新选任务。 */
    private boolean persistedRandomAssign = true;
    /** 上次保存时的 weeklyRandomAssign 值。 */
    private boolean persistedWeeklyRandomAssign = false;
    /** 上次保存时的 dailyEnabled 值。 */
    private boolean persistedDailyEnabled = true;
    /** 上次保存时的 weeklyEnabled 值。 */
    private boolean persistedWeeklyEnabled = false;

    private final Map<UUID, PlayerTaskState> playerStates = new HashMap<>();
    private final Random random = new Random();

    public TaskManager(BedwarsPRO plugin) {
        this.plugin = plugin;
        this.tasksDir = new File(plugin.getDataFolder(), "tasks");
        this.playersDir = new File(tasksDir, "players");
        this.stateFile = new File(tasksDir, "state.yml");
    }

    public static TaskManager getInstance() {
        return instance;
    }

    public static void setInstance(TaskManager inst) {
        instance = inst;
    }

    // ==================== 初始化 ====================

    public void init() {
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
        if (!playersDir.exists()) {
            playersDir.mkdirs();
        }
        loadApiConfig();
        loadConfig();
        loadState();
        refreshDailyIfNeeded();
        refreshWeeklyIfNeeded();
        plugin.getLogger().info("[TaskManager] 任务系统已加载: 每日池 " + dailyTaskPool.size()
                + ", 每周池 " + weeklyTaskPool.size()
                + ", 当日可选 " + dailyTasks.size()
                + ", 本周 " + weeklyTasks.size()
                + ", 特殊 " + specialTasks.size()
                + ", 天=" + currentDay + " 周=" + currentWeek
                + ", 奖励API=" + (apiUrl.isEmpty() ? "未配置" : "已配置"));
    }

    // ==================== 配置加载 ====================

    public void loadConfig() {
        File file = getTasksConfigFile();
        if (!file.exists()) {
            ensureTasksConfigFile();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        dailyEnabled = cfg.getBoolean("daily.enabled", true);
        weeklyEnabled = cfg.getBoolean("weekly.enabled", false);
        randomAssign = cfg.getBoolean("daily.random-assign", true);
        dailyCount = cfg.getInt("daily.count", 3);
        weeklyRandomAssign = cfg.getBoolean("weekly.random-assign", false);
        weeklyCount = cfg.getInt("weekly.count", 3);
        timedDurationMinutes = cfg.getInt("timed.duration-minutes", 60);
        if (timedDurationMinutes < 1) {
            timedDurationMinutes = 60;
        }
        timeMultiplier = cfg.getLong("debug.time-multiplier", 1L);
        if (timeMultiplier < 1L) {
            timeMultiplier = 1L;
        }

        // 对局内行为奖励
        ingameNormalKillExp = cfg.getInt("ingame-rewards.normal-kill.exp", 1);
        ingameNormalKillCoins = cfg.getInt("ingame-rewards.normal-kill.coins", 3);
        ingameFinalKillExp = cfg.getInt("ingame-rewards.final-kill.exp", 3);
        ingameFinalKillCoins = cfg.getInt("ingame-rewards.final-kill.coins", 1);
        ingameDestroyBedExp = cfg.getInt("ingame-rewards.destroy-bed.exp", 5);
        ingameDestroyBedCoins = cfg.getInt("ingame-rewards.destroy-bed.coins", 1);
        ingameWinExp = cfg.getInt("ingame-rewards.win.exp", 6);
        ingameWinCoins = cfg.getInt("ingame-rewards.win.coins", 2);

        // 追杀令奖励
        bountyRewardExp = cfg.getInt("bounty.reward-exp", 15);
        bountyRewardCoins = cfg.getInt("bounty.reward-coins", 2);

        // 加载每日任务池
        dailyTaskPool.clear();
        if (cfg.isList("daily-tasks")) {
            for (Map<?, ?> raw : cfg.getMapList("daily-tasks")) {
                Task t = parseDailyTask(raw);
                if (t != null) {
                    dailyTaskPool.add(t);
                }
            }
        }
        // 兼容旧配置键 "tasks"
        if (dailyTaskPool.isEmpty() && cfg.isList("tasks")) {
            for (Map<?, ?> raw : cfg.getMapList("tasks")) {
                Task t = parseDailyTask(raw);
                if (t != null) {
                    dailyTaskPool.add(t);
                }
            }
        }

        // 加载每周任务池
        weeklyTaskPool.clear();
        if (cfg.isList("weekly-tasks")) {
            for (Map<?, ?> raw : cfg.getMapList("weekly-tasks")) {
                Task t = parseWeeklyTask(raw);
                if (t != null) {
                    weeklyTaskPool.add(t);
                }
            }
        }
    }

    private Task parseDailyTask(Map<?, ?> raw) {
        Object typeObj = raw.get("type");
        Object nameObj = raw.get("name");
        if (typeObj == null || nameObj == null) {
            return null;
        }
        TaskType type = TaskType.safeValueOf(typeObj.toString());
        if (type == null || type.isWeekly()) {
            return null;
        }
        int target = parseInt(raw.get("target"), 1);
        String name = nameObj.toString();
        String desc = raw.get("description") == null ? "" : raw.get("description").toString();
        int rewardExp = parseInt(raw.get("reward-exp"), 0);
        int rewardCoins = parseInt(raw.get("reward-coins"), 0);
        String targetPlayer = raw.get("target-player") == null
                ? null : raw.get("target-player").toString();
        return new Task(type, target, name, desc, rewardExp, rewardCoins, false, false, targetPlayer);
    }

    private Task parseWeeklyTask(Map<?, ?> raw) {
        Object typeObj = raw.get("type");
        Object nameObj = raw.get("name");
        if (typeObj == null || nameObj == null) {
            return null;
        }
        TaskType type = TaskType.safeValueOf(typeObj.toString());
        if (type == null || !type.isWeekly()) {
            return null;
        }
        // 每周任务用 target-min 作为基础 target，实际值在选任务时随机
        int targetMin = parseInt(raw.get("target-min"), parseInt(raw.get("target"), 1));
        int targetMax = parseInt(raw.get("target-max"), targetMin);
        String name = nameObj.toString();
        String desc = raw.get("description") == null ? "" : raw.get("description").toString();
        int rewardExp = parseInt(raw.get("reward-exp"), 0);
        int rewardCoins = parseInt(raw.get("reward-coins"), 0);
        // 存储时用 targetMin，max 在选任务时由 readWeeklyTargetMax 读取
        return new Task(type, targetMin, name, desc, rewardExp, rewardCoins, false, true);
    }

    private int parseInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * tasks.yml 配置文件位置：plugins/BedwarsPRO/tasks/tasks.yml。
     */
    private File getTasksConfigFile() {
        return new File(tasksDir, "tasks.yml");
    }

    /**
     * 确保 tasks.yml 存在于 tasks/ 目录下。若旧位置存在则迁移，否则从 JAR 释放。
     */
    private void ensureTasksConfigFile() {
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
        File target = getTasksConfigFile();
        if (target.exists()) {
            return;
        }
        // 兼容旧位置：plugins/BedwarsPRO/tasks.yml
        File oldFile = new File(plugin.getDataFolder(), "tasks.yml");
        if (oldFile.exists()) {
            if (oldFile.renameTo(target)) {
                return;
            }
        }
        // 从 JAR 释放到根目录，再迁移到 tasks/ 目录
        try {
            plugin.saveResource("tasks.yml", true);
        } catch (IllegalArgumentException ignored) {
            // JAR 中无该资源时忽略
        }
        if (oldFile.exists() && !target.exists()) {
            oldFile.renameTo(target);
        }
    }

    /**
     * api.yml 配置文件位置：plugins/BedwarsPRO/tasks/api.yml。
     */
    private File getApiConfigFile() {
        return new File(tasksDir, "api.yml");
    }

    /**
     * 加载奖励 API 配置（url + api-key）。若文件不存在则从 JAR 释放。
     */
    public void loadApiConfig() {
        File file = getApiConfigFile();
        if (!file.exists()) {
            ensureApiConfigFile();
        }
        if (!file.exists()) {
            apiUrl = "";
            apiKey = "";
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        apiUrl = cfg.getString("url", "");
        apiKey = cfg.getString("api-key", "");
        if (apiUrl == null) {
            apiUrl = "";
        }
        if (apiKey == null) {
            apiKey = "";
        }
    }

    /**
     * 确保 api.yml 存在于 tasks/ 目录下。从 JAR 释放到根目录再迁移。
     */
    private void ensureApiConfigFile() {
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
        File target = getApiConfigFile();
        if (target.exists()) {
            return;
        }
        File oldFile = new File(plugin.getDataFolder(), "api.yml");
        if (oldFile.exists()) {
            if (oldFile.renameTo(target)) {
                return;
            }
        }
        try {
            plugin.saveResource("api.yml", true);
        } catch (IllegalArgumentException ignored) {
            // JAR 中无该资源时忽略
        }
        if (oldFile.exists() && !target.exists()) {
            oldFile.renameTo(target);
        }
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    // ==================== 时间计算 ====================

    public long getCurrentDay() {
        long dayMillis = 86400000L / timeMultiplier;
        if (dayMillis < 1L) {
            dayMillis = 1L;
        }
        return System.currentTimeMillis() / dayMillis;
    }

    /** 周编号 = 天编号 / 7。 */
    public long getCurrentWeek() {
        return getCurrentDay() / 7L;
    }

    // ==================== 每日任务刷新 ====================

    public void refreshDailyIfNeeded() {
        // 先清理过期的限时任务
        purgeExpiredSpecialTasks();

        long today = getCurrentDay();
        // 配置变化检测：randomAssign 或 dailyEnabled 改变时，需重新选择
        boolean configChanged = (persistedRandomAssign != randomAssign)
                || (persistedDailyEnabled != dailyEnabled);

        // 同一天且未变更配置且已有任务：无需刷新
        if (today == currentDay && !configChanged && !dailyTasks.isEmpty()) {
            return;
        }

        // 同一天但 dailyTasks 为空（重启后）：从持久化名单恢复
        if (today == currentDay && !configChanged && !dailyTaskNames.isEmpty()) {
            dailyTasks.clear();
            for (String name : dailyTaskNames) {
                Task t = findTaskByName(dailyTaskPool, name);
                if (t != null) {
                    dailyTasks.add(t);
                }
            }
            for (Task special : specialTasks) {
                dailyTasks.add(special);
            }
            return;
        }

        // 新一天或配置变更：重新选择
        currentDay = today;
        dailyTasks.clear();
        dailyTaskNames.clear();
        if (dailyEnabled && !dailyTaskPool.isEmpty()) {
            if (randomAssign) {
                // 随机抽取 count 个
                List<Task> pool = new ArrayList<>(dailyTaskPool);
                int count = Math.min(dailyCount, pool.size());
                for (int i = 0; i < count; i++) {
                    int idx = random.nextInt(pool.size());
                    Task picked = pool.remove(idx);
                    dailyTasks.add(picked);
                    dailyTaskNames.add(picked.getName());
                }
            } else {
                // 自由选择：全部放入
                for (Task t : dailyTaskPool) {
                    dailyTasks.add(t);
                    dailyTaskNames.add(t.getName());
                }
            }
        }
        for (Task special : specialTasks) {
            dailyTasks.add(special);
        }
        persistedRandomAssign = randomAssign;
        persistedDailyEnabled = dailyEnabled;
        saveState();
    }

    /**
     * 清理已过期的限时任务（按 timed.duration-minutes）。
     */
    private void purgeExpiredSpecialTasks() {
        if (specialTasks.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long durationMs = (long) timedDurationMinutes * 60L * 1000L;
        List<Task> expired = new ArrayList<>();
        for (Task t : specialTasks) {
            Long publishTime = specialTaskPublishTime.get(getSpecialTaskKey(t));
            if (publishTime == null) {
                // 历史遗留任务（无时间戳）：跳过，不自动清理
                continue;
            }
            if (now - publishTime >= durationMs) {
                expired.add(t);
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        for (Task t : expired) {
            specialTasks.removeIf(s -> s.getName().equalsIgnoreCase(t.getName())
                    && (!s.isBounty() || s.getTargetPlayer() == null
                    || s.getTargetPlayer().equalsIgnoreCase(t.getTargetPlayer())));
            specialTaskPublishTime.remove(getSpecialTaskKey(t));
            dailyTasks.removeIf(x -> x.isSpecial()
                    && x.getName().equalsIgnoreCase(t.getName())
                    && (!x.isBounty() || x.getTargetPlayer() == null
                    || x.getTargetPlayer().equalsIgnoreCase(t.getTargetPlayer())));
            broadcastExpire(t);
        }
        saveState();
    }

    // ==================== 每周任务刷新 ====================

    public void refreshWeeklyIfNeeded() {
        long thisWeek = getCurrentWeek();
        // 配置变化检测：weeklyRandomAssign 或 weeklyEnabled 改变时需重新选择
        boolean configChanged = (persistedWeeklyRandomAssign != weeklyRandomAssign)
                || (persistedWeeklyEnabled != weeklyEnabled);

        if (thisWeek == currentWeek && !configChanged && !weeklyTasks.isEmpty()) {
            return;
        }
        // 同一周但 weeklyTasks 为空（重启后）：从持久化名单恢复
        if (thisWeek == currentWeek && !configChanged && !weeklyTaskNames.isEmpty()) {
            weeklyTasks.clear();
            for (int i = 0; i < weeklyTaskNames.size() && i < weeklyTaskTargets.size(); i++) {
                Task base = findTaskByName(weeklyTaskPool, weeklyTaskNames.get(i));
                if (base != null) {
                    weeklyTasks.add(base.withTarget(weeklyTaskTargets.get(i)));
                }
            }
            return;
        }
        // 新一周或配置变更：重新选择 + 随机 target
        currentWeek = thisWeek;
        weeklyTasks.clear();
        weeklyTaskNames.clear();
        weeklyTaskTargets.clear();
        if (weeklyEnabled && !weeklyTaskPool.isEmpty()) {
            List<Task> pool = new ArrayList<>(weeklyTaskPool);
            if (weeklyRandomAssign && pool.size() > weeklyCount) {
                // 随机抽取 weeklyCount 个
                int count = Math.min(weeklyCount, pool.size());
                List<Task> picked = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int idx = random.nextInt(pool.size());
                    picked.add(pool.remove(idx));
                }
                pool = picked;
            }
            for (Task base : pool) {
                int targetMin = base.getTarget();
                // 从原始配置读取 target-max
                int targetMax = readWeeklyTargetMax(base.getName(), targetMin);
                int chosenTarget = targetMin;
                if (targetMax > targetMin) {
                    chosenTarget = targetMin + random.nextInt(targetMax - targetMin + 1);
                }
                Task wt = base.withTarget(chosenTarget);
                weeklyTasks.add(wt);
                weeklyTaskNames.add(wt.getName());
                weeklyTaskTargets.add(chosenTarget);
            }
        }
        persistedWeeklyRandomAssign = weeklyRandomAssign;
        persistedWeeklyEnabled = weeklyEnabled;
        saveState();
    }

    /** 从 tasks.yml 读取某每周任务的 target-max。 */
    private int readWeeklyTargetMax(String taskName, int defaultVal) {
        File file = getTasksConfigFile();
        if (!file.exists()) {
            return defaultVal;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isList("weekly-tasks")) {
            return defaultVal;
        }
        for (Map<?, ?> raw : cfg.getMapList("weekly-tasks")) {
            Object nameObj = raw.get("name");
            if (nameObj != null && nameObj.toString().equalsIgnoreCase(taskName)) {
                return parseInt(raw.get("target-max"), defaultVal);
            }
        }
        return defaultVal;
    }

    // ==================== 状态持久化 ====================

    private void loadState() {
        if (!stateFile.exists()) {
            currentDay = 0L;
            currentWeek = 0L;
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(stateFile);
        currentDay = cfg.getLong("currentDay", 0L);
        currentWeek = cfg.getLong("currentWeek", 0L);
        persistedRandomAssign = cfg.getBoolean("persistedRandomAssign", true);
        persistedDailyEnabled = cfg.getBoolean("persistedDailyEnabled", true);
        persistedWeeklyRandomAssign = cfg.getBoolean("persistedWeeklyRandomAssign", false);
        persistedWeeklyEnabled = cfg.getBoolean("persistedWeeklyEnabled", false);

        dailyTaskNames.clear();
        if (cfg.isList("dailyTaskNames")) {
            dailyTaskNames.addAll(cfg.getStringList("dailyTaskNames"));
        }

        weeklyTaskNames.clear();
        weeklyTaskTargets.clear();
        if (cfg.isList("weeklyTaskNames")) {
            weeklyTaskNames.addAll(cfg.getStringList("weeklyTaskNames"));
        }
        if (cfg.isList("weeklyTaskTargets")) {
            for (Object o : cfg.getIntegerList("weeklyTaskTargets")) {
                weeklyTaskTargets.add((Integer) o);
            }
        }

        specialTasks.clear();
        specialTaskPublishTime.clear();
        if (cfg.isList("specialTasks")) {
            for (Map<?, ?> raw : cfg.getMapList("specialTasks")) {
                Task t = parseDailyTask(raw);
                if (t != null) {
                    Task special = t.asSpecial();
                    specialTasks.add(special);
                    // 兼容历史数据：publishTime 字段缺失则不放 Map（不自动清理）
                    Object tsObj = raw.get("publishTime");
                    if (tsObj != null) {
                        try {
                            specialTaskPublishTime.put(getSpecialTaskKey(special),
                                    Long.parseLong(tsObj.toString()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
    }

    private void saveState() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("currentDay", currentDay);
        cfg.set("currentWeek", currentWeek);
        cfg.set("persistedRandomAssign", persistedRandomAssign);
        cfg.set("persistedDailyEnabled", persistedDailyEnabled);
        cfg.set("persistedWeeklyRandomAssign", persistedWeeklyRandomAssign);
        cfg.set("persistedWeeklyEnabled", persistedWeeklyEnabled);
        cfg.set("dailyTaskNames", dailyTaskNames);
        cfg.set("weeklyTaskNames", weeklyTaskNames);
        cfg.set("weeklyTaskTargets", weeklyTaskTargets);

        List<Map<String, Object>> specialList = new ArrayList<>();
        for (Task t : specialTasks) {
            Map<String, Object> map = taskToMap(t);
            Long ts = specialTaskPublishTime.get(getSpecialTaskKey(t));
            if (ts != null) {
                map.put("publishTime", ts);
            }
            specialList.add(map);
        }
        cfg.set("specialTasks", specialList);

        try {
            cfg.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[TaskManager] 保存 state.yml 失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> taskToMap(Task t) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", t.getType().name());
        map.put("target", t.getTarget());
        map.put("name", t.getName());
        map.put("description", t.getDescription());
        map.put("reward-exp", t.getRewardExp());
        map.put("reward-coins", t.getRewardCoins());
        if (t.getTargetPlayer() != null) {
            map.put("target-player", t.getTargetPlayer());
        }
        return map;
    }

    /** 限时任务发布时间的 Map key：追杀令用 name:targetPlayer 避免同名冲突。 */
    private String getSpecialTaskKey(Task t) {
        if (t.isBounty() && t.getTargetPlayer() != null && !t.getTargetPlayer().isEmpty()) {
            return t.getName() + ":" + t.getTargetPlayer();
        }
        return t.getName();
    }

    private File getPlayerFile(UUID uuid) {
        return new File(playersDir, uuid.toString() + ".yml");
    }

    public PlayerTaskState getPlayerState(UUID uuid) {
        PlayerTaskState state = playerStates.get(uuid);
        if (state != null) {
            return state;
        }
        File f = getPlayerFile(uuid);
        if (f.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            state = PlayerTaskState.load(uuid, cfg);
        } else {
            state = new PlayerTaskState(uuid);
        }
        playerStates.put(uuid, state);
        return state;
    }

    public void savePlayerState(UUID uuid) {
        PlayerTaskState state = playerStates.get(uuid);
        if (state == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        state.save(cfg);
        try {
            cfg.save(getPlayerFile(uuid));
        } catch (IOException ex) {
            plugin.getLogger().warning("[TaskManager] 保存玩家状态失败 " + uuid + ": " + ex.getMessage());
        }
    }

    public void saveAll() {
        saveState();
        for (UUID uuid : playerStates.keySet()) {
            savePlayerState(uuid);
        }
    }

    // ==================== 每日任务接受与进度 ====================

    public List<Task> getDailyTasks() {
        refreshDailyIfNeeded();
        return dailyTasks;
    }

    public int acceptTask(Player player, int index) {
        if (!dailyEnabled) {
            return -2;
        }
        refreshDailyIfNeeded();
        if (index < 0 || index >= dailyTasks.size()) {
            return -1;
        }
        PlayerTaskState state = getPlayerState(player.getUniqueId());
        long today = getCurrentDay();
        if (state.hasAcceptedToday(today)) {
            return 0;
        }
        if (state.getAcceptedDay() != today) {
            state.resetForNewDay();
        }
        state.setAcceptedDay(today);
        state.setAcceptedTaskIndex(index);
        Task t = dailyTasks.get(index);
        state.setAcceptedSpecialName(t.isSpecial() ? t.getName() : null);
        state.setAcceptedBountyTarget(t.getTargetPlayer());
        state.setProgress(0);
        state.setCompleted(false);
        savePlayerState(player.getUniqueId());
        return 1;
    }

    public Task getAcceptedTask(Player player) {
        PlayerTaskState state = getPlayerState(player.getUniqueId());
        long today = getCurrentDay();
        if (!state.hasAcceptedToday(today)) {
            if (state.getAcceptedDay() != today) {
                state.resetForNewDay();
                savePlayerState(player.getUniqueId());
            }
            return null;
        }
        refreshDailyIfNeeded();

        // 通过任务名 + 目标玩家（追杀令）查找，不再依赖索引
        String expectedName = state.getAcceptedSpecialName();
        String expectedTarget = state.getAcceptedBountyTarget();

        for (Task t : dailyTasks) {
            // 非特殊任务：名字匹配即可
            if (expectedName == null && !t.isSpecial()) {
                // 普通任务：使用 acceptedTaskIndex 作为后备
                int idx = state.getAcceptedTaskIndex();
                if (idx >= 0 && idx < dailyTasks.size() && dailyTasks.get(idx) == t) {
                    return t;
                }
                continue;
            }
            // 特殊任务：名字必须匹配
            if (expectedName != null && t.isSpecial()
                    && t.getName().equalsIgnoreCase(expectedName)) {
                // 追杀令：还需匹配目标玩家
                if (t.isBounty()) {
                    if (expectedTarget != null
                            && t.getTargetPlayer() != null
                            && t.getTargetPlayer().equalsIgnoreCase(expectedTarget)) {
                        return t;
                    }
                } else {
                    // 其他限时任务：名字匹配即可
                    return t;
                }
            }
        }
        return null;
    }

    public PlayerTaskState getState(Player player) {
        return getPlayerState(player.getUniqueId());
    }

    public int addProgress(Player player, TaskType type, int amount) {
        Task task = getAcceptedTask(player);
        if (task == null || task.getType() != type) {
            return 0;
        }
        PlayerTaskState state = getState(player);
        if (state.isCompleted()) {
            return 0;
        }
        int newProgress = Math.min(task.getTarget(), state.getProgress() + amount);
        int added = newProgress - state.getProgress();
        state.setProgress(newProgress);
        if (newProgress >= task.getTarget()) {
            state.setCompleted(true);
            notifyCompletion(player, task);
            grantReward(player, task);
        }
        savePlayerState(player.getUniqueId());
        return added;
    }

    // ==================== 每周任务进度 ====================

    public List<Task> getWeeklyTasks() {
        refreshWeeklyIfNeeded();
        return weeklyTasks;
    }

    /** 给玩家增加每周任务进度。type 必须是每周任务类型。 */
    public int addWeeklyProgress(Player player, TaskType type, int amount) {
        if (!weeklyEnabled || !type.isWeekly()) {
            return 0;
        }
        refreshWeeklyIfNeeded();
        Task weeklyTask = null;
        for (Task t : weeklyTasks) {
            if (t.getType() == type) {
                weeklyTask = t;
                break;
            }
        }
        if (weeklyTask == null) {
            return 0;
        }
        PlayerTaskState state = getPlayerState(player.getUniqueId());
        long thisWeek = getCurrentWeek();
        // 跨周重置
        if (state.getWeeklyWeek() != thisWeek) {
            state.resetWeekly(thisWeek);
        }
        if (state.isWeeklyCompleted(weeklyTask.getName())) {
            return 0;
        }
        int newProg = Math.min(weeklyTask.getTarget(),
                state.addWeeklyProgress(weeklyTask.getName(), amount));
        if (newProg >= weeklyTask.getTarget()) {
            state.markWeeklyCompleted(weeklyTask.getName());
            notifyWeeklyCompletion(player, weeklyTask);
            grantReward(player, weeklyTask);
        }
        savePlayerState(player.getUniqueId());
        return newProg;
    }

    public int getWeeklyProgress(Player player, String taskName) {
        PlayerTaskState state = getPlayerState(player.getUniqueId());
        long thisWeek = getCurrentWeek();
        if (state.getWeeklyWeek() != thisWeek) {
            return 0;
        }
        return state.getWeeklyProgress(taskName);
    }

    public boolean isWeeklyCompleted(Player player, String taskName) {
        PlayerTaskState state = getPlayerState(player.getUniqueId());
        long thisWeek = getCurrentWeek();
        if (state.getWeeklyWeek() != thisWeek) {
            return false;
        }
        return state.isWeeklyCompleted(taskName);
    }

    private void notifyCompletion(Player player, Task task) {
        TaskMessages.msg(player, "daily-complete", "task", task.getDisplayName());
        TaskMessages.msg(player, "reward-line",
                "exp", task.getRewardExp(), "coins", task.getRewardCoins());
    }

    private void notifyWeeklyCompletion(Player player, Task task) {
        TaskMessages.msg(player, "weekly-complete", "task", task.getDisplayName());
        TaskMessages.msg(player, "reward-line",
                "exp", task.getRewardExp(), "coins", task.getRewardCoins());
    }

    // ==================== 奖励发放（HTTP API 异步）====================

    /**
     * 异步调用奖励 API 给予玩家经验和栖云币。
     * 在任务完成（每日/每周/限时）时调用，仅触发一次。
     */
    private void grantReward(final Player player, final Task task) {
        grantRewardsAsync(player.getName(), task.getRewardExp(), task.getRewardCoins(), task.getName());
    }

    /**
     * 异步调用奖励 API 给予玩家指定经验和栖云币（通用方法）。
     * @param playerName 玩家名
     * @param exp 经验数量（<=0 则不发放经验）
     * @param coins 栖云币数量（<=0 则不发放栖云币）
     * @param source 来源描述（用于日志，如任务名或"普通击杀"）
     */
    public void grantRewardsAsync(final String playerName, final int exp, final int coins,
                                  final String source) {
        if (apiUrl == null || apiUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            plugin.getLogger().warning("[TaskManager] 奖励 API 未配置，跳过发放: "
                    + playerName + " / " + source);
            return;
        }
        if (exp <= 0 && coins <= 0) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                if (exp > 0) {
                    String resp = callRewardApi(playerName, "add_exp", exp);
                    plugin.getLogger().info("[TaskManager] 奖励API(exp) " + playerName
                            + " +" + exp + " (" + source + "): " + resp);
                }
                if (coins > 0) {
                    String resp = callRewardApi(playerName, "add_coins", coins);
                    plugin.getLogger().info("[TaskManager] 奖励API(coins) " + playerName
                            + " +" + coins + " (" + source + "): " + resp);
                }
            }
        });
    }

    // ==================== 对局内行为奖励（实时）====================

    /** 普通击杀奖励（可重复，每次击杀都发放） */
    public void rewardNormalKill(Player killer) {
        TaskMessages.msg(killer, "ingame-normal-kill",
                "exp", ingameNormalKillExp, "coins", ingameNormalKillCoins);
        grantRewardsAsync(killer.getName(), ingameNormalKillExp, ingameNormalKillCoins, "普通击杀");
    }

    /** 最终击杀奖励（可重复，每次最终击杀都发放） */
    public void rewardFinalKill(Player killer) {
        TaskMessages.msg(killer, "ingame-final-kill",
                "exp", ingameFinalKillExp, "coins", ingameFinalKillCoins);
        grantRewardsAsync(killer.getName(), ingameFinalKillExp, ingameFinalKillCoins, "最终击杀");
    }

    /** 拆床奖励（可重复，每次拆床都发放） */
    public void rewardDestroyBed(Player player) {
        TaskMessages.msg(player, "ingame-destroy-bed",
                "exp", ingameDestroyBedExp, "coins", ingameDestroyBedCoins);
        grantRewardsAsync(player.getName(), ingameDestroyBedExp, ingameDestroyBedCoins, "拆床");
    }

    /** 胜利奖励（每局仅一次） */
    public void rewardWin(Player player) {
        TaskMessages.msg(player, "ingame-win",
                "exp", ingameWinExp, "coins", ingameWinCoins);
        grantRewardsAsync(player.getName(), ingameWinExp, ingameWinCoins, "胜利");
    }

    // ==================== 追杀令（限时任务专用）====================

    /**
     * 发布追杀令限时任务。
     * @param targetPlayer 击杀目标玩家名
     * @return 1=成功, -1=同名目标已存在
     */
    public int publishBountyTask(String targetPlayer) {
        // 检查是否已存在针对同一目标的追杀令
        for (Task s : specialTasks) {
            if (s.isBounty() && s.getTargetPlayer() != null
                    && s.getTargetPlayer().equalsIgnoreCase(targetPlayer)) {
                return -1;
            }
        }
        String taskName = "击杀令";
        String desc = "在游戏中击杀目标玩家: " + targetPlayer;
        Task bounty = new Task(TaskType.BOUNTY, 1, taskName, desc,
                bountyRewardExp, bountyRewardCoins, true, false, targetPlayer);
        specialTasks.add(bounty);
        specialTaskPublishTime.put(getSpecialTaskKey(bounty),
                System.currentTimeMillis());
        refreshDailyIfNeeded();
        dailyTasks.add(bounty);
        saveState();
        broadcastPublish(bounty);
        return 1;
    }

    /**
     * 检查击杀事件是否触发追杀令完成。
     * 当杀手已接受追杀令任务且受害者为目标玩家时，完成任务并发放奖励。
     * @return true=触发追杀令完成
     */
    public boolean checkBountyKill(Player killer, Player victim) {
        Task accepted = getAcceptedTask(killer);
        if (accepted == null || !accepted.isBounty() || accepted.isSpecial() != true) {
            return false;
        }
        if (accepted.getTargetPlayer() == null) {
            return false;
        }
        if (!accepted.getTargetPlayer().equalsIgnoreCase(victim.getName())) {
            return false;
        }
        PlayerTaskState state = getState(killer);
        if (state.isCompleted()) {
            return false;
        }
        state.setProgress(1);
        state.setCompleted(true);
        TaskMessages.msg(killer, "bounty-complete", "target", victim.getName());
        TaskMessages.msg(killer, "reward-line",
                "exp", accepted.getRewardExp(), "coins", accepted.getRewardCoins());
        grantReward(killer, accepted);
        savePlayerState(killer.getUniqueId());
        return true;
    }

    /**
     * 调用奖励 API：POST api_key=&name=&lt;field&gt;=&lt;amount&gt;。
     * @param field "add_exp" 或 "add_coins"
     */
    private String callRewardApi(String playerName, String field, int amount) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            String body = "api_key=" + URLEncoder.encode(apiKey, "UTF-8")
                    + "&name=" + URLEncoder.encode(playerName, "UTF-8")
                    + "&" + field + "=" + amount;
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
            conn.disconnect();
            return resp;
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    // ==================== 限时任务发布（原特殊任务）====================

    public int publishSpecialTask(String name) {
        Task found = findTaskByName(dailyTaskPool, name);
        if (found == null) {
            return 0;
        }
        for (Task s : specialTasks) {
            if (s.getName().equalsIgnoreCase(found.getName())) {
                return -1;
            }
        }
        Task special = found.asSpecial();
        specialTasks.add(special);
        specialTaskPublishTime.put(getSpecialTaskKey(special), System.currentTimeMillis());
        refreshDailyIfNeeded();
        dailyTasks.add(special);
        saveState();
        broadcastPublish(special);
        return 1;
    }

    private void broadcastPublish(Task task) {
        String msg = TaskMessages.get("timed-publish-broadcast",
                "task", task.getDisplayName(),
                "description", task.getDescription(),
                "minutes", timedDurationMinutes);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isPlayerInGameOrQueue(p)) {
                p.sendMessage(msg);
            }
        }
    }

    /** 判断玩家是否在游戏对局或等待队列中。 */
    private boolean isPlayerInGameOrQueue(Player p) {
        BedwarsPRO bp = BedwarsPRO.getInstance();
        if (bp == null || bp.getGameManager() == null) {
            return false;
        }
        return bp.getGameManager().getGameOfPlayer(p) != null;
    }

    public void clearSpecialTasks() {
        specialTasks.clear();
        specialTaskPublishTime.clear();
        refreshDailyIfNeeded();
        saveState();
    }

    /**
     * 按 ID 移除单个限时任务。ID = 在列表中的位置 + 1（从1开始）。
     * @return 1=成功, 0=ID无效, -1=列表为空
     */
    public int removeSpecialTask(int id) {
        if (specialTasks.isEmpty()) {
            return -1;
        }
        int index = id - 1;
        if (index < 0 || index >= specialTasks.size()) {
            return 0;
        }
        Task removed = specialTasks.remove(index);
        specialTaskPublishTime.remove(getSpecialTaskKey(removed));
        // 重建当日任务列表（移除被删的限时任务）
        refreshDailyIfNeeded();
        dailyTasks.removeIf(t -> t.isSpecial()
                && t.getName().equalsIgnoreCase(removed.getName())
                && (!t.isBounty() || t.getTargetPlayer() == null
                || t.getTargetPlayer().equalsIgnoreCase(removed.getTargetPlayer())));
        saveState();
        broadcastRemove(removed, id);
        return 1;
    }

    private void broadcastRemove(Task task, int id) {
        String msg = TaskMessages.get("timed-remove",
                "id", id, "task", task.getDisplayName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    private void broadcastExpire(Task task) {
        String msg = TaskMessages.get("timed-expire", "task", task.getDisplayName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    /** 获取特殊任务列表（顺序即 ID 顺序，ID = index + 1）。 */
    public List<Task> getSpecialTasksList() {
        return new ArrayList<>(specialTasks);
    }

    // ==================== 工具方法 ====================

    private Task findTaskByName(List<Task> pool, String name) {
        if (name == null) {
            return null;
        }
        for (Task t : pool) {
            if (t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    // ==================== 配置开关 ====================

    public boolean isDailyEnabled() {
        return dailyEnabled;
    }

    public boolean isWeeklyEnabled() {
        return weeklyEnabled;
    }

    public boolean isRandomAssign() {
        return randomAssign;
    }

    public boolean isWeeklyRandomAssign() {
        return weeklyRandomAssign;
    }

    public int getDailyCount() {
        return dailyCount;
    }

    public int getWeeklyCount() {
        return weeklyCount;
    }

    public int getTimedDurationMinutes() {
        return timedDurationMinutes;
    }

    public long getTimeMultiplier() {
        return timeMultiplier;
    }

    public List<Task> getSpecialTasks() {
        return specialTasks;
    }

    public void setDailyEnabled(boolean enabled) {
        this.dailyEnabled = enabled;
        writeConfigBool("daily.enabled", enabled);
    }

    public void setWeeklyEnabled(boolean enabled) {
        this.weeklyEnabled = enabled;
        writeConfigBool("weekly.enabled", enabled);
        // 立即触发刷新
        refreshWeeklyIfNeeded();
    }

    /** 设置每日随机分配开关，写回 tasks.yml 并触发刷新。 */
    public void setRandomAssign(boolean enabled) {
        this.randomAssign = enabled;
        writeConfigBool("daily.random-assign", enabled);
        refreshDailyIfNeeded();
    }

    /** 设置每周随机分配开关，写回 tasks.yml 并触发刷新。 */
    public void setWeeklyRandomAssign(boolean enabled) {
        this.weeklyRandomAssign = enabled;
        writeConfigBool("weekly.random-assign", enabled);
        refreshWeeklyIfNeeded();
    }

    private void writeConfigBool(String key, boolean value) {
        File file = getTasksConfigFile();
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            cfg.set(key, value);
            try {
                cfg.save(file);
            } catch (IOException ex) {
                plugin.getLogger().warning("[TaskManager] 写回 tasks.yml 失败: " + ex.getMessage());
            }
        }
    }

    /** 热重载：重新加载 tasks.yml + api.yml 配置并刷新当日/本周任务。 */
    public void reload() {
        TaskMessages.reload();
        loadApiConfig();
        loadConfig();
        // 标记需要重新选择
        persistedRandomAssign = !randomAssign; // 强制触发 refreshDailyIfNeeded 中的 configChanged
        persistedWeeklyRandomAssign = !weeklyRandomAssign;
        refreshDailyIfNeeded();
        refreshWeeklyIfNeeded();
        plugin.getLogger().info("[TaskManager] 配置已重载: 每日池 " + dailyTaskPool.size()
                + ", 每周池 " + weeklyTaskPool.size()
                + ", 奖励API=" + (apiUrl.isEmpty() ? "未配置" : "已配置"));
    }

    // ==================== 玩家状态重置 ====================

    /**
     * 重置玩家任务进度。
     * @param uuid 玩家 UUID
     * @param type "daily" / "weekly" / "timed" / "all"
     * @return 1=成功, 0=玩家状态不存在, -1=类型无效
     */
    public int resetPlayerState(UUID uuid, String type) {
        if (type == null) {
            return -1;
        }
        String t = type.toLowerCase();
        if (!t.equals("daily") && !t.equals("weekly") && !t.equals("timed") && !t.equals("all")) {
            return -1;
        }
        PlayerTaskState state = playerStates.get(uuid);
        if (state == null) {
            // 尝试从磁盘加载
            File f = getPlayerFile(uuid);
            if (f.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                state = PlayerTaskState.load(uuid, cfg);
                playerStates.put(uuid, state);
            } else {
                return 0;
            }
        }
        boolean dailyReset = t.equals("daily") || t.equals("all");
        boolean weeklyReset = t.equals("weekly") || t.equals("all");
        boolean timedReset = t.equals("timed");

        if (timedReset) {
            // 仅当玩家今日已接受的是限时任务时才重置
            Task accepted = null;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                accepted = getAcceptedTask(player);
            }
            if (accepted != null && accepted.isSpecial()) {
                state.resetForNewDay();
            } else if (accepted != null && !accepted.isSpecial()) {
                // 接受的不是限时任务，无操作
            } else {
                // 玩家不在线或未接受任务：检查 acceptedSpecialName
                if (state.getAcceptedSpecialName() != null) {
                    state.resetForNewDay();
                }
            }
        }
        if (dailyReset) {
            state.resetForNewDay();
        }
        if (weeklyReset) {
            state.resetWeekly(getCurrentWeek());
        }
        savePlayerState(uuid);
        return 1;
    }
}
