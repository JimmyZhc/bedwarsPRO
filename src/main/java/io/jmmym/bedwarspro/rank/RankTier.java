package io.jmmym.bedwarspro.rank;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 段位体系（Minecraft 原版矿物版）。
 *
 * <p>共 15 个段位，按所需 ELO 升序排列。每个段位拥有独立的四名次加减分表与 MVP 加分
 * （下界合金 / 下界之星的 MVP 加分为 +5，其余为 +15）。中文/英文段位名称可通过
 * rank.yml 的 tiers 段自定义（未配置时使用下方默认值）。</p>
 */
public enum RankTier {

  COAL("煤炭", "Coal", 0, 35, 0, -5, -10, 15),
  IRON("铁", "Iron", 100, 30, 0, -5, -10, 15),
  COPPER("铜", "Copper", 200, 30, 0, -8, -15, 15),
  GOLD("金", "Gold", 300, 25, 0, -8, -15, 15),
  REDSTONE("红石", "Redstone", 400, 20, 0, -12, -25, 15),
  LAPIS("青金石", "Lapis Lazuli", 500, 15, 0, -12, -25, 15),
  EMERALD("绿宝石", "Emerald", 800, 15, 0, -15, -30, 15),
  DIAMOND("钻石", "Diamond", 900, 15, 0, -15, -30, 15),
  QUARTZ("下界石英", "Nether Quartz", 1100, 15, 0, -15, -30, 15),
  AMETHYST("紫水晶", "Amethyst", 1300, 10, 0, -18, -35, 15),
  DEBRIS("远古残骸", "Ancient Debris", 1400, 10, 0, -20, -40, 15),
  OBSIDIAN("黑曜石", "Obsidian", 1500, 10, 0, -22, -45, 15),
  BEDROCK("基岩", "Bedrock", 1600, 5, 0, -22, -45, 15),
  NETHERITE("下界合金", "Netherite", 1700, 5, 0, -25, -50, 5),
  NETHER_STAR("下界之星", "Nether Star", 1800, 5, 0, -28, -55, 5);

  /** rank.yml 中自定义的段位名称（cn/en），未配置的段位使用默认值。 */
  private static final Map<RankTier, String[]> nameOverrides = new EnumMap<>(RankTier.class);

  private final String cnName;
  private final String enName;
  /** 进入该段位所需的 ELO（最低段位为 0）。 */
  private final int requiredElo;
  private final int firstScore;
  private final int secondScore;
  private final int thirdScore;
  private final int fourthScore;
  private final int mvpScore;

  RankTier(String cnName, String enName, int requiredElo, int firstScore, int secondScore,
      int thirdScore, int fourthScore, int mvpScore) {
    this.cnName = cnName;
    this.enName = enName;
    this.requiredElo = requiredElo;
    this.firstScore = firstScore;
    this.secondScore = secondScore;
    this.thirdScore = thirdScore;
    this.fourthScore = fourthScore;
    this.mvpScore = mvpScore;
  }

  public String getCnName() {
    String[] override = nameOverrides.get(this);
    return override != null && override[0] != null && !override[0].isEmpty()
        ? override[0] : this.cnName;
  }

  public String getEnName() {
    String[] override = nameOverrides.get(this);
    return override != null && override[1] != null && !override[1].isEmpty()
        ? override[1] : this.enName;
  }

  public int getRequiredElo() {
    return this.requiredElo;
  }

  public int getFirstScore() {
    return this.firstScore;
  }

  public int getSecondScore() {
    return this.secondScore;
  }

  public int getThirdScore() {
    return this.thirdScore;
  }

  public int getFourthScore() {
    return this.fourthScore;
  }

  public int getMvpScore() {
    return this.mvpScore;
  }

  /** 按名次（1-4）获取基础加减分。 */
  public int getScore(int placement) {
    switch (placement) {
      case 1:
        return this.firstScore;
      case 2:
        return this.secondScore;
      case 3:
        return this.thirdScore;
      case 4:
      default:
        return this.fourthScore;
    }
  }

  /** 根据 ELO 分数获取当前段位（ELO 越高段位越高）。 */
  public static RankTier getByElo(int elo) {
    RankTier result = COAL;
    for (RankTier tier : values()) {
      if (elo >= tier.requiredElo) {
        result = tier;
      }
    }
    return result;
  }

  /** 根据配置中的英文名（忽略大小写）解析段位。 */
  public static RankTier parse(String value) {
    if (value == null) {
      return COAL;
    }
    for (RankTier tier : values()) {
      if (tier.name().equalsIgnoreCase(value) || tier.enName.equalsIgnoreCase(value)) {
        return tier;
      }
    }
    return COAL;
  }

  /**
   * 从 rank.yml 的 tiers 段加载自定义段位名称（未配置的段位保持默认值）。
   * 由 {@link RankManager#loadConfig()} 在启动与 reload 时调用。
   */
  public static void loadNames(YamlConfiguration config) {
    nameOverrides.clear();
    if (config == null) {
      return;
    }
    for (RankTier tier : values()) {
      String path = "tiers." + tier.name().toLowerCase();
      String cn = config.getString(path + ".cn");
      String en = config.getString(path + ".en");
      if (cn != null || en != null) {
        nameOverrides.put(tier, new String[]{cn, en});
      }
    }
  }
}
