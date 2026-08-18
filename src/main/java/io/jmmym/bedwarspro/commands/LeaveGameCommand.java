package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.rank.RankManager;
import java.util.ArrayList;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LeaveGameCommand extends BaseCommand {

  public LeaveGameCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;

    // 玩家加入排位匹配队列后：/bw leave 退出匹配队列。
    // 提示由 Game#playerLeave 统一输出（你离开了游戏! + 你已退出排位匹配队列!），
    // 与右键等待大厅粘液球返回大厅的行为保持一致，避免重复提示。
    if (RankManager.getInstance() != null
        && RankManager.getInstance().getRankedQueue().isQueued(player.getUniqueId())) {
      RankManager.getInstance().getRankedQueue().removePlayer(player);
      return true;
    }

    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return true;
    }

    game.playerLeave(player, false);
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "leave";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.leave.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.leave.name");
  }

  @Override
  public String getPermission() {
    return "base";
  }

}
