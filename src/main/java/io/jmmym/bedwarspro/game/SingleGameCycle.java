package io.jmmym.bedwarspro.game;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameEndEvent;
import io.jmmym.bedwarspro.statistics.PlayerStatistic;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SingleGameCycle extends GameCycle {

  public SingleGameCycle(Game game) {
    super(game);
  }

  private void kickPlayer(Player player, boolean wasSpectator) {
    for (Player freePlayer : this.getGame().getFreePlayers()) {
      player.showPlayer(freePlayer);
    }

    if (wasSpectator && this.getGame().isFull()) {
      this.getGame().playerLeave(player, false);
      return;
    }

    // 优先使用主大厅，其次使用等待大厅
    Location targetLocation = null;
    Location mainLobby = this.getGame().getMainLobby();
    if (mainLobby != null) {
      targetLocation = mainLobby;
    } else {
      targetLocation = this.getGame().getLobby();
    }

    if (BedwarsPRO.getInstance().toMainLobby()
        && BedwarsPRO.getInstance().allPlayersBackToMainLobby()) {
      // 仍然先传送到主大厅/等待大厅，再执行 playerLeave
      if (targetLocation != null) {
        player.teleport(targetLocation);
      }
      this.getGame().playerLeave(player, false);
      return;
    }

    if (targetLocation != null) {
      player.teleport(targetLocation);
    }

    if (BedwarsPRO.getInstance().isHologramsEnabled()
        && BedwarsPRO.getInstance().getHolographicInteractor() != null
        && targetLocation != null && targetLocation.getWorld() == player.getWorld()) {
      BedwarsPRO.getInstance().getHolographicInteractor().updateHolograms(player);
    }

    if (BedwarsPRO.getInstance().statisticsEnabled()) {
      PlayerStatistic statistic =
          BedwarsPRO.getInstance().getPlayerStatisticManager().getStatistic(player);
      BedwarsPRO.getInstance().getPlayerStatisticManager().storeStatistic(statistic);

      if (BedwarsPRO.getInstance().getBooleanConfig("statistics.show-on-game-end", true)) {
        BedwarsPRO.getInstance().getServer().dispatchCommand(player, "bw stats");
      }
    }

    this.getGame().setPlayerDamager(player, null);

    PlayerStorage storage = this.getGame().getPlayerStorage(player);
    storage.clean();
    storage.loadLobbyInventory(this.getGame());
  }

  @Override
  public void onGameEnds() {
    // Reset scoreboard first
    this.getGame().resetScoreboard();

    // First team players, they get a reserved slot in lobby
    for (Player p : this.getGame().getTeamPlayers()) {
      this.kickPlayer(p, false);
    }

    // and now the spectators
    List<Player> freePlayers = new ArrayList<Player>(this.getGame().getFreePlayers());
    for (Player p : freePlayers) {
      this.kickPlayer(p, true);
    }

    // 强制传送残留玩家（安全网）
    final Game game = this.getGame();
    new BukkitRunnable() {
      @Override
      public void run() {
        for (Player p : new ArrayList<>(game.getPlayers())) {
          if (p.isOnline()) {
            Location target = game.getMainLobby() != null ? game.getMainLobby() : game.getLobby();
            if (target != null) {
              p.teleport(target);
            }
            game.playerLeave(p, false);
          }
        }
        // 也处理freePlayers中可能残留的
        for (Player p : new ArrayList<>(game.getFreePlayers())) {
          if (p.isOnline()) {
            Location target = game.getMainLobby() != null ? game.getMainLobby() : game.getLobby();
            if (target != null) {
              p.teleport(target);
            }
            game.playerLeave(p, false);
          }
        }
      }
    }.runTaskLater(BedwarsPRO.getInstance(), 60L);

    // reset countdown prevention breaks
    this.setEndGameRunning(false);

    // Reset team chests
    for (Team team : this.getGame().getTeams().values()) {
      team.setInventory(null);
      team.getChests().clear();
    }

    // clear protections
    this.getGame().clearProtections();

    // reset region
    this.getGame().resetRegion();

    // set state and with that, the sign
    this.getGame().setState(GameState.WAITING);
    this.getGame().updateScoreboard();
  }

  @Override
  public void onGameLoaded() {
    // Reset on game end
  }

  @Override
  public void onGameOver(GameOverTask task) {
    if (task.getCounter() == task.getStartCount() && task.getWinner() != null) {
      for (Player aPlayer : this.getGame().getPlayers()) {
        if (aPlayer.isOnline()) {
          aPlayer.sendMessage(
              ChatWriter.pluginMessage(ChatColor.GOLD + BedwarsPRO._l(aPlayer, "ingame.teamwon",
                  ImmutableMap.of("team", task.getWinner().getDisplayName() + ChatColor.GOLD))));
        }
      }
      this.getGame().stopWorkers();
    } else if (task.getCounter() == task.getStartCount() && task.getWinner() == null) {
      for (Player aPlayer : this.getGame().getPlayers()) {
        if (aPlayer.isOnline()) {
          aPlayer.sendMessage(
              ChatWriter.pluginMessage(ChatColor.GOLD + BedwarsPRO._l(aPlayer, "ingame.draw")));
        }
      }
    }

    if (this.getGame().getPlayers().size() == 0 || task.getCounter() == 0) {
      BedwarsGameEndEvent endEvent = new BedwarsGameEndEvent(this.getGame());
      BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(endEvent);

      this.onGameEnds();
      task.cancel();
    } else {
      for (Player aPlayer : this.getGame().getPlayers()) {
        if (aPlayer.isOnline()) {
          aPlayer.sendMessage(
              ChatWriter.pluginMessage(
                  ChatColor.AQUA + BedwarsPRO
                      ._l(aPlayer, "ingame.backtolobby", ImmutableMap.of("sec",
                          ChatColor.YELLOW.toString() + task.getCounter() + ChatColor.AQUA))));
        }
      }
    }

    task.decCounter();
  }

  @Override
  public void onGameStart() {
    // Reset on game end
  }

  @Override
  public boolean onPlayerJoins(Player player) {
    if (this.getGame().isFull() && !player.hasPermission("bw.vip.joinfull")) {
      if (this.getGame().getState() != GameState.RUNNING
          || !BedwarsPRO.getInstance().spectationEnabled()) {
        player.sendMessage(
            ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "lobby.gamefull")));
        return false;
      }
    } else if (this.getGame().isFull() && player.hasPermission("bw.vip.joinfull")) {
      if (this.getGame().getState() == GameState.WAITING) {
        List<Player> players = this.getGame().getNonVipPlayers();

        if (players.size() == 0) {
          player.sendMessage(
              ChatWriter
                  .pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "lobby.gamefullpremium")));
          return false;
        }

        Player kickPlayer = null;
        if (players.size() == 1) {
          kickPlayer = players.get(0);
        } else {
          kickPlayer = players.get(Utils.randInt(0, players.size() - 1));
        }

        kickPlayer
            .sendMessage(
                ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
                    ._l(kickPlayer, "lobby.kickedbyvip")));
        this.getGame().playerLeave(kickPlayer, false);
      } else {
        if (this.getGame().getState() == GameState.RUNNING
            && !BedwarsPRO.getInstance().spectationEnabled()) {
          player.sendMessage(
              ChatWriter
                  .pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.cantjoingame")));
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public void onPlayerLeave(Player player) {
    // teleport to join location
    PlayerStorage storage = this.getGame().getPlayerStorage(player);

    // 优先传送到主大厅
    Location mainLobby = this.getGame().getMainLobby();
    if (mainLobby != null) {
      if (BedwarsPRO.getInstance().isHologramsEnabled()
          && BedwarsPRO.getInstance().getHolographicInteractor() != null
          && mainLobby.getWorld() == player.getWorld()) {
        BedwarsPRO.getInstance().getHolographicInteractor().updateHolograms(player);
      }
      player.teleport(mainLobby);
    } else if (BedwarsPRO.getInstance().toMainLobby()) {
      if (storage != null && storage.getLeft() != null) {
        player.teleport(storage.getLeft());
      } else if (this.getGame().getLobby() != null) {
        player.teleport(this.getGame().getLobby());
      }
    } else {
      if (storage != null && storage.getLeft() != null) {
        if (BedwarsPRO.getInstance().isHologramsEnabled()
            && BedwarsPRO.getInstance().getHolographicInteractor() != null
            && storage.getLeft().getWorld() == player.getWorld()) {
          BedwarsPRO.getInstance().getHolographicInteractor().updateHolograms(player);
        }
        player.teleport(storage.getLeft());
      } else if (this.getGame().getLobby() != null) {
        player.teleport(this.getGame().getLobby());
      }
    }

    if (this.getGame().getState() == GameState.RUNNING && !this.getGame().isStopping()) {
      this.checkGameOver();
    }
  }

}
