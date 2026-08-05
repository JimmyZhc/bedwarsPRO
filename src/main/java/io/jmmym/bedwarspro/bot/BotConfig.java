package io.jmmym.bedwarspro.bot;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Bot配置类。读取bot-config.yml中的配置项。
 */
public class BotConfig {

  private final BedwarsPRO plugin;

  private boolean enabled;
  private int maxBotsPerGame;
  private int minPlayersToAddBot;
  private double aiDifficulty;
  private int reactionTime;
  private double accuracy;
  private int targetRange;
  private int fleeHealth;
  private boolean autoBuy;
  private int attackDelay;
  private boolean autoJoin;

  public BotConfig(BedwarsPRO plugin) {
    this.plugin = plugin;
    loadConfig();
  }

  public void loadConfig() {
    plugin.saveResource("bot-config.yml", false);
    File botFile = new File(plugin.getDataFolder(), "bot-config.yml");
    FileConfiguration config = YamlConfiguration.loadConfiguration(botFile);

    this.enabled = config.getBoolean("bot.enabled", true);
    this.maxBotsPerGame = config.getInt("bot.max-bots-per-game", 8);
    this.minPlayersToAddBot = config.getInt("bot.min-players-to-add-bot", 2);
    this.autoJoin = config.getBoolean("bot.auto-join", true);
    this.aiDifficulty = config.getDouble("bot.ai.difficulty", 0.5);
    this.reactionTime = config.getInt("bot.ai.reaction-time", 200);
    this.accuracy = config.getDouble("bot.ai.accuracy", 0.8);
    this.targetRange = config.getInt("bot.combat.target-range", 16);
    this.fleeHealth = config.getInt("bot.combat.flee-health", 6);
    this.attackDelay = config.getInt("bot.combat.attack-delay", 10);
    this.autoBuy = config.getBoolean("bot.items.auto-buy", true);
  }

  public void reload() {
    loadConfig();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getMaxBotsPerGame() {
    return maxBotsPerGame;
  }

  public int getMinPlayersToAddBot() {
    return minPlayersToAddBot;
  }

  public double getAiDifficulty() {
    return aiDifficulty;
  }

  public int getReactionTime() {
    return reactionTime;
  }

  public double getAccuracy() {
    return accuracy;
  }

  public int getTargetRange() {
    return targetRange;
  }

  public int getFleeHealth() {
    return fleeHealth;
  }

  public boolean isAutoBuy() {
    return autoBuy;
  }

  public int getAttackDelay() {
    return attackDelay;
  }

  public boolean isAutoJoin() {
    return autoJoin;
  }
}
