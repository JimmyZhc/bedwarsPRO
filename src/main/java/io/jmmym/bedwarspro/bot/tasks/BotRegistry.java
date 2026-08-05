package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTask;
import java.util.ArrayList;
import java.util.List;

/**
 * Bot任务注册表。集中管理所有可用的AI任务。
 */
public class BotRegistry {

  private static final List<Class<? extends BotTask>> TASK_CLASSES = new ArrayList<>();

  static {
    // 按优先级从高到低注册
    TASK_CLASSES.add(AvoidVoidTask.class);
    TASK_CLASSES.add(DefendBedTask.class);
    TASK_CLASSES.add(HealTask.class);
    TASK_CLASSES.add(ReturnToBaseTask.class);
    TASK_CLASSES.add(CollectResourcesTask.class);
    TASK_CLASSES.add(BuyItemsTask.class);
    TASK_CLASSES.add(BuildBridgeTask.class);
    TASK_CLASSES.add(AttackEnemyTask.class);
    TASK_CLASSES.add(DestroyBedTask.class);
  }

  /**
   * 创建所有可用任务的实例。
   */
  public static List<BotTask> createAllTasks() {
    List<BotTask> tasks = new ArrayList<>();
    for (Class<? extends BotTask> clazz : TASK_CLASSES) {
      try {
        tasks.add(clazz.newInstance());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return tasks;
  }

  /**
   * 创建指定任务的实例。
   */
  public static BotTask createTask(Class<? extends BotTask> clazz) {
    try {
      return clazz.newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}
