package io.jmmym.bedwarspro.utils;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.commands.BaseCommand;
import io.jmmym.bedwarspro.events.BedwarsCommandExecutedEvent;
import io.jmmym.bedwarspro.events.BedwarsExecuteCommandEvent;
import java.util.ArrayList;
import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BedwarsCommandExecutor implements CommandExecutor {

  private BedwarsPRO plugin = null;

  public BedwarsCommandExecutor(BedwarsPRO plugin) {
    super();

    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
    // Handle /bwbot command
    if (cmd.getName().equals("bwbot")) {
      String[] newArgs = new String[args.length + 1];
      newArgs[0] = "bot";
      System.arraycopy(args, 0, newArgs, 1, args.length);
      args = newArgs;
    } else if (!cmd.getName().equals("bw")) {
      // Handle Bukkit treating "bw addteam" as a separate command
      if (cmd.getName().startsWith("bw ")) {
        String subCmd = cmd.getName().substring(3);
        if (args.length > 0) {
          String[] newArgs = new String[args.length + 1];
          newArgs[0] = subCmd;
          System.arraycopy(args, 0, newArgs, 1, args.length);
          args = newArgs;
        } else {
          args = new String[]{subCmd};
        }
      } else if (isKnownSubCommand(cmd.getName())) {
        String subCmd = cmd.getName();
        if (args.length > 0) {
          String[] newArgs = new String[args.length + 1];
          newArgs[0] = subCmd;
          System.arraycopy(args, 0, newArgs, 1, args.length);
          args = newArgs;
        } else {
          args = new String[]{subCmd};
        }
      } else {
        return false;
      }
    }

    if (args.length < 1) {
      return false;
    }

    String command = args[0];
    ArrayList<String> arguments = new ArrayList<String>(Arrays.asList(args));
    arguments.remove(0);

    for (BaseCommand bCommand : this.plugin.getCommands()) {
      if (bCommand.getCommand().equalsIgnoreCase(command)) {
        if (bCommand.getArguments().length > arguments.size()) {
          sender.sendMessage(
              ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                  ._l(sender, "errors.argumentslength")));
          return false;
        }

        BedwarsExecuteCommandEvent commandEvent =
            new BedwarsExecuteCommandEvent(sender, bCommand, arguments);
        BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(commandEvent);

        if (commandEvent.isCancelled()) {
          return true;
        }

        boolean result = bCommand.execute(sender, arguments);

        BedwarsCommandExecutedEvent executedEvent =
            new BedwarsCommandExecutedEvent(sender, bCommand, arguments, result);
        BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(executedEvent);

        return result;
      }
    }

    return false;
  }

  private boolean isKnownSubCommand(String name) {
    for (BaseCommand bCommand : this.plugin.getCommands()) {
      if (bCommand.getCommand().equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

}
