package io.jmmym.bedwarspro.xp;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameEndEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.utils.SoundMachine;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 经验起床（XP Bedwars）监听器：
 * 经验模式对局中捡起资源自动转换为经验；死亡按配置比例扣除经验；对局结束清理数据。
 */
public class XpListener implements Listener {

  public XpListener() {
    BedwarsPRO.getInstance().getServer().getPluginManager().registerEvents(this, BedwarsPRO.getInstance());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPickup(PlayerPickupItemEvent event) {
    Player player = event.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game == null || !XpManager.isXpMode(game)) {
      return;
    }
    if (game.getState() != GameState.RUNNING || game.isSpectator(player)) {
      return;
    }

    ItemStack item = event.getItem().getItemStack();
    int xp = XpManager.itemXp(item);
    if (xp <= 0) {
      return;
    }

    // 资源不进入背包，直接转换为经验
    event.setCancelled(true);
    event.getItem().remove();

    int maxXp = BedwarsPRO.getInstance().getIntConfig("xp-bedwars.max-xp", 999);
    if (maxXp > 0 && XpManager.getXp(game, player) >= maxXp) {
      player.sendMessage(ChatColor.GOLD + "[经验起床] " + ChatColor.RED + "经验已满！");
      return;
    }

    XpManager.addXp(game, player, xp);
    try {
      player.playSound(player.getLocation(), SoundMachine.get("ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP"), 0.2f, 1.5f);
    } catch (Exception ignored) {
    }
    // 经验显示在经验条上方（ActionBar）
    io.jmmym.bedwarspro.itemaddon.utils.Utils.sendPlayerActionbar(player,
        ChatColor.GREEN + "你获得了 " + ChatColor.YELLOW + xp + ChatColor.GREEN + " 点经验");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game == null || !XpManager.isXpMode(game)) {
      return;
    }
    if (game.getState() != GameState.RUNNING || game.isSpectator(player)) {
      return;
    }
    double ratio = BedwarsPRO.getInstance().getConfig().getDouble("xp-bedwars.death-cost", 0.0);
    if (ratio <= 0) {
      return;
    }
    int cur = XpManager.getXp(game, player);
    int lose = (int) Math.floor(cur * Math.min(1.0, ratio));
    if (lose <= 0) {
      return;
    }
    XpManager.setXp(game, player, cur - lose);
    player.sendMessage(ChatColor.GOLD + "[经验起床] " + ChatColor.RED + "你失去了 " + lose + " 经验");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onGameEnd(BedwarsGameEndEvent event) {
    Game game = event.getGame();
    if (game == null) {
      return;
    }
    // 经验模式对局结束：把等级数字 / 经验条复位，避免残留在对局外
    if (XpManager.isXpMode(game)) {
      for (Player p : game.getPlayers()) {
        if (p != null && p.isOnline()) {
          XpManager.syncVanillaXp(p, 0);
        }
      }
    }
    XpManager.clearGame(game);
  }
}
