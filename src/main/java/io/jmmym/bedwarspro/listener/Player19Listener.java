package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.BungeeGameCycle;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class Player19Listener extends BaseListener {

  @EventHandler
  public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
    Player player = event.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.WAITING
        || (game.getCycle() instanceof BungeeGameCycle && game.getCycle().isEndGameRunning()
        && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {
      event.setCancelled(true);
      return;
    }
  }

}
