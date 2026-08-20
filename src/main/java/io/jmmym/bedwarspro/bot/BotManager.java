package io.jmmym.bedwarspro.bot;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.bot.tasks.BotRegistry;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Bot管理器。管理所有机器人实例、任务运行器和游戏集成。
 */
public class BotManager {

  private final BedwarsPRO plugin;
  // 用并发 Map：CorpsePacketFilter 在 Netty 线程（非主线程）读取 bot 名单，
  // 普通 HashMap 在主线程增删时并发读会抛 ConcurrentModificationException
  private final Map<UUID, BotPlayer> botPlayers;
  private final Map<UUID, String> botNames;
  private final Map<Integer, BotTaskRunner> taskRunners;
  private final Random random;

  private static final String[] BOT_PREFIXES = {"Bot", "AI", "PvP", "Pro", "Warrior"};

  public BotManager(BedwarsPRO plugin) {
    this.plugin = plugin;
    this.botPlayers = new ConcurrentHashMap<>();
    this.botNames = new ConcurrentHashMap<>();
    this.taskRunners = new ConcurrentHashMap<>();
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
    // 手动加入的假人不会自动断开，需要手动移除
    BukkitFakePlayer.removeFakePlayer(player);
  }

  public void unregisterBot(UUID uuid) {
    BotPlayer bot = botPlayers.remove(uuid);
    if (bot != null) {
      bot.leaveGame();
    }
    botNames.remove(uuid);
    Player p = plugin.getServer().getPlayer(uuid);
    if (p != null) {
      BukkitFakePlayer.removeFakePlayer(p);
    }
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

    // 完整移除该对局的所有 bot：床战 + 服务器玩家列表 + 世界实体 + Tab 条目
    List<BotPlayer> bots = new ArrayList<>(getBotsInGame(game));
    for (BotPlayer bot : bots) {
      try {
        unregisterBot(bot.getBukkitPlayer());
      } catch (Exception ignored) {
      }
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
              // 优先加入调用方指定的游戏（/bwbot add <游戏>），
              // 否则从所有等待游戏中随机选一个（避免 bot 进错图）
              List<Game> games = plugin.getGameManager().getGames();
              Game target = null;
              if (game != null && game.getState() == io.jmmym.bedwarspro.game.GameState.WAITING
                  && game.checkGame() == io.jmmym.bedwarspro.game.GameCheckCode.OK) {
                target = game;
              }
              if (target == null) {
                List<Game> candidates = new ArrayList<>();
                for (Game g : games) {
                  if (g.getState() == io.jmmym.bedwarspro.game.GameState.WAITING
                      && g.checkGame() == io.jmmym.bedwarspro.game.GameCheckCode.OK) {
                    candidates.add(g);
                  }
                }
                if (!candidates.isEmpty()) {
                  target = candidates.get(io.jmmym.bedwarspro.utils.Utils.randInt(0, candidates.size() - 1));
                }
              }
              if (target == null) {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatWriter.pluginMessage(ChatColor.RED + "[Bot] " + fakePlayer.getName() + " 没有可用的等待中游戏"));
                // 清理：未加入游戏的假人不能残留
                unregisterBot(fakePlayer);
                return;
              }
              boolean joined = target.playerJoins(fakePlayer);
              if (joined) {
                // 绑定 bot 与游戏（否则 getBotsInGame/getBotCountInGame 永远为空，
                // maxBots 上限失效、游戏结束无法清理 bot、/bwbot list/remove 全失效）
                bot.joinGame(target);
                target.addBot(bot);
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatWriter.pluginMessage(ChatColor.GREEN + "[Bot] " + fakePlayer.getName() + " 已加入 " + target.getName()));
              } else {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatWriter.pluginMessage(ChatColor.RED + "[Bot] " + fakePlayer.getName() + " 加入游戏失败"));
                // 清理：加入失败则移除假人，等待下次自动补位
                unregisterBot(fakePlayer);
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
      try {
        BukkitFakePlayer.removeFakePlayer(bot.getBukkitPlayer());
      } catch (Exception ignored) {
      }
    }
    botPlayers.clear();
    botNames.clear();

    for (BotTaskRunner runner : taskRunners.values()) {
      runner.stop();
    }
    taskRunners.clear();
  }
}
