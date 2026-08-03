package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import java.util.ArrayList;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LeaveGameCommand extends BaseCommand {

  public LeaveGameCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return true;
    }

    game.playerLeave(player, false);
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "leave";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.leave.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.leave.name");
  }

  @Override
  public String getPermission() {
    return "base";
  }

}
