package io.jmmym.bedwarspro.quickstash;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 快捷存入核心逻辑。
 *
 * <p>普通模式：将玩家手持物品的整组数量全部存入目标箱子。
 * 潜行模式：将玩家背包（0-35 槽）内所有与手持物品同种的物品一次性全部存入。</p>
 *
 * <p>存入算法：优先合并到箱子中已有的同种堆（不超过最大堆叠），
 * 再放入空槽，直至物品全部存入或箱子已满。</p>
 */
public class DepositHandler {

    /** 存入结果类型。 */
    public enum ResultType {
        /** 全部存入成功。 */
        SUCCESS,
        /** 箱子空间不足，部分存入。 */
        PARTIAL,
        /** 物品在黑名单中，无法存入。 */
        FAIL_BLACKLIST,
        /** 空手/无同种物品。 */
        FAIL_EMPTY,
        /** 目标方块不支持（非箱子/末影箱）。 */
        FAIL_TARGET
    }

    /** 存入结果。 */
    public static class DepositResult {
        public final ResultType type;
        /** 实际存入数量。 */
        public final int stored;
        /** 本次尝试存入的总数量。 */
        public final int total;

        public DepositResult(ResultType type, int stored, int total) {
            this.type = type;
            this.stored = stored;
            this.total = total;
        }
    }

    private DepositHandler() {
    }

    /**
     * 执行快捷存入。
     *
     * @param player    玩家
     * @param block     被左键点击的目标方块（CHEST/TRAPPED_CHEST/ENDER_CHEST）
     * @param hand      玩家手持物品
     * @param sneakAll  true=潜行，存入背包内所有同种物品；false=仅存入手中整组
     */
    public static DepositResult deposit(Player player, Block block, ItemStack hand,
                                        boolean sneakAll) {
        if (hand == null || hand.getType() == Material.AIR) {
            return new DepositResult(ResultType.FAIL_EMPTY, 0, 0);
        }
        Config config = PunchToDeposit.getConfig();
        if (config.isBlacklisted(hand)) {
            return new DepositResult(ResultType.FAIL_BLACKLIST, 0, 0);
        }
        Inventory chest = getTargetInventory(player, block);
        if (chest == null) {
            return new DepositResult(ResultType.FAIL_TARGET, 0, 0);
        }

        // 收集要存入的数量与来源槽位
        PlayerInventory inv = player.getInventory();
        int total = 0;
        List<Integer> sourceSlots = new ArrayList<>();
        if (sneakAll) {
            // 背包 0-35（快捷栏+背包），不含护甲
            for (int i = 0; i < 36; i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && it.getType() != Material.AIR && it.isSimilar(hand)) {
                    total += it.getAmount();
                    sourceSlots.add(i);
                }
            }
        } else {
            total = hand.getAmount();
        }
        if (total <= 0) {
            return new DepositResult(ResultType.FAIL_EMPTY, 0, 0);
        }

        int stored = storeInto(chest, hand, total);
        if (stored <= 0) {
            // 箱子完全放不下
            return new DepositResult(ResultType.PARTIAL, 0, total);
        }
        // 从背包移除已存入的数量
        if (sneakAll) {
            removeFromSlots(inv, hand, stored, sourceSlots);
        } else {
            removeHand(inv, hand, stored);
        }
        if (stored >= total) {
            return new DepositResult(ResultType.SUCCESS, stored, total);
        }
        return new DepositResult(ResultType.PARTIAL, stored, total);
    }

    /** 获取目标箱子库存：箱子/陷阱箱返回方块库存，末影箱返回玩家个人末影箱。 */
    private static Inventory getTargetInventory(Player player, Block block) {
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
            if (block.getState() instanceof Chest) {
                return ((Chest) block.getState()).getInventory();
            }
            return null;
        }
        if (type == Material.ENDER_CHEST) {
            return player.getEnderChest();
        }
        return null;
    }

    /**
     * 将 amount 个 template 存入箱子。
     * 先合并到已有同种堆，再放入空槽。
     *
     * @return 实际存入数量
     */
    private static int storeInto(Inventory chest, ItemStack template, int amount) {
        int remaining = amount;
        int maxStack = template.getMaxStackSize();
        // 1. 合并到箱子中已有的同种堆
        for (int i = 0; i < chest.getSize() && remaining > 0; i++) {
            ItemStack slot = chest.getItem(i);
            if (slot != null && slot.getType() != Material.AIR
                    && slot.isSimilar(template) && slot.getAmount() < maxStack) {
                int space = maxStack - slot.getAmount();
                int add = Math.min(space, remaining);
                slot.setAmount(slot.getAmount() + add);
                remaining -= add;
            }
        }
        // 2. 放入空槽
        for (int i = 0; i < chest.getSize() && remaining > 0; i++) {
            ItemStack slot = chest.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                int add = Math.min(maxStack, remaining);
                ItemStack copy = template.clone();
                copy.setAmount(add);
                chest.setItem(i, copy);
                remaining -= add;
            }
        }
        return amount - remaining;
    }

    /** 从指定槽位（从后往前）扣减 amount 个物品。 */
    private static void removeFromSlots(PlayerInventory inv, ItemStack template,
                                        int amount, List<Integer> slots) {
        int remaining = amount;
        for (int i = slots.size() - 1; i >= 0 && remaining > 0; i--) {
            int slot = slots.get(i);
            ItemStack it = inv.getItem(slot);
            if (it == null || !it.isSimilar(template)) {
                continue;
            }
            if (it.getAmount() <= remaining) {
                remaining -= it.getAmount();
                inv.setItem(slot, null);
            } else {
                it.setAmount(it.getAmount() - remaining);
                inv.setItem(slot, it);
                remaining = 0;
            }
        }
    }

    /** 普通模式：扣减手持物品（可能部分存入）。 */
    private static void removeHand(PlayerInventory inv, ItemStack hand, int amount) {
        ItemStack cur = inv.getItemInHand();
        if (cur == null || !cur.isSimilar(hand)) {
            return;
        }
        if (cur.getAmount() <= amount) {
            inv.setItemInHand(null);
        } else {
            cur.setAmount(cur.getAmount() - amount);
            inv.setItemInHand(cur);
        }
    }
}
