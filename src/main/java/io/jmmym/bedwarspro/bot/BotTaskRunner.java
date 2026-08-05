package io.jmmym.bedwarspro.bot;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Bot任务运行器。每tick驱动所有Bot的AI决策。
 *
 * <p>注册到 {@link Game#addRunningTask} 实现游戏结束自动清理。</p>
 */
public class BotTaskRunner extends BukkitRunnable {

  private final BedwarsPRO plugin;
  private final Game game;
  private final List<UUID> activeBots;
  private boolean stopped;

  public BotTaskRunner(BedwarsPRO plugin, Game game) {
    this.plugin = plugin;
    this.game = game;
    this.activeBots = new ArrayList<>();
    this.stopped = false;
  }

  @Override
  public void run() {
    if (stopped || game.getState() != GameState.RUNNING) {
      return;
    }

    BotManager botManager = plugin.getBotManager();

    for (UUID botUuid : new ArrayList<>(activeBots)) {
      BotPlayer bot = botManager.getBotPlayer(botUuid);
      if (bot == null || !bot.isOnline() || !bot.isAlive()) {
        continue;
      }

      Player player = bot.getBukkitPlayer();
      if (player == null || !player.isOnline() || player.isDead()) {
        continue;
      }

      bot.tickCooldowns();

      List<BotTask> tasks = new ArrayList<>(bot.getTasks());
      tasks.sort(Comparator.comparingInt(BotTask::getPriority));

      for (BotTask task : tasks) {
        if (task.shouldExecute(game, player)) {
          task.execute(game, player);

          if (task.isComplete(game, player)) {
            task.cleanup(game, player);
          }
          break;
        }
      }
    }
  }

  private boolean started = false;

  public org.bukkit.scheduler.BukkitTask start() {
    if (started) {
      return null;
    }
    started = true;
    return this.runTaskTimer(plugin, 1L, 1L);
  }

  public void stop() {
    this.stopped = true;
    try {
      this.cancel();
    } catch (IllegalStateException ignored) {
    }
  }

  public void addBot(Player bot) {
    if (!activeBots.contains(bot.getUniqueId())) {
      activeBots.add(bot.getUniqueId());
    }
  }

  public void removeBot(Player bot) {
    activeBots.remove(bot.getUniqueId());
  }

  public boolean containsBot(Player bot) {
    return activeBots.contains(bot.getUniqueId());
  }

  public List<UUID> getActiveBots() {
    return new ArrayList<>(activeBots);
  }
}
