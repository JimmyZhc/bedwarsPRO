package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import java.util.ArrayList;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public class PlayerSpigotListener extends BaseListener {

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
    if (BedwarsPRO.getInstance().isBungee()) {
      Player player = event.getPlayer();

      ArrayList<Game> games = BedwarsPRO.getInstance().getGameManager().getGames();
      if (games.size() == 0) {
        return;
      }

      Game firstGame = games.get(0);

      event.setSpawnLocation(firstGame.getPlayerTeleportLocation(player));
    }
  }

}
