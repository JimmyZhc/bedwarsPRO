package io.jmmym.bedwarspro.bot;

import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * Bot玩家封装类。管理机器人的状态、任务和行为。
 */
public class BotPlayer {

  private final Player bukkitPlayer;
  private final String botName;
  private Game currentGame;
  private Team team;
  private BotState state;
  private int health;
  private int maxHealth;
  private int attackCooldown;
  private final List<BotTask> tasks;

  public enum BotState {
    IDLE,
    IN_GAME,
    DEAD,
    SPECTATING
  }

  public BotPlayer(Player bukkitPlayer, String botName) {
    this.bukkitPlayer = bukkitPlayer;
    this.botName = botName;
    this.state = BotState.IDLE;
    this.health = 20;
    this.maxHealth = 20;
    this.attackCooldown = 0;
    this.tasks = new ArrayList<>();
  }

  public void joinGame(Game game) {
    this.currentGame = game;
    this.state = BotState.IN_GAME;
  }

  public void leaveGame() {
    if (currentGame != null) {
      this.currentGame = null;
      this.team = null;
      this.state = BotState.IDLE;
      this.tasks.clear();
    }
  }

  public void die() {
    this.state = BotState.DEAD;
    this.health = 0;
    this.tasks.clear();
  }

  public void respawn() {
    this.state = BotState.IN_GAME;
    this.health = maxHealth;
    this.attackCooldown = 0;
  }

  public void setHealth(int health) {
    this.health = Math.max(0, Math.min(maxHealth, health));
  }

  public boolean isAlive() {
    return state == BotState.IN_GAME && health > 0;
  }

  public boolean isOnline() {
    return bukkitPlayer != null && bukkitPlayer.isOnline();
  }

  public void tickCooldowns() {
    if (attackCooldown > 0) {
      attackCooldown--;
    }
  }

  public boolean canAttack() {
    return attackCooldown <= 0;
  }

  public void resetAttackCooldown(int delay) {
    this.attackCooldown = delay;
  }

  public void addTask(BotTask task) {
    this.tasks.add(task);
  }

  public void removeTask(BotTask task) {
    this.tasks.remove(task);
  }

  public List<BotTask> getTasks() {
    return tasks;
  }

  public void setTasks(List<BotTask> newTasks) {
    this.tasks.clear();
    this.tasks.addAll(newTasks);
  }

  public Player getBukkitPlayer() {
    return bukkitPlayer;
  }

  public String getBotName() {
    return botName;
  }

  public Game getCurrentGame() {
    return currentGame;
  }

  public Team getTeam() {
    return team;
  }

  public BotState getState() {
    return state;
  }

  public int getHealth() {
    return health;
  }

  public int getMaxHealth() {
    return maxHealth;
  }

  public void setTeam(Team team) {
    this.team = team;
  }
}
