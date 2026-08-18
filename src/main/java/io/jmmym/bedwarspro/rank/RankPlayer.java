package io.jmmym.bedwarspro.rank;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * 玩家排位数据。
 *
 * <p>记录 ELO 分数、段位相关战绩（场次 / 各名次次数 / 胜负 / 连败连胜）、排位击杀与破床数、
 * 历史最高 ELO 等。由 {@link RankStorage} 负责本地 YAML 与数据库的双向持久化。</p>
 */
@Getter
@Setter
public class RankPlayer {

  private UUID uuid;
  private String name;
  /** 当前 ELO 分数。 */
  private int elo = 0;
  /** 排位总场次（含定级赛）。 */
  private int gamesPlayed = 0;
  /** 第 1 名次数（胜场）。 */
  private int wins = 0;
  /** 第 2 名次数。 */
  private int secondCount = 0;
  /** 第 3 名次数。 */
  private int thirdCount = 0;
  /** 第 4 名次数。 */
  private int fourthCount = 0;
  /** 历史最高 ELO。 */
  private int highestElo = 0;
  /** 排位总击杀（用于 MVP 评选与场均击杀）。 */
  private int kills = 0;
  /** 排位总破床数。 */
  private int bedsDestroyed = 0;
  /** 当前连胜。 */
  private int winStreak = 0;
  /** 当前连败。 */
  private int loseStreak = 0;

  public RankPlayer() {
  }

  public RankPlayer(UUID uuid, String name) {
    this.uuid = uuid;
    this.name = name;
  }

  /** 当前段位（由 ELO 推导）。 */
  public RankTier getTier() {
    return RankTier.getByElo(this.elo);
  }

  /** 总场次是否达到定级赛要求（定级赛期间使用新玩家 K 因子）。 */
  public boolean isPlacementDone() {
    return this.gamesPlayed >= RankManager.getInstance().getPlacementMatches();
  }

  /** 胜率（0-100，保留一位小数）。 */
  public double getWinRate() {
    if (this.gamesPlayed <= 0) {
      return 0.0;
    }
    return Math.round(this.wins * 1000.0 / this.gamesPlayed) / 10.0;
  }

  /** 场均击杀。 */
  public double getAvgKills() {
    if (this.gamesPlayed <= 0) {
      return 0.0;
    }
    return Math.round(this.kills * 10.0 / this.gamesPlayed) / 10.0;
  }

  /** 场均破床。 */
  public double getAvgBeds() {
    if (this.gamesPlayed <= 0) {
      return 0.0;
    }
    return Math.round(this.bedsDestroyed * 10.0 / this.gamesPlayed) / 10.0;
  }

  /** 应用一次 ELO 变化（保证不低于最低 ELO，并同步最高分）。 */
  public void applyEloChange(int change) {
    int minElo = RankManager.getInstance().getMinElo();
    this.elo = Math.max(minElo, this.elo + change);
    if (this.elo > this.highestElo) {
      this.highestElo = this.elo;
    }
  }

  /** 结算一次名次（更新场次/名次统计与连胜连败）。 */
  public void applyPlacement(int placement, int placementEloChange) {
    this.gamesPlayed++;
    boolean won = placement == 1;
    if (won) {
      this.wins++;
      this.winStreak++;
      this.loseStreak = 0;
    } else {
      this.winStreak = 0;
      this.loseStreak++;
    }
    switch (placement) {
      case 1:
        break;
      case 2:
        this.secondCount++;
        break;
      case 3:
        this.thirdCount++;
        break;
      default:
        this.fourthCount++;
        break;
    }
    this.applyEloChange(placementEloChange);
  }
}
