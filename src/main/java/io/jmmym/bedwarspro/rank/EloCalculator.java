package io.jmmym.bedwarspro.rank;

/**
 * ELO 计算器。
 *
 * <p>K 因子（波动系数）规则（策划案 3.1.2）：
 * <ul>
 *   <li>新玩家（前 10 场）：40 —— 快速定位真实段位</li>
 *   <li>常规玩家（ELO &lt; 500）：25 —— 适当波动</li>
 *   <li>常规玩家（ELO ≥ 500）：20 —— 稳定段位</li>
 *   <li>高分段玩家（ELO &gt; 1500）：16 —— 防止积分通胀</li>
 * </ul>
 * 加减分以段位表（{@link RankTier}）为基础分值，乘以 K/20 缩放：K=20 时按段位表原值结算，
 * 新玩家（K=40）波动翻倍，高分段（K=16）波动缩小。</p>
 */
public final class EloCalculator {

  private EloCalculator() {
  }

  /** 获取玩家当前适用的 K 因子。 */
  public static int getKFactor(RankPlayer player) {
    if (player == null) {
      return 20;
    }
    int placementGames = RankManager.getInstance().getPlacementGames();
    if (player.getGamesPlayed() < placementGames) {
      return RankManager.getInstance().getKFactorNew();
    }
    int elo = player.getElo();
    if (elo < RankManager.getInstance().getMatchThreshold()) {
      return RankManager.getInstance().getKFactorLow();
    }
    if (elo > RankManager.getInstance().getKFactorHighEloThreshold()) {
      return RankManager.getInstance().getKFactorHigh();
    }
    return RankManager.getInstance().getKFactorNormal();
  }

  /**
   * 计算玩家在某场比赛中某名次（1-4）的 ELO 变化。
   *
   * <p>基准分 = 玩家当前段位在该名次的加减分（段位表），再按 K/20 缩放取整。</p>
   *
   * @param player    玩家（用于取段位与 K 因子）
   * @param placement 名次：1 第1名 / 2 第2名 / 3 第3名 / 4 第4名
   */
  public static int calculateChange(RankPlayer player, int placement) {
    if (player == null) {
      return 0;
    }
    RankTier tier = player.getTier();
    int base = tier.getScore(placement);
    int k = getKFactor(player);
    return (int) Math.round(base * k / 20.0);
  }

  /** 计算 MVP 额外加分（按玩家当前段位的 MVP 分）。 */
  public static int calculateMvpBonus(RankPlayer player) {
    if (player == null) {
      return 0;
    }
    return player.getTier().getMvpScore();
  }
}
