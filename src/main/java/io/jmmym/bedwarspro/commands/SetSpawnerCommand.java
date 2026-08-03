package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.ResourceSpawner;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SetSpawnerCommand extends BaseCommand {

  public SetSpawnerCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;
    ArrayList<String> arguments = new ArrayList<String>(Arrays.asList(this.getResources()));
    String material = args.get(1).toString().toLowerCase();
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

    if (!arguments.contains(material)) {
      player
          .sendMessage(
              ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                  ._l(player, "errors.spawnerargument")));
      return false;
    }

    Location location = player.getLocation();
    ResourceSpawner spawner = new ResourceSpawner(game, material, location);
    game.addResourceSpawner(spawner);
    player.sendMessage(
        ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(player, "success.spawnerset",
            ImmutableMap.of("name", material + ChatColor.GREEN))));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game", "ressource"};
  }

  @Override
  public String getCommand() {
    return "setspawner";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.setspawner.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.setspawner.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

  private String[] getResources() {
    ConfigurationSection section =
        BedwarsPRO.getInstance().getConfig().getConfigurationSection("resource");
    if (section == null) {
      return new String[]{};
    }

    List<String> resources = new ArrayList<String>();
    for (String key : section.getKeys(false)) {
      resources.add(key.toLowerCase());
    }

    return resources.toArray(new String[resources.size()]);
  }

}
