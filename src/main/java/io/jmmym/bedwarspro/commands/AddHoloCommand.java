package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddHoloCommand extends BaseCommand implements ICommand {

  public AddHoloCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    if (!BedwarsPRO.getInstance().isHologramsEnabled()) {
      String missingholodependency = BedwarsPRO.getInstance().getMissingHoloDependency();

      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + BedwarsPRO._l(sender, "errors.holodependencynotfound",
          ImmutableMap.of("dependency", missingholodependency))));
      return true;
    }

    Player player = (Player) sender;
    BedwarsPRO.getInstance().getHolographicInteractor()
        .addHologramLocation(player.getEyeLocation());
    BedwarsPRO.getInstance().getHolographicInteractor().updateHolograms();
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "addholo";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.addholo.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.addholo.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
