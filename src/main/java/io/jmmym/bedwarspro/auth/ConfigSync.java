package io.jmmym.bedwarspro.auth;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 远程配置同步：插件周期向授权后台请求「插件配置」页下发的 config.yml / tasks.yml / 其它配置文件。
 *
 * 流程（与 AuthManager 共用签名协议）：
 *  1. 组装 JSON：md5 / ts / sid / ac / action=config_sync / cfg_ver（本地已应用版本）
 *  2. POST 到授权服务器（HMAC-SHA256 签名）
 *  3. 服务端版本 > cfg_ver 时返回 {"ok":true,"version":N,"config":"...","tasks":"...","files":"{...}"}
 *     无需更新时返回 {"ok":true,"version":N}（仅同步版本号，不重复传输）
 *     后台点了「读取本地配置到云端」时额外返回 "read_local":true，调用方随后上传本地配置文件内容。
 *
 * 调用方拿到 Result 后负责写文件 / 重载配置；本类不依赖 Bukkit API，可在异步线程调用。
 */
public final class ConfigSync {

    private static final Pattern OK_PATTERN =
            Pattern.compile("\"ok\"\\s*:\\s*true");

    /**
     * 本地可上报的其它配置文件（相对插件数据目录）。
     * config.yml / tasks/tasks.yml 走独立字段，其余文件打包进 files JSON。
     * 注意：messages.yml 实际位于 tasks/messages.yml（根目录 messages.yml 只是临时默认文件）；
     * api.yml 位于根目录。该清单同时用于「清除远程配置」时按 .bak 备份逐个恢复。
     */
    public static final String[] LOCAL_OTHER_FILES = {
            "tasks/messages.yml",
            "api.yml",
            "shop/item_shop.yml",
            "shop/xp_shop.yml",
            "Scoreboard/config.yml",
            "Scoreboard/join-item.yml",
            "QuickStash/config-quickstash.yml",
    };

    /** 同步结果。version 为服务端当前版本；config/tasks/files 仅在需要更新时非空。 */
    public static final class Result {
        /** 服务端远程配置版本号。 */
        public final int version;
        /** 需要写入 config.yml 的内容；无更新时为 null。 */
        public final String config;
        /** 需要写入 tasks.yml 的内容；无更新时为 null。 */
        public final String tasks;
        /** 其它配置文件内容 JSON（{"相对路径": "内容"}）；无更新时为 null。 */
        public final String files;
        /** 服务端各文件版本（插件相对路径 → 版本号）；未收到时为 null。调用方据此决定哪些文件需要拉取。 */
        public final Map<String, Integer> filesVer;
        /** 服务端是否返回了 files_ver 字段（v2 协议）。旧版授权服务器不返回，调用方应回退到全局版本号比较。 */
        public final boolean hasFilesVer;
        /** 服务端最近一次下发的文件（插件相对路径）；未下发过为空字符串。 */
        public final String lastFile;
        /** 服务端配置版本低于本地已应用版本 = 后台已清除远程配置；true 时调用方应从 .bak 恢复本地文件。 */
        public final boolean cleared;
        /** 后台请求读取本地配置到云端；true 时调用方应上传本地配置文件内容。 */
        public final boolean readLocal;

        Result(int version, String config, String tasks, String files,
               Map<String, Integer> filesVer, boolean hasFilesVer, String lastFile,
               boolean cleared, boolean readLocal) {
            this.version = version;
            this.config = config;
            this.tasks = tasks;
            this.files = files;
            this.filesVer = filesVer;
            this.hasFilesVer = hasFilesVer;
            this.lastFile = lastFile;
            this.cleared = cleared;
            this.readLocal = readLocal;
        }
    }

    private ConfigSync() {
    }

    /**
     * 执行一次配置同步。
     *
     * @param jar       本插件 JAR 文件
     * @param sid       服务器实例唯一标识（与心跳一致）
     * @param authCheck 插件 auth-check 开关（随请求上报）
     * @param cfgVer    本地当前已应用的配置版本（首次为 0）
     * @param filesVer  本地当前已应用的各文件版本（插件相对路径 → 版本号）；用于服务端按文件比对、只下发变更文件
     * @return 同步结果；网络异常 / 未授权 / 响应异常时返回 null（调用方静默跳过）
     */
    public static Result sync(File jar, String sid, boolean authCheck, int cfgVer,
                              Map<String, Integer> filesVer) {
        try {
            String md5 = AuthManager.jarMd5(jar);
            long ts = System.currentTimeMillis() / 1000L;
            String sig = AuthManager.sign(Str.s(Cfg.SECRET), md5, ts, sid);

            String body = payload(md5, ts, sig, sid, authCheck, cfgVer, filesVer);
            String resp = Post.jsonFirstAvailable(body, 5000, 5000);
            if (resp == null || !OK_PATTERN.matcher(resp).find()) {
                return null;
            }
            int version = extractInt(resp, "version");
            if (version < 0) {
                return null;
            }
            String config = extractString(resp, "config");
            String tasks = extractString(resp, "tasks");
            String files = extractString(resp, "files");
            // files_ver：{"config.yml":1,"tasks/tasks.yml":0,...} → 插件路径 → 版本号
            Map<String, Integer> fv = new LinkedHashMap<String, Integer>();
            String filesVerRaw = extractString(resp, "files_ver");
            if (filesVerRaw != null) {
                Map<String, String> raw = parseJsonObject(filesVerRaw);
                for (Map.Entry<String, String> e : raw.entrySet()) {
                    try {
                        fv.put(e.getKey(), Integer.parseInt(e.getValue().trim()));
                    } catch (NumberFormatException ignored) {
                        // 忽略非法版本号
                    }
                }
            }
            boolean cleared = extractBool(resp, "cleared");
            boolean readLocal = extractBool(resp, "read_local");
            String lastFile = extractString(resp, "last_file");
            return new Result(version, config, tasks, files, fv, filesVerRaw != null,
                    lastFile == null ? "" : lastFile, cleared, readLocal);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 后台发起「读取本地配置到云端」后上传服务器本地配置文件内容。
     * config.yml / tasks/tasks.yml 走独立字段，其余文件打包为 {"相对路径": 内容} 存入 files。
     * 服务端版本不变（不触发下发），仅填充后台编辑器。
     *
     * @param dataFolder 插件数据目录
     * @param jar        本插件 JAR 文件
     * @param sid        服务器实例唯一标识
     * @param authCheck  插件 auth-check 开关
     * @return 上传是否成功
     */
    public static boolean uploadLocal(File dataFolder, File jar, String sid, boolean authCheck) {
        try {
            String md5 = AuthManager.jarMd5(jar);
            long ts = System.currentTimeMillis() / 1000L;
            String sig = AuthManager.sign(Str.s(Cfg.SECRET), md5, ts, sid);

            String config = readUtf8(new File(dataFolder, "config.yml"));
            String tasks = readUtf8(new File(dataFolder, "tasks/tasks.yml"));

            StringBuilder files = new StringBuilder(512);
            files.append('{');
            boolean first = true;
            for (String p : LOCAL_OTHER_FILES) {
                String content = readUtf8(new File(dataFolder, p));
                if (content == null) {
                    continue;
                }
                if (!first) {
                    files.append(',');
                }
                first = false;
                files.append('"').append(jsonEscape(p)).append("\":\"").append(jsonEscape(content)).append('"');
            }
            files.append('}');

            String body = uploadPayload(md5, ts, sig, sid, authCheck,
                    config == null ? "" : config, tasks == null ? "" : tasks, files.toString());
            String resp = Post.jsonFirstAvailable(body, 5000, 5000);
            return resp != null && OK_PATTERN.matcher(resp).find();
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析 {"key":"value",...} 形式的 JSON 对象为有序 Map（键值均为字符串）。 */
    public static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (json == null || json.isEmpty()) {
            return map;
        }
        int i = json.indexOf('{');
        if (i < 0) {
            return map;
        }
        i++;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length() || json.charAt(i) == '}') {
                break;
            }
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            StrPos k = extractStringAt(json, i);
            if (k == null) {
                i++;
                continue;
            }
            i = k.end;
            while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) {
                i++;
            }
            if (i < json.length() && json.charAt(i) == '"') {
                StrPos v = extractStringAt(json, i);
                if (v != null) {
                    map.put(k.value, v.value);
                    i = v.end;
                    continue;
                }
            }
            // 数字值（如服务端 files_ver 的 {"api.yml":5}）：原样记录为字符串
            int j = i;
            if (j < json.length() && (json.charAt(j) == '-' || json.charAt(j) == '+')) {
                j++;
            }
            while (j < json.length() && Character.isDigit(json.charAt(j))) {
                j++;
            }
            if (j > i) {
                map.put(k.value, json.substring(i, j));
                i = j;
                continue;
            }
            i++;
        }
        return map;
    }

    /** 组装 config_sync 请求体（字段名与 AuthManager.payload 风格一致）。 */
    private static String payload(String md5, long ts, String sig, String sid,
                                  boolean authCheck, int cfgVer, Map<String, Integer> filesVer) {
        StringBuilder p = new StringBuilder(256);
        p.append('{');
        p.append('"').append(Str.s(Cfg.K_MD5)).append("\":\"").append(md5).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_TS)).append("\":").append(ts);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_AC)).append("\":").append(authCheck ? 1 : 0);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_ACTION)).append("\":\"config_sync\"");
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_CFG_VER)).append("\":").append(Math.max(0, cfgVer));
        p.append(',');
        // 本地已应用的各文件版本（插件路径 → 版本号），服务端据此只下发变更的文件
        p.append("\"cfg_files_ver\":\"").append(jsonEscape(buildFilesVerJson(filesVer))).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_SIG)).append("\":\"").append(sig).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_SID)).append("\":\"").append(sid).append('"');
        p.append('}');
        return p.toString();
    }

    /** 把本地各文件版本序列化为 {"插件路径": 版本号} 形式的 JSON 字符串（无文件版本时返回 {}）。 */
    private static String buildFilesVerJson(Map<String, Integer> filesVer) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        boolean first = true;
        if (filesVer != null) {
            for (Map.Entry<String, Integer> e : filesVer.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(jsonEscape(e.getKey())).append("\":").append(e.getValue() == null ? 0 : e.getValue());
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /** 组装 config_upload 请求体（上报服务器本地配置文件内容）。 */
    private static String uploadPayload(String md5, long ts, String sig, String sid,
                                        boolean authCheck, String config, String tasks, String files) {
        StringBuilder p = new StringBuilder(512);
        p.append('{');
        p.append('"').append(Str.s(Cfg.K_MD5)).append("\":\"").append(md5).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_TS)).append("\":").append(ts);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_AC)).append("\":").append(authCheck ? 1 : 0);
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_ACTION)).append("\":\"config_upload\"");
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_SIG)).append("\":\"").append(sig).append('"');
        p.append(',');
        p.append('"').append(Str.s(Cfg.K_SID)).append("\":\"").append(sid).append('"');
        p.append(',');
        p.append("\"config\":\"").append(jsonEscape(config)).append('"');
        p.append(',');
        p.append("\"tasks\":\"").append(jsonEscape(tasks)).append('"');
        p.append(',');
        p.append("\"files\":\"").append(jsonEscape(files)).append('"');
        p.append('}');
        return p.toString();
    }

    /** 读取文件内容（UTF-8）；文件不存在 / 读取失败返回 null。 */
    private static String readUtf8(File f) {
        try {
            if (!f.isFile()) {
                return null;
            }
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
        } catch (Exception e) {
            return null;
        }
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

    // ==================== 极简 JSON 提取（响应结构固定，无需第三方库） ====================

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

    /** 提取整数字段，如 {"version":3}。找不到返回 -1。 */
    private static int extractInt(String json, String key) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) {
            return -1;
        }
        i += k.length();
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\t')) {
            i++;
        }
        int j = i;
        while (j < json.length() && Character.isDigit(json.charAt(j))) {
            j++;
        }
        if (j == i) {
            return -1;
        }
        try {
            return Integer.parseInt(json.substring(i, j));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

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
}
