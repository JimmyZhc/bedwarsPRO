package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameEndEvent;
import io.jmmym.bedwarspro.events.BedwarsGameStartedEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerKilledEvent;
import io.jmmym.bedwarspro.events.BedwarsTargetBlockDestroyedEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.task.Task;
import io.jmmym.bedwarspro.task.TaskGUI;
import io.jmmym.bedwarspro.task.TaskManager;
import io.jmmym.bedwarspro.task.TaskMessages;
import io.jmmym.bedwarspro.task.TaskType;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * 监听游戏事件以更新玩家每日+每周任务进度，并处理 TaskGUI 点击。
 */
public class TaskListener implements Listener {

    private static final long QUICK_WIN_THRESHOLD_MS = 5L * 60L * 1000L;

    private final Map<String, Long> gameStartTimes = new HashMap<>();

    public TaskListener() {
        BedwarsPRO.getInstance().getServer().getPluginManager()
                .registerEvents(this, BedwarsPRO.getInstance());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameStarted(BedwarsGameStartedEvent event) {
        Game game = event.getGame();
        if (game == null) {
            return;
        }
        gameStartTimes.put(game.getName(), System.currentTimeMillis());
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        for (Player p : game.getPlayers()) {
            tm.addProgress(p, TaskType.PARTICIPATE, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKilled(BedwarsPlayerKilledEvent event) {
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        Player killer = event.getKiller();
        Player victim = event.getPlayer();
        Game game = event.getGame();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        // 每日击杀
        tm.addProgress(killer, TaskType.KILL, 1);
        // 每周累计击杀
        tm.addWeeklyProgress(killer, TaskType.WEEKLY_KILL, 1);
        // 对局内普通击杀奖励（实时）
        tm.rewardNormalKill(killer);

        // 最终击杀：受害者队伍的床已被破坏
        if (game != null && victim != null) {
            Team victimTeam = game.getPlayerTeam(victim);
            if (victimTeam != null && victimTeam.isDead(game)) {
                tm.addProgress(killer, TaskType.FINAL_KILL, 1);
                // 对局内最终击杀奖励（实时）
                tm.rewardFinalKill(killer);
            }
        }

        // 追杀令检查：杀手是否已接受针对受害者的追杀令
        tm.checkBountyKill(killer, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetBlockDestroyed(BedwarsTargetBlockDestroyedEvent event) {
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        Player p = event.getPlayer();
        if (p != null) {
            tm.addProgress(p, TaskType.DESTROY_BED, 1);
            tm.addWeeklyProgress(p, TaskType.WEEKLY_DESTROY_BED, 1);
            // 对局内拆床奖励（实时）
            tm.rewardDestroyBed(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        Player p = event.getPlayer();
        Game game = BedwarsPRO.getInstance().getGameManager() == null
                ? null : BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
        if (game == null || game.getState() != GameState.RUNNING) {
            return;
        }
        if (game.isSpectator(p)) {
            return;
        }
        int amount = event.getItem().getItemStack().getAmount();
        tm.addProgress(p, TaskType.COLLECT_RESOURCE, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameEnd(BedwarsGameEndEvent event) {
        Game game = event.getGame();
        if (game == null) {
            return;
        }
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        Long startMs = gameStartTimes.remove(game.getName());
        long durationMs = startMs == null ? Long.MAX_VALUE : (System.currentTimeMillis() - startMs);
        boolean quickWin = durationMs <= QUICK_WIN_THRESHOLD_MS;

        Team winnerTeam = null;
        for (Team team : game.getTeams().values()) {
            if (!team.isDead(game) && team.getPlayers().size() > 0) {
                winnerTeam = team;
                break;
            }
        }
        if (winnerTeam == null) {
            return;
        }
        for (Player p : winnerTeam.getPlayers()) {
            tm.addProgress(p, TaskType.WIN, 1);
            tm.addWeeklyProgress(p, TaskType.WEEKLY_WIN, 1);
            if (quickWin) {
                tm.addProgress(p, TaskType.QUICK_WIN, 1);
            }
            // 对局内胜利奖励（实时）
            tm.rewardWin(p);
        }
    }

    // ==================== 玩家加入：提示活跃限时/追杀令任务 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // 延迟 2 秒发送，避免被加入消息刷屏
        Bukkit.getScheduler().runTaskLater(BedwarsPRO.getInstance(), new Runnable() {
            @Override
            public void run() {
                notifyActiveSpecialTasks(player);
            }
        }, 40L);
    }

    /** 向刚加入的玩家提示当前活跃的限时/追杀令任务。 */
    private void notifyActiveSpecialTasks(Player player) {
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        List<Task> specials = tm.getSpecialTasksList();
        if (specials == null || specials.isEmpty()) {
            return;
        }
        TaskMessages.msg(player, "join-header");
        for (Task t : specials) {
            if (t.isBounty()) {
                TaskMessages.msg(player, "join-bounty-entry",
                        "task", t.getDisplayName(), "description", t.getDescription());
            } else {
                TaskMessages.msg(player, "join-timed-entry",
                        "task", t.getDisplayName(), "description", t.getDescription());
            }
        }
        TaskMessages.msg(player, "join-footer");
    }

    // ==================== GUI 点击：接受每日任务 ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TaskGUI.isTaskGUI(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        org.bukkit.inventory.ItemStack clicked = event.getInventory().getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        TaskGUI holder = (TaskGUI) event.getInventory().getHolder();
        if (holder == null) {
            return;
        }

        // 每周任务槽位：不处理接受，只刷新
        if (holder.isWeeklySlot(slot)) {
            return;
        }

        // 每日任务槽位：尝试接受
        int dailyIndex = holder.getDailyTaskIndex(slot);
        if (dailyIndex < 0) {
            return;
        }
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        int result = tm.acceptTask(player, dailyIndex);
        switch (result) {
            case 1:
                TaskMessages.msg(player, "gui-accept-success");
                player.closeInventory();
                TaskGUI.open(player);
                break;
            case 0:
                TaskMessages.msg(player, "gui-already-accepted");
                player.closeInventory();
                break;
            case -1:
                TaskMessages.msg(player, "gui-invalid-slot");
                break;
            case -2:
                TaskMessages.msg(player, "gui-daily-disabled");
                player.closeInventory();
                break;
            default:
                break;
        }
    }
}
