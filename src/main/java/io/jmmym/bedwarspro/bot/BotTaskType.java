package io.jmmym.bedwarspro.bot;

/**
 * Bot任务类型枚举。优先级数字越小越优先执行。
 */
public enum BotTaskType {

  VOID_AVOID(0, "避免虚空"),
  BED_DEFENSE(1, "保护床"),
  HEAL(2, "治疗"),
  COLLECT_RESOURCES(10, "收集资源"),
  UPGRADE_EQUIPMENT(11, "升级装备"),
  BUILD_BRIDGE(20, "搭桥"),
  GO_TO_SHOP(21, "前往商店"),
  BUY_ITEMS(22, "购买物品"),
  ATTACK_ENEMY(30, "攻击敌人"),
  DESTROY_BED(31, "破坏床"),
  RETURN_TO_BASE(40, "返回基地"),
  IDLE(50, "待机");

  private final int priority;
  private final String displayName;

  BotTaskType(int priority, String displayName) {
    this.priority = priority;
    this.displayName = displayName;
  }

  public int getPriority() {
    return priority;
  }

  public String getDisplayName() {
    return displayName;
  }
}
