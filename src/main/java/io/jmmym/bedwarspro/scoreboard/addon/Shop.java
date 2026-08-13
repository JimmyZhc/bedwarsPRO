package io.jmmym.bedwarspro.scoreboard.addon;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.shop.NewItemShop;
import lombok.Getter;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.api.HolographicAPI;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.events.BoardAddonPlayerOpenItemShopEvent;
import io.jmmym.bedwarspro.scoreboard.utils.BedwarsUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.skin.SkinnableEntity;
import net.citizensnpcs.trait.Gravity;
import net.citizensnpcs.trait.LookClose;

public class Shop {

	@Getter
	private Game game;
	@Getter
	private Arena arena;
	private List<NPC> shops;
	private List<HolographicAPI> titles;
	private List<Integer> npcid;
	private WrappedDataWatcher.Serializer booleanserializer;

	public Shop(Arena arena) {
		this.arena = arena;
		this.game = arena.getGame();
		shops = new ArrayList<NPC>();
		titles = new ArrayList<HolographicAPI>();
		npcid = new ArrayList<Integer>();
		if (!BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
			booleanserializer = WrappedDataWatcher.Registry.get(Boolean.class);
		}
		if (Config.shop_enabled) {
			if (Config.game_shop_item.containsKey(game.getName())) {
				for (String loc : Config.game_shop_item.get(game.getName())) {
					Location location = toLocation(loc);
					if (location != null) {
						shops.add(spawnShop(location.clone(), Config.shop_item_shop_look, Config.shop_item_shop_type, Config.shop_item_shop_skin));
						setTitle(location.clone().add(0, -0.1, 0), Config.shop_item_shop_name);
					}
				}
			}
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				if (game.getState() != GameState.RUNNING || game.getPlayers().size() < 1) {
					cancel();
					for (NPC npc : shops) {
						CitizensAPI.getNPCRegistry().deregister(npc);
					}
					for (HolographicAPI holo : titles) {
						holo.remove();
					}
				}
			}
		}.runTaskTimer(Main.getPlugin(), 0L, 0L);
	}

	public boolean isShopNPC(int id) {
		return npcid.contains(id);
	}

	public Boolean onNPCClick(Player player, NPC npc, Boolean isCancelled) {
		if (!Config.shop_enabled) {
			return isCancelled;
		}
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game != null) {
			if (shops.contains(npc)) {
				if (isGamePlayer(player)) {
					isCancelled = true;
					BoardAddonPlayerOpenItemShopEvent openItemhopEvent = new BoardAddonPlayerOpenItemShopEvent(game, player);
					Bukkit.getPluginManager().callEvent(openItemhopEvent);
					if (!openItemhopEvent.isCancelled()) {
						player.closeInventory();
						NewItemShop itemShop = game.openNewItemShop(player);
						itemShop.setCurrentCategory(null);
						itemShop.openCategoryInventory(player);
					}
				}
			}
		}
		return isCancelled;
	}

	public void onPlayerJoined(Player player) {
		if (game.getState() == GameState.RUNNING) {
			new BukkitRunnable() {
				@Override
				public void run() {
					if (game.getState() == GameState.RUNNING && player.isOnline() && game.getPlayers().contains(player)) {
						for (HolographicAPI holo : titles) {
							holo.display(player);
						}
					}
				}
			}.runTaskLater(Main.getPlugin(), 10L);
		}
	}

	private Boolean isGamePlayer(Player player) {
		Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
		if (game == null) {
			return false;
		}
		if (BedwarsUtil.isSpectator(game, player)) {
			return false;
		}
		if (player.getGameMode() == GameMode.SPECTATOR) {
			return false;
		}
		return true;
	}

	private NPC spawnShop(Location location, boolean look, String type, String skin) {
		if (!location.getBlock().getChunk().isLoaded()) {
			location.getBlock().getChunk().load(true);
		}
		NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "");
		npc.setProtected(true);
		npc.getTrait(Gravity.class).toggle();
		if (look) {
			npc.getTrait(LookClose.class).toggle();
		}
		npc.spawn(location);
		try {
			EntityType entityType = EntityType.valueOf(type);
			npc.setBukkitEntityType(entityType);
		} catch (Exception e) {
		}
		npc.data().setPersistent("silent-sounds", true);
		if (npc.getEntity() != null) {
			npcid.add(npc.getEntity().getEntityId());
			hideEntityTag(npc.getEntity());
		}
		Config.addShopNPC(npc.getId());
		try {
			if (npc.isSpawned() && npc.getEntity() instanceof SkinnableEntity) {
				SkinnableEntity skinnable = (SkinnableEntity) npc.getEntity();
				skinnable.setSkinName(skin, true);
			}
		} catch (Exception e) {
		}
		return npc;
	}

	private void hideEntityTag(Entity entity) {
		Bukkit.getScheduler().runTaskLater(Main.getPlugin(), () -> {
			ProtocolManager man = ProtocolLibrary.getProtocolManager();
			PacketContainer packet = man.createPacket(PacketType.Play.Server.ENTITY_METADATA);
			packet.getIntegers().write(0, entity.getEntityId());
			WrappedDataWatcher wrappedDataWatcher = new WrappedDataWatcher();
			if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
				wrappedDataWatcher.setObject(3, (byte) 0);
			} else {
				wrappedDataWatcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, booleanserializer), false);
			}
			packet.getWatchableCollectionModifier().write(0, wrappedDataWatcher.getWatchableObjects());
			for (Player player : game.getPlayers()) {
				try {
					man.sendServerPacket(player, packet, false);
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}, 1L);
	}

	private void setTitle(Location location, List<String> title) {
		Location loc = location.clone();
		if (!loc.getBlock().getChunk().isLoaded()) {
			loc.getBlock().getChunk().load(true);
		}
		List<String> list = new ArrayList<String>();
		list.addAll(title);
		Collections.reverse(list);
		for (String line : list) {
			HolographicAPI holo = new HolographicAPI(loc, line);
			titles.add(holo);
			new BukkitRunnable() {
				@Override
				public void run() {
					for (Player player : game.getPlayers()) {
						holo.display(player);
					}
				}
			}.runTaskLater(Main.getPlugin(), 20L);
			loc.add(0, 0.3, 0);
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				if (game.getState() == GameState.RUNNING) {
					if (!loc.getBlock().getChunk().isLoaded()) {
						loc.getBlock().getChunk().load(true);
					}
				} else {
					this.cancel();
				}
			}
		}.runTaskTimer(Main.getPlugin(), 0L, 0L);
	}

	private Location toLocation(String loc) {
		try {
			String[] ary = loc.split(", ");
			if (Bukkit.getWorld(ary[0]) != null) {
				Location location = new Location(Bukkit.getWorld(ary[0]), Double.valueOf(ary[1]), Double.valueOf(ary[2]), Double.valueOf(ary[3]));
				if (ary.length > 4) {
					location.setYaw(Float.valueOf(ary[4]));
					location.setPitch(Float.valueOf(ary[5]));
				}
				return location;
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public void remove() {
		for (NPC npc : shops) {
			CitizensAPI.getNPCRegistry().deregister(npc);
		}
		for (HolographicAPI holo : titles) {
			holo.remove();
		}
	}
}
