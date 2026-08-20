package io.jmmym.bedwarspro.auth;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 版本更新流程编排（心跳轮询 + 聊天框二次确认 + 下载校验替换 jar + 自动关服重启）。
 *
 * <p>流程：/bwpro check 查版本 → /bwpro update 提交请求 → 网站后台同意/拒绝 →
 * 心跳检测到 approved 后向在线管理员发送可点击的聊天消息 → 管理员点击确认 →
 * 下载新 jar → 校验 MD5 → 替换当前插件文件 → 广播后自动 {@link Bukkit#shutdown()} 重启。
 *
 * <p>网络请求均在异步线程执行（心跳本身异步；命令入口自行调度异步任务）。
 */
public final class UpdateFlow {

    /** 已处理过的请求标记（file|status），避免每个心跳周期重复提示/重复下载。 */
    private static final Set<String> HANDLED = ConcurrentHashMap.newKeySet();
    /** 正在安装更新（下载/替换中），防止并发重复安装。 */
    private static volatile boolean installing = false;

    private UpdateFlow() {
    }

    // ==================== /bwpro check ====================

    /** /bwpro check — 查询授权服务器最新版本并提示是否更新。 */
    public static void checkCommand(final BedwarsPRO plugin, final CommandSender sender) {
        final String sid = sidOf(plugin);
        final String ver = versionOf(plugin);
        final boolean ac = authCheckOf(plugin);
        if (sid.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "[更新] 未找到服务器 UUID（config.yml 的 auth-server-id），无法检查更新。");
            return;
        }
        sender.sendMessage(ChatColor.GRAY + "[更新] 正在检查授权服务器上的插件版本…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final UpdateManager.CheckResult r = UpdateManager.check(plugin.getPluginJarFile(), sid, ver, ac);
                final UpdateManager.CheckResult result = r;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        printCheck(sender, result, ver);
                    }
                });
            }
        });
    }

    private static void printCheck(CommandSender sender, UpdateManager.CheckResult r, String localVer) {
        if (r == null) {
            sender.sendMessage(ChatColor.RED + "[更新] 无法连接授权服务器，请稍后重试（/bwpro check）。");
            return;
        }
        sender.sendMessage(ChatColor.WHITE + "---------------- " + ChatColor.AQUA + "BedwarsPRO 版本检测"
                + ChatColor.WHITE + " ----------------");
        String cur = r.current.isEmpty() ? localVer : r.current;
        sender.sendMessage(ChatColor.GREEN + "当前版本: " + ChatColor.YELLOW + cur);
        if (r.latest.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "最新版本: " + ChatColor.YELLOW + "网站后台 update/ 目录中暂无新插件");
        } else {
            sender.sendMessage(ChatColor.GREEN + "最新版本: " + ChatColor.YELLOW + r.latest
                    + ChatColor.GRAY + "（" + r.file + "）");
        }
        if (!r.pending.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "当前更新请求状态: " + statusLabel(r.pending));
        }
        if (r.update) {
            sender.sendMessage(ChatColor.GREEN + "检测到新版本！输入 " + ChatColor.AQUA + "/bwpro update"
                    + ChatColor.GREEN + " 向网站后台提交更新请求。");
        } else if (r.latest.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "请先在网站后台将新插件 jar 上传到 update/ 目录。");
        } else {
            sender.sendMessage(ChatColor.GREEN + "已是最新版本，无需更新。");
        }
    }

    // ==================== /bwpro update ====================

    /**
     * /bwpro update — 提交更新请求；/bwpro update confirm — 二次确认并下载安装；
     * /bwpro update cancel — 取消本次已同意的更新。
     */
    public static void updateCommand(final BedwarsPRO plugin, final CommandSender sender, final String[] args) {
        final String sid = sidOf(plugin);
        final String ver = versionOf(plugin);
        final boolean ac = authCheckOf(plugin);
        if (sid.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "[更新] 未找到服务器 UUID（config.yml 的 auth-server-id），无法更新。");
            return;
        }
        final String sub = args.length >= 2 ? args[1].toLowerCase() : "";
        if (sub.equals("confirm")) {
            confirmNow(plugin, sender);
            return;
        }
        if (sub.equals("cancel")) {
            cancelNow(plugin, sender);
            return;
        }
        // 提交更新请求
        sender.sendMessage(ChatColor.GRAY + "[更新] 正在向网站后台提交更新请求…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final UpdateManager.StatusResult r = UpdateManager.request(plugin.getPluginJarFile(), sid, ver, ac);
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (r == null) {
                            sender.sendMessage(ChatColor.RED + "[更新] 无法连接授权服务器，提交失败，请稍后重试。");
                            return;
                        }
                        if (!r.ok) {
                            sender.sendMessage(ChatColor.RED + "[更新] 提交失败：" + reasonLabel(r.reason));
                            return;
                        }
                        sender.sendMessage(ChatColor.GREEN + "[更新] 更新请求已提交（" + ver + " → "
                                + (r.target.isEmpty() ? "新版本" : r.target) + "）。");
                        sender.sendMessage(ChatColor.GOLD + "[更新] 等待网站后台「检测更新」页审核，通过后我会在此提醒你确认。");
                    }
                });
            }
        });
    }

    /** 服务端二次确认：查状态 → update_confirm → 下载安装。 */
    private static void confirmNow(final BedwarsPRO plugin, final CommandSender sender) {
        final String sid = sidOf(plugin);
        final boolean ac = authCheckOf(plugin);
        sender.sendMessage(ChatColor.GRAY + "[更新] 正在确认更新…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final UpdateManager.StatusResult st = UpdateManager.status(plugin.getPluginJarFile(), sid, ac);
                if (st == null) {
                    msg(sender, ChatColor.RED + "[更新] 无法连接授权服务器，请稍后重试。");
                    return;
                }
                if (!UpdateManager.ST_APPROVED.equals(st.status)) {
                    msg(sender, ChatColor.RED + "[更新] 当前没有已同意待确认的更新请求（状态："
                            + statusLabel(st.status) + "）。");
                    return;
                }
                if (!UpdateManager.confirm(plugin.getPluginJarFile(), sid, ac)) {
                    msg(sender, ChatColor.RED + "[更新] 确认失败（请求可能已被取消或超时），请稍后重试。");
                    return;
                }
                msg(sender, ChatColor.GREEN + "[更新] 已确认，开始下载新插件并自动重启服务器…");
                downloadAndInstall(plugin, sid,
                        st.file == null ? "" : st.file,
                        st.fileMd5 == null ? "" : st.fileMd5);
            }
        });
    }

    /** 服务端取消：把已同意的请求标记为 cancelled（后台不再抖动提醒）。 */
    private static void cancelNow(final BedwarsPRO plugin, final CommandSender sender) {
        final String sid = sidOf(plugin);
        final boolean ac = authCheckOf(plugin);
        sender.sendMessage(ChatColor.GRAY + "[更新] 正在取消更新…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final boolean ok = UpdateManager.cancel(plugin.getPluginJarFile(), sid, ac);
                msg(sender, ok
                        ? ChatColor.GREEN + "[更新] 已取消本次更新，服务器保持当前版本继续运行。"
                        : ChatColor.RED + "[更新] 取消失败：当前没有已同意待确认的更新请求。");
            }
        });
    }

    // ==================== 心跳轮询 ====================

    /**
     * 心跳周期调用（异步线程）：
     * approved → 向在线管理员发送可点击的聊天确认；rejected/cancelled → 说明原因（仅一次）；
     * confirmed → 直接下载安装并重启。
     */
    public static void poll(final BedwarsPRO plugin, final String sid, final boolean authCheck) {
        if (installing) {
            return;
        }
        final UpdateManager.StatusResult st = UpdateManager.status(plugin.getPluginJarFile(), sid, authCheck);
        if (st == null) {
            return;
        }
        if (UpdateManager.ST_PENDING.equals(st.status) || UpdateManager.ST_NONE.equals(st.status)) {
            // 新一轮请求开始：清空已处理标记，保证后续批准/拒绝能重新提示
            HANDLED.clear();
            return;
        }
        final String file = st.file == null ? "" : st.file;
        final String key = file + "|" + st.status;
        if (UpdateManager.ST_APPROVED.equals(st.status)) {
            if (HANDLED.add(key)) {
                promptConfirm(plugin, st);
            }
        } else if (UpdateManager.ST_REJECTED.equals(st.status)) {
            if (HANDLED.add(key)) {
                notifyStatus(plugin, st, "网站已拒绝本次更新请求。", "拒绝原因：" + (st.reason.isEmpty() ? "未填写" : st.reason));
            }
        } else if (UpdateManager.ST_CANCELLED.equals(st.status)) {
            if (HANDLED.add(key)) {
                notifyStatus(plugin, st, "本次更新已被取消，服务器保持当前版本。",
                        st.reason == null || st.reason.isEmpty() || "server_cancelled".equals(st.reason)
                                ? null : "原因：" + st.reason);
            }
        } else if (UpdateManager.ST_PUSHED.equals(st.status)) {
            // 后台批量推送更新：10 秒冷静期内（cooldown=true）等待（可反悔），冷静期结束后自动确认并下载安装重启
            handlePushed(plugin, st, sid, authCheck, file);
        } else if (UpdateManager.ST_CONFIRMED.equals(st.status)) {
            if (HANDLED.add(key)) {
                plugin.getLogger().info("[更新] 更新已确认，开始下载新插件…");
                if (!downloadAndInstall(plugin, sid, file, st.fileMd5 == null ? "" : st.fileMd5)) {
                    // 网络类失败：下个心跳周期自动重试
                    HANDLED.remove(key);
                }
            }
        }
    }

    /**
     * 网站后台批量推送更新（无需服务器二次确认）：
     * 1. 10 秒冷静期内（cooldown=true）：仅提醒一次，等待站长在网站端反悔取消或冷静期结束；
     * 2. 冷静期结束后（cooldown=false）：自动确认（update_push_ack）→ 下载安装并重启。
     * 心跳线程调用。
     */
    private static void handlePushed(final BedwarsPRO plugin, final UpdateManager.StatusResult st,
                                     final String sid, final boolean authCheck, final String file) {
        if (st.cooldown) {
            final String waitKey = file + "|push_wait";
            if (HANDLED.add(waitKey)) {
                notifyStatus(plugin, st,
                        "网站后台已推送批量更新（10 秒冷静期内可在网站端反悔取消），冷静期结束后本服务器将自动更新并重启。", null);
            }
            return;
        }
        final String goKey = file + "|push_go";
        if (HANDLED.add(goKey)) {
            plugin.getLogger().info("[更新] 网站后台已推送批量更新，10 秒冷静期已过，服务器自动确认并开始下载新插件…");
            // 服务器第 30 秒心跳才首次感知到推送时冷静期往往已过，仍必须通知在线管理员
            notifyStatus(plugin, st,
                    "网站后台已推送批量更新，10 秒冷静期已过，本服务器即将自动下载新插件并自动重启（无需二次确认）。",
                    "若站长在冷静期内反悔取消了本次推送，本服务器会保持当前版本不更新。");
            if (UpdateManager.ackPush(plugin.getPluginJarFile(), sid, authCheck, versionOf(plugin))) {
                if (!downloadAndInstall(plugin, sid, file, st.fileMd5 == null ? "" : st.fileMd5)) {
                    // 网络类失败：下个心跳周期自动重试
                    HANDLED.remove(goKey);
                }
            } else {
                // 确认失败（请求可能已被网站在冷静期内取消或已被覆盖）：下个心跳周期根据最新状态重新判断
                plugin.getLogger().warning("[更新] 推送更新确认失败（请求可能已被取消），下个心跳周期自动重试。");
                HANDLED.remove(goKey);
            }
        }
    }

    /**
     * 插件启动时自动检测一次版本差异（onEnable 调用，内部异步执行不阻塞主线程）。
     * 发现后台 update/ 目录有更高版本且当前无进行中的更新请求时，
     * 向控制台与在线管理员各提示一次：输入 /bwpro update 提交更新请求。
     */
    public static void startupCheck(final BedwarsPRO plugin) {
        final String sid = sidOf(plugin);
        final boolean authCheck = authCheckOf(plugin);
        final String ver = versionOf(plugin);
        if (sid.isEmpty() || ver.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                versionCheckIfNeeded(plugin, sid, authCheck);
            }
        });
    }

    /**
     * 检测版本差异（异步线程调用）：发现后台 update/ 目录有更高版本时，
     * 向控制台与在线管理员各提醒一次（存在进行中的更新请求时不再重复提醒）。
     */
    private static void versionCheckIfNeeded(final BedwarsPRO plugin, final String sid, final boolean authCheck) {
        final String ver = versionOf(plugin);
        if (sid.isEmpty() || ver.isEmpty()) {
            return;
        }
        final UpdateManager.CheckResult r = UpdateManager.check(plugin.getPluginJarFile(), sid, ver, authCheck);
        if (r == null) {
            // 网络异常 / 后台无响应：无法判定版本，明确提示，避免误以为检测功能失效
            plugin.getLogger().info("[更新] 启动版本检测失败：无法连接授权服务器，"
                    + "服务器网络恢复后可执行 /bwpro check 手动检测。");
            return;
        }
        final String latest = r.latest == null ? "" : r.latest;
        if (!r.update || latest.isEmpty()) {
            // 已是最新（或后台 update/ 目录暂无新插件）：每次启动明确输出一次检测结果
            plugin.getLogger().info("[更新] 启动版本检测完成：当前已是最新版本（"
                    + (r.current == null || r.current.isEmpty() ? ver : r.current) + "）。");
            return;
        }
        // 已提交过更新请求（pending/approved/confirmed）时不再重复提醒；
        // finished（上次更新已完成）不拦截：它针对的是旧目标文件，后台判定有新版本时仍须提示。
        final String st = r.pending == null ? "" : r.pending;
        if (!st.isEmpty() && !UpdateManager.ST_NONE.equals(st)
                && !UpdateManager.ST_REJECTED.equals(st) && !UpdateManager.ST_CANCELLED.equals(st)
                && !UpdateManager.ST_FINISHED.equals(st)) {
            return;
        }
        final String file = r.file == null ? "" : r.file;
        plugin.getLogger().info("[更新] 检测到新版本 " + latest + "（" + file + "），"
                + "请管理员执行 /bwpro update 提交更新请求。");
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("bwpro.task.admin") || p.isOp()) {
                        p.sendMessage(ChatColor.GOLD + "[BedwarsPRO 更新] 检测到新版本 "
                                + ChatColor.AQUA + latest
                                + ChatColor.GOLD + "（" + file + "）。");
                        p.sendMessage(ChatColor.GREEN + "输入 " + ChatColor.AQUA + "/bwpro update"
                                + ChatColor.GREEN + " 可向网站后台提交更新请求；"
                                + ChatColor.AQUA + "/bwpro check" + ChatColor.GREEN + " 可查看详情。");
                    }
                }
            }
        });
    }

    /** 网站已同意：向在线管理员发送可点击的聊天消息（点击执行确认/取消命令）。 */
    private static void promptConfirm(final BedwarsPRO plugin, final UpdateManager.StatusResult st) {
        final String target = st.target == null ? "" : st.target;
        final String file = st.file == null ? "" : st.file;
        plugin.getLogger().info("[更新] 网站后台已同意版本更新（" + st.curVer + " → " + target + "）。"
                + "请管理员在游戏内点击上方「✔ 确认更新」聊天消息快速确认，或手动输入 /bwpro update confirm；"
                + "输入 /bwpro update cancel 可取消本次更新。");
        final TextComponent main = new TextComponent("[BedwarsPRO 更新] 网站已同意将插件更新到 "
                + target + "（" + file + "）。点击下方「✔ 确认更新」可快速确认下载并重启服务器：");
        main.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        final TextComponent yes = new TextComponent(" [✔ 确认更新] ");
        yes.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        yes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bwpro update confirm"));
        yes.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder("点击后立即确认更新并下载新插件").create()));
        final TextComponent no = new TextComponent("[✘ 取消]");
        no.setColor(net.md_5.bungee.api.ChatColor.RED);
        no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bwpro update cancel"));
        no.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder("点击后取消本次更新").create()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("bwpro.task.admin") || p.isOp()) {
                p.spigot().sendMessage(main, yes, no);
            }
        }
    }

    /** 拒绝/取消等状态通知（控制台 + 在线管理员各提示一次）。 */
    private static void notifyStatus(final BedwarsPRO plugin, final UpdateManager.StatusResult st, String head, String detail) {
        plugin.getLogger().info("[更新] " + head + (detail == null ? "" : " " + detail));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("bwpro.task.admin") || p.isOp()) {
                p.sendMessage(ChatColor.RED + "[更新] " + head);
                if (detail != null) {
                    p.sendMessage(ChatColor.GRAY + "[更新] " + detail);
                }
            }
        }
    }

    // ==================== 下载 / 校验 / 替换 / 重启 ====================

    /** 下载进度：向在线管理员广播百分比（主线程）。 */
    private static void broadcastProgress(final BedwarsPRO plugin, final int pct) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("bwpro.task.admin") || p.isOp()) {
                        p.sendMessage(ChatColor.GOLD + "[更新] 下载进度：" + pct + "%");
                    }
                }
            }
        });
    }

    /**
     * 下载新插件 jar → 校验 MD5 → 替换当前插件文件 → 广播后自动关服重启。
     * 必须从异步线程调用（内部含阻塞 IO）。
     *
     * @return true = 本次已处理完成（无需自动重试）；false = 网络类失败，建议稍后自动重试
     */
    private static boolean downloadAndInstall(final BedwarsPRO plugin, final String sid,
                                              final String file, final String fileMd5) {
        if (!tryBeginInstall()) {
            return true;
        }
        if (file == null || file.isEmpty()) {
            plugin.getLogger().warning("[更新] 缺少更新文件名，本次更新中止。");
            endInstall();
            return true;
        }
        // 本服务器已经在运行目标版本（更新完成重启后再次轮询到 confirmed 时的防重复保护）
        if (!fileMd5.isEmpty()
                && AuthManager.jarMd5(plugin.getPluginJarFile()).equalsIgnoreCase(fileMd5)) {
            plugin.getLogger().info("[更新] 本服务器已运行目标版本（" + file + "），无需重复更新。");
            // 通知后台本次更新已完成，避免请求列表卡在「服务器已确认，正在更新」
            notifyFinished(plugin, sid);
            endInstall();
            return true;
        }
        plugin.getLogger().info("[更新] 正在下载新插件：" + file);
        final long[] lastPct = {-1};
        final long[] lastKb = {0};
        final byte[] data = UpdateManager.download(plugin.getPluginJarFile(), sid, file,
                new Post.ProgressListener() {
                    @Override
                    public void onProgress(long read, long total) {
                        if (total > 0) {
                            int pct = (int) (read * 100 / total);
                            if (pct != lastPct[0]) {
                                lastPct[0] = pct;
                                plugin.getLogger().info("[更新] 下载进度：" + pct + "%（"
                                        + (read / 1024) + "/" + (total / 1024) + " KB）");
                                if (pct % 20 == 0) {
                                    final int fp = pct;
                                    broadcastProgress(plugin, fp);
                                }
                            }
                        } else {
                            long kb = read / 1024;
                            if (kb - lastKb[0] >= 512) {
                                lastKb[0] = kb;
                                plugin.getLogger().info("[更新] 下载进度：" + kb + " KB…");
                            }
                        }
                    }
                });
        if (data == null || data.length == 0) {
            plugin.getLogger().warning("[更新] 下载新插件失败，将自动重试（也可稍后输入 /bwpro update confirm 手动重试）。");
            endInstall();
            return false;
        }
        final File target = plugin.getPluginJarFile();
        final File pluginsDir = target.getParentFile();
        final File tmp = new File(pluginsDir, target.getName() + ".update.tmp");
        try {
            java.nio.file.Files.write(tmp.toPath(), data);
        } catch (Exception e) {
            plugin.getLogger().warning("[更新] 写入临时文件失败：" + e.getMessage());
            endInstall();
            return true;
        }
        // 校验 MD5，防止下载损坏或文件被篡改
        final String got = AuthManager.jarMd5(tmp);
        if (!fileMd5.isEmpty() && !got.equalsIgnoreCase(fileMd5)) {
            plugin.getLogger().warning("[更新] 下载文件校验失败（期望 " + fileMd5 + "，实际 " + got
                    + "），本次更新中止。请站长检查 update/ 目录中的文件。");
            tmp.delete();
            endInstall();
            return true;
        }
        // 替换当前插件 jar：Linux 可直接覆盖；Windows 上运行中的 jar 被 JVM 锁定，改用延迟替换脚本
        boolean ok = false;
        boolean viaScript = false;
        try {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ok = true;
        } catch (Exception e) {
            try {
                if (target.exists() && !target.delete()) {
                    throw new Exception("旧插件文件无法删除（可能被 JVM 锁定）");
                }
                java.nio.file.Files.move(tmp.toPath(), target.toPath());
                ok = true;
            } catch (Exception e2) {
                // Windows：jar 被 JVM 打开句柄锁定，删除/覆盖均失败。
                // 把新 jar 保存为 <插件名>.new，并生成延迟替换 bat（等待服务器进程退出后自动完成替换）。
                try {
                    java.nio.file.Path newFile = pluginsDir.toPath().resolve(target.getName() + ".new");
                    java.nio.file.Files.move(tmp.toPath(), newFile,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    java.nio.file.Path bat = pluginsDir.toPath().resolve("apply_update.bat");
                    // 注意：不要删除 bat 自身（del "%~f0"）——cmd 运行中的批处理被删后
                    // 继续读取下一行会弹出「请插入包含批处理文件的软盘，然后按任意键」。
                    // bat 保留在 plugins/ 目录无害，下次更新同名覆盖。
                    String script = "@echo off\r\n"
                            + "rem BedwarsPRO auto-update: wait for server exit then replace jar\r\n"
                            + "timeout /t 15 /nobreak >nul\r\n"
                            + "move /y \"%~dp0" + target.getName() + ".new\" \"%~dp0" + target.getName() + "\"\r\n"
                            + "exit\r\n";
                    java.nio.file.Files.write(bat, script.getBytes("UTF-8"));
                    new ProcessBuilder("cmd", "/c", "start", "/min", "", bat.toAbsolutePath().toString()).start();
                    viaScript = true;
                    ok = true;
                } catch (Exception e3) {
                    plugin.getLogger().warning("[更新] 替换插件文件失败：" + e3.getMessage()
                            + "。服务器重启后请手动将新插件放到 plugins/ 目录。");
                }
            }
        }
        final boolean okf = ok;
        final boolean scriptf = viaScript;
        // 替换成功后同步版本日志 README.txt（jar 内 images/README.txt → 插件目录 images/）
        if (okf) {
            try {
                io.jmmym.bedwarspro.scoreboard.config.Config.syncReadme();
            } catch (Exception e) {
                // 同步失败不影响更新流程，下次启动 loadImages 会再同步
            }
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!okf) {
                    plugin.getLogger().warning("[更新] 替换插件文件失败，本次更新中止。");
                    endInstall();
                    return;
                }
                if (scriptf) {
                    plugin.getLogger().info("[更新] 旧插件文件被系统锁定，已启动自动替换脚本（apply_update.bat），"
                            + "服务器关闭后会自动完成替换，10 秒后自动重启服务器…");
                } else {
                    plugin.getLogger().info("[更新] 新插件已就绪（" + file + "），10 秒后自动重启服务器…");
                }
                // 通知后台本次更新已完成（替换成功即视为完成，脚本场景由重启后「已运行目标版本」兜底）
                notifyFinished(plugin, sid);
                // 更新提示只发给在线管理员（控制台已有 getLogger 日志），普通玩家不可见
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("bwpro.task.admin") || p.isOp()) {
                        p.sendMessage(ChatColor.GOLD + "[BedwarsPRO 更新] 插件已更新到新版本，服务器将于 10 秒后自动重启。");
                    }
                }
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override
                    public void run() {
                        Bukkit.shutdown();
                    }
                }, 200L);
            }
        });
        return true;
    }

    private static synchronized boolean tryBeginInstall() {
        if (installing) {
            return false;
        }
        installing = true;
        return true;
    }

    private static synchronized void endInstall() {
        installing = false;
    }

    /** 异步通知后台本次更新已完成（避免请求卡在 confirmed）。 */
    private static void notifyFinished(final BedwarsPRO plugin, final String sid) {
        final String s = sid == null ? "" : sid;
        final boolean ac = authCheckOf(plugin);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (UpdateManager.finish(plugin.getPluginJarFile(), s, ac,
                            AuthManager.jarMd5(plugin.getPluginJarFile()))) {
                        plugin.getLogger().info("[更新] 已通知网站后台本次更新完成。");
                    } else {
                        plugin.getLogger().info("[更新] 通知后台更新完成失败（本机可能尚未运行目标版本，"
                                + "或请求已被取消，后台可手动处理该记录）。");
                    }
                } catch (Exception e) {
                    plugin.getLogger().info("[更新] 通知后台更新完成异常：" + e.getMessage());
                }
            }
        }, "bwpro-update-finish").start();
    }

    // ==================== 工具 ====================

    private static String sidOf(BedwarsPRO plugin) {
        return plugin.getConfig().getString("auth-server-id", "");
    }

    private static String versionOf(BedwarsPRO plugin) {
        try {
            String v = plugin.getDescription().getVersion();
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean authCheckOf(BedwarsPRO plugin) {
        return plugin.getConfig().getBoolean("auth-check", true);
    }

    /** 请求状态 → 中文说明。 */
    private static String statusLabel(String s) {
        if (s == null) {
            return "未知";
        }
        if (UpdateManager.ST_PENDING.equals(s)) {
            return "待网站审核";
        }
        if (UpdateManager.ST_APPROVED.equals(s)) {
            return "网站已同意，等待二次确认（输入 /bwpro update confirm）";
        }
        if (UpdateManager.ST_CONFIRMED.equals(s)) {
            return "已确认，正在更新";
        }
        if (UpdateManager.ST_PUSHED.equals(s)) {
            return "后台已推送（10 秒冷静期内，即将自动更新）";
        }
        if (UpdateManager.ST_REJECTED.equals(s)) {
            return "已被网站拒绝";
        }
        if (UpdateManager.ST_CANCELLED.equals(s)) {
            return "已取消";
        }
        if (UpdateManager.ST_FINISHED.equals(s)) {
            return "已是最新版本，无需更新";
        }
        if (UpdateManager.ST_NONE.equals(s)) {
            return "无更新请求";
        }
        return s;
    }

    /** 后台返回的错误码 → 中文说明。 */
    private static String reasonLabel(String r) {
        if (r == null || r.isEmpty()) {
            return "未知错误";
        }
        if ("not_licensed".equals(r)) {
            return "插件未授权";
        }
        if ("no_update_file".equals(r)) {
            return "网站后台 update/ 目录没有新的插件文件";
        }
        if ("bad_sig".equals(r)) {
            return "签名校验失败";
        }
        if ("stale_ts".equals(r)) {
            return "时间戳过期";
        }
        if ("not_approved".equals(r)) {
            return "更新请求尚未被网站同意";
        }
        return r;
    }

    /** 回主线程发消息（命令可能在异步流程中）。 */
    private static void msg(final CommandSender sender, final String text) {
        if (sender == null) {
            return;
        }
        Bukkit.getScheduler().runTask(BedwarsPRO.getInstance(), new Runnable() {
            @Override
            public void run() {
                sender.sendMessage(text);
            }
        });
    }
}
