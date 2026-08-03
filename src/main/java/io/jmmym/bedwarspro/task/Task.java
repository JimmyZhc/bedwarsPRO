package io.jmmym.bedwarspro.task;

import org.bukkit.ChatColor;

/**
 * 任务域类。描述一个可被玩家接受的每日/每周任务定义。
 *
 * <p>奖励通过外部 HTTP API 发放：完成时由 TaskManager 调用 api.yml 配置的接口，
 * 给予玩家 {@link #rewardExp} 点经验和 {@link #rewardCoins} 枚栖云币。</p>
 *
 * <p>追杀令（BOUNTY）任务通过 {@link #targetPlayer} 指定击杀目标玩家。</p>
 */
public class Task {

    private final TaskType type;
    private final int target;
    private final String name;
    private final String description;
    private final int rewardExp;
    private final int rewardCoins;
    /** 是否为管理员发布的限时任务（金色名） */
    private final boolean special;
    /** 是否为每周任务 */
    private final boolean weekly;
    /** 追杀令目标玩家名（仅 BOUNTY 类型使用，其他为 null） */
    private final String targetPlayer;

    public Task(TaskType type, int target, String name, String description, int rewardExp) {
        this(type, target, name, description, rewardExp, 0, false, type.isWeekly(), null);
    }

    public Task(TaskType type, int target, String name, String description, int rewardExp,
                boolean special) {
        this(type, target, name, description, rewardExp, 0, special, type.isWeekly(), null);
    }

    public Task(TaskType type, int target, String name, String description, int rewardExp,
                boolean special, boolean weekly) {
        this(type, target, name, description, rewardExp, 0, special, weekly, null);
    }

    public Task(TaskType type, int target, String name, String description,
                int rewardExp, int rewardCoins, boolean special, boolean weekly) {
        this(type, target, name, description, rewardExp, rewardCoins, special, weekly, null);
    }

    public Task(TaskType type, int target, String name, String description,
                int rewardExp, int rewardCoins, boolean special, boolean weekly,
                String targetPlayer) {
        this.type = type;
        this.target = target;
        this.name = name;
        this.description = description;
        this.rewardExp = rewardExp;
        this.rewardCoins = rewardCoins;
        this.special = special;
        this.weekly = weekly;
        this.targetPlayer = targetPlayer;
    }

    public TaskType getType() {
        return type;
    }

    public int getTarget() {
        return target;
    }

    public String getName() {
        return name;
    }

    /** 获取带颜色前缀的展示名：限时任务为金色，每周任务为紫色，普通任务为白色。
     *  追杀令任务附带目标玩家名。 */
    public String getDisplayName() {
        String base = name;
        if (targetPlayer != null && !targetPlayer.isEmpty()) {
            base = name + " (目标: " + targetPlayer + ")";
        }
        if (special) {
            return ChatColor.GOLD + base + ChatColor.RESET;
        }
        if (weekly) {
            return ChatColor.LIGHT_PURPLE + base + ChatColor.RESET;
        }
        return ChatColor.WHITE + base + ChatColor.RESET;
    }

    public String getDescription() {
        return description;
    }

    public int getRewardExp() {
        return rewardExp;
    }

    public int getRewardCoins() {
        return rewardCoins;
    }

    public boolean isSpecial() {
        return special;
    }

    public boolean isWeekly() {
        return weekly;
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    public boolean isBounty() {
        return type == TaskType.BOUNTY;
    }

    /** 构造一个标记为限时任务的副本（保留原任务定义，仅切换 special 标记）。 */
    public Task asSpecial() {
        return new Task(type, target, name, description, rewardExp, rewardCoins, true, weekly, targetPlayer);
    }

    /** 构造一个替换 target 值的副本（用于每周任务随机选取目标）。 */
    public Task withTarget(int newTarget) {
        return new Task(type, newTarget, name, description, rewardExp, rewardCoins, special, weekly, targetPlayer);
    }
}
