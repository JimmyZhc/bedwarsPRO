package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class SaveGameCommand extends BaseCommand implements ICommand {

  public SaveGameCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!sender.hasPermission("bw." + this.getPermission())) {
      return false;
    }

    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    if (game == null) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + BedwarsPRO
          ._l(sender, "errors.gamenotfound", ImmutableMap.of("game", args.get(0).toString()))));
      return false;
    }

    if (game.getState() == GameState.RUNNING) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
              ._l(sender, "errors.notwhilegamerunning")));
      return false;
    }

    if (!game.saveGame(sender, true)) {
      return false;
    }

    sender
        .sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO
            ._l(sender, "success.saved")));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game"};
  }

  @Override
  public String getCommand() {
    return "save";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.save.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.save.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
