package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

public class SetGameBlockCommand extends BaseCommand implements ICommand {

  public SetGameBlockCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!sender.hasPermission("bw." + this.getPermission())) {
      return false;
    }

    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    String material = args.get(1).toString();

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

    Material targetMaterial = Utils.parseMaterial(material);
    if (targetMaterial == null && !"DEFAULT".equals(material)) {
      sender
          .sendMessage(
              ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                  ._l(sender, "errors.novalidmaterial")));
      return true;
    }

    if ("DEFAULT".equalsIgnoreCase(material)) {
      game.setTargetMaterial(null);
    } else {
      game.setTargetMaterial(targetMaterial);
    }

    sender.sendMessage(
        ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(sender, "success.materialset")));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game", "blocktype"};
  }

  @Override
  public String getCommand() {
    return "setgameblock";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.setgameblock.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.setgameblock.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
