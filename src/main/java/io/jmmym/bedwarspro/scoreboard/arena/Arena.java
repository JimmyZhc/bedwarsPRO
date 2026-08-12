package io.jmmym.bedwarspro.scoreboard.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameOverEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerKilledEvent;
import io.jmmym.bedwarspro.events.BedwarsTargetBlockDestroyedEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import lombok.Getter;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.addon.Actionbar;
import io.jmmym.bedwarspro.scoreboard.addon.DeathMode;
import io.jmmym.bedwarspro.scoreboard.addon.GameChest;
import io.jmmym.bedwarspro.scoreboard.addon.Graffiti;
import io.jmmym.bedwarspro.scoreboard.addon.HealthLevel;
import io.jmmym.bedwarspro.scoreboard.addon.Holographic;
import io.jmmym.bedwarspro.scoreboard.addon.InvisibilityPlayer;
import io.jmmym.bedwarspro.scoreboard.addon.LobbyBlock;
import io.jmmym.bedwarspro.scoreboard.addon.NoBreakBed;
import io.jmmym.bedwarspro.scoreboard.addon.PlaySound;
import io.jmmym.bedwarspro.scoreboard.addon.Rejoin;
import io.jmmym.bedwarspro.scoreboard.addon.ResourceUpgrade;
import io.jmmym.bedwarspro.scoreboard.addon.Respawn;
import io.jmmym.bedwarspro.scoreboard.addon.ScoreBoard;
import io.jmmym.bedwarspro.scoreboard.addon.Shop;
import io.jmmym.bedwarspro.scoreboard.addon.TimeTask;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.storage.PlayerGameStorage;
import io.jmmym.bedwarspro.scoreboard.utils.BedwarsUtil;
import io.jmmym.bedwarspro.scoreboard.utils.PlaceholderAPIUtil;

public class Arena {

	@Getter
	private Game game;
	@Getter
	private ScoreBoard scoreBoard;
	@Getter
	private PlayerGameStorage playerGameStorage;
	@Getter
	private DeathMode deathMode;
	@Getter
	private HealthLevel healthLevel;
	@Getter
	private NoBreakBed noBreakBed;
	@Getter
	private ResourceUpgrade resourceUpgrade;
	@Getter
	private Holographic holographic;
	@Getter
	private InvisibilityPlayer invisiblePlayer;
	@Getter
	private LobbyBlock lobbyBlock;
	@Getter
	private Respawn respawn;
	@Getter
	private Actionbar actionbar;
	@Getter
	private Graffiti graffiti;
	@Getter
	private GameChest gameChest;
	@Getter
	private Rejoin rejoin;
	@Getter
	private Shop shop;
	@Getter
	private TimeTask timeTask;
	private Boolean isOver;
	private List<BukkitTask> gameTasks;

	public Arena(Game game) {
		Main.getInstance().getArenaManager().addArena(game.getName(), this);
		this.game = game;
		gameTasks = new ArrayList<BukkitTask>();
		playerGameStorage = new PlayerGameStorage(this);
		
		// 逐个初始化子模块，防止单个模块初始化失败导致整个 Arena 崩溃
		try {
			scoreBoard = new ScoreBoard(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 ScoreBoard 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			deathMode = new DeathMode(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 DeathMode 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			healthLevel = new HealthLevel(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 HealthLevel 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			noBreakBed = new NoBreakBed(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 NoBreakBed 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			resourceUpgrade = new ResourceUpgrade(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 ResourceUpgrade 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			holographic = new Holographic(this, resourceUpgrade);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 Holographic 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			invisiblePlayer = new InvisibilityPlayer(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 InvisibilityPlayer 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			lobbyBlock = new LobbyBlock(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 LobbyBlock 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			respawn = new Respawn(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 Respawn 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			actionbar = new Actionbar(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 Actionbar 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			graffiti = new Graffiti(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 Graffiti 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			gameChest = new GameChest(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 GameChest 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			rejoin = new Rejoin(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 Rejoin 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		if (Main.getInstance().isEnabledCitizens()) {
			try {
				shop = new Shop(this);
			} catch (Exception e) {
				BedwarsPRO.getInstance().getLogger().warning("初始化 Shop 失败: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			BedwarsPRO.getInstance().getLogger().warning("未检测到 Citizens 插件，物品商店/队伍商店 NPC 不会生成（" + game.getName() + "）");
		}
		
		try {
			timeTask = new TimeTask(this);
		} catch (Exception e) {
			BedwarsPRO.getInstance().getLogger().warning("初始化 TimeTask 失败: " + e.getMessage());
			e.printStackTrace();
		}
		
		isOver = false;
		
		// 启动游戏状态检测计时器
		addGameTask(new BukkitRunnable() {
			@Override
			public void run() {
				if (!game.getState().equals(GameState.RUNNING)) {
					onOver(new BedwarsGameOverEvent(game, null));
					onEnd();
				}
			}
		}.runTaskTimer(Main.getPlugin(), 1L, 1L));
	}

	public void addGameTask(BukkitTask task) {
		gameTasks.add(task);
	}

	public Boolean isOver() {
		return isOver;
	}

	public void onTargetBlockDestroyed(BedwarsTargetBlockDestroyedEvent e) {
		if (!isAlivePlayer(e.getPlayer())) {
			return;
		}
		Map<String, Integer> beds = playerGameStorage.getPlayerBeds();
		Player player = e.getPlayer();
		if (beds.containsKey(player.getName())) {
			beds.put(player.getName(), beds.get(player.getName()) + 1);
		} else {
			beds.put(player.getName(), 1);
		}
		if (holographic != null) {
			holographic.onTargetBlockDestroyed(e);
		}
	}

	public void onDeath(Player player) {
		if (invisiblePlayer != null) {
			invisiblePlayer.removePlayer(player);
		}
		if (!isGamePlayer(player)) {
			return;
		}
		Map<String, Integer> dies = playerGameStorage.getPlayerDies();
		if (dies.containsKey(player.getName())) {
			dies.put(player.getName(), dies.get(player.getName()) + 1);
		} else {
			dies.put(player.getName(), 1);
		}
		PlaySound.playSound(player, Config.play_sound_sound_death);
	}

	public void onDamage(EntityDamageEvent e) {
		if (respawn != null) {
			respawn.onDamage(e);
		}
	}

	public void onInteractEntity(PlayerInteractEntityEvent e) {
		if (graffiti != null) {
			graffiti.onInteractEntity(e);
		}
	}

	public void onInteract(PlayerInteractEvent e) {
		if (gameChest != null) {
			gameChest.onInteract(e);
		}
	}

	public void onHangingBreak(HangingBreakEvent e) {
		if (graffiti != null) {
			graffiti.onHangingBreak(e);
		}
	}

	public void onRespawn(Player player) {
		if (!isGamePlayer(player)) {
			return;
		}
		if (respawn != null) {
			respawn.onRespawn(player, false);
		}
	}

	public void onPlayerKilled(BedwarsPlayerKilledEvent e) {
		if (!isGamePlayer(e.getPlayer()) || !isGamePlayer(e.getKiller())) {
			return;
		}
		Player player = e.getPlayer();
		Player killer = e.getKiller();
		if (!game.getPlayers().contains(player) || !game.getPlayers().contains(killer) || game.isSpectator(player) || game.isSpectator(killer)) {
			return;
		}
		Map<String, Integer> totalkills = playerGameStorage.getPlayerTotalKills();
		Map<String, Integer> kills = playerGameStorage.getPlayerKills();
		Map<String, Integer> finalkills = playerGameStorage.getPlayerFinalKills();
		if (!game.getPlayerTeam(player).isDead(game)) {
			if (kills.containsKey(killer.getName())) {
				kills.put(killer.getName(), kills.get(killer.getName()) + 1);
			} else {
				kills.put(killer.getName(), 1);
			}
		}
		if (game.getPlayerTeam(player).isDead(game)) {
			if (finalkills.containsKey(killer.getName())) {
				finalkills.put(killer.getName(), finalkills.get(killer.getName()) + 1);
			} else {
				finalkills.put(killer.getName(), 1);
			}
		}
		if (totalkills.containsKey(killer.getName())) {
			totalkills.put(killer.getName(), totalkills.get(killer.getName()) + 1);
		} else {
			totalkills.put(killer.getName(), 1);
		}
		playerGameStorage.incrementKillStreak(killer.getName());
		playerGameStorage.resetKillStreak(player.getName());
		PlaySound.playSound(killer, Config.play_sound_sound_kill);
	}

	public void onOver(BedwarsGameOverEvent e) {
		if (e.getGame().getName().equals(this.game.getName())) {
			isOver = true;
		}
	}

	public void onEnd() {
		gameTasks.forEach(task -> {
			task.cancel();
		});
		if (noBreakBed != null) {
			noBreakBed.onEnd();
		}
		if (holographic != null) {
			holographic.remove();
		}
		if (Main.getInstance().isEnabledCitizens() && shop != null) {
			shop.remove();
		}
		if (graffiti != null) {
			graffiti.reset();
		}
		if (gameChest != null) {
			gameChest.clearChest();
		}
	}

	public void onDisable() {
		if (holographic != null) {
			holographic.remove();
		}
		if (Main.getInstance().isEnabledCitizens() && shop != null) {
			shop.remove();
		}
		if (graffiti != null) {
			graffiti.reset();
		}
		if (gameChest != null) {
			gameChest.clearChest();
		}
	}

	public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
		if (holographic != null) {
			holographic.onArmorStandManipulate(e);
		}
	}

	public void onItemMerge(ItemMergeEvent e) {
		if (!Config.item_merge && game.getRegion().isInRegion(e.getEntity().getLocation())) {
			e.setCancelled(true);
		}
	}

	public void onPlayerLeave(Player player) {
		if (holographic != null) {
			holographic.onPlayerLeave(player);
		}
		if (Config.rejoin_enabled && rejoin != null) {
			if (game.getState() == GameState.RUNNING && !game.isSpectator(player)) {
				Team team = game.getPlayerTeam(player);
				if (team != null) {
					if (team.getPlayers().size() > 1 && !team.isDead(game)) {
						rejoin.addPlayer(player);
						return;
					}
				}
			}
			rejoin.removePlayer(player.getName());
		}
		if (respawn != null) {
			respawn.onPlayerLeave(player);
		}
	}

	public void onPlayerJoined(Player player) {
		if (Config.rejoin_enabled && rejoin != null) {
			rejoin.rejoin(player);
		}
		if (respawn != null) {
			respawn.onPlayerJoined(player);
		}
		if (holographic != null) {
			holographic.onPlayerJoin(player);
		}
		if (graffiti != null) {
			graffiti.onPlayerJoin(player);
		}
		if (Main.getInstance().isEnabledCitizens() && shop != null) {
			shop.onPlayerJoined(player);
		}
	}

	private Boolean isGamePlayer(Player player) {
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game == null) {
			return false;
		}
		if (!game.getName().equals(this.game.getName())) {
			return false;
		}
		if (game.isSpectator(player)) {
			return false;
		}
		return true;
	}

	private Boolean isAlivePlayer(Player player) {
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game == null) {
			return false;
		}
		if (!game.getName().equals(this.game.getName())) {
			return false;
		}
		if (BedwarsUtil.isSpectator(game, player)) {
			return false;
		}
		return true;
	}
}
