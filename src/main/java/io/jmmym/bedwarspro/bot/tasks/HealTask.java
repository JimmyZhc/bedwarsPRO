package io.jmmym.bedwarspro.bot.tasks;

import io.jmmym.bedwarspro.bot.BotTaskType;
import io.jmmym.bedwarspro.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 治疗任务。血量低时吃食物回血。
 *
 * <p>成功率65%：模拟真人有时候血量管理不当，吃了但没回够。</p>
 */
public class HealTask extends AbstractBotTask {

  private int eatAttempts = 0;
  private static final int MAX_EAT_ATTEMPTS = 3;
  private static final int EAT_COOLDOWN = 15;

  @Override
  public BotTaskType getTaskType() {
    return BotTaskType.HEAL;
  }

  @Override
  protected double getSuccessRate() {
    return 0.65;
  }

  @Override
  public boolean shouldExecute(Game game, Player bot) {
    if (bot.isDead() || !bot.isOnline() || game.isSpectator(bot)) {
      return false;
    }

    // 血量低于14时尝试吃食物
    if (bot.getHealth() < 14) {
      return hasFood(bot);
    }

    // 饥饿值低于15
    if (bot.getFoodLevel() < 15) {
      return hasFood(bot);
    }

    return false;
  }

  @Override
  public void execute(Game game, Player bot) {
    eatAttempts++;

    if (eatAttempts > MAX_EAT_ATTEMPTS) {
      complete = true;
      return;
    }

    // 寻找食物
    int foodSlot = findFoodSlot(bot);
    if (foodSlot < 0) {
      complete = true;
      return;
    }

    // 切换到食物栏
    ItemStack current = bot.getInventory().getItemInHand();
    bot.getInventory().setItemInHand(bot.getInventory().getItem(foodSlot));
    bot.getInventory().setItem(foodSlot, current);

    // 右键使用食物
    if (rollSuccess()) {
      // 模拟吃食物的延迟
      bot.setSprinting(false);
      // 给予饥饿恢复效果
      bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 6));
    } else {
      // 吃了但没效果（模拟被打断）
    }

    complete = true;
  }

  private int findFoodSlot(Player bot) {
    for (int i = 0; i < bot.getInventory().getSize(); i++) {
      ItemStack item = bot.getInventory().getItem(i);
      if (item != null && isFood(item.getType())) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void cleanup(Game game, Player bot) {
    eatAttempts = 0;
    complete = false;
  }
}
