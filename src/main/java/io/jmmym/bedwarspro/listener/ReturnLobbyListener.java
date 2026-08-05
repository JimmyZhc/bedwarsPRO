package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 游戏结束菜单管理器。
 * 第9格：菜单（下界之星）— 右键打开菜单
 * 菜单内：返回大厅 / 再来一次
 */
public class ReturnLobbyListener implements Listener {

  private static ReturnLobbyListener instance = null;

  private static final String MENU_ITEM_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "游戏菜单";
  private static final String MENU_TITLE = ChatColor.DARK_GRAY + "游戏菜单";
  private static final String LOBBY_OPTION_NAME = ChatColor.GREEN + "返回大厅";
  private static final String REJOIN_OPTION_NAME = ChatColor.AQUA + "再来一次";

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

  public static boolean isMenuItem(ItemStack item) {
    if (item == null || item.getType() != Material.NETHER_STAR) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasDisplayName()) return false;
    return meta.getDisplayName().equals(MENU_ITEM_NAME);
  }

  private ItemStack createMenuItem() {
    ItemStack item = new ItemStack(Material.NETHER_STAR);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(MENU_ITEM_NAME);
    List<String> lore = new ArrayList<>();
    lore.add(ChatColor.GRAY + "右键打开菜单");
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }

  /** 给玩家菜单物品（放在第9格） */
  public void giveReturnLobbySlimeBalls(Player player) {
    player.getInventory().clear();
    player.getInventory().setItem(8, createMenuItem());
    player.updateInventory();
  }

  public void giveReturnLobbySlimeBallsToGamePlayers(Game game) {
    for (Player player : game.getPlayers()) {
      giveReturnLobbySlimeBalls(player);
    }
  }

  /** 打开菜单GUI */
  private void openMenu(Player player) {
    Inventory menu = Bukkit.createInventory(null, 27, MENU_TITLE);

    // 装饰玻璃板
    ItemStack glass = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
    ItemMeta glassMeta = glass.getItemMeta();
    glassMeta.setDisplayName(" ");
    glass.setItemMeta(glassMeta);
    for (int i = 0; i < 27; i++) {
      menu.setItem(i, glass);
    }

    // 返回大厅（左）- 红色羊毛 data=14
    ItemStack lobbyItem = new ItemStack(Material.WOOL, 1, (short) 14);
    ItemMeta lobbyMeta = lobbyItem.getItemMeta();
    lobbyMeta.setDisplayName(LOBBY_OPTION_NAME);
    List<String> lobbyLore = new ArrayList<>();
    lobbyLore.add(ChatColor.GRAY + "右键返回游戏大厅");
    lobbyMeta.setLore(lobbyLore);
    lobbyItem.setItemMeta(lobbyMeta);
    menu.setItem(11, lobbyItem);

    // 再来一次（右）- 浅绿羊毛 data=5
    ItemStack rejoinItem = new ItemStack(Material.WOOL, 1, (short) 5);
    ItemMeta rejoinMeta = rejoinItem.getItemMeta();
    rejoinMeta.setDisplayName(REJOIN_OPTION_NAME);
    List<String> rejoinLore = new ArrayList<>();
    rejoinLore.add(ChatColor.GRAY + "右键重新加入游戏");
    rejoinMeta.setLore(rejoinLore);
    rejoinItem.setItemMeta(rejoinMeta);
    menu.setItem(15, rejoinItem);

    player.openInventory(menu);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerInteract(PlayerInteractEvent event) {
    Action action = event.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player player = event.getPlayer();
    ItemStack item = event.getItem();

    if (isMenuItem(item)) {
      event.setCancelled(true);
      openMenu(player);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    Player player = (Player) event.getWhoClicked();

    // 防止菜单物品被移动
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game != null) {
      ItemStack current = event.getCurrentItem();
      ItemStack cursor = event.getCursor();
      if (isMenuItem(current) || isMenuItem(cursor)) {
        event.setCancelled(true);
        return;
      }
    }

    // 处理菜单内点击
    if (event.getView().getTitle().equals(MENU_TITLE)) {
      event.setCancelled(true);
      int slot = event.getRawSlot();
      if (slot < 0 || slot >= 27) return;

      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || clicked.getType() == Material.AIR) return;

      if (clicked.getType() == Material.WOOL && clicked.getDurability() == 14) {
        // 返回大厅
        player.closeInventory();
        handleLobbyReturn(player);
      } else if (clicked.getType() == Material.WOOL && clicked.getDurability() == 5) {
        // 再来一次
        player.closeInventory();
        handleRejoin(player);
      }
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    // 关闭菜单时不做特殊处理
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    Player player = (Player) event.getWhoClicked();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game == null) return;

    ItemStack oldCursor = event.getCursor();
    if (isMenuItem(oldCursor)) {
      event.setCancelled(true);
    }
  }

  private void handleLobbyReturn(Player player) {
    String playerName = player.getName();
    if (pendingTeleports.containsKey(playerName)) {
      cancelPendingTeleport(player, playerName);
      return;
    }
    startCountdown(player, playerName);
  }

  private void handleRejoin(Player player) {
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    if (game == null) {
      game = BedwarsPRO.getInstance().getGameManager().getGameByLocation(player.getLocation());
    }
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "无法重新加入：未找到游戏"));
      return;
    }
    if (game.getState() != GameState.WAITING) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "游戏已开始，无法加入"));
      return;
    }
    player.getInventory().clear();
    if (game.playerJoins(player)) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "已重新加入游戏！"));
    } else {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "加入游戏失败"));
    }
  }

  private void startCountdown(final Player player, final String playerName) {
    cancelPendingTeleport(player, playerName);
    final int[] remainingSeconds = {3};
    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + "3秒后返回大厅，再次右键菜单取消"));

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
        try {
          player.sendTitle("", ChatColor.YELLOW.toString() + remainingSeconds[0] + "秒后返回大厅");
        } catch (Exception e) {
          player.sendMessage(ChatColor.YELLOW + String.valueOf(remainingSeconds[0]) + "秒后返回大厅");
        }
        remainingSeconds[0]--;
      }
    };

    int taskId = task.runTaskTimer(BedwarsPRO.getInstance(), 20L, 20L).getTaskId();
    pendingTeleports.put(playerName, task);
    countdownTasks.put(playerName, taskId);
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
      try {
        player.sendTitle("", ChatColor.RED + "已取消返回大厅");
      } catch (Exception e) {
        player.sendMessage(ChatColor.RED + "已取消返回大厅");
      }
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
      }
    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "传送失败：" + e.getMessage()));
    }
  }

  public void cleanup(String playerName) {
    cancelPendingTeleport(null, playerName);
  }
}
