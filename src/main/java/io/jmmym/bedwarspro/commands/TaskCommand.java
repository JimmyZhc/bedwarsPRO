package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.auth.AuthManager;
import io.jmmym.bedwarspro.auth.Cfg;
import io.jmmym.bedwarspro.auth.Str;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.rank.RankManager;
import io.jmmym.bedwarspro.rank.RankMessages;
import io.jmmym.bedwarspro.task.PlayerTaskState;
import io.jmmym.bedwarspro.task.Task;
import io.jmmym.bedwarspro.task.TaskGUI;
import io.jmmym.bedwarspro.task.TaskManager;
import io.jmmym.bedwarspro.task.TaskMessages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 *   <li>/bwpro task publish 击杀令 &lt;玩家名&gt;  — 发布追杀令（仅支持追杀令）</li>
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

    /** 清除确认码的有效期（毫秒）。 */
    private static final long CONFIRM_TTL_MS = 60_000L;
    /** 待二次确认的清除操作：key = 发送者|类型|目标。 */
    private final Map<String, PendingConfirm> pendingClears = new HashMap<>();

    /** 一次待确认的清除操作。 */
    private static class PendingConfirm {
        final String code;
        final long expiresAt;

        PendingConfirm(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 支持 /bwpro reload（不带 task 前缀）
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            TaskManager tm = TaskManager.getInstance();
            if (tm == null) {
                TaskMessages.msg(sender, "cmd-system-not-init");
                return true;
            }
            return handleReload(sender, tm);
        }

        // /bwpro quickstash ... 快捷存入模块命令
        if (args.length >= 1 && args[0].equalsIgnoreCase("quickstash")) {
            return io.jmmym.bedwarspro.quickstash.PunchToDeposit.handleCommand(sender, args);
        }

        // /bwpro check — 检查授权服务器上是否有新版本插件（admin）
        if (args.length >= 1 && args[0].equalsIgnoreCase("check")) {
            if (!hasAdminPerm(sender)) {
                TaskMessages.msg(sender, "cmd-no-permission");
                return true;
            }
            io.jmmym.bedwarspro.auth.UpdateFlow.checkCommand(BedwarsPRO.getInstance(), sender);
            return true;
        }

        // /bwpro update [confirm|cancel] — 提交版本更新请求 / 二次确认 / 取消（admin）
        if (args.length >= 1 && args[0].equalsIgnoreCase("update")) {
            if (!hasAdminPerm(sender)) {
                TaskMessages.msg(sender, "cmd-no-permission");
                return true;
            }
            io.jmmym.bedwarspro.auth.UpdateFlow.updateCommand(BedwarsPRO.getInstance(), sender, args);
            return true;
        }

        // /bwpro info — 显示本服务器授权信息（服务器UUID / 插件授权码 / 授权服务器地址）
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            return handleAuthInfo(sender);
        }

        // /bwpro scoreboard ... 世界计分板模块命令
        if (args.length >= 1 && args[0].equalsIgnoreCase("scoreboard")) {
            return handleModuleReload(sender, "scoreboard", args);
        }

        // /bwpro joinitem ... 加入物品模块命令
        if (args.length >= 1 && args[0].equalsIgnoreCase("joinitem")) {
            return io.jmmym.bedwarspro.joinitem.JoinItem.handleCommand(sender, args);
        }

        // /bwpro clearstats <玩家> [确认码] — 清除玩家起床战争统计数据（确认码二次确认）
        if (args.length >= 1 && args[0].equalsIgnoreCase("clearstats")) {
            return handleClearStats(sender, args);
        }

        // /bwpro clearrecord <地图> [确认码] — 清除地图最快通关记录（确认码二次确认）
        if (args.length >= 1 && args[0].equalsIgnoreCase("clearrecord")) {
            return handleClearRecord(sender, args);
        }

        // /bwpro mapgui — 地图管理界面（列出等待中/可用游戏，左键切换排位/休闲）
        if (args.length >= 1 && args[0].equalsIgnoreCase("mapgui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "该命令只能由玩家在游戏内执行！");
                return true;
            }
            if (!hasAdminPerm(sender)) {
                RankMessages.msg(sender, "cmd.no-permission");
                return true;
            }
            io.jmmym.bedwarspro.rank.RankedGameGUI.open((Player) sender, 0);
            return true;
        }

        // /bwpro rankreload — 重读排位配置与语言文件（手动改 rank.yml 后生效）
        if (args.length >= 1 && args[0].equalsIgnoreCase("rankreload")) {
            if (!hasAdminPerm(sender)) {
                RankMessages.msg(sender, "cmd.no-permission");
                return true;
            }
            RankManager.getInstance().reload();
            RankMessages.msg(sender, "cmd.reload-success");
            return true;
        }

        // /bwpro debug [on|off|status] — Bot 调试日志开关（admin，默认关闭）
        if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
            if (!hasAdminPerm(sender)) {
                TaskMessages.msg(sender, "cmd-no-permission");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /bwpro debug <on|off|status>");
                return true;
            }
            String mode = args[1].toLowerCase();
            if (mode.equals("on")) {
                BedwarsPRO.getInstance().setBotDebug(true);
                sender.sendMessage(ChatColor.GREEN + "[Bot] 调试模式已开启（假人日志将输出到控制台）");
            } else if (mode.equals("off")) {
                BedwarsPRO.getInstance().setBotDebug(false);
                sender.sendMessage(ChatColor.GREEN + "[Bot] 调试模式已关闭");
            } else if (mode.equals("status")) {
                sender.sendMessage(BedwarsPRO.getInstance().isBotDebug()
                        ? ChatColor.GREEN + "[Bot] 调试模式: 开启"
                        : ChatColor.YELLOW + "[Bot] 调试模式: 关闭");
            } else {
                sender.sendMessage(ChatColor.RED + "用法: /bwpro debug <on|off|status>");
            }
            return true;
        }

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
            case "taskrefresh":
                return handleTaskRefresh(sender, args, tm);
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
            // 重载插件主配置 config.yml
            BedwarsPRO.getInstance().reloadConfig();
            // 重载消息文件 messages.yml
            TaskMessages.reload();
            // 重载任务配置 tasks.yml + api.yml
            tm.reload();
            // 重载快捷存储配置 config-quickstash.yml
            io.jmmym.bedwarspro.quickstash.PunchToDeposit.reload();
            // 重载世界计分板配置 Scoreboard/config.yml
            io.jmmym.bedwarspro.worldscoreboard.WorldScoreboard.reload();
            // 重载加入物品配置 Scoreboard/join-item.yml
            io.jmmym.bedwarspro.joinitem.JoinItem.reload();
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

    /** 分步重载指令：/bwpro scoreboard reload 等（分别刷新对应模块配置）。 */
    private boolean handleModuleReload(CommandSender sender, String module, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("reload")) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "用法: /bwpro " + module + " reload");
            return true;
        }
        if (!sender.hasPermission(PERM_RELOAD) && !hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        try {
            if (module.equals("scoreboard")) {
                io.jmmym.bedwarspro.worldscoreboard.WorldScoreboard.reload();
            }
            TaskMessages.msg(sender, "cmd-module-reload-success", "module", module);
        } catch (Exception e) {
            TaskMessages.msg(sender, "cmd-reload-fail", "error", e.getMessage());
        }
        return true;
    }

    /**
     * /bwpro clearstats &lt;玩家名&gt; [确认码] — 清除玩家起床战争统计数据（确认码二次确认）。
     *
     * <p>第一次输入只生成确认码并警告，第二次必须带上确认码才会真正执行清除。</p>
     */
    private boolean handleClearStats(CommandSender sender, String[] args) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(ChatColor.RED + "用法: /bwpro clearstats <玩家名> [确认码]");
            return true;
        }
        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "玩家不存在: " + playerName);
            return true;
        }
        if (args.length == 2) {
            // 第一步：生成确认码，等待二次确认
            String code = issueConfirmCode(sender, "clearstats", playerName);
            sender.sendMessage(ChatColor.RED + "警告：即将永久清除玩家 " + target.getName()
                    + " 的全部起床战争统计数据（击杀/死亡/胜场/败场/摧毁床/积分）！");
            sender.sendMessage(ChatColor.GOLD + "确认码: " + code + ChatColor.GRAY + "（60 秒内有效）");
            sender.sendMessage(ChatColor.GOLD + "请再次输入 /bwpro clearstats " + playerName
                    + " " + code + " 以执行清除。");
            return true;
        }
        // 第二步：校验确认码
        if (!checkConfirmCode(sender, "clearstats", playerName, args[2])) {
            sender.sendMessage(ChatColor.RED + "确认码错误或已过期，请重新输入 /bwpro clearstats "
                    + playerName + " 获取新的确认码。");
            return true;
        }
        BedwarsPRO.getInstance().getPlayerStatisticManager().resetStatistic(target);
        sender.sendMessage(ChatColor.GREEN + "已清除玩家 " + target.getName()
                + " 的起床战争统计数据。");
        return true;
    }

    /**
     * /bwpro clearrecord &lt;地图名&gt; [确认码] — 清除地图最快通关记录（确认码二次确认）。
     *
     * <p>第一次输入只生成确认码并警告，第二次必须带上确认码才会真正执行清除。</p>
     */
    private boolean handleClearRecord(CommandSender sender, String[] args) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(ChatColor.RED + "用法: /bwpro clearrecord <地图名> [确认码]");
            return true;
        }
        String mapName = args[1];
        Game game = BedwarsPRO.getInstance().getGameManager().getGame(mapName);
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "未找到地图: " + mapName);
            return true;
        }
        if (args.length == 2) {
            // 第一步：生成确认码，等待二次确认
            String code = issueConfirmCode(sender, "clearrecord", mapName);
            sender.sendMessage(ChatColor.RED + "警告：即将清除地图 " + mapName
                    + " 的最快通关记录（当前: " + game.getFormattedRecord() + "）！");
            sender.sendMessage(ChatColor.GOLD + "确认码: " + code + ChatColor.GRAY + "（60 秒内有效）");
            sender.sendMessage(ChatColor.GOLD + "请再次输入 /bwpro clearrecord " + mapName
                    + " " + code + " 以执行清除。");
            return true;
        }
        // 第二步：校验确认码
        if (!checkConfirmCode(sender, "clearrecord", mapName, args[2])) {
            sender.sendMessage(ChatColor.RED + "确认码错误或已过期，请重新输入 /bwpro clearrecord "
                    + mapName + " 获取新的确认码。");
            return true;
        }
        game.setRecord(BedwarsPRO.getInstance().getMaxLength());
        game.getRecordHolders().clear();
        game.saveRecord();
        sender.sendMessage(ChatColor.GREEN + "已清除地图 " + mapName + " 的最快通关记录。");
        return true;
    }

    /** 生成 6 位随机确认码并登记待确认操作。 */
    private String issueConfirmCode(CommandSender sender, String type, String target) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        pendingClears.put(confirmKey(sender, type, target),
                new PendingConfirm(code, System.currentTimeMillis() + CONFIRM_TTL_MS));
        return code;
    }

    /** 校验确认码：匹配且未过期返回 true（无论成败都会移除该待确认项）。 */
    private boolean checkConfirmCode(CommandSender sender, String type, String target, String code) {
        PendingConfirm p = pendingClears.remove(confirmKey(sender, type, target));
        if (p == null) {
            return false;
        }
        if (System.currentTimeMillis() > p.expiresAt) {
            return false;
        }
        return p.code.equals(code);
    }

    /** 待确认项的唯一键：发送者|类型|目标（均小写）。 */
    private String confirmKey(CommandSender sender, String type, String target) {
        return sender.getName().toLowerCase() + "|" + type + "|" + target.toLowerCase();
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
        // 仅支持发布追杀令
        TaskMessages.msg(sender, "timed-publish-only-bounty");
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

    private boolean handleTaskRefresh(CommandSender sender, String[] args, TaskManager tm) {
        if (!hasAdminPerm(sender)) {
            TaskMessages.msg(sender, "cmd-no-permission");
            return true;
        }
        boolean refreshDaily = true;
        boolean refreshWeekly = true;
        if (args.length >= 3) {
            String type = args[2].toLowerCase();
            if (type.equals("daily")) {
                refreshWeekly = false;
            } else if (type.equals("weekly")) {
                refreshDaily = false;
            }
        }
        if (refreshDaily) {
            tm.forceRefreshDaily();
            TaskMessages.msg(sender, "cmd-taskrefresh-daily");
        }
        if (refreshWeekly) {
            tm.forceRefreshWeekly();
            TaskMessages.msg(sender, "cmd-taskrefresh-weekly");
        }
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
            TaskMessages.msg(sender, "help-scoreboard-reload");
            TaskMessages.msg(sender, "help-joinitem-reload");
            TaskMessages.msg(sender, "help-taskrefresh");
            TaskMessages.msg(sender, "help-remove");
            TaskMessages.msg(sender, "help-clear");
            TaskMessages.msg(sender, "help-reset");
            TaskMessages.msg(sender, "help-clearstats");
            TaskMessages.msg(sender, "help-clearrecord");
            sender.sendMessage(ChatColor.AQUA + "/bwpro check " + ChatColor.GRAY + "- 检查插件版本是否有更新");
            sender.sendMessage(ChatColor.AQUA + "/bwpro update " + ChatColor.GRAY + "- 提交更新请求（confirm=二次确认 / cancel=取消）");
            sender.sendMessage(ChatColor.AQUA + "/bwpro debug <on|off|status> " + ChatColor.GRAY + "- Bot 调试日志开关（默认关闭）");
        }
        if (sender.hasPermission(PERM_PUBLISH) || sender.isOp()) {
            TaskMessages.msg(sender, "help-publish");
        }
    }

    /** /bwpro info — 显示本服务器的授权信息（服务器UUID / 插件授权码 / 授权服务器地址）。 */
    private boolean handleAuthInfo(CommandSender sender) {
        BedwarsPRO plugin = BedwarsPRO.getInstance();
        String sid = plugin.getConfig().getString("auth-server-id", "");
        String md5 = AuthManager.jarMd5(plugin.getPluginJarFile());
        sender.sendMessage(ChatColor.WHITE + "---------------------------------");
        sender.sendMessage(ChatColor.AQUA + "          BedwarsPRO 授权信息");
        sender.sendMessage(ChatColor.GREEN + "服务器UUID: " + ChatColor.YELLOW + sid);
        sender.sendMessage(ChatColor.GREEN + "插件授权码: " + ChatColor.YELLOW + md5);
        sender.sendMessage(ChatColor.GREEN + "授权服务器: " + ChatColor.YELLOW + Str.s(Cfg.URL));
        sender.sendMessage(ChatColor.GREEN + "备用授权服务器: " + ChatColor.YELLOW + Str.s(Cfg.URL_BACKUP));
        sender.sendMessage(ChatColor.WHITE + "---------------------------------");
        return true;
    }
}
