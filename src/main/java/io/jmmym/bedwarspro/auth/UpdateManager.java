package io.jmmym.bedwarspro.auth;

import java.io.File;
import java.util.regex.Pattern;

/**
 * 版本更新协议（网络层，不依赖 Bukkit API，可在异步线程调用）。
 *
 * <p>与授权后台的更新流程配合：
 * <ol>
 *   <li>管理员执行 /bwpro check → {@link #check(File, String, String, boolean)} 查询是否有更高版本；</li>
 *   <li>管理员执行 /bwpro update → {@link #request(File, String, String, boolean)} 提交更新请求（pending）；</li>
 *   <li>站长在后台「检测更新」页同意（approved）或拒绝（rejected）；</li>
 *   <li>插件心跳轮询 {@link #status(File, String, boolean)}；approved 时向管理员发送聊天框二次确认；</li>
 *   <li>管理员点击确认 → {@link #confirm(File, String, boolean)}（confirmed），随后
 *       {@link #download(File, String, String)} 凭签名 GET 下载新插件 jar；</li>
 *   <li>管理员点击取消 → {@link #cancel(File, String, boolean)}（cancelled）。</li>
 * </ol>
 *
 * 下载接口签名覆盖文件名：HMAC-SHA256(md5|ts|sid|file)，防止任意文件下载。
 */
public final class UpdateManager {

    /** 请求状态常量（与后台 update_requests.status 一致）。 */
    public static final String ST_NONE = "none";
    public static final String ST_PENDING = "pending";
    public static final String ST_APPROVED = "approved";
    public static final String ST_CONFIRMED = "confirmed";
    public static final String ST_FINISHED = "finished";
    public static final String ST_REJECTED = "rejected";
    public static final String ST_CANCELLED = "cancelled";
    /** 后台批量推送更新：站长在网站端勾选在线服务器直接推送，无需服务器二次确认；10 秒冷静期后自动执行。 */
    public static final String ST_PUSHED = "pushed";

    private static final Pattern OK_PATTERN =
            Pattern.compile("\"ok\"\\s*:\\s*true");

    private UpdateManager() {
    }

    /** /bwpro check 的结果。update=false 表示已是最新。 */
    public static final class CheckResult {
        public boolean update;
        public String current = "";
        public String latest = "";
        public String file = "";
        public String latestMd5 = "";
        /** 当前已存在的更新请求状态（无请求时为空字符串）。 */
        public String pending = "";
    }

    /** update_request / update_status 等动作的结果。ok=false 时 reason 为后台返回的错误码。 */
    public static final class StatusResult {
        public boolean ok = true;
        public String status = "";
        public String reason = "";
        public String curVer = "";
        public String target = "";
        public String file = "";
        public String fileMd5 = "";
        /** 后台推送更新（status=pushed）时：true = 仍在 10 秒冷静期内（服务器应等待），false = 冷静期已过（可以执行更新）。 */
        public boolean cooldown = false;
    }

    /**
     * 查询是否有新版本（对应后台 action=update_check）。
     *
     * @return 查询结果；网络异常 / 响应异常返回 null
     */
    public static CheckResult check(File jar, String sid, String ver, boolean authCheck) {
        try {
            String resp = post(jar, sid, authCheck, "update_check", ver);
            if (resp == null || !OK_PATTERN.matcher(resp).find()) {
                return null;
            }
            CheckResult r = new CheckResult();
            r.update = extractBool(resp, "update");
            r.current = extractString(resp, "current");
            r.latest = extractString(resp, "latest");
            r.file = extractString(resp, "file");
            r.latestMd5 = extractString(resp, "latest_md5");
            r.pending = extractString(resp, "pending");
            if (r.current == null) {
                r.current = ver == null ? "" : ver;
            }
            if (r.latest == null) {
                r.latest = "";
            }
            if (r.file == null) {
                r.file = "";
            }
            if (r.latestMd5 == null) {
                r.latestMd5 = "";
            }
            if (r.pending == null) {
                r.pending = "";
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提交更新请求（对应后台 action=update_request，仅已授权服务器允许）。
     * 成功时 status=pending。
     *
     * @return 请求结果；网络异常返回 null
     */
    public static StatusResult request(File jar, String sid, String ver, boolean authCheck) {
        try {
            String resp = post(jar, sid, authCheck, "update_request", ver);
            if (resp == null) {
                return null;
            }
            StatusResult r = new StatusResult();
            r.ok = OK_PATTERN.matcher(resp).find();
            r.status = extractString(resp, "status");
            r.reason = extractString(resp, "reason");
            r.target = extractString(resp, "target");
            r.file = extractString(resp, "file");
            r.fileMd5 = extractString(resp, "file_md5");
            if (r.status == null) {
                r.status = r.ok ? "" : "error";
            }
            if (r.reason == null) {
                r.reason = "";
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查询当前更新请求状态（对应后台 action=update_status）。
     * 无请求记录时 status=none。
     *
     * @return 状态结果；网络异常返回 null
     */
    public static StatusResult status(File jar, String sid, boolean authCheck) {
        try {
            String resp = post(jar, sid, authCheck, "update_status", null);
            if (resp == null || !OK_PATTERN.matcher(resp).find()) {
                return null;
            }
            StatusResult r = new StatusResult();
            r.status = extractString(resp, "status");
            r.reason = extractString(resp, "reason");
            r.curVer = extractString(resp, "cur_ver");
            r.target = extractString(resp, "target");
            r.file = extractString(resp, "file");
            r.fileMd5 = extractString(resp, "file_md5");
            r.cooldown = extractBool(resp, "cooldown");
            if (r.status == null) {
                r.status = ST_NONE;
            }
            if (r.reason == null) {
                r.reason = "";
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 服务端二次确认（对应后台 action=update_confirm，仅 approved 状态允许）。
     *
     * @return 确认是否成功；网络异常返回 false
     */
    public static boolean confirm(File jar, String sid, boolean authCheck) {
        try {
            String resp = post(jar, sid, authCheck, "update_confirm", null);
            return resp != null && OK_PATTERN.matcher(resp).find()
                    && ST_CONFIRMED.equals(extractString(resp, "status"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 服务端取消本次更新（对应后台 action=update_cancel，仅 approved 状态允许）。
     *
     * @return 是否取消成功；网络异常返回 false
     */
    public static boolean cancel(File jar, String sid, boolean authCheck) {
        try {
            String resp = post(jar, sid, authCheck, "update_cancel", null);
            return resp != null && OK_PATTERN.matcher(resp).find()
                    && ST_CANCELLED.equals(extractString(resp, "status"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 后台批量推送更新：10 秒冷静期结束后由服务器自动确认（对应后台 action=update_push_ack，
     * 仅 pushed 状态且冷静期已过允许），状态 pushed → confirmed，随后即可下载新插件并重启。
     *
     * @param ver 插件当前版本号，随请求上报用于补齐后台 cur_ver 记录
     * @return 是否确认成功；网络异常 / 仍在冷静期返回 false
     */
    public static boolean ackPush(File jar, String sid, boolean authCheck, String ver) {
        try {
            String resp = post(jar, sid, authCheck, "update_push_ack", ver);
            return resp != null && OK_PATTERN.matcher(resp).find()
                    && ST_CONFIRMED.equals(extractString(resp, "status"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通知后台本次更新已完成（对应后台 action=update_finish）。
     * 下载替换成功、或检测到本服务器已运行目标版本时调用，避免后台请求卡在 confirmed。
     * 必须上报本机插件 jar 的 MD5：后台据此校验是否已真正运行目标版本，
     * 防止多台服务器共用同一实例标识（auth-server-id）时，一台更新完成导致另一台旧版本被误标「已是最新」。
     *
     * @param jarMd5 本机当前插件 jar 文件的 MD5（替换成功后即新版本 jar 的 MD5）
     * @return 是否通知成功；网络异常 / 版本不匹配返回 false
     */
    public static boolean finish(File jar, String sid, boolean authCheck, String jarMd5) {
        try {
            String resp = post(jar, sid, authCheck, "update_finish", jarMd5);
            return resp != null && OK_PATTERN.matcher(resp).find()
                    && ST_FINISHED.equals(extractString(resp, "status"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 下载新插件 jar（GET update_download，签名覆盖 md5|ts|sid|file）。
     * 后台要求请求已同意（approved/confirmed）且当前插件授权码在白名单中。
     *
     * @return 插件 jar 的原始字节；网络异常 / 拒绝下载返回 null
     */
    public static byte[] download(File jar, String sid, String file) {
        return download(jar, sid, file, null);
    }

    /**
     * 下载新插件 jar 并实时上报进度（GET update_download，签名覆盖 md5|ts|sid|file）。
     * 后台要求请求已同意（approved/confirmed）且当前插件授权码在白名单中。
     *
     * @param listener 进度回调（可为 null）
     * @return 插件 jar 的原始字节；网络异常 / 拒绝下载返回 null
     */
    public static byte[] download(File jar, String sid, String file, Post.ProgressListener listener) {
        try {
            String md5 = AuthManager.jarMd5(jar);
            long ts = System.currentTimeMillis() / 1000L;
            String secret = Str.s(Cfg.SECRET);
            String sig = Hmac.sha256(secret, md5 + "|" + ts + "|" + sid + "|" + file);
            StringBuilder q = new StringBuilder(256);
            q.append("action=update_download");
            q.append("&md5=").append(md5);
            q.append("&ts=").append(ts);
            q.append("&sid=").append(urlEncode(sid));
            q.append("&file=").append(urlEncode(file));
            q.append("&sig=").append(sig);
            return Post.getFirstAvailable(q.toString(), 8000, 30000, listener);
        } catch (Exception e) {
            return null;
        }
    }

    /** 发送 POST JSON 请求体，返回响应字符串；全部服务器地址失败返回 null。 */
    private static String post(File jar, String sid, boolean authCheck, String action, String ver)
            throws Exception {
        String md5 = AuthManager.jarMd5(jar);
        long ts = System.currentTimeMillis() / 1000L;
        String sig = AuthManager.sign(Str.s(Cfg.SECRET), md5, ts, sid);

        StringBuilder p = new StringBuilder(256);
        p.append('{');
        p.append('"').append(Str.s(Cfg.K_MD5)).append("\":\"").append(md5).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_TS)).append("\":").append(ts);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_AC)).append("\":").append(authCheck ? 1 : 0);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_ACTION)).append("\":\"").append(action).append('"');
        if (ver != null && !ver.isEmpty()) {
            p.append(',');
            p.append("\"ver\":\"").append(jsonEscape(ver)).append('"');
        }
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_SIG)).append("\":\"").append(sig).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_SID)).append("\":\"").append(jsonEscape(sid)).append('"');
        p.append('}');
        return Post.jsonFirstAvailable(p.toString(), 5000, 8000);
    }

    // ==================== 极简 JSON 提取（响应结构固定，无需第三方库） ====================

    /** 提取字符串字段值（还原 JSON 转义）。字段不存在时返回 null。 */
    private static String extractString(String json, String key) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) {
            return null;
        }
        i += k.length();
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\t')) {
            i++;
        }
        StrPos pos = extractStringAt(json, i);
        return pos == null ? null : pos.value;
    }

    /** 提取布尔字段值（{"key":true/false} 或 {"key":"true"/"false"} 两种形式都兼容）。字段不存在返回 false。 */
    private static boolean extractBool(String json, String key) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) {
            return false;
        }
        i += k.length();
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\t')) {
            i++;
        }
        if (json.startsWith("true", i)) {
            return true;
        }
        if (i < json.length() && json.charAt(i) == '"') {
            StrPos pos = extractStringAt(json, i);
            return pos != null && "true".equals(pos.value);
        }
        return false;
    }

    /** JSON 字符串值转义。 */
    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** 简单 URL 编码（仅编码 URL 查询串中不允许出现的字符）。 */
    private static String urlEncode(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else {
                byte[] b = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte x : b) {
                    sb.append('%');
                    sb.append(String.format("%02X", x & 0xFF));
                }
            }
        }
        return sb.toString();
    }

    /** 解析结果：value 为字符串值，end 为闭合引号后的下标。 */
    private static final class StrPos {
        final String value;
        final int end;

        StrPos(String value, int end) {
            this.value = value;
            this.end = end;
        }
    }

    /** 从 start 下标（指向 '"'）解析 JSON 字符串值；不合法返回 null。 */
    private static StrPos extractStringAt(String json, int start) {
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        int i = start + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                if (i + 1 >= json.length()) {
                    break;
                }
                char e = json.charAt(i + 1);
                if (e == 'u' && i + 5 < json.length()) {
                    try {
                        sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                    } catch (NumberFormatException ex) {
                        sb.append('?');
                    }
                    i += 6;
                } else {
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '/': sb.append('/'); break;
                        case '\\': sb.append('\\'); break;
                        case '"': sb.append('"'); break;
                        default: sb.append(e);
                    }
                    i += 2;
                }
                continue;
            }
            if (c == '"') {
                return new StrPos(sb.toString(), i + 1);
            }
            sb.append(c);
            i++;
        }
        return null;
    }
}
