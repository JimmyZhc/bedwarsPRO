package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.listener.ReturnLobbyListener;
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

    // 记录玩家本次 /bw join 的选择，供游戏结束菜单「再来一次」按原选择重新加入
    // （casual item / casual xp / ranked / random / 指定图名）
    if (sender instanceof Player) {
      ReturnLobbyListener.recordJoinMode(
          ((Player) sender).getUniqueId(), String.join(" ", args));
    }

    Player player = (Player) sender;
    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    Game gameOfPlayer = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (gameOfPlayer != null) {
      if (gameOfPlayer.getState() == GameState.RUNNING) {
        // 游戏结束流程中（runGameOver 已发放「再来一次」菜单、玩家还没被踢出大厅时，
        // 游戏状态仍是 RUNNING）：旧游戏已结束，允许先退出旧游戏再重新加入，
        // 否则「再来一次」会被 notwhileingame（你不能在一个已开始中的游戏）拒绝
        if (gameOfPlayer.getCycle().isEndGameRunning()) {
          gameOfPlayer.playerLeave(player, false);
        } else {
          sender.sendMessage(
              ChatWriter
                  .pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.notwhileingame")));
          return false;
        }
      } else if (gameOfPlayer.getState() == GameState.WAITING) {
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

      // /bw join casual xp|item — 指定商店模式，只从经验模式/物品模式的休闲图中随机
      // casual 必须指定商店模式：不带 xp/item 参数不允许加入
      Boolean xpFilter = null;
      if (args.get(0).equalsIgnoreCase("casual")) {
        if (args.size() > 1 && args.get(1).equalsIgnoreCase("xp")) {
          xpFilter = Boolean.TRUE;
        } else if (args.size() > 1 && args.get(1).equalsIgnoreCase("item")) {
          xpFilter = Boolean.FALSE;
        } else {
          sender.sendMessage(ChatWriter.pluginMessage(
              ChatColor.RED + "请指定商店模式：/bw join casual <xp|item>"));
          return true;
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
      // 智能选图：优先加入已有人在等待的对局（聚人开局），
      // 若所有等待对局都无人，再从全部等待对局中随机选一张
      ArrayList<Game> withPlayers = new ArrayList<>();
      for (Game g : games) {
        if (!g.getPlayers().isEmpty()) {
          withPlayers.add(g);
        }
      }
      ArrayList<Game> pool = withPlayers.isEmpty() ? games : withPlayers;
      game = pool.get(Utils.randInt(0, pool.size() - 1));
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