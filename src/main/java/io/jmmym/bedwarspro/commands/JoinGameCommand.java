package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
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
      if (!args.get(0).equalsIgnoreCase("random")) {
        sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
            + BedwarsPRO
            ._l(sender, "errors.gamenotfound", ImmutableMap.of("game", args.get(0).toString()))));
        return true;
      }

      ArrayList<Game> games = new ArrayList<>();
      for (Game g : this.getPlugin().getGameManager().getGames()) {
        if (g.getState() == GameState.WAITING) {
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
