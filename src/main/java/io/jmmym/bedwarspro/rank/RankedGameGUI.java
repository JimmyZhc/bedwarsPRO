package io.jmmym.bedwarspro.rank;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameCheckCode;
import io.jmmym.bedwarspro.game.GameState;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 排位图管理界面（/bwpro mapgui）。
 *
 * <p>列出当前所有「等待中且可用」的游戏，左键点击即可切换该图为排位图 / 休闲图，
 * 配置持久化到 rank.yml 的 ranked-games 列表（见 {@link RankManager#setRankedGame(String, boolean)}）。
 * 地图较多时分页展示（每页 {@link #MAX_ITEMS} 张）。</p>
 */
public class RankedGameGUI implements InventoryHolder {

  public static final int SIZE = 54;
  /** 每页可展示的图数量（前 45 格）。 */
  public static final int MAX_ITEMS = 45;
  /** 上一页按钮槽位。 */
  public static final int PAGE_PREV = 45;
  /** 下一页按钮槽位。 */
  public static final int PAGE_NEXT = 53;

  private final Inventory inventory;
  private final int page;
  private final List<Game> games;

  public RankedGameGUI(int page) {
    this.page = Math.max(0, page);
    this.games = collectGames();
    this.inventory = Bukkit.createInventory(this, SIZE,
        ChatColor.DARK_GRAY + "地图管理 (第 " + (this.page + 1) + " 页)");
    populate();
  }

  /** 收集当前所有等待中且配置合法（可用）的游戏。 */
  private List<Game> collectGames() {
    List<Game> result = new ArrayList<>();
    for (Game g : BedwarsPRO.getInstance().getGameManager().getGames()) {
      if (g.getState() == GameState.WAITING && g.checkGame() == GameCheckCode.OK) {
        result.add(g);
      }
    }
    return result;
  }

  private void populate() {
    // 分隔玻璃板铺底
    for (int slot = 0; slot < SIZE; slot++) {
      inventory.setItem(slot, separator());
    }

    int start = this.page * RankedGameGUI.MAX_ITEMS;
    int end = Math.min(start + RankedGameGUI.MAX_ITEMS, this.games.size());
    for (int i = start; i < end; i++) {
      inventory.setItem(i - start, buildGameItem(this.games.get(i)));
    }

    // 翻页按钮
    if (this.page > 0) {
      inventory.setItem(RankedGameGUI.PAGE_PREV, pageButton("上一页"));
    }
    if (end < this.games.size()) {
      inventory.setItem(RankedGameGUI.PAGE_NEXT, pageButton("下一页"));
    }
  }

  /** 游戏按钮：排位图 → 钻石剑（左键切为休闲）；休闲图 → 纸（左键切为排位）。 */
  private ItemStack buildGameItem(Game game) {
    RankManager rm = RankManager.getInstance();
    boolean ranked = rm != null && rm.isRankedGame(game.getName());
    // 休闲图用 PAPER 而非 MAP：1.8.8/1.12.2 客户端对无地图数据的 MAP 物品会显示 "unknown map"
    ItemStack item = new ItemStack(ranked ? Material.DIAMOND_SWORD : Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName((ranked ? ChatColor.RED : ChatColor.GREEN) + game.getName());
    List<String> lore = new ArrayList<>();
    lore.add((ranked ? ChatColor.RED : ChatColor.GRAY) + "当前: "
        + (ranked ? "排位图" : "休闲图"));
    lore.add(ChatColor.GRAY + "人数: " + game.getPlayers().size());
    lore.add(ChatColor.YELLOW + "左键点击: " + (ranked ? "切换为休闲图" : "切换为排位图"));
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack pageButton(String name) {
    ItemStack item = new ItemStack(Material.ARROW);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(ChatColor.YELLOW + name);
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack separator() {
    ItemStack sep = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
    ItemMeta meta = sep.getItemMeta();
    meta.setDisplayName(" ");
    sep.setItemMeta(meta);
    return sep;
  }

  public int getPage() {
    return this.page;
  }

  /** 槽位对应的游戏（越界 / 空格返回 null）。 */
  public Game getGameAt(int slot) {
    int index = this.page * RankedGameGUI.MAX_ITEMS + slot;
    if (slot < 0 || slot >= RankedGameGUI.MAX_ITEMS || index < 0 || index >= this.games.size()) {
      return null;
    }
    return this.games.get(index);
  }

  @Override
  public Inventory getInventory() {
    return this.inventory;
  }

  public static boolean isGameGUI(Inventory inv) {
    return inv != null && inv.getHolder() instanceof RankedGameGUI;
  }

  public static void open(Player player, int page) {
    if (player == null) {
      return;
    }
    player.openInventory(new RankedGameGUI(page).getInventory());
  }
}
