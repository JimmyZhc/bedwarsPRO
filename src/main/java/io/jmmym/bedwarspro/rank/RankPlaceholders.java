package io.jmmym.bedwarspro.rank;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * 排位赛 PlaceholderAPI 占位符扩展（identifier: {@code bwpro}）。
 *
 * <p>玩家数据占位符（如 %bwpro_rank_tier%）：<br>
 * rank_tier / rank_tier_en / rank_elo / rank_tier_elo / rank_progress /
 * rank_highest_elo / rank_games / rank_wins / rank_second / rank_third /
 * rank_fourth / rank_winrate / rank_kills / rank_beds / rank_avg_kills /
 * rank_avg_beds / rank_win_streak / rank_lose_streak / rank_placement</p>
 *
 * <p>服务器级占位符（无需玩家参数）：queue</p>
 */
public class RankPlaceholders extends PlaceholderExpansion {

  @Override
  public String getIdentifier() {
    return "bwpro";
  }

  @Override
  public String getAuthor() {
    return "JmmYm";
  }

  @Override
  public String getVersion() {
    return "1.2.2";
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public String onRequest(OfflinePlayer player, String params) {
    if (params == null) {
      return "";
    }
    String p = params.toLowerCase();
    if (p.equals("queue")) {
      return RankManager.getInstance() == null ? "0"
          : String.valueOf(RankManager.getInstance().getRankedQueue().size());
    }

    if (player == null || RankManager.getInstance() == null) {
      return "";
    }
    RankPlayer rp = RankManager.getInstance().getPlayer(player.getUniqueId(), player.getName());
    if (rp == null) {
      return "";
    }

    switch (p) {
      case "rank_tier":
        return rp.getTier().getCnName();
      case "rank_tier_en":
        return rp.getTier().getEnName();
      case "rank_elo":
        return String.valueOf(rp.getElo());
      case "rank_tier_elo":
        return rp.getTier().getCnName() + " (" + rp.getElo() + ")";
      case "rank_progress":
        return String.valueOf(tierProgress(rp));
      case "rank_highest_elo":
        return String.valueOf(rp.getHighestElo());
      case "rank_games":
        return String.valueOf(rp.getGamesPlayed());
      case "rank_wins":
        return String.valueOf(rp.getWins());
      case "rank_second":
        return String.valueOf(rp.getSecondCount());
      case "rank_third":
        return String.valueOf(rp.getThirdCount());
      case "rank_fourth":
        return String.valueOf(rp.getFourthCount());
      case "rank_winrate":
        return String.format("%.1f", rp.getWinRate());
      case "rank_kills":
        return String.valueOf(rp.getKills());
      case "rank_beds":
        return String.valueOf(rp.getBedsDestroyed());
      case "rank_avg_kills":
        return String.format("%.1f", rp.getAvgKills());
      case "rank_avg_beds":
        return String.format("%.1f", rp.getAvgBeds());
      case "rank_win_streak":
        return String.valueOf(rp.getWinStreak());
      case "rank_lose_streak":
        return String.valueOf(rp.getLoseStreak());
      case "rank_placement":
        int total = RankManager.getInstance().getPlacementMatches();
        if (rp.isPlacementDone()) {
          return RankMessages.get("placement.done");
        }
        return RankMessages.get("placement.pending",
            "current", Math.min(rp.getGamesPlayed(), total), "total", total);
      default:
        return "";
    }
  }

  /** 计算玩家 ELO 在当前段位内的进度（0-100，最高段位恒为 100）。 */
  private int tierProgress(RankPlayer rp) {
    int elo = rp.getElo();
    RankTier cur = rp.getTier();
    RankTier next = null;
    boolean found = false;
    for (RankTier tier : RankTier.values()) {
      if (tier == cur) {
        found = true;
        continue;
      }
      if (found) {
        next = tier;
        break;
      }
    }
    if (next == null) {
      return 100;
    }
    int base = cur.getRequiredElo();
    int target = next.getRequiredElo();
    int range = target - base;
    if (range <= 0) {
      return 100;
    }
    int progress = (int) Math.round((elo - base) * 100.0 / range);
    return Math.max(0, Math.min(100, progress));
  }
}
