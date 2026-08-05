package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.bot.BotConfig;
import io.jmmym.bedwarspro.bot.BotManager;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /bwbot 命令 - Bot管理命令。
 */
public class BotCommand extends BaseCommand {

  public BotCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "此命令只能由玩家执行！"));
      return false;
    }

    Player player = (Player) sender;
    BotManager botManager = BedwarsPRO.getInstance().getBotManager();
    BotConfig botConfig = BedwarsPRO.getInstance().getBotConfig();

    if (args.size() < 1) {
      sendHelp(sender);
      return true;
    }

    String subCommand = args.get(0).toLowerCase();

    switch (subCommand) {
      case "add":
        return handleAdd(player, args, botManager, botConfig);
      case "remove":
        return handleRemove(player, args, botManager);
      case "list":
        return handleList(player, botManager);
      case "reload":
        return handleReload(player, botConfig);
      case "info":
        return handleInfo(player, botManager);
      default:
        sendHelp(sender);
        return true;
    }
  }

  private boolean handleAdd(Player player, ArrayList<String> args, BotManager botManager, BotConfig botConfig) {
    if (!botConfig.isEnabled()) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "Bot系统未启用！请在 bot-config.yml 中启用。"));
      return true;
    }

    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "你当前不在任何游戏中！"));
      return true;
    }

    int count = 1;
    if (args.size() > 1) {
      try {
        count = Integer.parseInt(args.get(1));
        if (count < 1 || count > botConfig.getMaxBotsPerGame()) {
          player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "数量必须在1-" + botConfig.getMaxBotsPerGame() + "之间！"));
          return true;
        }
      } catch (NumberFormatException e) {
        player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "无效的数量！"));
        return true;
      }
    }

    int currentBots = botManager.getBotCountInGame(game);
    int maxBots = botConfig.getMaxBotsPerGame();

    if (currentBots >= maxBots) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "游戏中Bot数量已达上限(" + maxBots + ")！"));
      return true;
    }

    for (int i = 0; i < count; i++) {
      if (currentBots + i >= maxBots) {
        break;
      }
      botManager.addBotToGame(game);
    }

    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "已向游戏 " + game.getName() + " 添加 " + count + " 个机器人。"));
    return true;
  }

  private boolean handleRemove(Player player, ArrayList<String> args, BotManager botManager) {
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "你当前不在任何游戏中！"));
      return true;
    }

    if (args.size() > 1) {
      String botName = args.get(1);
      boolean found = false;
      for (io.jmmym.bedwarspro.bot.BotPlayer bot : botManager.getBotsInGame(game)) {
        if (bot.getBotName().equalsIgnoreCase(botName)) {
          game.removeBot(bot);
          botManager.unregisterBot(bot.getBukkitPlayer());
          player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "已移除机器人 " + botName));
          found = true;
          break;
        }
      }
      if (!found) {
        player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "未找到名为 " + botName + " 的机器人！"));
      }
    } else {
      botManager.removeBotsFromGame(game);
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "已移除游戏中所有机器人。"));
    }
    return true;
  }

  private boolean handleList(Player player, BotManager botManager) {
    player.sendMessage(ChatWriter.pluginMessage(ChatColor.YELLOW + "===== Bot列表 ====="));
    boolean anyBot = false;
    for (Game game : BedwarsPRO.getInstance().getGameManager().getGames()) {
      int botCount = botManager.getBotCountInGame(game);
      if (botCount > 0) {
        anyBot = true;
        player.sendMessage(ChatColor.GREEN + game.getName() + ": " + botCount + " 个机器人");
        for (io.jmmym.bedwarspro.bot.BotPlayer bot : botManager.getBotsInGame(game)) {
          player.sendMessage(ChatColor.GRAY + "  - " + bot.getBotName() + " [" + bot.getState() + "]");
        }
      }
    }
    if (!anyBot) {
      player.sendMessage(ChatColor.GRAY + "当前没有任何机器人。");
    }
    return true;
  }

  private boolean handleReload(Player player, BotConfig botConfig) {
    botConfig.reload();
    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "Bot配置已重载。"));
    return true;
  }

  private boolean handleInfo(Player player, BotManager botManager) {
    BotConfig config = BedwarsPRO.getInstance().getBotConfig();
    player.sendMessage(ChatWriter.pluginMessage(ChatColor.YELLOW + "===== Bot系统信息 ====="));
    player.sendMessage(ChatColor.GREEN + "启用状态: " + (config.isEnabled() ? "是" : "否"));
    player.sendMessage(ChatColor.GREEN + "每游戏最大Bot: " + config.getMaxBotsPerGame());
    player.sendMessage(ChatColor.GREEN + "AI难度: " + config.getAiDifficulty());
    player.sendMessage(ChatColor.GREEN + "反应时间: " + config.getReactionTime() + "ms");
    player.sendMessage(ChatColor.GREEN + "攻击准确度: " + config.getAccuracy());
    player.sendMessage(ChatColor.GREEN + "当前Bot总数: " + botManager.getAllBots().size());
    return true;
  }

  private void sendHelp(CommandSender sender) {
    sender.sendMessage(ChatWriter.pluginMessage(ChatColor.YELLOW + "===== Bot命令帮助 ====="));
    sender.sendMessage(ChatColor.GREEN + "/bwbot add [数量] " + ChatColor.GRAY + "- 向当前游戏添加Bot");
    sender.sendMessage(ChatColor.GREEN + "/bwbot remove [名称] " + ChatColor.GRAY + "- 移除Bot");
    sender.sendMessage(ChatColor.GREEN + "/bwbot list " + ChatColor.GRAY + "- 查看所有Bot");
    sender.sendMessage(ChatColor.GREEN + "/bwbot info " + ChatColor.GRAY + "- 查看Bot系统信息");
    sender.sendMessage(ChatColor.GREEN + "/bwbot reload " + ChatColor.GRAY + "- 重载Bot配置");
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "bot";
  }

  @Override
  public String getDescription() {
    return "Bot管理命令";
  }

  @Override
  public String getName() {
    return "Bot";
  }

  @Override
  public String getPermission() {
    return "bot.admin";
  }
}
