package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.rank.RankManager;
import io.jmmym.bedwarspro.rank.RankMessages;
import io.jmmym.bedwarspro.rank.RankPlayer;
import io.jmmym.bedwarspro.statistics.PlayerStatistic;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.UUIDFetcher;
import java.util.ArrayList;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand extends BaseCommand implements ICommand {

  public StatsCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;

    if (!player.hasPermission("bw.otherstats") && args.size() > 0) {
      args.clear();
    }

    player.sendMessage(ChatWriter.pluginMessage(
        ChatColor.GREEN + "----------- " + BedwarsPRO._l(player, "stats.header") + " -----------"));

    if (args.size() == 1) {
      String playerStats = args.get(0).toString();
      OfflinePlayer offPlayer = BedwarsPRO.getInstance().getServer().getPlayerExact(playerStats);

      if (offPlayer != null) {
        PlayerStatistic statistic =
            BedwarsPRO.getInstance().getPlayerStatisticManager().getStatistic(offPlayer);
        if (statistic == null) {
          player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
              + BedwarsPRO
              ._l(player, "stats.statsnotfound", ImmutableMap.of("player", playerStats))));
          return true;
        }

        this.sendStats(player, statistic);
        return true;
      }

      UUID offUUID = null;
      try {
        offUUID = UUIDFetcher.getUUIDOf(playerStats);
        if (offUUID == null) {
          player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
              + BedwarsPRO
              ._l(player, "stats.statsnotfound", ImmutableMap.of("player", playerStats))));
          return true;
        }
      } catch (Exception e) {
        BedwarsPRO.getInstance().getBugsnag().notify(e);
        e.printStackTrace();
      }

      offPlayer = BedwarsPRO.getInstance().getServer().getOfflinePlayer(offUUID);
      if (offPlayer == null) {
        player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
            + BedwarsPRO
            ._l(player, "stats.statsnotfound", ImmutableMap.of("player", playerStats))));
        return true;
      }

      PlayerStatistic statistic =
          BedwarsPRO.getInstance().getPlayerStatisticManager().getStatistic(offPlayer);
      if (statistic == null) {
        player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
            + BedwarsPRO
            ._l(player, "stats.statsnotfound", ImmutableMap.of("player", offPlayer.getName()))));
        return true;
      }

      this.sendStats(player, statistic);
      return true;
    } else if (args.size() == 0) {
      PlayerStatistic statistic =
          BedwarsPRO.getInstance().getPlayerStatisticManager().getStatistic(player);
      if (statistic == null) {
        player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
            + BedwarsPRO
            ._l(player, "stats.statsnotfound", ImmutableMap.of("player", player.getName()))));
        return true;
      }

      this.sendStats(player, statistic);
      return true;
    }

    return false;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "stats";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.stats.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.stats.name");
  }

  @Override
  public String getPermission() {
    return "base";
  }

  private void sendStats(Player player, PlayerStatistic statistic) {
    for (String line : BedwarsPRO.getInstance().getPlayerStatisticManager()
        .createStatisticLines(statistic, false, ChatColor.GRAY, ChatColor.YELLOW)) {
      player.sendMessage(line);
    }

    // 排位战绩（段位 / ELO / 场次 / 名次统计 / 连胜连败）
    if (RankManager.getInstance() != null && statistic.getId() != null) {
      RankPlayer rp = RankManager.getInstance().getStorage().getLoaded(statistic.getId());
      if (rp == null) {
        rp = RankManager.getInstance().getStorage().load(statistic.getId(), statistic.getName());
      }
      player.sendMessage(ChatWriter.pluginMessage(RankMessages.get("stats.header")));
      if (rp.getGamesPlayed() <= 0) {
        player.sendMessage(ChatWriter.pluginMessage(RankMessages.get("stats.no-data")));
        return;
      }
      player.sendMessage(ChatWriter.pluginMessage(
          RankMessages.get("stats.tier", "tier", rp.getTier().getCnName())));
      player.sendMessage(ChatWriter.pluginMessage(
          RankMessages.get("stats.elo", "elo", rp.getElo(), "highest", rp.getHighestElo())));
      player.sendMessage(ChatWriter.pluginMessage(RankMessages.get("stats.games",
          "games", rp.getGamesPlayed(), "wins", rp.getWins(), "rate", rp.getWinRate())));
      player.sendMessage(ChatWriter.pluginMessage(RankMessages.get("stats.placement",
          "first", rp.getWins(), "second", rp.getSecondCount(),
          "third", rp.getThirdCount(), "fourth", rp.getFourthCount())));
      player.sendMessage(ChatWriter.pluginMessage(RankMessages.get("stats.streak",
          "win", rp.getWinStreak(), "lose", rp.getLoseStreak())));
      player.sendMessage(ChatWriter.pluginMessage(RankMessages.get("stats.avg",
          "kills", rp.getAvgKills(), "beds", rp.getAvgBeds())));
      // 打开段位进度界面（奶桶 = 每个 ELO 段位，空桶 = 已达成）
      io.jmmym.bedwarspro.rank.RankTierGUI.open(player, rp);
    }
  }

}
