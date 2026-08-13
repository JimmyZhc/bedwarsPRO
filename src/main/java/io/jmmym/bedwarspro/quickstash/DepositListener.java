package io.jmmym.bedwarspro.quickstash;

import io.jmmym.bedwarspro.BedwarsPRO;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 快捷存入事件监听器。
 *
 * <p>监听：
 * <ul>
 *   <li>PlayerInteractEvent — 玩家左键点击箱子/陷阱箱/末影箱时触发快捷存入
 *       （右键保持原版打开箱子行为，不做任何处理）</li>
 *   <li>InventoryClickEvent — 处理 /bwpro quickstash gui 开关界面点击</li>
 * </ul></p>
 */
public class DepositListener implements Listener {

    private final PunchToDeposit module;

    public DepositListener(PunchToDeposit module) {
        this.module = module;
        BedwarsPRO.getInstance().getServer().getPluginManager()
                .registerEvents(this, BedwarsPRO.getInstance());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        // 仅处理左键点击方块；右键保持原版行为（打开箱子界面）
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Config config = module.getConfig();
        if (config == null || !config.isEnabled()) {
            return;
        }
        // 只处理配置指定的箱子类型
        if (!config.isTargetBlock(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        // 玩家个人开关
        if (!module.isEnabledFor(player)) {
            return;
        }
        // 世界隔离（仅配置的游戏世界生效，大厅/主城不触发）
        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        // 取消默认行为：防止左键破坏箱子方块
        event.setCancelled(true);

        // 空手左键：无任何反应（需求）
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return;
        }

        // 执行快捷存入（成功/失败均不发送提示、不播放音效）
        DepositHandler.deposit(player, block, hand, player.isSneaking());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        module.handleGuiClick(event);
    }
}
