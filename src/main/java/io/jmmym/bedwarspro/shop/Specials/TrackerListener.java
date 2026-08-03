package io.jmmym.bedwarspro.shop.Specials;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameStartEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class TrackerListener implements Listener {

  @EventHandler
  public void onInteract(PlayerInteractEvent ev) {
    Player player = ev.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() != GameState.RUNNING) {
      return;
    }

    if (game.isSpectator(player)) {
      return;
    }

    Tracker tracker = new Tracker();
    if (!ev.getMaterial().equals(tracker.getItemMaterial())) {
      return;
    }

    if (ev.getAction().equals(Action.LEFT_CLICK_AIR)
        || ev.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
      return;
    }

    tracker.setPlayer(player);
    tracker.setGame(game);
    tracker.trackPlayer();
    ev.setCancelled(true);
  }

  @EventHandler
  public void onStart(BedwarsGameStartEvent ev) {
    final Game game = ev.getGame();

    if (game == null) {
      return;
    }

    Tracker tracker = new Tracker();
    tracker.setGame(game);
    tracker.createTask();

  }

}
