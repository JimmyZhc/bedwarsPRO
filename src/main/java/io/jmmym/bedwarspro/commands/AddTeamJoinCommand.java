package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.game.TeamJoinMetaDataValue;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AddTeamJoinCommand extends BaseCommand {

  public AddTeamJoinCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;
    String team = args.get(1);

    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
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

    Team gameTeam = game.getTeam(team);

    if (gameTeam == null) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.teamnotfound")));
      return false;
    }

    // only in lobby
    if (game.getLobby() == null || !player.getWorld().equals(game.getLobby().getWorld())) {
      player.sendMessage(
          ChatWriter
              .pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.mustbeinlobbyworld")));
      return false;
    }

    if (player.hasMetadata("bw-addteamjoin")) {
      player.removeMetadata("bw-addteamjoin", BedwarsPRO.getInstance());
    }

    player.setMetadata("bw-addteamjoin", new TeamJoinMetaDataValue(gameTeam));
    final Player runnablePlayer = player;

    new BukkitRunnable() {

      @Override
      public void run() {
        try {
          if (!runnablePlayer.hasMetadata("bw-addteamjoin")) {
            return;
          }

          runnablePlayer.removeMetadata("bw-addteamjoin", BedwarsPRO.getInstance());
        } catch (Exception ex) {
          BedwarsPRO.getInstance().getBugsnag().notify(ex);
          // just ignore
        }
      }
    }.runTaskLater(BedwarsPRO.getInstance(), 20L * 10L);

    player.sendMessage(
        ChatWriter
            .pluginMessage(
                ChatColor.GREEN + BedwarsPRO._l(player, "success.selectteamjoinentity")));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game", "team"};
  }

  @Override
  public String getCommand() {
    return "addteamjoin";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.addteamjoin.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.addteamjoin.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
