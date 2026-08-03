package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand extends BaseCommand implements ICommand {

  public SetSpawnCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;

    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + BedwarsPRO
          ._l(player, "errors.gamenotfound", ImmutableMap.of("game", args.get(0).toString()))));
      return false;
    }

    if (game.getState() == GameState.RUNNING) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
              ._l(sender, "errors.notwhilegamerunning")));
      return false;
    }

    Team team = game.getTeam(args.get(1));
    if (team == null) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.teamnotfound")));
      return false;
    }

    team.setSpawnLocation(player.getLocation());
    player
        .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO
            ._l(player, "success.spawnset",
                ImmutableMap
                    .of("team", team.getChatColor() + team.getDisplayName() + ChatColor.GREEN))));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game", "team"};
  }

  @Override
  public String getCommand() {
    return "setspawn";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.setspawn.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.setspawn.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
