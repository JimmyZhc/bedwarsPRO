package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.task.PlayerTaskState;
import io.jmmym.bedwarspro.task.Task;
import io.jmmym.bedwarspro.task.TaskGUI;
import io.jmmym.bedwarspro.task.TaskManager;
import io.jmmym.bedwarspro.task.TaskMessages;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 处理 /bwpro 命令。
 *
 * <p>子命令：
 * <ul>
 *   <li>/bwpro task gui                       — 打开每日/每周任务菜单</li>
 *   <li>/bwpro task info                      — 查看当前任务进度</li>
 *   <li>/bwpro task on                        — 开启每日任务（admin）</li>
 *   <li>/bwpro task off                       — 关闭每日任务（admin）</li>
 *   <li>/bwpro task random on|off             — 开关每日随机分配（admin）</li>
 *   <li>/bwpro task weekly on|off             — 开关每周任务（admin）</li>
 *   <li>/bwpro task wrandom on|off            — 开关每周随机分配（admin）</li>
 *   <li>/bwpro task reload                    — 重载 tasks.yml 配置（admin）</li>
 *   <li>/bwpro task publish &lt;name&gt;         — 发布金色名限时任务（publish）</li>
 *   <li>/bwpro task publish 击杀令 &lt;玩家名&gt;  — 发布追杀令</li>
 *   <li>/bwpro task list                      — 列出所有已发布限时任务及ID</li>
 *   <li>/bwpro task remove &lt;id&gt;           — 按ID移除单个限时任务（admin）</li>
 *   <li>/bwpro task clear                     — 清空所有限时任务（admin）</li>
 *   <li>/bwpro task reset &lt;player&gt; [daily|weekly|timed|all] — 清除玩家任务（admin）</li>
 * </ul></p>
 */
public class TaskCommand implements CommandExecutor {

    private static final String PERM_PUBLISH = "bwpro.task.publish";
    private static final String PERM_ADMIN = "bwpro.task.admin";
    private static final String PERM_RELOAD = "bwpro.task.reload";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("task")) {
            sendHelp(sender);
            return true;
        }

        TaskManager tm = TaskManager.getInstance();
        if (tm == null) {
            TaskMessages.msg(sender, "cmd-system-not-init");
            return true;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";

        switch (sub) {
            case "gui":
                return handleGui(sender);
            case "info":
                return handleInfo(sender, tm);
            case "on":
                return handleDailyToggle(sender, tm, true);
            case "off":
                return handleDailyToggle(sender, tm, false);
            case "random":
                return handleRandomToggle(sender, args, tm);
            case "weekly":
                return handleWeeklyToggle(sender, args, tm);
            case "wrandom":
                return handleWeeklyRandomToggle(sender, args, tm);
            case "reload":
                return handleReload(sender, tm);
            case "publish":
                return handlePublish(sender, args, tm);
            case "list":
                return handleList(sender, tm);
            case "remove":
                return handleRemove(sender, args, tm);
            case "clear":
                return handleClear(sender, tm);
            case "reset":
                return handleReset(sender, args, tm);
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player)) {
            TaskMessages.msg(sender, "cmd-not-player");
            return true;
        }
        Player player = (Player) sender;
        TaskManager tm = TaskManager.getInstance();
        if (!tm.isDailyEnabled() && !tm.isWeeklyEnabled()) {
            TaskMessages.msg(player, "cmd-daily-weekly-disabled");
            return true;
        }
        TaskGUI.open(player);
        return true;
    }

    private boolean handleDailyToggle(CommandSender sender, TaskManager tm, boolean enable) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        tm.setDailyEnabled(enable);
        TaskMessages.msg(sender, "cmd-daily-toggle", "state", enable ? "开启" : "关闭");
        return true;
    }

    private boolean handleRandomToggle(CommandSender sender, String[] args, TaskManager tm) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        if (args.length < 3) {
            TaskMessages.msg(sender, "cmd-usage-random");
            return true;
        }
        boolean enable = args[2].equalsIgnoreCase("on");
        tm.setRandomAssign(enable);
        TaskMessages.msg(sender, "cmd-random-toggle", "state", enable ? "开启" : "关闭");
        return true;
    }

    private boolean handleWeeklyToggle(CommandSender sender, String[] args, TaskManager tm) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        if (args.length < 3) {
            TaskMessages.msg(sender, "cmd-usage-weekly");
            return true;
        }
        boolean enable = args[2].equalsIgnoreCase("on");
        tm.setWeeklyEnabled(enable);
        TaskMessages.msg(sender, "cmd-weekly-toggle", "state", enable ? "开启" : "关闭");
        return true;
    }

    private boolean handleWeeklyRandomToggle(CommandSender sender, String[] args, TaskManager tm) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        if (args.length < 3) {
            TaskMessages.msg(sender, "cmd-usage-wrandom");
            return true;
        }
        boolean enable = args[2].equalsIgnoreCase("on");
        tm.setWeeklyRandomAssign(enable);
        TaskMessages.msg(sender, "cmd-wrandom-toggle", "state", enable ? "开启" : "关闭");
        return true;
    }

    private boolean handleReload(CommandSender sender, TaskManager tm) {
        if (!sender.hasPermission(PERM_RELOAD) && !hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        try {
            tm.reload();
            TaskMessages.msg(sender, "cmd-reload-success");
            TaskMessages.msg(sender, "cmd-reload-detail",
                    "daily", tm.isDailyEnabled() ? "开" : "关",
                    "weekly", tm.isWeeklyEnabled() ? "开" : "关",
                    "random", tm.isRandomAssign() ? "开" : "关",
                    "wrandom", tm.isWeeklyRandomAssign() ? "开" : "关");
        } catch (Exception e) {
            TaskMessages.msg(sender, "cmd-reload-fail", "error", e.getMessage());
        }
        return true;
    }

    private boolean handlePublish(CommandSender sender, String[] args, TaskManager tm) {
        if (!sender.hasPermission(PERM_PUBLISH) && !sender.isOp()) {
            TaskMessages.msg(sender, "cmd-no-permission-publish");
            return true;
        }
        if (args.length < 3) {
            TaskMessages.msg(sender, "cmd-usage-publish");
            return true;
        }
        // 追杀令特殊处理：/bwpro task publish 击杀令 <目标玩家>
        if (args[2].equals("击杀令")) {
            if (args.length < 4) {
                TaskMessages.msg(sender, "cmd-usage-publish-bounty");
                return true;
            }
            String targetPlayer = args[3];
            int result = tm.publishBountyTask(targetPlayer);
            if (result == 1) {
                TaskMessages.msg(sender, "bounty-publish-success",
                        "target", targetPlayer, "minutes", tm.getTimedDurationMinutes());
            } else if (result == -1) {
                TaskMessages.msg(sender, "bounty-publish-duplicate", "target", targetPlayer);
            }
            return true;
        }
        // 普通限时任务发布
        String name = args[2];
        int result = tm.publishSpecialTask(name);
        if (result == 1) {
            TaskMessages.msg(sender, "timed-publish-success",
                    "task", name, "minutes", tm.getTimedDurationMinutes());
        } else if (result == 0) {
            TaskMessages.msg(sender, "timed-publish-not-found", "task", name);
        } else if (result == -1) {
            TaskMessages.msg(sender, "timed-publish-duplicate");
        }
        return true;
    }

    private boolean handleList(CommandSender sender, TaskManager tm) {
        java.util.List<Task> specials = tm.getSpecialTasksList();
        if (specials.isEmpty()) {
            TaskMessages.msg(sender, "timed-list-empty");
            return true;
        }
        TaskMessages.msg(sender, "timed-list-header");
        for (int i = 0; i < specials.size(); i++) {
            Task t = specials.get(i);
            int id = i + 1;
            TaskMessages.msg(sender, "timed-list-entry",
                    "id", id, "task", t.getDisplayName(),
                    "description", t.getDescription(), "target", t.getTarget());
        }
        TaskMessages.msg(sender, "timed-list-footer");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args, TaskManager tm) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        if (args.length < 3) {
            TaskMessages.msg(sender, "cmd-usage-remove");
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            TaskMessages.msg(sender, "cmd-invalid-id");
            return true;
        }
        int result = tm.removeSpecialTask(id);
        if (result == 1) {
            TaskMessages.msg(sender, "cmd-remove-success", "id", id);
        } else if (result == 0) {
            TaskMessages.msg(sender, "cmd-id-not-found", "id", id);
        } else if (result == -1) {
            TaskMessages.msg(sender, "timed-list-empty");
        }
        return true;
    }

    private boolean handleClear(CommandSender sender, TaskManager tm) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        tm.clearSpecialTasks();
        TaskMessages.msg(sender, "timed-cleared");
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args, TaskManager tm) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        if (args.length < 3) {
            TaskMessages.msg(sender, "cmd-usage-reset");
            return true;
        }
        String playerName = args[2];
        String type = args.length >= 4 ? args[3].toLowerCase() : "all";
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            TaskMessages.msg(sender, "cmd-player-not-found", "player", playerName);
            return true;
        }
        UUID uuid = target.getUniqueId();
        int result = tm.resetPlayerState(uuid, type);
        if (result == 1) {
            TaskMessages.msg(sender, "cmd-reset-success",
                    "player", playerName, "type", type);
        } else if (result == 0) {
            TaskMessages.msg(sender, "cmd-player-no-data", "player", playerName);
        } else if (result == -1) {
            TaskMessages.msg(sender, "cmd-invalid-type", "type", type);
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, TaskManager tm) {
        if (!(sender instanceof Player)) {
            TaskMessages.msg(sender, "cmd-not-player");
            return true;
        }
        Player player = (Player) sender;
        Task task = tm.getAcceptedTask(player);
        PlayerTaskState state = tm.getState(player);
        if (task == null) {
            TaskMessages.msg(player, "info-no-daily");
        } else {
            String tagKey = task.isSpecial() ? "info-daily-tag" : "info-daily-tag";
            String tag = TaskMessages.get(tagKey).replace("每日",
                    task.isSpecial() ? "限时" : "每日");
            player.sendMessage(tag + task.getDisplayName());
            player.sendMessage(TaskMessages.get("info-progress",
                    "progress", state.getProgress(), "max", task.getTarget())
                    + (state.isCompleted() ? TaskMessages.get("info-completed-mark") : ""));
        }
        if (tm.isWeeklyEnabled()) {
            for (Task wt : tm.getWeeklyTasks()) {
                int prog = tm.getWeeklyProgress(player, wt.getName());
                boolean done = tm.isWeeklyCompleted(player, wt.getName());
                player.sendMessage(TaskMessages.get("info-weekly-tag") + wt.getDisplayName()
                        + " " + prog + "/" + wt.getTarget()
                        + (done ? TaskMessages.get("info-completed-mark") : ""));
            }
        }
        TaskMessages.msg(player, "info-day-debug",
                "day", tm.getCurrentDay(), "week", tm.getCurrentWeek(),
                "multiplier", tm.getTimeMultiplier() + "x");
        return true;
    }

    private boolean hasAdminPerm(CommandSender sender) {
        return sender.hasPermission(PERM_ADMIN) || sender.isOp();
    }

    private void sendHelp(CommandSender sender) {
        TaskMessages.msg(sender, "help-header");
        TaskMessages.msg(sender, "help-gui");
        TaskMessages.msg(sender, "help-info");
        TaskMessages.msg(sender, "help-list");
        if (hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "help-onoff");
            TaskMessages.msg(sender, "help-random");
            TaskMessages.msg(sender, "help-weekly");
            TaskMessages.msg(sender, "help-wrandom");
            TaskMessages.msg(sender, "help-reload");
            TaskMessages.msg(sender, "help-remove");
            TaskMessages.msg(sender, "help-clear");
            TaskMessages.msg(sender, "help-reset");
        }
        if (sender.hasPermission(PERM_PUBLISH) || sender.isOp()) {
            TaskMessages.msg(sender, "help-publish");
            TaskMessages.msg(sender, "help-publish-bounty");
        }
    }
}
