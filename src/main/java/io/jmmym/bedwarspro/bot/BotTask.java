package io.jmmym.bedwarspro.bot;

import io.jmmym.bedwarspro.game.Game;
import org.bukkit.entity.Player;

/**
 * Bot任务接口。定义AI行为单元的生命周期。
 *
 * <p>每tick由 {@link BotTaskRunner} 驱动，按优先级顺序评估并执行。</p>
 */
public interface BotTask {

  /**
   * 获取任务优先级。数字越小优先级越高。
   */
  int getPriority();

  /**
   * 获取任务类型。
   */
  BotTaskType getTaskType();

  /**
   * 初始化任务。当任务被分配给Bot时调用一次。
   *
   * @param game 当前游戏
   * @param bot 机器人玩家
   */
  void initialize(Game game, Player bot);

  /**
   * 判断是否应该执行此任务。每tick调用。
   *
   * @param game 当前游戏
   * @param bot 机器人玩家
   * @return true表示应执行，false表示跳过
   */
  boolean shouldExecute(Game game, Player bot);

  /**
   * 执行任务逻辑。每tick调用一次，必须在一tick内完成（非阻塞）。
   *
   * @param game 当前游戏
   * @param bot 机器人玩家
   */
  void execute(Game game, Player bot);

  /**
   * 检查任务是否完成。
   *
   * @param game 当前游戏
   * @param bot 机器人玩家
   * @return true表示已完成
   */
  boolean isComplete(Game game, Player bot);

  /**
   * 清理任务状态。任务完成或被中断时调用。
   *
   * @param game 当前游戏
   * @param bot 机器人玩家
   */
  void cleanup(Game game, Player bot);
}
