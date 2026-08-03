package io.jmmym.bedwarspro.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 玩家任务进度状态。
 *
 * <p>每日任务：每天只能接受1个，通过 acceptedDay + acceptedTaskIndex 标识。
 *    对于追杀令（BOUNTY）任务，额外保存 acceptedBountyTarget 用于精确匹配。</p>
 * <p>每周任务：无需接受，按 weeklyWeek 编号跟踪每个任务的进度。</p>
 */
public class PlayerTaskState {

    private final UUID playerId;

    // ===== 每日任务状态 =====
    private long acceptedDay = 0L;
    private int acceptedTaskIndex = -1;
    private String acceptedSpecialName = null;
    /** 追杀令接受时的目标玩家名，用于精确定位任务（其他类型为 null）。 */
    private String acceptedBountyTarget = null;
    private int progress = 0;
    private boolean completed = false;

    // ===== 每周任务状态 =====
    /** 当前进度对应的周编号。0 表示未初始化。 */
    private long weeklyWeek = 0L;
    /** 每周任务进度：key=任务名, value=进度。 */
    private final Map<String, Integer> weeklyProgress = new HashMap<>();
    /** 已完成的每周任务名集合。 */
    private final Set<String> weeklyCompleted = new HashSet<>();

    public PlayerTaskState(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    // ===== 每日任务方法 =====

    public long getAcceptedDay() {
        return acceptedDay;
    }

    public void setAcceptedDay(long acceptedDay) {
        this.acceptedDay = acceptedDay;
    }

    public int getAcceptedTaskIndex() {
        return acceptedTaskIndex;
    }

    public void setAcceptedTaskIndex(int acceptedTaskIndex) {
        this.acceptedTaskIndex = acceptedTaskIndex;
    }

    public String getAcceptedSpecialName() {
        return acceptedSpecialName;
    }

    public void setAcceptedSpecialName(String acceptedSpecialName) {
        this.acceptedSpecialName = acceptedSpecialName;
    }

    public String getAcceptedBountyTarget() {
        return acceptedBountyTarget;
    }

    public void setAcceptedBountyTarget(String acceptedBountyTarget) {
        this.acceptedBountyTarget = acceptedBountyTarget;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean hasAcceptedToday(long currentDay) {
        return acceptedDay == currentDay && acceptedTaskIndex >= 0;
    }

    public void resetForNewDay() {
        this.acceptedDay = 0L;
        this.acceptedTaskIndex = -1;
        this.acceptedSpecialName = null;
        this.acceptedBountyTarget = null;
        this.progress = 0;
        this.completed = false;
    }

    // ===== 每周任务方法 =====

    public long getWeeklyWeek() {
        return weeklyWeek;
    }

    public void setWeeklyWeek(long weeklyWeek) {
        this.weeklyWeek = weeklyWeek;
    }

    /** 获取某每周任务的进度，不存在返回0。 */
    public int getWeeklyProgress(String taskName) {
        return weeklyProgress.getOrDefault(taskName, 0);
    }

    /** 设置某每周任务的进度。 */
    public void setWeeklyProgress(String taskName, int value) {
        weeklyProgress.put(taskName, value);
    }

    /** 增加某每周任务的进度，返回增加后的新值。 */
    public int addWeeklyProgress(String taskName, int amount) {
        int newVal = getWeeklyProgress(taskName) + amount;
        weeklyProgress.put(taskName, newVal);
        return newVal;
    }

    public boolean isWeeklyCompleted(String taskName) {
        return weeklyCompleted.contains(taskName);
    }

    public void markWeeklyCompleted(String taskName) {
        weeklyCompleted.add(taskName);
    }

    /** 跨周重置：清空进度和完成状态。 */
    public void resetWeekly(long newWeek) {
        this.weeklyWeek = newWeek;
        this.weeklyProgress.clear();
        this.weeklyCompleted.clear();
    }

    // ===== 序列化 =====

    public void save(ConfigurationSection section) {
        section.set("acceptedDay", acceptedDay);
        section.set("acceptedTaskIndex", acceptedTaskIndex);
        section.set("progress", progress);
        section.set("completed", completed);
        section.set("acceptedSpecialName", acceptedSpecialName);
        section.set("acceptedBountyTarget", acceptedBountyTarget);

        section.set("weeklyWeek", weeklyWeek);
        ConfigurationSection weeklySec = section.createSection("weeklyProgress");
        for (Map.Entry<String, Integer> e : weeklyProgress.entrySet()) {
            weeklySec.set(e.getKey(), e.getValue());
        }
        section.set("weeklyCompleted", new java.util.ArrayList<>(weeklyCompleted));
    }

    public static PlayerTaskState load(UUID playerId, ConfigurationSection section) {
        PlayerTaskState state = new PlayerTaskState(playerId);
        if (section == null) {
            return state;
        }
        state.acceptedDay = section.getLong("acceptedDay", 0L);
        state.acceptedTaskIndex = section.getInt("acceptedTaskIndex", -1);
        state.progress = section.getInt("progress", 0);
        state.completed = section.getBoolean("completed", false);
        state.acceptedSpecialName = section.getString("acceptedSpecialName", null);
        state.acceptedBountyTarget = section.getString("acceptedBountyTarget", null);

        state.weeklyWeek = section.getLong("weeklyWeek", 0L);
        ConfigurationSection weeklySec = section.getConfigurationSection("weeklyProgress");
        if (weeklySec != null) {
            for (String key : weeklySec.getKeys(false)) {
                state.weeklyProgress.put(key, weeklySec.getInt(key, 0));
            }
        }
        for (String s : section.getStringList("weeklyCompleted")) {
            state.weeklyCompleted.add(s);
        }
        return state;
    }
}
