package io.jmmym.bedwarspro.shop;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.SoundMachine;
import io.jmmym.bedwarspro.utils.Utils;
import io.jmmym.bedwarspro.villager.MerchantCategory;
import io.jmmym.bedwarspro.villager.VillagerTrade;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

public class NewItemShop {

  private List<MerchantCategory> categories = null;
  private MerchantCategory currentCategory = null;
  private static final String BINDING_LORE = "§b绑定护甲";

  public NewItemShop(List<MerchantCategory> categories) {
    this.categories = categories;
  }

  @SuppressWarnings("deprecation")
  private void addCategoriesToInventory(Inventory inventory, Player player, Game game) {
    for (MerchantCategory category : this.categories) {
      if (category.getMaterial() == null) {
        BedwarsPRO.getInstance().getServer().getConsoleSender()
                .sendMessage(ChatWriter.pluginMessage(ChatColor.RED
                        + "Careful: Not supported material in shop category '" + category.getName() + "'"));
        continue;
      }
      if (player != null && !player.hasPermission(category.getPermission())) continue;
      ItemStack is = new ItemStack(category.getMaterial(), 1);
      ItemMeta im = is.getItemMeta();
      if (Utils.isColorable(is)) {
        is.setDurability(game.getPlayerTeam(player).getColor().getDyeColor().getWoolData());
      }
      if (this.currentCategory != null && this.currentCategory.equals(category)) {
        im.addEnchant(Enchantment.DAMAGE_ALL, 1, true);
        im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      }
      im.setDisplayName(category.getName());
      im.setLore(category.getLores());
      im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
      is.setItemMeta(im);
      inventory.addItem(is);
    }
  }

  private boolean isLeggings(Material material) {
    return material == Material.CHAINMAIL_LEGGINGS ||
            material == Material.IRON_LEGGINGS ||
            material == Material.GOLD_LEGGINGS ||
            material == Material.DIAMOND_LEGGINGS ||
            material == Material.LEATHER_LEGGINGS;
  }

  private Material getBootsForLeggings(Material leggingsMaterial) {
    switch (leggingsMaterial) {
      case CHAINMAIL_LEGGINGS: return Material.CHAINMAIL_BOOTS;
      case IRON_LEGGINGS: return Material.IRON_BOOTS;
      case GOLD_LEGGINGS: return Material.GOLD_BOOTS;
      case DIAMOND_LEGGINGS: return Material.DIAMOND_BOOTS;
      case LEATHER_LEGGINGS: return Material.LEATHER_BOOTS;
      default: return null;
    }
  }

  private int getArmorLevel(ItemStack armor) {
    if (armor == null) return 0;
    String name = armor.getType().name();
    if (name.contains("LEATHER")) return 1;
    if (name.contains("GOLD")) return 2;
    if (name.contains("CHAINMAIL")) return 3;
    if (name.contains("IRON")) return 4;
    if (name.contains("DIAMOND")) return 5;
    return 0;
  }

  private int getSwordLevel(ItemStack sword) {
    if (sword == null) return 0;
    String name = sword.getType().name();
    if (name.equals("WOOD_SWORD") || name.equals("WOODEN_SWORD")) return 1;
    if (name.equals("STONE_SWORD")) return 2;
    if (name.equals("IRON_SWORD")) return 3;
    if (name.equals("DIAMOND_SWORD")) return 4;
    return 0;
  }

  private boolean isSword(Material material) {
    String name = material.name();
    return name.equals("WOOD_SWORD") || name.equals("WOODEN_SWORD")
        || name.equals("STONE_SWORD")
        || name.equals("IRON_SWORD")
        || name.equals("DIAMOND_SWORD");
  }

  /** 查找玩家背包中第一个符合条件的剑的槽位 */
  private int findSwordSlot(PlayerInventory inv, int maxHotbarSlot) {
    for (int i = 0; i <= maxHotbarSlot; i++) {
      ItemStack item = inv.getItem(i);
      if (item != null && isSword(item.getType())) {
        return i;
      }
    }
    return -1;
  }

  private boolean equipItem(Player player, ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return false;
    PlayerInventory inv = player.getInventory();
    Material type = item.getType();
    if (isLeggings(type)) {
      ItemStack current = inv.getLeggings();
      if (current != null && getArmorLevel(current) >= getArmorLevel(item)) return false;
      inv.setLeggings(item);
      return true;
    }
    if (type.name().endsWith("_BOOTS")) {
      ItemStack current = inv.getBoots();
      if (current != null && getArmorLevel(current) >= getArmorLevel(item)) return false;
      inv.setBoots(item);
      return true;
    }
    return false;
  }

  private void copyMeta(ItemStack from, ItemStack to) {
    if (from == null || to == null) return;
    ItemMeta fromMeta = from.getItemMeta();
    if (fromMeta == null) return;
    ItemMeta toMeta = to.getItemMeta();
    if (toMeta == null) return;
    if (fromMeta.hasDisplayName()) toMeta.setDisplayName(fromMeta.getDisplayName());
    if (fromMeta.hasLore()) toMeta.setLore(fromMeta.getLore());
    if (fromMeta.hasEnchants()) {
      for (Enchantment ench : fromMeta.getEnchants().keySet()) {
        toMeta.addEnchant(ench, fromMeta.getEnchantLevel(ench), true);
      }
    }
    to.setItemMeta(toMeta);
  }

  private void markAsBound(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;
    List<String> lore = new ArrayList<>();
    lore.add("§r");
    meta.setLore(lore);
    meta.addEnchant(Enchantment.DURABILITY, 10, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    item.setItemMeta(meta);
  }

  private boolean deductResources(Player player, VillagerTrade trade) {
    PlayerInventory inventory = player.getInventory();
    if (!hasEnoughRessource(player, trade)) {
      return false;
    }

    int item1ToPay = trade.getItem1().getAmount();
    Iterator<?> stackIterator = inventory.all(trade.getItem1().getType()).entrySet().iterator();
    int firstItem1 = inventory.first(trade.getItem1());
    if (firstItem1 > -1) {
      inventory.clear(firstItem1);
    } else {
      while (stackIterator.hasNext()) {
        Entry<Integer, ? extends ItemStack> entry = (Entry<Integer, ? extends ItemStack>) stackIterator.next();
        ItemStack stack = entry.getValue();
        int endAmount = stack.getAmount() - item1ToPay;
        if (endAmount < 0) endAmount = 0;
        item1ToPay = item1ToPay - stack.getAmount();
        stack.setAmount(endAmount);
        inventory.setItem(entry.getKey(), stack);
        if (item1ToPay <= 0) break;
      }
    }

    if (trade.getItem2() != null) {
      int item2ToPay = trade.getItem2().getAmount();
      stackIterator = inventory.all(trade.getItem2().getType()).entrySet().iterator();
      int firstItem2 = inventory.first(trade.getItem2());
      if (firstItem2 > -1) {
        inventory.clear(firstItem2);
      } else {
        while (stackIterator.hasNext()) {
          Entry<Integer, ? extends ItemStack> entry = (Entry<Integer, ? extends ItemStack>) stackIterator.next();
          ItemStack stack = entry.getValue();
          int endAmount = stack.getAmount() - item2ToPay;
          if (endAmount < 0) endAmount = 0;
          item2ToPay = item2ToPay - stack.getAmount();
          stack.setAmount(endAmount);
          inventory.setItem(entry.getKey(), stack);
          if (item2ToPay <= 0) break;
        }
      }
    }

    player.updateInventory();
    return true;
  }

  private void refundResources(Player player, VillagerTrade trade) {
    PlayerInventory inventory = player.getInventory();
    if (trade.getItem1() != null) {
      ItemStack refund1 = trade.getItem1().clone();
      inventory.addItem(refund1);
    }
    if (trade.getItem2() != null) {
      ItemStack refund2 = trade.getItem2().clone();
      inventory.addItem(refund2);
    }
    player.updateInventory();
  }

  private int parseRomanNumeral(String text) {
    if (text.contains("V")) return 5;
    if (text.contains("IV")) return 4;
    if (text.contains("III")) return 3;
    if (text.contains("II")) return 2;
    if (text.contains("I")) return 1;
    return 0;
  }

  private void applyUpgradeEffect(Team team, String upgradeType, int level) {
    if (upgradeType.equals("weapon")) {
      Enchantment enchantment = Enchantment.DAMAGE_ALL;
      for (Player member : team.getPlayers()) {
        // 遍历背包所有物品（包括快捷栏和背包）
        for (ItemStack item : member.getInventory().getContents()) {
          if (item != null && item.getType() != Material.AIR) {
            String typeName = item.getType().name();
            if (typeName.contains("SWORD") || typeName.contains("AXE")) {
              if (item.containsEnchantment(enchantment)) {
                item.removeEnchantment(enchantment);
              }
              item.addUnsafeEnchantment(enchantment, level);
              ItemMeta heldMeta = item.getItemMeta();
              if (heldMeta != null) {
                heldMeta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(heldMeta);
              }
            }
          }
        }
        // 同时更新主手（确保当前手持的武器也更新）
        ItemStack held = member.getItemInHand();
        if (held != null && held.getType() != Material.AIR) {
          String typeName = held.getType().name();
          if (typeName.contains("SWORD") || typeName.contains("AXE")) {
            if (held.containsEnchantment(enchantment)) held.removeEnchantment(enchantment);
            held.addUnsafeEnchantment(enchantment, level);
            ItemMeta heldMeta = held.getItemMeta();
            if (heldMeta != null) {
              heldMeta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
              held.setItemMeta(heldMeta);
            }
            member.setItemInHand(held);
          }
        }
      }

    } else if (upgradeType.equals("leggings") || upgradeType.equals("boots")) {
      Enchantment enchantment = Enchantment.PROTECTION_ENVIRONMENTAL;
      for (Player member : team.getPlayers()) {
        ItemStack armor = null;
        if (upgradeType.equals("leggings")) {
          armor = member.getInventory().getLeggings();
        } else {
          armor = member.getInventory().getBoots();
        }
        if (armor != null && armor.getType() != Material.AIR) {
          if (armor.containsEnchantment(enchantment)) armor.removeEnchantment(enchantment);
          armor.addUnsafeEnchantment(enchantment, level);
          ItemMeta armorMeta = armor.getItemMeta();
          if (armorMeta != null) {
            armorMeta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            armor.setItemMeta(armorMeta);
          }
          if (upgradeType.equals("leggings")) {
            member.getInventory().setLeggings(armor);
          } else {
            member.getInventory().setBoots(armor);
          }
        }
      }
    }
  }

  private void processTeamUpgrade(Player player, VillagerTrade trade, Game game) {
    Team team = game.getPlayerTeam(player);
    if (team == null) {
      player.sendMessage(ChatColor.RED + "你不在任何队伍中！");
      return;
    }

    ItemStack rewardItem = trade.getRewardItem();
    if (rewardItem == null || !rewardItem.hasItemMeta()) {
      player.sendMessage(ChatColor.RED + "无法识别升级物品！");
      return;
    }
    String displayName = rewardItem.getItemMeta().getDisplayName();
    if (displayName == null) displayName = "";

    String upgradeType = null;
    int level = 0;

    if (displayName.contains("武器附魔")) {
      upgradeType = "weapon";
      level = parseRomanNumeral(displayName);
    } else if (displayName.contains("护腿保护")) {
      upgradeType = "leggings";
      level = parseRomanNumeral(displayName);
    } else if (displayName.contains("靴子保护")) {
      upgradeType = "boots";
      level = parseRomanNumeral(displayName);
    } else {
      player.sendMessage(ChatColor.RED + "未知的团队升级类型！");
      return;
    }

    if (level <= 0) {
      player.sendMessage(ChatColor.RED + "无法识别升级等级！");
      return;
    }

    int currentLevel = team.getUpgradeLevel(upgradeType);
    if (currentLevel >= level) {
      player.sendMessage(ChatColor.RED + "你的队伍已拥有相同或更高等级的 " + upgradeType + " 升级！");
      return;
    }

    if (!deductResources(player, trade)) {
      player.sendMessage(ChatColor.RED + "灵魂不足！");
      return;
    }

    team.setUpgradeLevel(upgradeType, level);
    applyUpgradeEffect(team, upgradeType, level);
  }

  @SuppressWarnings("unchecked")
  private boolean buyItem(VillagerTrade trade, ItemStack item, Player player, MerchantCategory category) {
    // ---- 检测货币：如果是下界之星 ----
    if (trade.getItem1().getType() == Material.NETHER_STAR) {
      ItemStack reward = trade.getRewardItem();
      // ---- 如果是宠物蛋（怪物蛋），走普通购买流程（不进入团队升级） ----
      if (reward != null && reward.getType() == Material.MONSTER_EGG) {
        // 继续执行普通购买流程
      } else {
        // ---- 否则走团队升级路径 ----
        Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
        processTeamUpgrade(player, trade, game);
        return true;
      }
    }

    // ---- 普通购买流程 ----
    PlayerInventory inventory = player.getInventory();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    ItemStack addingItem = item.clone();
    boolean isLeggings = isLeggings(addingItem.getType());
    boolean equipped = false;

    // 先检查资源是否足够
    if (!deductResources(player, trade)) {
      player.sendMessage(ChatColor.RED + "资源不足！");
      return false;
    }

    if (isLeggings) {
      // ========== 护甲购买限制（不能购买同等级或更低等级） ==========
      int targetLevel = getArmorLevel(addingItem);
      ItemStack currentLeggings = inventory.getLeggings();
      int currentLeggingsLevel = currentLeggings == null ? 0 : getArmorLevel(currentLeggings);
      ItemStack currentBoots = inventory.getBoots();
      int currentBootsLevel = currentBoots == null ? 0 : getArmorLevel(currentBoots);

      if (currentLeggingsLevel >= targetLevel || currentBootsLevel >= targetLevel) {
        // 资源已扣除，需要退还
        refundResources(player, trade);
        player.sendMessage(ChatColor.RED + "你已经拥有更好的护甲，无法购买！");
        return false;
      }
      // ========== 结束 ==========

      // 装备护甲
      markAsBound(addingItem);
      applyTeamArmorEnchantment(addingItem, game, player);
      boolean leggingsEquipped = equipItem(player, addingItem);
      if (leggingsEquipped) equipped = true;

      Material bootsType = getBootsForLeggings(addingItem.getType());
      if (bootsType != null) {
        ItemStack boots = new ItemStack(bootsType, 1);
        copyMeta(addingItem, boots);
        markAsBound(boots);
        applyTeamArmorEnchantment(boots, game, player);
        boolean bootsEquipped = equipItem(player, boots);
        if (!bootsEquipped) {
          inventory.addItem(boots);
        }
      }
    }

    ItemMeta meta = addingItem.getItemMeta();
    List<String> lore = meta.getLore();
    if (lore != null && lore.size() > 0) {
      lore.remove(lore.size() - 1);
      if (trade.getItem2() != null) lore.remove(lore.size() - 1);
    }
    meta.setLore(lore);
    addingItem.setItemMeta(meta);

    if (!isLeggings && addingItem != null) {
      applyTeamWeaponEnchantment(addingItem, game, player);
    }

    if (!isLeggings) {
      // 如果是剑，尝试替换原有剑（同位置）
      if (isSword(addingItem.getType())) {
        int targetSlot = findSwordSlot(inventory, 8);
        int newLevel = getSwordLevel(addingItem);
        if (targetSlot >= 0) {
          ItemStack oldSword = inventory.getItem(targetSlot);
          int oldLevel = getSwordLevel(oldSword);
          if (newLevel > oldLevel) {
            // 替换旧剑
            inventory.setItem(targetSlot, addingItem);
            player.sendMessage(ChatColor.GREEN + "已替换为更高级的剑！");
            player.updateInventory();
            return true;
          } else if (newLevel == oldLevel) {
            refundResources(player, trade);
            player.sendMessage(ChatColor.RED + "你已经拥有同等级的剑，无法购买！");
            return false;
          } else {
            refundResources(player, trade);
            player.sendMessage(ChatColor.RED + "你已经拥有更好的剑，无法购买！");
            return false;
          }
        }
        // 没有旧剑，检查背包空间
        HashMap<Integer, ItemStack> notStored = inventory.addItem(addingItem);
        if (notStored.size() > 0) {
          refundResources(player, trade);
          player.sendMessage(ChatColor.RED + "背包已满，购买失败！");
          return false;
        }
      } else {
        HashMap<Integer, ItemStack> notStored = inventory.addItem(addingItem);
        if (notStored.size() > 0) {
          ItemStack notAddedItem = notStored.get(0);
          int removingAmount = addingItem.getAmount() - notAddedItem.getAmount();
          addingItem.setAmount(removingAmount);
          inventory.removeItem(addingItem);
          player.sendMessage(ChatColor.RED + "背包已满，购买失败！");
          return false;
        }
      }
    } else {
      if (!equipped) {
        inventory.addItem(addingItem);
      }
    }

    player.updateInventory();
    return true;
  }

  private void applyTeamWeaponEnchantment(ItemStack item, Game game, Player player) {
    if (item == null || item.getType() == Material.AIR) return;
    Team team = game.getPlayerTeam(player);
    if (team == null) return;
    int level = team.getUpgradeLevel("weapon");
    if (level <= 0) return;
    String typeName = item.getType().name();
    if (typeName.contains("SWORD") || typeName.contains("AXE")) {
      if (item.containsEnchantment(Enchantment.DAMAGE_ALL)) {
        item.removeEnchantment(Enchantment.DAMAGE_ALL);
      }
      item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, level);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
      }
    }
  }

  // ---------- 以下为原有方法，不做修改 ----------
  private void changeToOldShop(Game game, Player player) {
    game.getPlayerSettings(player).setUseOldShop(true);
    player.playSound(player.getLocation(), SoundMachine.get("CLICK", "UI_BUTTON_CLICK"),
            Float.valueOf("1.0"), Float.valueOf("1.0"));
    MerchantCategory.openCategorySelection(player, game);
  }

  private int getBuyInventorySize(int sizeCategories, int sizeOffers) {
    return this.getInventorySize(sizeCategories) + this.getInventorySize(sizeOffers);
  }

  public List<MerchantCategory> getCategories() {
    return this.categories;
  }

  private int getCategoriesSize(Player player) {
    int size = 0;
    for (MerchantCategory cat : this.categories) {
      if (cat.getMaterial() == null) continue;
      if (player != null && !player.hasPermission(cat.getPermission())) continue;
      size++;
    }
    return size;
  }

  private MerchantCategory getCategoryByMaterial(Material material) {
    for (MerchantCategory category : this.categories) {
      if (category.getMaterial() == material) return category;
    }
    return null;
  }

  private int getInventorySize(int itemAmount) {
    int nom = (itemAmount % 9 == 0) ? 9 : (itemAmount % 9);
    return itemAmount + (9 - nom);
  }

  private VillagerTrade getTradingItem(MerchantCategory category, ItemStack stack, Game game, Player player) {
    for (VillagerTrade trade : category.getOffers()) {
      if (trade.getItem1().getType() == Material.AIR && trade.getRewardItem().getType() == Material.AIR)
        continue;
      ItemStack iStack = this.toItemStack(trade, player, game);
      if (iStack.getType() == Material.ENDER_CHEST && stack.getType() == Material.ENDER_CHEST) {
        return trade;
      } else if ((iStack.getType() == Material.POTION
              || (!BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")
              && (iStack.getType().equals(Material.valueOf("TIPPED_ARROW"))
              || iStack.getType().equals(Material.valueOf("LINGERING_POTION"))
              || iStack.getType().equals(Material.valueOf("SPLASH_POTION")))))) {
        if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
          if (iStack.getItemMeta().equals(stack.getItemMeta())) return trade;
        } else {
          PotionMeta iStackMeta = (PotionMeta) iStack.getItemMeta();
          PotionMeta stackMeta = (PotionMeta) stack.getItemMeta();
          if (iStackMeta.getBasePotionData().equals(stackMeta.getBasePotionData()) &&
                  iStackMeta.getCustomEffects().equals(stackMeta.getCustomEffects())) {
            return trade;
          }
        }
      } else if (iStack.equals(stack)) {
        return trade;
      }
    }
    return null;
  }

  private void handleBuyInventoryClick(InventoryClickEvent ice, Game game, Player player) {
    int sizeCategories = this.getCategoriesSize(player);
    List<VillagerTrade> offers = this.currentCategory.getOffers();
    int sizeItems = offers.size();
    int totalSize = this.getBuyInventorySize(sizeCategories, sizeItems);

    ItemStack item = ice.getCurrentItem();
    boolean cancel = false;
    int bought = 0;
    boolean oneStackPerShift = game.getPlayerSettings(player).oneStackPerShift();

    if (this.currentCategory == null) {
      player.closeInventory();
      return;
    }

    if (ice.getRawSlot() < sizeCategories) {
      ice.setCancelled(true);
      if (item == null) return;
      if (item.getType().equals(this.currentCategory.getMaterial())) {
        this.currentCategory = null;
        this.openCategoryInventory(player);
      } else {
        this.handleCategoryInventoryClick(ice, game, player);
      }
    } else if (ice.getRawSlot() < totalSize) {
      ice.setCancelled(true);
      if (item == null || item.getType() == Material.AIR) return;

      MerchantCategory category = this.currentCategory;
      VillagerTrade trade = this.getTradingItem(category, item, game, player);
      if (trade == null) return;

      if (ice.isShiftClick()) {
        while (this.hasEnoughRessource(player, trade) && !cancel) {
          cancel = !this.buyItem(trade, ice.getCurrentItem(), player, category);
          if (!cancel && oneStackPerShift) {
            bought = bought + item.getAmount();
            cancel = ((bought + item.getAmount()) > 64);
          }
        }
        bought = 0;
      } else {
        this.buyItem(trade, ice.getCurrentItem(), player, category);
      }
    } else {
      if (ice.isShiftClick()) ice.setCancelled(true);
      else ice.setCancelled(false);
    }
  }

  private void handleCategoryInventoryClick(InventoryClickEvent ice, Game game, Player player) {
    int catSize = this.getCategoriesSize(player);
    int sizeCategories = this.getInventorySize(catSize) + 9;
    int rawSlot = ice.getRawSlot();

    if (rawSlot >= this.getInventorySize(catSize) && rawSlot < sizeCategories) {
      ice.setCancelled(true);
      return;
    }

    if (rawSlot >= sizeCategories) {
      if (ice.isShiftClick()) ice.setCancelled(true);
      else ice.setCancelled(false);
      return;
    }

    MerchantCategory clickedCategory = this.getCategoryByMaterial(ice.getCurrentItem().getType());
    if (clickedCategory == null) {
      if (ice.isShiftClick()) ice.setCancelled(true);
      else ice.setCancelled(false);
      return;
    }

    this.openBuyInventory(clickedCategory, player, game);
  }

  public void handleInventoryClick(InventoryClickEvent ice, Game game, Player player) {
    if (!this.hasOpenCategory()) {
      this.handleCategoryInventoryClick(ice, game, player);
    } else {
      this.handleBuyInventoryClick(ice, game, player);
    }
  }

  private boolean hasEnoughRessource(Player player, VillagerTrade trade) {
    ItemStack item1 = trade.getItem1();
    ItemStack item2 = trade.getItem2();
    PlayerInventory inventory = player.getInventory();

    if (item2 != null) {
      if (!inventory.contains(item1.getType(), item1.getAmount()) ||
              !inventory.contains(item2.getType(), item2.getAmount())) {
        return false;
      }
    } else {
      if (!inventory.contains(item1.getType(), item1.getAmount())) {
        return false;
      }
    }
    return true;
  }

  public boolean hasOpenCategory() {
    return (this.currentCategory != null);
  }

  public boolean hasOpenCategory(MerchantCategory category) {
    if (this.currentCategory == null) return false;
    return (this.currentCategory.equals(category));
  }

  private void openBuyInventory(MerchantCategory category, Player player, Game game) {
    List<VillagerTrade> offers = category.getOffers();
    int sizeCategories = this.getCategoriesSize(player);
    int sizeItems = offers.size();
    int invSize = this.getBuyInventorySize(sizeCategories, sizeItems);

    player.playSound(player.getLocation(), SoundMachine.get("CLICK", "UI_BUTTON_CLICK"),
            Float.valueOf("1.0"), Float.valueOf("1.0"));

    this.currentCategory = category;
    Inventory buyInventory = Bukkit.createInventory(player, invSize, BedwarsPRO._l(player, "ingame.shop.name"));
    this.addCategoriesToInventory(buyInventory, player, game);

    for (int i = 0; i < offers.size(); i++) {
      VillagerTrade trade = offers.get(i);
      if (trade.getItem1().getType() == Material.AIR && trade.getRewardItem().getType() == Material.AIR) continue;

      int slot = (this.getInventorySize(sizeCategories)) + i;
      ItemStack tradeStack = this.toItemStack(trade, player, game);
      buyInventory.setItem(slot, tradeStack);
    }

    player.openInventory(buyInventory);
  }

  public void openCategoryInventory(Player player) {
    int catSize = this.getCategoriesSize(player);
    int nom = (catSize % 9 == 0) ? 9 : (catSize % 9);
    int size = (catSize + (9 - nom)) + 9;

    Inventory inventory = Bukkit.createInventory(player, size, BedwarsPRO._l(player, "ingame.shop.name"));
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    this.addCategoriesToInventory(inventory, player, game);
    player.openInventory(inventory);
  }

  public void setCurrentCategory(MerchantCategory category) {
    this.currentCategory = category;
  }

  private ItemStack toItemStack(VillagerTrade trade, Player player, Game game) {
    ItemStack tradeStack = trade.getRewardItem().clone();
    Method colorable = Utils.getColorableMethod(tradeStack.getType());
    ItemMeta meta = tradeStack.getItemMeta();
    ItemStack item1 = trade.getItem1();
    ItemStack item2 = trade.getItem2();
    if (Utils.isColorable(tradeStack)) {
      tradeStack.setDurability(game.getPlayerTeam(player).getColor().getDyeColor().getWoolData());
    } else if (colorable != null) {
      colorable.setAccessible(true);
      try {
        colorable.invoke(meta, new Object[]{game.getPlayerTeam(player).getColor().getColor()});
      } catch (Exception e) {
        BedwarsPRO.getInstance().getBugsnag().notify(e);
        e.printStackTrace();
      }
    }
    List<String> lores = meta.getLore();
    if (lores == null) lores = new ArrayList<String>();

    lores.add(ChatColor.WHITE + String.valueOf(item1.getAmount()) + " "
            + item1.getItemMeta().getDisplayName());
    if (item2 != null) {
      lores.add(ChatColor.WHITE + String.valueOf(item2.getAmount()) + " "
              + item2.getItemMeta().getDisplayName());
    }

    meta.setLore(lores);
    tradeStack.setItemMeta(meta);
    return tradeStack;
  }
  private void applyTeamArmorEnchantment(ItemStack armor, Game game, Player player) {
    if (armor == null || armor.getType() == Material.AIR) return;
    Team team = game.getPlayerTeam(player);
    if (team == null) return;
    String type = armor.getType().name().contains("BOOTS") ? "boots" : "leggings";
    int level = team.getUpgradeLevel(type);
    if (level <= 0) return;
    Enchantment ench = Enchantment.PROTECTION_ENVIRONMENTAL;
    if (armor.containsEnchantment(ench)) armor.removeEnchantment(ench);
    armor.addUnsafeEnchantment(ench, level);
    ItemMeta meta = armor.getItemMeta();
    if (meta != null) {
      meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
      armor.setItemMeta(meta);
    }
  }
}