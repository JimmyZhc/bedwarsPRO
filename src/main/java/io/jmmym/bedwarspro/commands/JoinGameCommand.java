package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.rank.RankManager;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import io.jmmym.bedwarspro.xp.XpManager;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JoinGameCommand extends BaseCommand {

  public JoinGameCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;
    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    Game gameOfPlayer = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (gameOfPlayer != null) {
      if (gameOfPlayer.getState() == GameState.RUNNING) {
        sender.sendMessage(
            ChatWriter
                .pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.notwhileingame")));
        return false;
      }

      if (gameOfPlayer.getState() == GameState.WAITING) {
        gameOfPlayer.playerLeave(player, false);
      }
    }

    if (game == null) {
      if (args.get(0).equalsIgnoreCase("ranked")) {
        // /bw join ranked — 进排位匹配队列：优先复用有人排位等待房，
        // 否则在配置为排位图（rank.yml ranked-games）的地图中随机新建排位房
        if (RankManager.getInstance() != null) {
          RankManager.getInstance().getRankedQueue().addPlayer(player);
        }
        return true;
      }
      if (!args.get(0).equalsIgnoreCase("casual")
          && !args.get(0).equalsIgnoreCase("random")) {
        sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
            + BedwarsPRO
            ._l(sender, "errors.gamenotfound", ImmutableMap.of("game", args.get(0).toString()))));
        return true;
      }

      // /bw join casual|random — 随机进一张「未配置为排位图」的休闲等待图
      // /bw join casual xp|item — 指定商店模式，只从经验模式/物品模式的休闲图中随机
      Boolean xpFilter = null;
      if (args.size() > 1 && args.get(0).equalsIgnoreCase("casual")) {
        if (args.get(1).equalsIgnoreCase("xp")) {
          xpFilter = Boolean.TRUE;
        } else if (args.get(1).equalsIgnoreCase("item")) {
          xpFilter = Boolean.FALSE;
        }
      }
      ArrayList<Game> games = new ArrayList<>();
      for (Game g : this.getPlugin().getGameManager().getGames()) {
        if (g.getState() == GameState.WAITING
            && !RankManager.getInstance().isRankedGame(g.getName())
            && (xpFilter == null || XpManager.isXpMode(g) == xpFilter.booleanValue())) {
          games.add(g);
        }
      }
      if (games.size() == 0) {
        sender.sendMessage(
            ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.nofreegames")));
        return true;
      }
      game = games.get(Utils.randInt(0, games.size() - 1));
    }

    if (game.playerJoins(player)) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(sender, "success.joined")));
    }
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game"};
  }

  @Override
  public String getCommand() {
    return "join";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.join.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.join.name");
  }

  @Override
  public String getPermission() {
    return "base";
  }

}