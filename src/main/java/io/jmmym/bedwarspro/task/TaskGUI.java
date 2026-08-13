package io.jmmym.bedwarspro.task;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 每日+每周+限时任务统一 GUI（固定6行54槽位）。
 *
 * <p>布局：
 * <pre>
 *  行1 (slot  0-8)  : 每日任务（可点击接受）
 *  行2 (slot  9-17) : 灰色玻璃板分隔（每周任务标签）
 *  行3 (slot 18-26) : 每周任务（仅展示进度）
 *  行4 (slot 27-35) : 灰色玻璃板分隔（限时任务标签）
 *  行5 (slot 36-44) : 限时任务（可点击接受，金色名）
 *  行6 (slot 45-53) : 灰色玻璃板分隔 + 时钟
 * </pre></p>
 */
public class TaskGUI implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int DAILY_START = 0;
    /** 每日任务最大展示数量（1行，每行9个）。 */
    public static final int DAILY_SLOTS = 9;
    public static final int WEEKLY_START = 18;
    public static final int SPECIAL_START = 36;

    private final Inventory inventory;
    /** 纯每日任务（不含限时任务）。 */
    private final List<Task> pureDailyTasks;
    /** 限时任务。 */
    private final List<Task> specialTasks;
    /** 每周任务。 */
    private final List<Task> weeklyTasks;
    /** GUI 槽位 → dailyTasks 原始列表索引 的映射（每日+限时都可接受）。 */
    private final Map<Integer, Integer> slotToDailyIndex = new HashMap<>();

    public TaskGUI(Player player) {
        TaskManager tm = TaskManager.getInstance();
        this.pureDailyTasks = new ArrayList<>();
        this.specialTasks = new ArrayList<>();
        List<Task> allDaily = tm.getDailyTasks();
        for (Task t : allDaily) {
            if (t.isSpecial()) {
                specialTasks.add(t);
            } else {
                pureDailyTasks.add(t);
            }
        }
        this.weeklyTasks = tm.isWeeklyEnabled() ? new ArrayList<>(tm.getWeeklyTasks()) : new ArrayList<>();

        this.inventory = Bukkit.createInventory(this, SIZE, TaskMessages.get("gui-title"));
        populate(player, allDaily);
    }

    private void populate(Player player, List<Task> allDaily) {
        TaskManager tm = TaskManager.getInstance();
        PlayerTaskState state = tm.getState(player);
        long today = tm.getCurrentDay();
        boolean hasAccepted = state.hasAcceptedToday(today);
        Task accepted = tm.getAcceptedTask(player);

        // ===== 行1: 每日任务 (slot 0-8，最多9个) =====
        for (int i = 0; i < pureDailyTasks.size() && i < DAILY_SLOTS; i++) {
            Task t = pureDailyTasks.get(i);
            int dailyIdx = allDaily.indexOf(t);
            slotToDailyIndex.put(DAILY_START + i, dailyIdx);
            inventory.setItem(DAILY_START + i,
                    buildDailyItem(t, i + 1, hasAccepted, accepted, state));
        }
        // 未使用的每日槽位用灰色玻璃填充，避免空位
        fillSeparator(Math.min(pureDailyTasks.size(), DAILY_SLOTS), DAILY_SLOTS - 1, " ");

        // ===== 行2: 分隔 (slot 9-17) =====
        fillSeparator(9, 17, TaskMessages.get("gui-weekly-separator"));

        // ===== 行3: 每周任务 (slot 18-26) =====
        if (tm.isWeeklyEnabled()) {
            for (int i = 0; i < weeklyTasks.size() && i < 9; i++) {
                Task t = weeklyTasks.get(i);
                inventory.setItem(WEEKLY_START + i, buildWeeklyItem(t, player, tm));
            }
        }

        // ===== 行4: 分隔 (slot 27-35) =====
        fillSeparator(27, 35, TaskMessages.get("gui-timed-separator"));

        // ===== 行5: 限时任务 (slot 36-44) =====
        for (int i = 0; i < specialTasks.size() && i < 9; i++) {
            Task t = specialTasks.get(i);
            int dailyIdx = allDaily.indexOf(t);
            slotToDailyIndex.put(SPECIAL_START + i, dailyIdx);
            if (t.isBounty()) {
                inventory.setItem(SPECIAL_START + i, buildBountyItem(t, i + 1, state));
            } else {
                inventory.setItem(SPECIAL_START + i,
                        buildDailyItem(t, i + 1, hasAccepted, accepted, state));
            }
        }

        // ===== 行6: 分隔+时钟 (slot 45-53) =====
        fillClockSeparator(45, 53, TaskMessages.get("gui-footer-separator"), player);
    }

    private ItemStack buildBountyItem(Task t, int displayNum, PlayerTaskState state) {
        Material mat = Material.GOLDEN_APPLE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        StringBuilder displayName = new StringBuilder();
        displayName.append(ChatColor.GOLD).append(displayNum).append(". ").append(t.getName());
        if (t.getTargetPlayer() != null) {
            displayName.append(TaskMessages.get("gui-bounty-target",
                    "target", t.getTargetPlayer()));
        }
        meta.setDisplayName(displayName.toString());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + t.getDescription());
        lore.add(TaskMessages.get("gui-target", "target", t.getTarget()));
        lore.add(buildRewardLore(t));

        if (state.hasBountyCompleted(t.getTargetPlayer())) {
            lore.add(TaskMessages.get("gui-completed"));
        } else if (state.hasActiveBounty()
                && t.getTargetPlayer() != null
                && t.getTargetPlayer().equalsIgnoreCase(state.getActiveBountyTarget())) {
            lore.add(TaskMessages.get("gui-accepted"));
        } else if (state.hasActiveBounty()) {
            lore.add(ChatColor.RED + "已有活跃击杀令，完成后可接取");
        } else {
            lore.add(TaskMessages.get("gui-click-accept"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildDailyItem(Task t, int displayNum, boolean hasAccepted,
                                     Task accepted, PlayerTaskState state) {
        boolean isAccepted = hasAccepted && accepted != null
                && accepted.getName().equals(t.getName());
        Material mat = materialFor(t.getType(), t.isSpecial());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String prefix = t.isSpecial() ? ChatColor.GOLD.toString() : ChatColor.AQUA.toString();

        StringBuilder displayName = new StringBuilder();
        displayName.append(prefix).append(displayNum).append(". ").append(t.getName());
        // 追杀令显示目标
        if (t.isBounty() && t.getTargetPlayer() != null) {
            displayName.append(TaskMessages.get("gui-bounty-target",
                    "target", t.getTargetPlayer()));
        }
        meta.setDisplayName(displayName.toString());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + t.getDescription());
        lore.add(TaskMessages.get("gui-target", "target", t.getTarget()));
        lore.add(buildRewardLore(t));
        if (isAccepted) {
            lore.add(TaskMessages.get("gui-progress-line",
                    "progress", state.getProgress(), "max", t.getTarget()));
            lore.add(state.isCompleted()
                    ? TaskMessages.get("gui-completed")
                    : TaskMessages.get("gui-accepted"));
        } else if (hasAccepted) {
            lore.add(TaskMessages.get("gui-no-accept"));
        } else {
            lore.add(TaskMessages.get("gui-click-accept"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildWeeklyItem(Task t, Player player, TaskManager tm) {
        Material mat = materialFor(t.getType(), false);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(TaskMessages.get("gui-weekly-name", "name", t.getName()));

        int prog = tm.getWeeklyProgress(player, t.getName());
        boolean done = tm.isWeeklyCompleted(player, t.getName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + t.getDescription());
        lore.add(TaskMessages.get("gui-weekly-target", "target", t.getTarget()));
        lore.add(buildRewardLore(t));
        lore.add(TaskMessages.get("gui-weekly-progress",
                "progress", prog, "max", t.getTarget()));
        lore.add(done ? TaskMessages.get("gui-weekly-done") : TaskMessages.get("gui-weekly-auto"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 构造奖励说明行。 */
    private String buildRewardLore(Task t) {
        StringBuilder sb = new StringBuilder();
        sb.append(TaskMessages.get("gui-reward-prefix"));
        if (t.getRewardExp() > 0) {
            sb.append(TaskMessages.get("gui-exp", "exp", t.getRewardExp()));
        }
        if (t.getRewardExp() <= 0) {
            sb.append(TaskMessages.get("gui-no-reward"));
        }
        return sb.toString();
    }

    private void fillSeparator(int from, int to, String label) {
        int color = BedwarsPRO.getInstance().getConfig().getInt("task-gui.separator-color", 3);
        for (int slot = from; slot <= to; slot++) {
            ItemStack sep = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) color);
            ItemMeta meta = sep.getItemMeta();
            meta.setDisplayName(slot == (from + to) / 2 ? label : " ");
            sep.setItemMeta(meta);
            inventory.setItem(slot, sep);
        }
    }

    /** 第6行分隔：中间放时钟显示刷新/领取时间 */
    private void fillClockSeparator(int from, int to, String label, Player player) {
        TaskManager tm = TaskManager.getInstance();
        int color = BedwarsPRO.getInstance().getConfig().getInt("task-gui.separator-color", 3);
        int clockOffset = BedwarsPRO.getInstance().getConfig().getInt("task-gui.clock-slot", 4);
        int clockSlot = from + clockOffset;
        String clockMatName = BedwarsPRO.getInstance().getConfig().getString("task-gui.clock-material", "WATCH");
        Material clockMat;
        try {
            clockMat = Material.valueOf(clockMatName);
        } catch (IllegalArgumentException e) {
            clockMat = Material.WATCH;
        }

        long nextDaily = tm != null ? tm.getNextDailyRefreshTime() : 0;
        long nextWeekly = tm != null ? tm.getNextWeeklyRefreshTime() : 0;
        String dailyTime = TaskManager.formatTime(nextDaily);
        String weeklyTime = TaskManager.formatTime(nextWeekly);

        // 领取时间：玩家已接受任务则显示 acceptedTime+24h，否则显示"无"
        String claimTime;
        PlayerTaskState state = tm != null ? tm.getState(player) : null;
        if (state != null && state.getAcceptedTime() > 0) {
            claimTime = TaskManager.formatTime(state.getAcceptedTime() + 86400000L);
        } else {
            claimTime = ChatColor.GRAY + "无";
        }

        for (int slot = from; slot <= to; slot++) {
            if (slot == clockSlot) {
                // 时钟物品
                ItemStack clock = new ItemStack(clockMat);
                ItemMeta meta = clock.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + "任务时间信息");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.YELLOW + "每日刷新: " + ChatColor.WHITE + dailyTime);
                lore.add(ChatColor.YELLOW + "每周刷新: " + ChatColor.WHITE + weeklyTime);
                lore.add(ChatColor.YELLOW + "下次领取: " + ChatColor.WHITE + claimTime);
                meta.setLore(lore);
                clock.setItemMeta(meta);
                inventory.setItem(slot, clock);
            } else {
                ItemStack sep = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) color);
                ItemMeta meta = sep.getItemMeta();
                meta.setDisplayName(slot == (from + to) / 2 ? label : " ");
                sep.setItemMeta(meta);
                inventory.setItem(slot, sep);
            }
        }
    }

    private Material materialFor(TaskType type, boolean special) {
        if (special) {
            return Material.GOLDEN_APPLE;
        }
        switch (type) {
            case PARTICIPATE:       return Material.IRON_SWORD;
            case KILL:              return Material.DIAMOND_SWORD;
            case DESTROY_BED:       return Material.BED;
            case COLLECT_RESOURCE:  return Material.CHEST;
            case WIN:               return Material.GOLD_INGOT;
            case FINAL_KILL:        return Material.REDSTONE;
            case QUICK_WIN:         return Material.WATCH;
            case BOW_KILL:          return Material.BOW;
            case KILL_STREAK:       return Material.BLAZE_ROD;
            case COUNTER_ATTACK:    return Material.APPLE;
            case PEARL_KILL:        return Material.ENDER_PEARL;
            case TNT_KILL:          return Material.TNT;
            case VOID_KILL:         return Material.ENDER_PEARL;
            case SURVIVOR:          return Material.BREAD;
            case COMEBACK:          return Material.EMERALD;
            case UNDEFEATED:        return Material.DIAMOND_CHESTPLATE;
            case FIRST_BLOOD:       return Material.REDSTONE_BLOCK;
            case WEEKLY_KILL:       return Material.DIAMOND_SWORD;
            case WEEKLY_WIN:        return Material.GOLD_BLOCK;
            case WEEKLY_DESTROY_BED:return Material.OBSIDIAN;
            case WEEKLY_GENERAL_WIN:return Material.DIAMOND_BLOCK;
            default:                return Material.PAPER;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isTaskGUI(Inventory inv) {
        return inv != null && inv.getHolder() instanceof TaskGUI;
    }

    public static void open(Player player) {
        TaskGUI gui = new TaskGUI(player);
        player.openInventory(gui.getInventory());
    }

    /**
     * 判断点击槽位是否为可接受的每日/限时任务。
     * @return 对应 dailyTasks 原始索引，-1 表示不可接受（分隔/每周区域）。
     */
    public int getDailyTaskIndex(int slot) {
        return slotToDailyIndex.getOrDefault(slot, -1);
    }

    /** 判断槽位是否在每周任务区域（不可点击接受）。 */
    public boolean isWeeklySlot(int slot) {
        return slot >= WEEKLY_START && slot < WEEKLY_START + 9;
    }

    public List<Task> getPureDailyTasks() {
        return pureDailyTasks;
    }

    public List<Task> getSpecialTasks() {
        return specialTasks;
    }

    public List<Task> getWeeklyTasks() {
        return weeklyTasks;
    }
}
