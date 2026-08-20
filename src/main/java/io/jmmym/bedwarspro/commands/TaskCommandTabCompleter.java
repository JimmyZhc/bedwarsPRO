package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameManager;
import io.jmmym.bedwarspro.task.TaskManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * /bwpro 命令的 Tab 补齐。
 *
 * <p>补齐层级：
 * <ul>
 *   <li>第1级: task</li>
 *   <li>第2级: gui / info / list / on / off / random / weekly / wrandom / reload
 *       / publish / remove / clear / reset / help（按权限过滤）</li>
 *   <li>第3级: random|weekly|wrandom → on|off；publish → 任务名；
 *       remove → 可用ID；reset → 在线玩家名</li>
 *   <li>第4级: reset → daily|weekly|timed|all</li>
 * </ul></p>
 */
public class TaskCommandTabCompleter implements TabCompleter {

    private static final List<String> SUB_COMMANDS_PLAYER =
            Arrays.asList("gui", "info", "list", "help");
    private static final List<String> SUB_COMMANDS_ADMIN =
            Arrays.asList("gui", "info", "list", "on", "off", "random",
                    "weekly", "wrandom", "reload", "remove", "clear", "reset", "taskrefresh", "help");
    private static final List<String> SUB_COMMANDS_PUBLISHER =
            Arrays.asList("gui", "info", "list", "on", "off", "random",
                    "weekly", "wrandom", "reload", "publish", "remove", "clear", "reset", "taskrefresh", "help");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label,
                                      String[] args) {
        List<String> suggest = getSuggest(sender, args);
        if (suggest == null || suggest.isEmpty()) {
            return new ArrayList<>();
        }
        String last = args[args.length - 1];
        if (last.isEmpty()) {
            return suggest;
        }
        List<String> filtered = new ArrayList<>();
        for (String s : suggest) {
            if (s.toLowerCase().startsWith(last.toLowerCase())) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    private List<String> getSuggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> firstLevel = new ArrayList<>();
            if ("task".startsWith(args[0].toLowerCase())) {
                firstLevel.add("task");
            }
            if ("reload".startsWith(args[0].toLowerCase())) {
                firstLevel.add("reload");
            }
            if ("quickstash".startsWith(args[0].toLowerCase())) {
                firstLevel.add("quickstash");
            }
            if ("scoreboard".startsWith(args[0].toLowerCase())) {
                firstLevel.add("scoreboard");
            }
            if ("joinitem".startsWith(args[0].toLowerCase())) {
                firstLevel.add("joinitem");
            }
            if (isAdmin(sender)) {
                if ("mapgui".startsWith(args[0].toLowerCase())) {
                    firstLevel.add("mapgui");
                }
                if ("rankreload".startsWith(args[0].toLowerCase())) {
                    firstLevel.add("rankreload");
                }
                if ("debug".startsWith(args[0].toLowerCase())) {
                    firstLevel.add("debug");
                }
                if ("clearstats".startsWith(args[0].toLowerCase())) {
                    firstLevel.add("clearstats");
                }
                if ("clearrecord".startsWith(args[0].toLowerCase())) {
                    firstLevel.add("clearrecord");
                }
                if ("check".startsWith(args[0].toLowerCase())) {
                    firstLevel.add("check");
                }
                if ("update".startsWith(args[0].toLowerCase())) {
                    firstLevel.add("update");
                }
            }
            return firstLevel;
        }
        // /bwpro clearstats <玩家> [确认码]
        if (args[0].equalsIgnoreCase("clearstats")) {
            if (!isAdmin(sender)) {
                return new ArrayList<>();
            }
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                for (OfflinePlayer op : Bukkit.getOnlinePlayers()) {
                    names.add(op.getName());
                }
                return names;
            }
            // 第3参数为随机生成的确认码，无法补全
            return new ArrayList<>();
        }
        // /bwpro clearrecord <地图> [确认码]
        if (args[0].equalsIgnoreCase("clearrecord")) {
            if (!isAdmin(sender)) {
                return new ArrayList<>();
            }
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                GameManager gm = BedwarsPRO.getInstance().getGameManager();
                if (gm != null) {
                    for (Game game : gm.getGames()) {
                        names.add(game.getName());
                    }
                }
                return names;
            }
            // 第3参数为随机生成的确认码，无法补全
            return new ArrayList<>();
        }
        // /bwpro quickstash <gui|on|off|status|reload>
        if (args[0].equalsIgnoreCase("quickstash")) {
            if (args.length == 2) {
                List<String> qs = new ArrayList<>();
                qs.add("gui");
                qs.add("on");
                qs.add("off");
                qs.add("status");
                if (sender.hasPermission("bwpro.quickstash.admin") || sender.isOp()) {
                    qs.add("reload");
                }
                return qs;
            }
            return new ArrayList<>();
        }
        // /bwpro scoreboard <reload>
        if (args[0].equalsIgnoreCase("scoreboard") && args.length == 2) {
            if (sender.hasPermission("bwpro.task.reload")
                    || sender.hasPermission("bwpro.task.admin") || sender.isOp()) {
                return Arrays.asList("reload");
            }
            return new ArrayList<>();
        }
        // /bwpro joinitem <reload>
        if (args[0].equalsIgnoreCase("joinitem") && args.length == 2) {
            if (sender.hasPermission("bwpro.joinitem.admin") || sender.isOp()) {
                return Arrays.asList("reload");
            }
            return new ArrayList<>();
        }
        // /bwpro update [confirm|cancel] — 版本更新（admin）
        if (args[0].equalsIgnoreCase("update")) {
            if (!isAdmin(sender)) {
                return new ArrayList<>();
            }
            if (args.length == 2) {
                return Arrays.asList("confirm", "cancel");
            }
            return new ArrayList<>();
        }
        // /bwpro debug <on|off|status> — Bot 调试日志开关（admin）
        if (args[0].equalsIgnoreCase("debug")) {
            if (!isAdmin(sender)) {
                return new ArrayList<>();
            }
            if (args.length == 2) {
                return Arrays.asList("on", "off", "status");
            }
            return new ArrayList<>();
        }
        if (!args[0].equalsIgnoreCase("task")) {
            return new ArrayList<>();
        }
        // 第2级
        if (args.length == 2) {
            if (sender.hasPermission("bwpro.task.publish") || sender.isOp()) {
                return SUB_COMMANDS_PUBLISHER;
            }
            if (sender.hasPermission("bwpro.task.admin")) {
                return SUB_COMMANDS_ADMIN;
            }
            return SUB_COMMANDS_PLAYER;
        }
        // 第3级: random / weekly / wrandom → on|off
        if (args.length == 3 && (args[1].equalsIgnoreCase("random")
                || args[1].equalsIgnoreCase("weekly")
                || args[1].equalsIgnoreCase("wrandom"))) {
            if (sender.hasPermission("bwpro.task.admin") || sender.isOp()) {
                return Arrays.asList("on", "off");
            }
            return new ArrayList<>();
        }
        // 第3级: publish 击杀令 <目标玩家>
        if (args.length == 3 && args[1].equalsIgnoreCase("publish")) {
            if (!sender.hasPermission("bwpro.task.publish") && !sender.isOp()) {
                return new ArrayList<>();
            }
            return Arrays.asList("击杀令");
        }
        // 第4级: publish 击杀令 <目标玩家> — 补齐在线玩家名
        if (args.length == 4 && args[1].equalsIgnoreCase("publish")
                && args[2].equals("击杀令")) {
            if (!sender.hasPermission("bwpro.task.publish") && !sender.isOp()) {
                return new ArrayList<>();
            }
            List<String> names = new ArrayList<>();
            for (OfflinePlayer op : Bukkit.getOnlinePlayers()) {
                names.add(op.getName());
            }
            return names;
        }
        // 第3级: remove <ID> — 补齐当前可用 ID
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            if (!sender.hasPermission("bwpro.task.admin") && !sender.isOp()) {
                return new ArrayList<>();
            }
            TaskManager tm = TaskManager.getInstance();
            if (tm == null) {
                return new ArrayList<>();
            }
            List<String> ids = new ArrayList<>();
            int count = tm.getSpecialTasksList().size();
            for (int i = 1; i <= count; i++) {
                ids.add(String.valueOf(i));
            }
            return ids;
        }
        // 第3级: taskrefresh → daily|weekly
        if (args.length == 3 && args[1].equalsIgnoreCase("taskrefresh")) {
            if (sender.hasPermission("bwpro.task.admin") || sender.isOp()) {
                return Arrays.asList("daily", "weekly");
            }
            return new ArrayList<>();
        }
        // 第3级: reset <player> — 补齐在线玩家名
        if (args.length == 3 && args[1].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("bwpro.task.admin") && !sender.isOp()) {
                return new ArrayList<>();
            }
            List<String> names = new ArrayList<>();
            for (OfflinePlayer op : Bukkit.getOnlinePlayers()) {
                names.add(op.getName());
            }
            return names;
        }
        // 第4级: reset <player> [daily|weekly|timed|all]
        if (args.length == 4 && args[1].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("bwpro.task.admin") && !sender.isOp()) {
                return new ArrayList<>();
            }
            return Arrays.asList("daily", "weekly", "timed", "all");
        }
        return new ArrayList<>();
    }

    /** 是否拥有 bwpro 管理权限（clearstats/clearrecord 需要）。 */
    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("bwpro.task.admin") || sender.isOp();
    }
}
