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
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * 监听游戏事件以更新玩家每日+每周任务进度，并处理 TaskGUI 点击。
 */
public class TaskListener implements Listener {

    private static final long QUICK_WIN_THRESHOLD_MS = 5L * 60L * 1000L;
    /** 末影珍珠传送后击杀窗口（毫秒） */
    private static final long PEARL_KILL_WINDOW_MS = 5000L;

    private final Map<String, Long> gameStartTimes = new HashMap<>();

    // ===== 进阶任务跟踪 =====
    /** 玩家末影珍珠传送时间戳（uuid -> ms） */
    private final Map<UUID, Long> lastPearlTeleport = new HashMap<>();
    /** 玩家最近一次被谁攻击（victim -> damager），用于判断弓/TNT/虚空击杀归属 */
    private final Map<UUID, UUID> lastHitBy = new HashMap<>();
    /** 游戏是否已产生首杀（gameName -> 已首杀） */
    private final Map<String, Boolean> gameFirstKill = new HashMap<>();
    /** 玩家本局死亡次数（uuid -> 死亡数），用于无伤获胜 */
    private final Map<UUID, Integer> gameDeaths = new HashMap<>();
    /** 玩家当前连杀数（uuid -> 连杀数），用于连杀达人 */
    private final Map<UUID, Integer> killStreak = new HashMap<>();

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
        gameFirstKill.put(game.getName(), false);
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return;
        }
        for (Player p : game.getPlayers()) {
            // 新游戏开始：重置该玩家本局跟踪数据，避免与上一局/其他游戏串扰
            UUID id = p.getUniqueId();
            killStreak.remove(id);
            gameDeaths.remove(id);
            lastHitBy.remove(id);
            lastPearlTeleport.remove(id);
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
            // 自杀/环境死亡：只记录死亡次数并清零连杀
            if (victim != null) {
                recordDeath(victim);
            }
            return;
        }

        // 记录受害者死亡次数、清零其连杀
        recordDeath(victim);

        // 每日击杀
        tm.addProgress(killer, TaskType.KILL, 1);
        // 每周累计击杀
        tm.addWeeklyProgress(killer, TaskType.WEEKLY_KILL, 1);
        // 对局内普通击杀奖励（实时）
        tm.rewardNormalKill(killer);

        // 首杀：游戏中第一次击杀
        Boolean first = gameFirstKill.get(game == null ? "" : game.getName());
        if (first != null && !first) {
            gameFirstKill.put(game.getName(), true);
            tm.addProgress(killer, TaskType.FIRST_BLOOD, 1);
        }

        // 连杀达人：连续击杀（死亡时清零）
        int streak = killStreak.getOrDefault(killer.getUniqueId(), 0) + 1;
        killStreak.put(killer.getUniqueId(), streak);
        int streakTarget = getAcceptedTaskTarget(killer, TaskType.KILL_STREAK);
        if (streakTarget > 0 && streak >= streakTarget) {
            tm.addProgress(killer, TaskType.KILL_STREAK, 1);
            killStreak.put(killer.getUniqueId(), 0);
        }

        // 反击者：生命值低于5颗心（10点）时击杀
        if (killer.getHealth() < 10.0D) {
            tm.addProgress(killer, TaskType.COUNTER_ATTACK, 1);
        }

        // 偷袭者：末影珍珠传送后5秒内击杀
        Long pearlMs = lastPearlTeleport.get(killer.getUniqueId());
        if (pearlMs != null && (System.currentTimeMillis() - pearlMs) <= PEARL_KILL_WINDOW_MS) {
            tm.addProgress(killer, TaskType.PEARL_KILL, 1);
        }

        // 逆风翻盘：队伍只剩1人时击杀敌人
        if (game != null) {
            Team killerTeam = game.getPlayerTeam(killer);
            if (killerTeam != null && !killerTeam.isDead(game)
                    && killerTeam.getPlayers().size() == 1) {
                tm.addProgress(killer, TaskType.COMEBACK, 1);
            }
        }

        // 通过伤害原因细分：弓/TNT/虚空
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = lastDamage == null
                ? null : lastDamage.getCause();
        if (cause != null) {
            // 弓箭手：被箭/投射物击杀
            if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                tm.addProgress(killer, TaskType.BOW_KILL, 1);
            }
            // TNT狂人：爆炸击杀
            if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                tm.addProgress(killer, TaskType.TNT_KILL, 1);
            }
            // 虚空猎手：受害者在虚空中死亡，且此前被 killer 攻击过
            if (cause == EntityDamageEvent.DamageCause.VOID) {
                UUID hitter = lastHitBy.get(victim.getUniqueId());
                if (hitter != null && hitter.equals(killer.getUniqueId())) {
                    tm.addProgress(killer, TaskType.VOID_KILL, 1);
                }
            }
        }
        lastHitBy.remove(victim.getUniqueId());

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
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // 记录末影珍珠传送时间，供"偷袭者"任务判断5秒击杀窗口
        if (event.getCause() == TeleportCause.ENDER_PEARL) {
            lastPearlTeleport.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 记录最近一次攻击者，供"虚空猎手"判断击入虚空的归属
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        UUID damager = getDamagerUuid(event.getDamager());
        if (damager != null && !damager.equals(victim.getUniqueId())) {
            lastHitBy.put(victim.getUniqueId(), damager);
        }
    }

    /** 从伤害来源解析攻击者 UUID（玩家 / 投射物射手 / TNT 引爆者）。 */
    private UUID getDamagerUuid(Entity damager) {
        if (damager instanceof Player) {
            return damager.getUniqueId();
        }
        if (damager instanceof Projectile) {
            if (((Projectile) damager).getShooter() instanceof Player) {
                return ((Player) ((Projectile) damager).getShooter()).getUniqueId();
            }
        }
        if (damager instanceof TNTPrimed) {
            if (((TNTPrimed) damager).getSource() instanceof Player) {
                return ((Player) ((TNTPrimed) damager).getSource()).getUniqueId();
            }
        }
        return null;
    }

    /** 记录一次死亡：死亡次数 +1，连杀清零。 */
    private void recordDeath(Player victim) {
        gameDeaths.put(victim.getUniqueId(), gameDeaths.getOrDefault(victim.getUniqueId(), 0) + 1);
        killStreak.remove(victim.getUniqueId());
    }

    /** 获取玩家已接受的某类型每日任务的 target（未接受返回0）。 */
    private int getAcceptedTaskTarget(Player player, TaskType type) {
        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            return 0;
        }
        Task task = tm.getAcceptedTask(player);
        if (task != null && task.getType() == type) {
            return task.getTarget();
        }
        return 0;
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
        long durationMinutes = durationMs / 60000L;

        Team winnerTeam = null;
        for (Team team : game.getTeams().values()) {
            if (!team.isDead(game) && team.getPlayers().size() > 0) {
                winnerTeam = team;
                break;
            }
        }
        if (winnerTeam != null) {
            for (Player p : winnerTeam.getPlayers()) {
                tm.addProgress(p, TaskType.WIN, 1);
                tm.addWeeklyProgress(p, TaskType.WEEKLY_WIN, 1);
                // 每周累计胜场（百胜将军）
                tm.addWeeklyProgress(p, TaskType.WEEKLY_GENERAL_WIN, 1);
                if (quickWin) {
                    tm.addProgress(p, TaskType.QUICK_WIN, 1);
                }
                // 生存专家：本局游戏时长（分钟）
                tm.addProgress(p, TaskType.SURVIVOR, (int) durationMinutes);
                // 无伤获胜：本局未死亡并获胜
                if (gameDeaths.getOrDefault(p.getUniqueId(), 0) == 0) {
                    tm.addProgress(p, TaskType.UNDEFEATED, 1);
                }
                // 对局内胜利奖励（实时）
                tm.rewardWin(p);
            }
        }
        // 清理本局按游戏维度的数据（玩家维度数据由下一局 onGameStarted 重置，避免影响并行游戏）
        gameFirstKill.remove(game.getName());
    }

    // ==================== 玩家加入：提示活跃限时/追杀令任务 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // 多服同步：清除内存缓存，重新从数据库读取最新任务进度
        TaskManager tm = TaskManager.getInstance();
        if (tm != null) {
            tm.removeCachedState(player.getUniqueId());
        }
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

        // 判断是否为击杀令（使用独立的接取逻辑）
        List<Task> allDaily = tm.getDailyTasks();
        if (dailyIndex < allDaily.size()) {
            Task clickedTask = allDaily.get(dailyIndex);
            if (clickedTask.isBounty() && clickedTask.getTargetPlayer() != null) {
                // 击杀令：使用独立接取方法
                int result = tm.acceptBountyTask(player, clickedTask.getTargetPlayer());
                switch (result) {
                    case 1:
                        TaskMessages.msg(player, "gui-accept-success");
                        player.closeInventory();
                        TaskGUI.open(player);
                        break;
                    case 0:
                        player.sendMessage(ChatColor.RED + "该击杀令已完成，无法重复接取！");
                        player.closeInventory();
                        break;
                    case -1:
                        player.sendMessage(ChatColor.RED + "已有活跃击杀令，完成后可接取新的！");
                        player.closeInventory();
                        break;
                    default:
                        break;
                }
                return;
            }
        }

        // 普通每日/限时任务：原有逻辑
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
