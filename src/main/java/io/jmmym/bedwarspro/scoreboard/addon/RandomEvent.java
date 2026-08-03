package io.jmmym.bedwarspro.scoreboard.addon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.jmmym.bedwarspro.game.Game;
import lombok.Getter;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.utils.ColorUtil;

public class RandomEvent {

	private Game game;
	private Random random = new Random();
	private boolean triggered = false;

	@Getter
	private String currentEventName;

	private static final List<Event> events = new ArrayList<>();

	static {
		events.add(new Event("全体速度提升", PotionEffectType.SPEED, 1, 30));
		events.add(new Event("全体力量提升", PotionEffectType.INCREASE_DAMAGE, 0, 30));
		events.add(new Event("全体跳跃提升", PotionEffectType.JUMP, 2, 30));
		events.add(new Event("全体生命恢复", PotionEffectType.REGENERATION, 1, 20));
		events.add(new Event("全体抗性提升", PotionEffectType.DAMAGE_RESISTANCE, 0, 25));
	}

	public RandomEvent(Game game) {
		this.game = game;
	}

	public void trigger() {
		if (triggered) {
			return;
		}
		triggered = true;

		Event event = events.get(random.nextInt(events.size()));
		currentEventName = event.getName();

		for (Player player : game.getPlayers()) {
			if (player.isOnline()) {
				player.addPotionEffect(new PotionEffect(event.getType(), event.getDuration() * 20, event.getAmplifier(), false, true));
			}
		}
	}

	public boolean isTriggered() {
		return triggered;
	}

	public void reset() {
		triggered = false;
		currentEventName = null;
	}

	private static class Event {
		private String name;
		private PotionEffectType type;
		private int amplifier;
		private int duration;

		public Event(String name, PotionEffectType type, int amplifier, int duration) {
			this.name = name;
			this.type = type;
			this.amplifier = amplifier;
			this.duration = duration;
		}

		public String getName() {
			return name;
		}

		public PotionEffectType getType() {
			return type;
		}

		public int getAmplifier() {
			return amplifier;
		}

		public int getDuration() {
			return duration;
		}
	}
}