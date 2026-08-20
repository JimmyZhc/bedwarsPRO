package io.jmmym.bedwarspro.joinitem;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 加入物品监听器：进入服务器 / 切换世界时发放物品，右键物品时执行加入指令。
 *
 * <p>位置锁定：加入物品固定在发放槽位，玩家不能通过点击/拖动移动它，
 * 与 PlayerDropItemEvent（禁止丢弃）配合，保证快捷物品始终在快捷栏。</p>
 */
public class JoinItemListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        JoinItem.getInstance().apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        JoinItem.getInstance().apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // MONITOR 优先级 + ignoreCancelled：
        // 床战游戏在等待大厅会取消粘液球等交互事件（事件取消后这里直接跳过），
        // 由游戏自身处理离开对局等逻辑，避免本模块再执行 /hub 把玩家送回主城
        JoinItem.getInstance().onInteract(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        // 锁定加入物品：点击时无论是被点物品还是光标物品，均禁止移动/交换
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (JoinItem.isJoinItem(current) || JoinItem.isJoinItem(cursor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        // 锁定加入物品：拖动放置过程中包含加入物品即取消
        for (ItemStack item : event.getNewItems().values()) {
            if (JoinItem.isJoinItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // 清理冷却记录，避免内存残留
        JoinItem.getInstance().onQuit(event.getPlayer());
    }
}
