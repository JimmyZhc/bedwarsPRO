package io.jmmym.bedwarspro.bot;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.bot.tasks.BotRegistry;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Bot管理器。管理所有机器人实例、任务运行器和游戏集成。
 */
public class BotManager {

  private final BedwarsPRO plugin;
  private final Map<UUID, BotPlayer> botPlayers;
  private final Map<UUID, String> botNames;
  private final Map<Integer, BotTaskRunner> taskRunners;
  private final Random random;

  private static final String[] BOT_PREFIXES = {"Bot", "AI", "PvP", "Pro", "Warrior"};

  public BotManager(BedwarsPRO plugin) {
    this.plugin = plugin;
    this.botPlayers = new HashMap<>();
    this.botNames = new HashMap<>();
    this.taskRunners = new HashMap<>();
    this.random = new Random();
  }

  public boolean isBot(Player player) {
    return botPlayers.containsKey(player.getUniqueId());
  }

  public boolean isBot(UUID uuid) {
    return botPlayers.containsKey(uuid);
  }

  public BotPlayer getBotPlayer(Player player) {
    return botPlayers.get(player.getUniqueId());
  }

  public BotPlayer getBotPlayer(UUID uuid) {
    return botPlayers.get(uuid);
  }

  public List<BotPlayer> getAllBots() {
    return new ArrayList<>(botPlayers.values());
  }

  public List<BotPlayer> getBotsInGame(Game game) {
    List<BotPlayer> bots = new ArrayList<>();
    for (BotPlayer bot : botPlayers.values()) {
      if (bot.getCurrentGame() == game) {
        bots.add(bot);
      }
    }
    return bots;
  }

  public int getBotCountInGame(Game game) {
    return getBotsInGame(game).size();
  }

  public void registerBot(Player player, BotPlayer bot) {
    botPlayers.put(player.getUniqueId(), bot);
    botNames.put(player.getUniqueId(), bot.getBotName());
  }

  public void unregisterBot(Player player) {
    BotPlayer bot = botPlayers.remove(player.getUniqueId());
    if (bot != null) {
      bot.leaveGame();
    }
    botNames.remove(player.getUniqueId());
  }

  public void unregisterBot(UUID uuid) {
    BotPlayer bot = botPlayers.remove(uuid);
    if (bot != null) {
      bot.leaveGame();
    }
    botNames.remove(uuid);
  }

  public String generateBotName() {
    String prefix = BOT_PREFIXES[random.nextInt(BOT_PREFIXES.length)];
    int number = random.nextInt(9000) + 1000;
    String name = prefix + number;
    while (botNames.containsValue(name)) {
      number = random.nextInt(9000) + 1000;
      name = prefix + number;
    }
    return name;
  }

  public BotTaskRunner getOrCreateTaskRunner(Game game) {
    int gameId = System.identityHashCode(game);
    return taskRunners.computeIfAbsent(gameId, id -> new BotTaskRunner(plugin, game));
  }

  public BotTaskRunner getTaskRunner(Game game) {
    return taskRunners.get(System.identityHashCode(game));
  }

  public void onGameEnd(Game game) {
    BotTaskRunner runner = taskRunners.remove(System.identityHashCode(game));
    if (runner != null) {
      runner.stop();
    }

    List<BotPlayer> bots = getBotsInGame(game);
    for (BotPlayer bot : bots) {
      bot.leaveGame();
    }
  }

  /**
   * 添加bot到游戏。bot作为真实玩家通过 /bw autojoin 加入。
   */
  public void addBotToGame(Game game) {
    if (!plugin.getBotConfig().isEnabled()) {
      return;
    }

    int currentBots = getBotCountInGame(game);
    int maxBots = plugin.getBotConfig().getMaxBotsPerGame();
    if (currentBots >= maxBots) {
      return;
    }

    plugin.getServer().getConsoleSender().sendMessage(
        ChatWriter.pluginMessage(ChatColor.GREEN + "[Bot] 正在向游戏 " + game.getName() + " 添加机器人..."));

    String botName = generateBotName();

    BukkitFakePlayer.createFakePlayer(game.getRegion().getWorld(), botName, null,
        fakePlayer -> {
          if (fakePlayer == null) {
            plugin.getServer().getConsoleSender().sendMessage(
                ChatWriter.pluginMessage(ChatColor.RED + "[Bot] 创建假人失败！"));
            return;
          }

          // 注册bot状态
          BotPlayer bot = new BotPlayer(fakePlayer, fakePlayer.getName());
          registerBot(fakePlayer, bot);

          // 延迟加入游戏
          plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
              // 直接找WAITING状态的游戏加入，不走命令（命令需要真实在线玩家）
              List<Game> games = plugin.getGameManager().getGames();
              Game target = null;
              for (Game g : games) {
                if (g.getState() == io.jmmym.bedwarspro.game.GameState.WAITING
                    && g.checkGame() == io.jmmym.bedwarspro.game.GameCheckCode.OK) {
                  target = g;
                  break;
                }
              }
              if (target == null) {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatWriter.pluginMessage(ChatColor.RED + "[Bot] " + fakePlayer.getName() + " 没有可用的等待中游戏"));
                return;
              }
              boolean joined = target.playerJoins(fakePlayer);
              if (joined) {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatWriter.pluginMessage(ChatColor.GREEN + "[Bot] " + fakePlayer.getName() + " 已加入 " + target.getName()));
              } else {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatWriter.pluginMessage(ChatColor.RED + "[Bot] " + fakePlayer.getName() + " 加入游戏失败"));
              }
            } catch (Exception e) {
              plugin.getServer().getConsoleSender().sendMessage(
                  ChatWriter.pluginMessage(ChatColor.RED + "[Bot] " + fakePlayer.getName() + " 加入游戏异常: " + e.getMessage()));
              e.printStackTrace();
            }
          }, 5L);
        });
  }

  public void removeBotsFromGame(Game game) {
    List<BotPlayer> bots = getBotsInGame(game);
    for (BotPlayer bot : new ArrayList<>(bots)) {
      game.removeBot(bot);
      unregisterBot(bot.getBukkitPlayer());
      plugin.getServer().getConsoleSender().sendMessage(
          ChatWriter.pluginMessage(ChatColor.YELLOW + "[Bot] 机器人 " + bot.getBotName() + " 已从游戏移除"));
    }
  }

  public void shutdown() {
    for (BotPlayer bot : new ArrayList<>(botPlayers.values())) {
      if (bot.getCurrentGame() != null) {
        bot.getCurrentGame().removeBot(bot);
      }
      bot.leaveGame();
    }
    botPlayers.clear();
    botNames.clear();

    for (BotTaskRunner runner : taskRunners.values()) {
      runner.stop();
    }
    taskRunners.clear();
  }
}
