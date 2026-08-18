package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.statistics.PlayerStatistic;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 起床战争统计排行榜。
 *
 * <p>语法：/bw leaderboard [kills|deaths|wins|loses|beds|score|kd]，
 * 展示全服对应数据的 Top 10（默认按击杀排序）。</p>
 */
public class LeaderboardCommand extends BaseCommand implements ICommand {

  public LeaderboardCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;

    String field = "kills";
    if (args.size() >= 1) {
      field = args.get(0);
    }
    String normalized = normalize(field);
    if (normalized == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + "无效的排行榜类型，可用：kills / deaths / wins / loses / beds / score / kd"));
      return true;
    }

    List<PlayerStatistic> top =
        BedwarsPRO.getInstance().getPlayerStatisticManager().getStatisticsLeaderboard(normalized, 10);

    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "========= "
        + ChatColor.AQUA + "起床战争排行榜 [" + label(normalized) + "]"
        + ChatColor.GREEN + " ========="));

    if (top.isEmpty()) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "暂无数据"));
      return true;
    }

    for (int i = 0; i < top.size(); i++) {
      PlayerStatistic ps = top.get(i);
      String name = (ps.getName() == null || ps.getName().isEmpty()) ? "未知玩家" : ps.getName();
      String value = formatValue(ps, normalized);
      ChatColor rankColor = i == 0 ? ChatColor.GOLD : ChatColor.YELLOW;
      player.sendMessage(ChatWriter.pluginMessage(rankColor + "" + (i + 1) + ". "
          + ChatColor.WHITE + name + ChatColor.GRAY + " - " + ChatColor.AQUA + value));
    }
    return true;
  }

  /** 规范化字段并校验合法性（无效返回 null）。 */
  private String normalize(String field) {
    if (field == null) {
      return "kills";
    }
    switch (field.toLowerCase().replace("-", "")) {
      case "kill":
      case "kills":
        return "kills";
      case "death":
      case "deaths":
        return "deaths";
      case "win":
      case "wins":
        return "wins";
      case "lose":
      case "loss":
      case "loses":
      case "losses":
        return "loses";
      case "bed":
      case "beds":
      case "destroyedbeds":
        return "destroyedBeds";
      case "score":
      case "points":
        return "score";
      case "kd":
      case "k/d":
      case "killdeath":
        return "kd";
      default:
        return null;
    }
  }

  /** 字段中文名（用于标题显示）。 */
  private String label(String normalized) {
    switch (normalized) {
      case "deaths":
        return "死亡";
      case "wins":
        return "胜利";
      case "loses":
        return "失败";
      case "destroyedBeds":
        return "拆床";
      case "score":
        return "积分";
      case "kd":
        return "KD";
      default:
        return "击杀";
    }
  }

  /** 按排序字段格式化数值。 */
  private String formatValue(PlayerStatistic ps, String normalized) {
    switch (normalized) {
      case "deaths":
        return String.valueOf(ps.getDeaths());
      case "wins":
        return String.valueOf(ps.getWins());
      case "loses":
        return String.valueOf(ps.getLoses());
      case "destroyedBeds":
        return String.valueOf(ps.getDestroyedBeds());
      case "score":
        return String.valueOf(ps.getScore());
      case "kd":
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.##");
        return df.format(ps.getKD());
      default:
        return String.valueOf(ps.getKills());
    }
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "leaderboard";
  }

  @Override
  public String getDescription() {
    return "显示起床战争统计排行榜";
  }

  @Override
  public String getName() {
    return "排行榜";
  }

  @Override
  public String getPermission() {
    return "base";
  }

}
