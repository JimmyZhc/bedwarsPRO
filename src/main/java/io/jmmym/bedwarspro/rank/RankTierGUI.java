package io.jmmym.bedwarspro.rank;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 段位进度界面（奶桶表示每个 ELO 段位）。
 *
 * <p>共 15 个段位，每个段位用一个奶桶表示：
 * <ul>
 *   <li>空桶（{@link Material#BUCKET}）＝ 已达成该段位（ELO 达到所需分数）；</li>
 *   <li>奶桶（{@link Material#MILK_BUCKET}）＝ 尚未达成该段位。</li>
 * </ul>
 * 顶部展示玩家当前段位 / ELO / 距下一段位所需分数。
 * 界面文本全部来自 rank/messages.yml 的 tiergui 段，可自由配置。</p>
 */
public class RankTierGUI implements InventoryHolder {

  /** 第 1 行（0-8）：第 5 格为玩家段位信息横幅。 */
  public static final int INFO_SLOT = 4;
  /** 第 2 行（9-17）：前 9 个段位。 */
  private static final int[] BUCKET_SLOTS = {
      9, 10, 11, 12, 13, 14, 15, 16, 17,
      18, 19, 20, 21, 22, 23
  };
  public static final int SIZE = 36;

  private final Inventory inventory;

  public RankTierGUI(RankPlayer rp) {
    this.inventory = Bukkit.createInventory(this, SIZE, RankMessages.get("tiergui.title"));
    populate(rp);
  }

  private void populate(RankPlayer rp) {
    // 铺底玻璃板
    for (int slot = 0; slot < SIZE; slot++) {
      inventory.setItem(slot, separator());
    }
    // 玩家段位信息横幅
    inventory.setItem(INFO_SLOT, buildInfo(rp));
    // 15 个段位奶桶：第 2 行 9 个 + 第 3 行 6 个
    RankTier[] tiers = RankTier.values();
    for (int i = 0; i < tiers.length && i < BUCKET_SLOTS.length; i++) {
      inventory.setItem(BUCKET_SLOTS[i], buildBucket(tiers[i], rp));
    }
  }

  /** 段位奶桶：空桶 = 已达成，奶桶 = 未达成。 */
  private ItemStack buildBucket(RankTier tier, RankPlayer rp) {
    boolean reached = rp.getElo() >= tier.getRequiredElo();
    Material mat = reached ? Material.BUCKET : Material.MILK_BUCKET;
    String name = reached
        ? RankMessages.get("tiergui.bucket-reached-name", "tier", tier.getCnName())
        : RankMessages.get("tiergui.bucket-unreached-name", "tier", tier.getCnName());
    List<String> lore = reached
        ? RankMessages.getList("tiergui.bucket-reached-lore",
            "tier", tier.getCnName(), "elo", tier.getRequiredElo())
        : RankMessages.getList("tiergui.bucket-unreached-lore",
            "tier", tier.getCnName(), "elo", tier.getRequiredElo());
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(name);
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }

  /** 玩家段位信息横幅：当前段位 / ELO / 距下一段位。 */
  private ItemStack buildInfo(RankPlayer rp) {
    ItemStack item = new ItemStack(Material.COMPASS);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(RankMessages.get("tiergui.info-name", "player", rp.getName()));
    List<String> lore = RankMessages.getList("tiergui.info-lore",
        "player", rp.getName(), "tier", rp.getTier().getCnName(),
        "elo", rp.getElo(), "highest", rp.getHighestElo(),
        "next-hint", nextTierHint(rp));
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }

  /** 距下一段位的提示文本（已达最高段位时显示完成提示）。 */
  private String nextTierHint(RankPlayer rp) {
    RankTier cur = rp.getTier();
    RankTier next = null;
    boolean found = false;
    for (RankTier tier : RankTier.values()) {
      if (tier == cur) {
        found = true;
        continue;
      }
      if (found) {
        next = tier;
        break;
      }
    }
    if (next == null) {
      return RankMessages.get("tiergui.at-max");
    }
    int need = Math.max(0, next.getRequiredElo() - rp.getElo());
    return RankMessages.get("tiergui.next-need", "next", next.getCnName(), "need", need);
  }

  private ItemStack separator() {
    ItemStack sep = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
    ItemMeta meta = sep.getItemMeta();
    meta.setDisplayName(RankMessages.get("tiergui.separator"));
    sep.setItemMeta(meta);
    return sep;
  }

  @Override
  public Inventory getInventory() {
    return this.inventory;
  }

  public static boolean isTierGUI(Inventory inv) {
    return inv != null && inv.getHolder() instanceof RankTierGUI;
  }

  public static void open(Player player, RankPlayer rp) {
    if (player == null || rp == null) {
      return;
    }
    player.openInventory(new RankTierGUI(rp).getInventory());
  }
}
