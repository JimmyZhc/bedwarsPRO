package io.jmmym.bedwarspro.task;

/**
 * 任务类型枚举。
 */
public enum TaskType {
    // ===== 每日任务类型 =====
    /** 参与对局 */
    PARTICIPATE,
    /** 击杀玩家 */
    KILL,
    /** 破坏床 */
    DESTROY_BED,
    /** 收集资源 */
    COLLECT_RESOURCE,
    /** 获胜对局 */
    WIN,
    /** 最终击杀（床被破坏后击杀玩家） */
    FINAL_KILL,
    /** 快速获胜（5分钟内获胜） */
    QUICK_WIN,

    // ===== 新增每日任务类型 =====
    /** 弓箭手：用弓击杀玩家 */
    BOW_KILL,
    /** 连杀达人：连续击杀玩家未死亡 */
    KILL_STREAK,
    /** 反击者：生命值低于5颗心时击杀玩家 */
    COUNTER_ATTACK,
    /** 偷袭者：末影珍珠传送后5秒内击杀玩家 */
    PEARL_KILL,
    /** TNT狂人：用TNT击杀玩家 */
    TNT_KILL,
    /** 虚空猎手：将玩家击入虚空 */
    VOID_KILL,
    /** 生存专家：一局游戏中存活N分钟 */
    SURVIVOR,
    /** 逆风翻盘：队伍只剩1人时击杀敌人 */
    COMEBACK,
    /** 无伤获胜：一场游戏中未死亡并获胜 */
    UNDEFEATED,
    /** 首杀：一场游戏中获得首杀 */
    FIRST_BLOOD,

    /** 追杀令（限时任务专用，击杀指定玩家） */
    BOUNTY,

    // ===== 每周任务类型 =====
    /** 每周累计击杀 */
    WEEKLY_KILL,
    /** 每周累计胜场 */
    WEEKLY_WIN,
    /** 每周累计拆床 */
    WEEKLY_DESTROY_BED,
    /** 每周累计胜场（百胜将军，target 较大，独立类型避免与 WEEKLY_WIN 冲突） */
    WEEKLY_GENERAL_WIN;

    /** 是否为每周任务类型。 */
    public boolean isWeekly() {
        return this == WEEKLY_KILL || this == WEEKLY_WIN || this == WEEKLY_DESTROY_BED
                || this == WEEKLY_GENERAL_WIN;
    }

    public static TaskType safeValueOf(String name) {
        if (name == null) {
            return null;
        }
        try {
            return TaskType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
