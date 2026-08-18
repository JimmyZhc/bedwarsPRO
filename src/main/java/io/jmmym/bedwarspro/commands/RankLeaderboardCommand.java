package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.rank.RankManager;
import io.jmmym.bedwarspro.rank.RankPlayer;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 排位赛 ELO 排行榜。
 *
 * <p>语法：/bw rankleaderboard，展示全服排位 ELO 的 Top 10（含段位）。</p>
 */
public class RankLeaderboardCommand extends BaseCommand implements ICommand {

  public RankLeaderboardCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;

    RankManager rankManager = RankManager.getInstance();
    if (rankManager == null || rankManager.getStorage() == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "排位赛系统未启用"));
      return true;
    }

    List<RankPlayer> top = rankManager.getStorage().getEloLeaderboard(10);

    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "========= "
        + ChatColor.AQUA + "排位赛 ELO 排行榜" + ChatColor.GREEN + " ========="));

    if (top.isEmpty()) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "暂无数据"));
      return true;
    }

    for (int i = 0; i < top.size(); i++) {
      RankPlayer rp = top.get(i);
      String name = (rp.getName() == null || rp.getName().isEmpty()) ? "未知玩家" : rp.getName();
      ChatColor rankColor = i == 0 ? ChatColor.GOLD : ChatColor.YELLOW;
      player.sendMessage(ChatWriter.pluginMessage(rankColor + "" + (i + 1) + ". "
          + ChatColor.WHITE + name + ChatColor.GRAY + " - " + ChatColor.AQUA + rp.getElo()
          + " ELO" + ChatColor.GRAY + " (" + ChatColor.YELLOW + rp.getTier().getCnName()
          + ChatColor.GRAY + ")"));
    }
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "rankleaderboard";
  }

  @Override
  public String getDescription() {
    return "显示排位赛 ELO 排行榜";
  }

  @Override
  public String getName() {
    return "排位排行榜";
  }

  @Override
  public String getPermission() {
    return "base";
  }

}
