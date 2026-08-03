package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.TeamColor;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class AddTeamCommand extends BaseCommand {

  public AddTeamCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!sender.hasPermission("bw." + this.getPermission())) {
      return false;
    }

    if (args.size() < 4) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "参数不足！需要4个参数：游戏名 队伍名 颜色 最大玩家数"));
      sender.sendMessage(ChatWriter.pluginMessage("用法: /bw addteam <游戏名> <队伍名> <颜色> <最大玩家数>"));
      return false;
    }

    String gameName = args.get(0);
    String teamName = args.get(1);
    String colorStr = args.get(2);
    String maxPlayersStr = args.get(3);

    Game game = this.getPlugin().getGameManager().getGame(gameName);
    TeamColor tColor = null;

    try {
      tColor = TeamColor.valueOf(colorStr.toUpperCase());
    } catch (Exception e) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + "无效的颜色: '" + colorStr + "'。有效的颜色: RED, BLUE, GREEN, YELLOW, PINK, AQUA, WHITE, GRAY"));
      return false;
    }

    if (game == null) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + BedwarsPRO
          ._l(sender, "errors.gamenotfound", ImmutableMap.of("game", gameName))));
      return false;
    }

    if (game.getState() != GameState.STOPPED) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
              ._l(sender, "errors.notwhilegamerunning")));
      return false;
    }

    int playerMax;
    try {
      playerMax = Integer.parseInt(maxPlayersStr);
    } catch (NumberFormatException e) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "无效的最大玩家数: '" + maxPlayersStr + "'。请输入一个数字（1-24）"));
      return false;
    }

    if (playerMax < 1 || playerMax > 24) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.playeramount")));
      return false;
    }

    if (teamName.length() < 3 || teamName.length() > 20) {
      sender
          .sendMessage(
              ChatWriter
                  .pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.teamnamelength")));
      return false;
    }

    if (game.getTeam(teamName) != null) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(sender, "errors.teamnameinuse")));
      return false;
    }

    game.addTeam(teamName, tColor, playerMax);
    sender.sendMessage(ChatWriter.pluginMessage(
        ChatColor.GREEN + BedwarsPRO
            ._l(sender, "success.teamadded", ImmutableMap.of("team", teamName))));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game", "name", "color", "maxplayers"};
  }

  @Override
  public String getCommand() {
    return "addteam";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.addteam.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.addteam.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
