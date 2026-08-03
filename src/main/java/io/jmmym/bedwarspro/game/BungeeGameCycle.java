package io.jmmym.bedwarspro.game;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameEndEvent;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class BungeeGameCycle extends GameCycle {

  public BungeeGameCycle(Game game) {
    super(game);
  }

  public void bungeeSendToServer(final String server, final Player player, boolean preventDelay) {
    if (server == null) {
      player
          .sendMessage(
              ChatWriter
                  .pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.bungeenoserver")));
      return;
    }

    new BukkitRunnable() {

      @Override
      public void run() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);

        try {
          out.writeUTF("Connect");
          out.writeUTF(server);
        } catch (Exception e) {
          BedwarsPRO.getInstance().getBugsnag().notify(e);
          e.printStackTrace();
          return;
        }

        if (b != null) {
          player.sendPluginMessage(BedwarsPRO.getInstance(), "BungeeCord", b.toByteArray());
        }
      }
    }.runTaskLater(BedwarsPRO.getInstance(), (preventDelay) ? 0L : 20L);
  }

  private void kickAllPlayers() {
    for (Player player : this.getGame().getTeamPlayers()) {
      for (Player freePlayer : this.getGame().getFreePlayers()) {
        player.showPlayer(freePlayer);
      }
      this.getGame().playerLeave(player, false);
    }

    for (Player freePlayer : this.getGame().getFreePlayersClone()) {
      this.getGame().playerLeave(freePlayer, false);
    }
  }

  @Override
  public void onGameEnds() {
    if (BedwarsPRO.getInstance().getBooleanConfig("bungeecord.full-restart", true)) {
      this.kickAllPlayers();

      this.getGame().resetRegion();
      new BukkitRunnable() {

        @Override
        public void run() {
          if (BedwarsPRO.getInstance().isSpigot()
              && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.spigot-restart", true)) {
            BedwarsPRO.getInstance().getServer()
                .dispatchCommand(BedwarsPRO.getInstance().getServer().getConsoleSender(),
                    "restart");
          } else {
            Bukkit.shutdown();
          }
        }
      }.runTaskLater(BedwarsPRO.getInstance(), 70L);
    } else {
      // Reset scoreboard first
      this.getGame().resetScoreboard();

      // Kick all players
      this.kickAllPlayers();

      // reset countdown prevention breaks
      this.setEndGameRunning(false);

      // Reset team chests
      for (Team team : this.getGame().getTeams().values()) {
        team.setInventory(null);
        team.getChests().clear();
      }

      // clear protections
      this.getGame().clearProtections();

      // set state and with that, the sign
      this.getGame().setState(GameState.WAITING);
      this.getGame().updateScoreboard();

      // reset region
      this.getGame().resetRegion();
    }
  }

  @Override
  public void onGameLoaded() {
    // Reset on game end
  }

  @Override
  public void onGameOver(GameOverTask task) {
    if (BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true)) {
      final ArrayList<Player> players = new ArrayList<Player>();
      final Game game = this.getGame();
      players.addAll(this.getGame().getTeamPlayers());
      players.addAll(this.getGame().getFreePlayers());
      
      // 确定目标大厅位置
      Location targetLobby = null;
      if (BedwarsPRO.getInstance().toMainLobby() && game.getMainLobby() != null) {
        targetLobby = game.getMainLobby();
      } else {
        targetLobby = game.getLobby();
      }
      
      if (targetLobby != null) {
        final Location finalTargetLobby = targetLobby;
        for (Player player : players) {
          if (!player.getWorld().equals(finalTargetLobby.getWorld())) {
            game.getPlayerSettings(player).setTeleporting(true);
            player.teleport(finalTargetLobby);
            game.getPlayerStorage(player).clean();
          }
        }
      }

      new BukkitRunnable() {
        @Override
        public void run() {
          for (Player player : players) {
            game.setPlayerGameMode(player);
            game.setPlayerVisibility(player);

            if (!player.getInventory().contains(Material.SLIME_BALL)) {
              // Leave game (Slimeball)
              ItemStack leaveGame = new ItemStack(Material.SLIME_BALL, 1);
              ItemMeta im = leaveGame.getItemMeta();
              im.setDisplayName(BedwarsPRO._l(player, "lobby.leavegame"));
              leaveGame.setItemMeta(im);
              player.getInventory().setItem(8, leaveGame);
              player.updateInventory();
            }
          }
        }
      }.runTaskLater(BedwarsPRO.getInstance(), 20L);
    }
    if (task.getCounter() == task.getStartCount() && task.getWinner() != null) {
      for (Player aPlayer : this.getGame().getPlayers()) {
        if (aPlayer.isOnline()) {
          aPlayer.sendMessage(
              ChatWriter.pluginMessage(ChatColor.GOLD + BedwarsPRO._l(aPlayer, "ingame.teamwon",
                  ImmutableMap.of("team", task.getWinner().getDisplayName() + ChatColor.GOLD))));
        }
      }
    } else if (task.getCounter() == task.getStartCount() && task.getWinner() == null) {
      for (Player aPlayer : this.getGame().getPlayers()) {
        if (aPlayer.isOnline()) {
          aPlayer.sendMessage(
              ChatWriter.pluginMessage(ChatColor.GOLD + BedwarsPRO._l(aPlayer, "ingame.draw")));
        }
      }
    }

    // game over
    if (this.getGame().getPlayers().size() == 0 || task.getCounter() == 0) {
      BedwarsGameEndEvent endEvent = new BedwarsGameEndEvent(this.getGame());
      BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(endEvent);

      this.onGameEnds();
      task.cancel();
    } else if ((task.getCounter() == task.getStartCount()) || (task.getCounter() % 10 == 0)
        || (task.getCounter() <= 5 && (task.getCounter() > 0))) {
      for (Player aPlayer : this.getGame().getPlayers()) {
        if (aPlayer.isOnline()) {
          aPlayer.sendMessage(ChatWriter
              .pluginMessage(ChatColor.AQUA + BedwarsPRO
                  ._l(aPlayer, "ingame.serverrestart", ImmutableMap
                      .of("sec",
                          ChatColor.YELLOW.toString() + task.getCounter() + ChatColor.AQUA))));
        }
      }
    }

    task.decCounter();
  }

  @Override
  public void onGameStart() {
    // do nothing, world will be reseted on restarting
  }

  @Override
  public boolean onPlayerJoins(Player player) {
    final Player p = player;

    if (this.getGame().isFull() && !player.hasPermission("bw.vip.joinfull")) {
      if (this.getGame().getState() != GameState.RUNNING
          || !BedwarsPRO.getInstance().spectationEnabled()) {
        this.bungeeSendToServer(BedwarsPRO.getInstance().getBungeeHub(), p, false);
        new BukkitRunnable() {

          @Override
          public void run() {
            BungeeGameCycle.this.sendBungeeMessage(p,
                ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(p, "lobby.gamefull")));
          }
        }.runTaskLater(BedwarsPRO.getInstance(), 60L);

        return false;
      }
    } else if (this.getGame().isFull() && player.hasPermission("bw.vip.joinfull")) {
      if (this.getGame().getState() == GameState.WAITING) {
        List<Player> players = this.getGame().getNonVipPlayers();

        if (players.size() == 0) {
          this.bungeeSendToServer(BedwarsPRO.getInstance().getBungeeHub(), p, false);
          new BukkitRunnable() {

            @Override
            public void run() {
              BungeeGameCycle.this.sendBungeeMessage(p,
                  ChatWriter
                      .pluginMessage(ChatColor.RED + BedwarsPRO._l(p, "lobby.gamefullpremium")));
            }
          }.runTaskLater(BedwarsPRO.getInstance(), 60L);
          return false;
        }

        Player kickPlayer = null;
        if (players.size() == 1) {
          kickPlayer = players.get(0);
        } else {
          kickPlayer = players.get(Utils.randInt(0, players.size() - 1));
        }

        final Player kickedPlayer = kickPlayer;

        this.getGame().playerLeave(kickedPlayer, false);
        new BukkitRunnable() {

          @Override
          public void run() {
            BungeeGameCycle.this.sendBungeeMessage(kickedPlayer,
                ChatWriter
                    .pluginMessage(
                        ChatColor.RED + BedwarsPRO._l(kickedPlayer, "lobby.kickedbyvip")));
          }
        }.runTaskLater(BedwarsPRO.getInstance(), 60L);
      } else {
        if (this.getGame().getState() == GameState.RUNNING
            && !BedwarsPRO.getInstance().spectationEnabled()) {

          new BukkitRunnable() {

            @Override
            public void run() {
              BungeeGameCycle.this
                  .bungeeSendToServer(BedwarsPRO.getInstance().getBungeeHub(), p, false);
            }

          }.runTaskLater(BedwarsPRO.getInstance(), 5L);

          new BukkitRunnable() {

            @Override
            public void run() {
              BungeeGameCycle.this.sendBungeeMessage(p,
                  ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(p, "lobby.gamefull")));
            }
          }.runTaskLater(BedwarsPRO.getInstance(), 60L);
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public void onPlayerLeave(Player player) {
    if (player.isOnline() || player.isDead()) {
      this.bungeeSendToServer(BedwarsPRO.getInstance().getBungeeHub(), player, true);
    }

    if (this.getGame().getState() == GameState.RUNNING && !this.getGame().isStopping()) {
      this.checkGameOver();
    }
  }

  public void sendBungeeMessage(Player player, String message) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();

    out.writeUTF("Message");
    out.writeUTF(player.getName());
    out.writeUTF(message);

    player.sendPluginMessage(BedwarsPRO.getInstance(), "BungeeCord", out.toByteArray());
  }

}
