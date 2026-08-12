package io.jmmym.bedwarspro.auth;

import java.io.File;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;

/**
 * 授权验证入口（onEnable 中调用）与心跳上报。
 *
 * 流程：
 *  1. 计算自身 JAR 的 MD5
 *  2. 组装签名请求体 md5 / ts / sid / sig（HMAC-SHA256，防重放与伪造）
 *  3. POST 到授权服务器
 *  4. 服务器返回 {"ok":true} 视为授权通过
 *
 * 说明：
 *  - 1 个授权码对应 1 个插件，同一插件可部署多台服务器。
 *  - sid（服务器实例唯一标识）由插件生成并持久化到 config.yml，
 *    授权请求与心跳都携带它，授权服务器据此统计“该授权码正在被使用”与在线数量。
 *  - 授权通过后由插件主类周期调用 {@link #heartbeat(File, String)}（每 30 秒一次）。
 *
 * 整个校验过程不需要玩家/管理员任何手动配置。
 */
public final class AuthManager {

    private static final Pattern OK_PATTERN =
            Pattern.compile("\"ok\"\\s*:\\s*true");
    private static final Pattern BANNED_PATTERN =
            Pattern.compile("\"reason\"\\s*:\\s*\"server_banned\"");

    private AuthManager() {
    }

    /** 授权校验结果。 */
    public enum Result {
        /** 授权通过。 */
        OK,
        /** 授权码不在后台白名单（未授权）。 */
        NOT_LICENSED,
        /** 本服务器实例被后台禁用（server_banned），应立即停服。 */
        BANNED,
        /** 无法连接授权服务器 / 请求异常，通常稍后重试即可。 */
        UNREACHABLE
    }

    /** 计算插件 JAR 的 MD5（供控制台打印 / 后台录入白名单）。 */
    public static String jarMd5(File jar) {
        return Md5.of(jar);
    }

    /**
     * 联网校验并返回细分结果：
     * 当后台在“查看”中禁用了本服务器实例时返回 {@link Result#BANNED}，
     * 调用方应据此立即停服；网络异常返回 {@link Result#UNREACHABLE}，可稍后重试。
     *
     * @param jar     本插件 JAR 文件
     * @param sid     服务器实例唯一标识
     * @param authCheck 插件 auth-check 开关（true=严格验证 / false=软管控），随请求上报供后台区分服务器类型
     * @param verbose true 输出请求信息（onEnable 校验时用），false 静默（心跳用）
     * @return 授权结果
     */
    public static Result check(File jar, String sid, boolean authCheck, boolean verbose) {
        try {
            String md5 = jarMd5(jar);
            long ts = System.currentTimeMillis() / 1000L;
            String sig = sign(Str.s(Cfg.SECRET), md5, ts, sid);

            if (verbose) {
                // 输出本插件授权码与请求状态，便于管理员在授权后台录入白名单 / 排查问题
                Bukkit.getLogger().info("[校验系统] 本插件授权码：" + md5);
                Bukkit.getLogger().info("[校验系统] 本服务器UUID：" + (sid == null ? "" : sid));
            }

            String body = payload(md5, ts, sig, sid, authCheck);
            String primary = Str.s(Cfg.URL);
            String backup = Str.s(Cfg.URL_BACKUP);
            if (verbose) {
                Bukkit.getLogger().info("[校验系统] 正在请求授权服务器：" + primary);
            }
            String resp;
            String failDetail = "";
            try {
                resp = Post.json(primary, body, 5000, 5000);
            } catch (Exception e) {
                resp = null;
                failDetail = String.valueOf(e);
            }
            if (resp == null) {
                if (verbose) {
                    Bukkit.getLogger().warning("[校验系统] " + primary + " 无响应（" + failDetail + "），正在尝试备用授权服务器：" + backup);
                }
                try {
                    resp = Post.json(backup, body, 5000, 5000);
                } catch (Exception e) {
                    resp = null;
                    failDetail = String.valueOf(e);
                }
            }
            if (resp == null) {
                if (verbose) {
                    Bukkit.getLogger().warning("[校验系统] 授权服务器无响应（" + backup + "），原因：" + failDetail);
                }
                return Result.UNREACHABLE;
            }
            if (OK_PATTERN.matcher(resp).find()) {
                return Result.OK;
            }
            if (BANNED_PATTERN.matcher(resp).find()) {
                return Result.BANNED;
            }
            return Result.NOT_LICENSED;
        } catch (Exception e) {
            if (verbose) {
                Bukkit.getLogger().warning("[校验系统] 授权服务器连接失败: " + e.getMessage());
            }
            return Result.UNREACHABLE;
        }
    }

    /** HMAC 签名：带 sid 时用 md5|ts|sid（与服务器端一致）。 */
    static String sign(String secret, String md5, long ts, String sid) {
        String base = md5 + "|" + ts;
        if (sid != null && !sid.isEmpty()) {
            base += "|" + sid;
        }
        return Hmac.sha256(secret, base);
    }

    /** 组装 JSON 请求体（固定结构，无需第三方 JSON 库）。 */
    private static String payload(String md5, long ts, String sig, String sid, boolean authCheck) {
        StringBuilder p = new StringBuilder(224);
        p.append('{');
        p.append('"').append(Str.s(Cfg.K_MD5)).append("\":\"").append(md5).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_TS)).append("\":").append(ts);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_AC)).append("\":").append(authCheck ? 1 : 0);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_SIG)).append("\":\"").append(sig).append('"');
        if (sid != null && !sid.isEmpty()) {
            p.append(',');
            p.append('"').append(Str.s(Cfg.K_SID)).append("\":\"").append(sid).append('"');
        }
        p.append('}');
        return p.toString();
    }
}
