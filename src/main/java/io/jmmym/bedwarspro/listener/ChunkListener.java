package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListener implements Listener {

  @EventHandler
  public void onUnload(ChunkUnloadEvent unload) {
    Game game = BedwarsPRO.getInstance().getGameManager()
        .getGameByChunkLocation(unload.getChunk().getX(),
            unload.getChunk().getZ());
    if (game == null) {
      return;
    }

    if (game.getState() != GameState.RUNNING) {
      return;
    }

    unload.setCancelled(true);
  }

}
