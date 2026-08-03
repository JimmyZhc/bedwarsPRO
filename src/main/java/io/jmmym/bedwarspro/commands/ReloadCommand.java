package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.updater.ConfigUpdater;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.io.File;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends BaseCommand {

  public ReloadCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!sender.hasPermission(this.getPermission())) {
      return false;
    }

    File config = new File(BedwarsPRO.getInstance().getDataFolder(), "config.yml");
    String command = "";

    if (args.size() > 0) {
      command = args.get(0);
    } else {
      command = "all";
    }

    if (command.equalsIgnoreCase("all")) {
      // save default config
      if (!config.exists()) {
        BedwarsPRO.getInstance().saveDefaultConfig();
      }

      BedwarsPRO.getInstance().loadConfigInUTF();

      BedwarsPRO.getInstance().getConfig().options().copyDefaults(true);
      BedwarsPRO.getInstance().getConfig().options().copyHeader(true);

      ConfigUpdater configUpdater = new ConfigUpdater();
      configUpdater.addConfigs();
      BedwarsPRO.getInstance().saveConfiguration();
      BedwarsPRO.getInstance().loadConfigInUTF();
      BedwarsPRO.getInstance().loadShop();

      if (BedwarsPRO.getInstance().isHologramsEnabled()
          && BedwarsPRO.getInstance().getHolographicInteractor() != null) {
        BedwarsPRO.getInstance().getHolographicInteractor().loadHolograms();
      }

      BedwarsPRO.getInstance().reloadLocalization();
      BedwarsPRO.getInstance().getGameManager().reloadGames();
    } else if (command.equalsIgnoreCase("shop")) {
      BedwarsPRO.getInstance().loadShop();
    } else if (command.equalsIgnoreCase("games")) {
      BedwarsPRO.getInstance().getGameManager().reloadGames();
    } else if (command.equalsIgnoreCase("holo")) {
      if (BedwarsPRO.getInstance().isHologramsEnabled()) {
        BedwarsPRO.getInstance().getHolographicInteractor().loadHolograms();
      }
    } else if (command.equalsIgnoreCase("config")) {
      // save default config
      if (!config.exists()) {
        BedwarsPRO.getInstance().saveDefaultConfig();
      }

      BedwarsPRO.getInstance().loadConfigInUTF();

      BedwarsPRO.getInstance().getConfig().options().copyDefaults(true);
      BedwarsPRO.getInstance().getConfig().options().copyHeader(true);

      ConfigUpdater configUpdater = new ConfigUpdater();
      configUpdater.addConfigs();
      BedwarsPRO.getInstance().saveConfiguration();
      BedwarsPRO.getInstance().loadConfigInUTF();
    } else if (command.equalsIgnoreCase("locale")) {
      BedwarsPRO.getInstance().reloadLocalization();
    } else {
      return false;
    }

    sender.sendMessage(
        ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(sender, "success.reloadconfig")));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "reload";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.reload.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.reload.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
