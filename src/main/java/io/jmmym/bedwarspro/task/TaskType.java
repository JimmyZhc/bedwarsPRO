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

    /** 追杀令（限时任务专用，击杀指定玩家） */
    BOUNTY,

    // ===== 每周任务类型 =====
    /** 每周累计击杀 */
    WEEKLY_KILL,
    /** 每周累计胜场 */
    WEEKLY_WIN,
    /** 每周累计拆床 */
    WEEKLY_DESTROY_BED;

    /** 是否为每周任务类型。 */
    public boolean isWeekly() {
        return this == WEEKLY_KILL || this == WEEKLY_WIN || this == WEEKLY_DESTROY_BED;
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
