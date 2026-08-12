package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 返回大厅粘液球管理器。
 *
 * <p>功能：当玩家被最终击杀（队伍床已毁且死亡）或游戏结束时，将物品栏的一行替换为粘液球。
 * 右键粘液球启动3秒倒计时返回大厅，再次右键取消。</p>
 */
public class ReturnLobbyListener implements Listener {

  private static ReturnLobbyListener instance = null;

  private static final String SLIME_BALL_NAME = ChatColor.YELLOW + "返回大厅";
  private static final String SLIME_BALL_LORE = ChatColor.GRAY + "右键3秒后返回大厅，再次右键取消";

  public ReturnLobbyListener() {
    if (instance == null) {
      instance = this;
      BedwarsPRO.getInstance().getServer().getPluginManager()
          .registerEvents(this, BedwarsPRO.getInstance());
    }
  }

  public static ReturnLobbyListener getInstance() {
    if (instance == null) {
      instance = new ReturnLobbyListener();
    }
    return instance;
  }

  private final Map<String, BukkitRunnable> pendingTeleports = new HashMap<>();
  private final Map<String, Integer> countdownTasks = new HashMap<>();

  /** 检查ItemStack是否为返回大厅粘液球 */
  public static boolean isReturnLobbyItem(ItemStack item) {
    if (item == null || item.getType() != Material.SLIME_BALL) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasDisplayName()) return false;
    return meta.getDisplayName().equals(SLIME_BALL_NAME);
  }

  /** 创建返回大厅粘液球 */
  private ItemStack createSlimeBall() {
    ItemStack slimeBall = new ItemStack(Material.SLIME_BALL);
    ItemMeta meta = slimeBall.getItemMeta();
    meta.setDisplayName(SLIME_BALL_NAME);
    List<String> lore = new ArrayList<>();
    lore.add(SLIME_BALL_LORE);
    meta.setLore(lore);
    slimeBall.setItemMeta(meta);
    return slimeBall;
  }

  /** 为玩家设置返回大厅粘液球（替换物品栏所有物品） */
  public void giveReturnLobbySlimeBalls(Player player) {
    ItemStack slimeBall = createSlimeBall();
    player.getInventory().clear();
    for (int i = 0; i < 9; i++) {
      player.getInventory().setItem(i, slimeBall);
    }
    player.updateInventory();
  }

  /** 为在线游戏玩家设置返回大厅粘液球 */
  public void giveReturnLobbySlimeBallsToGamePlayers(Game game) {
    for (Player player : game.getPlayers()) {
      giveReturnLobbySlimeBalls(player);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerInteract(PlayerInteractEvent event) {
    Action action = event.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player player = event.getPlayer();
    ItemStack item = event.getItem();

    if (!isReturnLobbyItem(item)) return;

    event.setCancelled(true);

    String playerName = player.getName();

    if (pendingTeleports.containsKey(playerName)) {
      cancelPendingTeleport(player, playerName);
      return;
    }

    startCountdown(player, playerName);
  }

  private void startCountdown(final Player player, final String playerName) {
    cancelPendingTeleport(player, playerName);

    final int[] remainingSeconds = {3};

    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "3秒后返回大厅，再次右键取消"));

    BukkitRunnable task = new BukkitRunnable() {
      @Override
      public void run() {
        if (!player.isOnline()) {
          cancelPendingTeleport(player, playerName);
          return;
        }

        if (remainingSeconds[0] <= 0) {
          teleportToLobby(player);
          cancelPendingTeleport(player, playerName);
          return;
        }

        sendActionBar(player, ChatColor.YELLOW.toString() + remainingSeconds[0] + "秒后返回大厅");
        remainingSeconds[0]--;
      }
    };

    int taskId = task.runTaskTimer(BedwarsPRO.getInstance(), 20L, 20L).getTaskId();
    pendingTeleports.put(playerName, task);
    countdownTasks.put(playerName, taskId);
  }

  private void sendActionBar(Player player, String message) {
    try {
      player.sendTitle("", message);
    } catch (Exception e) {
      player.sendMessage(message);
    }
  }

  private void cancelPendingTeleport(Player player, String playerName) {
    BukkitRunnable task = pendingTeleports.remove(playerName);
    if (task != null) {
      task.cancel();
    }
    Integer taskId = countdownTasks.remove(playerName);
    if (taskId != null) {
      Bukkit.getScheduler().cancelTask(taskId);
    }
    if (player != null && player.isOnline()) {
      sendActionBar(player, ChatColor.RED + "已取消返回大厅");
    }
  }

  private void teleportToLobby(Player player) {
    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "正在返回大厅..."));
    player.getInventory().clear();

    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "无法返回大厅：未找到游戏"));
      return;
    }

    try {
      if (game.getMainLobby() != null) {
        player.teleport(game.getMainLobby());
      } else if (game.getLobby() != null) {
        player.teleport(game.getLobby());
      } else {
        player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "大厅未设置，无法传送"));
        return;
      }

      // 单服模式下正确退出游戏并清理游戏状态，
      // 避免倒计时结束后 onGameEnds 仍统计到该玩家并显示游戏统计
      if (!BedwarsPRO.getInstance().isBungee()) {
        game.playerLeave(player, false);
      }
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "传送失败：" + e.getMessage()));
    }
  }

  /** 清理玩家的待传送任务 */
  public void cleanup(String playerName) {
    cancelPendingTeleport(null, playerName);
  }
}
