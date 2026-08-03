package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetAutobalanceCommand extends BaseCommand implements ICommand {

  public SetAutobalanceCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!sender.hasPermission("bw." + this.getPermission())) {
      return false;
    }

    Player player = (Player) sender;

    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    String value = args.get(1).toString().trim();

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

    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")
        && !value.equalsIgnoreCase("off") && !value.equalsIgnoreCase("on")
        && !value.equalsIgnoreCase("1") && !value.equalsIgnoreCase("0")) {
      player
          .sendMessage(
              ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                  ._l(player, "errors.wrongvalueonoff")));
      return true;
    }

    boolean autobalance = false;
    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
        || value.equalsIgnoreCase("1")) {
      autobalance = true;
    }

    game.setAutobalance(autobalance);

    if (autobalance) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO
              ._l(player, "success.autobalanceseton")));
    } else {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO
              ._l(player, "success.autobalancesetoff")));
    }
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game", "value"};
  }

  @Override
  public String getCommand() {
    return "setautobalance";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.setautobalance.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.setautobalance.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
