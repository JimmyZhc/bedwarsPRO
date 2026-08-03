package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class RemoveHoloCommand extends BaseCommand implements ICommand {

  public RemoveHoloCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    final Player player = (Player) sender;
    player.setMetadata("bw-remove-holo", new FixedMetadataValue(BedwarsPRO.getInstance(), true));
    if (BedwarsPRO.getInstance().getHolographicInteractor().getType()
        .equalsIgnoreCase("HolographicDisplays")) {
      player.sendMessage(
          ChatWriter
              .pluginMessage(
                  ChatColor.GREEN + BedwarsPRO._l(player, "commands.removeholo.explain")));

    } else if (BedwarsPRO.getInstance().getHolographicInteractor().getType()
        .equalsIgnoreCase("HologramAPI")) {

      for (Location location : BedwarsPRO.getInstance().getHolographicInteractor()
          .getHologramLocations()) {
        if (player.getEyeLocation().getBlockX() == location.getBlockX()
            && player.getEyeLocation().getBlockY() == location.getBlockY()
            && player.getEyeLocation().getBlockZ() == location.getBlockZ()) {
          BedwarsPRO.getInstance().getHolographicInteractor().onHologramTouch(player, location);
        }
      }
      BedwarsPRO.getInstance().getServer().getScheduler().runTaskLater(BedwarsPRO.getInstance(),
          new Runnable() {

            @Override
            public void run() {
              if (player.hasMetadata("bw-remove-holo")) {
                player.removeMetadata("bw-remove-holo", BedwarsPRO.getInstance());
              }
            }

          }, 10L * 20L);

    }
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "removeholo";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.removeholo.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.removeholo.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
