package io.jmmym.bedwarspro.scoreboard.storage;

import java.util.HashMap;
import java.util.Map;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;

public class PlayerGameStorage {

	private Arena arena;
	private Map<String, Integer> totalkills;
	private Map<String, Integer> kills;
	private Map<String, Integer> finalkills;
	private Map<String, Integer> dies;
	private Map<String, Integer> beds;
	private Map<String, Integer> currentKillStreak;
	private Map<String, Integer> maxKillStreak;

	public PlayerGameStorage(Arena arena) {
		this.arena = arena;
		totalkills = new HashMap<String, Integer>();
		kills = new HashMap<String, Integer>();
		finalkills = new HashMap<String, Integer>();
		dies = new HashMap<String, Integer>();
		beds = new HashMap<String, Integer>();
		currentKillStreak = new HashMap<String, Integer>();
		maxKillStreak = new HashMap<String, Integer>();
	}

	public Arena getArena() {
		return arena;
	}

	public Game getGame() {
		return arena.getGame();
	}

	public Map<String, Integer> getPlayerTotalKills() {
		return totalkills;
	}

	public Map<String, Integer> getPlayerKills() {
		return kills;
	}

	public Map<String, Integer> getPlayerFinalKills() {
		return finalkills;
	}

	public Map<String, Integer> getPlayerDies() {
		return dies;
	}

	public Map<String, Integer> getPlayerBeds() {
		return beds;
	}
	
	public Map<String, Integer> getPlayerMaxKillStreak() {
		return maxKillStreak;
	}
	
	public void incrementKillStreak(String playerName) {
		int current = currentKillStreak.getOrDefault(playerName, 0) + 1;
		currentKillStreak.put(playerName, current);
		int max = maxKillStreak.getOrDefault(playerName, 0);
		if (current > max) {
			maxKillStreak.put(playerName, current);
		}
	}
	
	public void resetKillStreak(String playerName) {
		currentKillStreak.put(playerName, 0);
	}
}
