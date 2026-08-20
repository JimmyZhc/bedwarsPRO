package io.jmmym.bedwarspro.auth;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 极简 HTTP POST（HttpURLConnection，兼容 Java 8，无第三方依赖）。 */
public final class Post {

    /**
     * 忽略 SSL 证书校验（仅调试用）。
     * 由插件启动时根据 config.yml 的 ignore-ssl-errors 设置；
     * 开启后跳过证书有效期/域名匹配校验，存在中间人风险。
     */
    public static volatile boolean IGNORE_SSL = false;

    private Post() {
    }

    /**
     * 若启用了忽略 SSL 校验且目标为 https，则注入信任所有证书的 TrustManager 与宽松 HostnameVerifier。
     */
    private static void applySslTrust(HttpURLConnection c) throws Exception {
        if (!IGNORE_SSL || !(c instanceof javax.net.ssl.HttpsURLConnection)) {
            return;
        }
        javax.net.ssl.HttpsURLConnection h = (javax.net.ssl.HttpsURLConnection) c;
        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
        sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        }}, new java.security.SecureRandom());
        h.setSSLSocketFactory(sc.getSocketFactory());
        h.setHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
            @Override
            public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                return true;
            }
        });
    }

    /**
     * 发送 JSON 请求体，返回响应体字符串。
     * 手动跟随 301/302 重定向并保持 POST 方法与请求体（HttpURLConnection 的自动跟随
     * 会把 POST 改成 GET 并丢弃 body，导致 http → https 跳转的备用域名收到空请求）。
     *
     * @throws Exception 网络异常、超时或 HTTP 错误码均向上抛出，由调用方统一处理
     */
    public static String json(String url, String body, int connectMs, int readMs) throws Exception {
        String current = url;
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            try {
                c.setRequestMethod("POST");
                c.setConnectTimeout(connectMs);
                c.setReadTimeout(readMs);
                c.setDoOutput(true);
                c.setDoInput(true);
                c.setUseCaches(false);
                c.setInstanceFollowRedirects(false); // 手动处理重定向，保持 POST + body
                applySslTrust(c); // 调试模式：忽略证书校验
                c.setRequestProperty("User-Agent", Str.s(Cfg.UA));
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                c.setRequestProperty("Accept", "application/json");

                OutputStream out = c.getOutputStream();
                try {
                    out.write(body.getBytes("UTF-8"));
                    out.flush();
                } finally {
                    out.close();
                }

                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null || loc.isEmpty()) {
                        throw new Exception("HTTP " + code + " 重定向缺少 Location");
                    }
                    // 相对路径基于当前 URL 解析
                    current = new URL(c.getURL(), loc).toExternalForm();
                    continue;
                }
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in == null) {
                    return "";
                }
                String resp = new String(readAll(in), "UTF-8");
                if (code >= 400) {
                    // 错误响应体可能是整页 HTML（虚拟主机错误页），压缩成单行并截断，避免刷屏
                    String brief = resp.replaceAll("\\s+", " ").trim();
                    if (brief.length() > 200) {
                        brief = brief.substring(0, 200) + "…";
                    }
                    throw new Exception("HTTP " + code + " " + brief);
                }
                return resp;
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("HTTP 重定向次数过多");
    }

    /**
     * 发送 GET 请求，返回响应体原始字节（用于下载新插件 jar）。
     *
     * @throws Exception 网络异常、超时或 HTTP 错误码（含 403 not_approved 等）均向上抛出，由调用方统一处理
     */
    public static byte[] get(String url, int connectMs, int readMs) throws Exception {
        return get(url, connectMs, readMs, null);
    }

    /**
     * 发送 GET 请求并实时上报下载进度（用于新插件 jar 下载）。
     *
     * @param listener 进度回调（可为 null）；read 为已读字节，total 为总字节（未知为 -1），在读取线程内调用
     * @throws Exception 网络异常、超时或 HTTP 错误码（含 403 not_approved 等）均向上抛出，由调用方统一处理
     */
    public static byte[] get(String url, int connectMs, int readMs, ProgressListener listener) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setDoInput(true);
            c.setUseCaches(false);
            // 跟随重定向：允许 http → https / 域名跳转（如备用域名不带证书跳主站）。
            // 关闭会导致收到 301/302 空响应体，被上层误判为「未授权」。
            c.setInstanceFollowRedirects(true);
            applySslTrust(c); // 调试模式：忽略证书校验
            c.setRequestProperty("User-Agent", Str.s(Cfg.UA));
            c.setRequestProperty("Accept", "application/java-archive,*/*");

            int code = c.getResponseCode();
            if (code >= 400) {
                InputStream es = c.getErrorStream();
                String detail = "";
                if (es != null) {
                    detail = " " + new String(readAll(es), "UTF-8");
                }
                throw new Exception("HTTP " + code + detail);
            }
            InputStream in = c.getInputStream();
            if (in == null) {
                return new byte[0];
            }
            long total = c.getContentLengthLong();
            if (listener == null || total <= 0) {
                return readAll(in);
            }
            return readAll(in, total, listener);
        } finally {
            c.disconnect();
        }
    }

    /**
     * 多授权服务器地址故障转移下载：依次尝试主地址 {@link Cfg#URL}、备用地址 {@link Cfg#URL_BACKUP}。
     * 全部失败返回 null。
     */
    public static byte[] getFirstAvailable(String query, int connectMs, int readMs) {
        return getFirstAvailable(query, connectMs, readMs, null);
    }

    /**
     * 多授权服务器地址故障转移下载（带进度回调）：依次尝试主地址 {@link Cfg#URL}、备用地址 {@link Cfg#URL_BACKUP}。
     * 全部失败返回 null。
     */
    public static byte[] getFirstAvailable(String query, int connectMs, int readMs, ProgressListener listener) {
        String[] urls = {Str.s(Cfg.URL), Str.s(Cfg.URL_BACKUP)};
        for (String url : urls) {
            try {
                return get(url + (url.contains("?") ? "&" : "?") + query, connectMs, readMs, listener);
            } catch (Exception ignored) {
                // 当前地址连接失败或拒绝下载，尝试下一个备用地址
            }
        }
        return null;
    }

    /** 读取输入流全部字节。 */
    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        return bos.toByteArray();
    }

    /** 读取输入流全部字节，并按块上报进度（total 为总字节数）。 */
    private static byte[] readAll(InputStream in, long total, ProgressListener listener) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long read = 0;
        int n;
        try {
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
                read += n;
                listener.onProgress(read, total);
            }
        } finally {
            in.close();
        }
        return bos.toByteArray();
    }

    /** GET 下载进度回调（在读取线程内同步调用，实现方注意线程安全）。 */
    public interface ProgressListener {
        /**
         * 进度回调。
         *
         * @param read  已读字节数
         * @param total 总字节数（未知时为 -1）
         */
        void onProgress(long read, long total);
    }

    /**
     * 多授权服务器地址故障转移发送：依次尝试主地址 {@link Cfg#URL}、备用地址 {@link Cfg#URL_BACKUP}。
     * 仅当当前地址网络异常（抛异常）或响应为 null 时才切换下一个；业务响应（含未授权等）不转移。
     * 全部失败返回 null。
     */
    public static String jsonFirstAvailable(String body, int connectMs, int readMs) {
        String[] urls = {Str.s(Cfg.URL), Str.s(Cfg.URL_BACKUP)};
        for (String url : urls) {
            try {
                String r = json(url, body, connectMs, readMs);
                if (r != null) {
                    return r;
                }
            } catch (Exception ignored) {
                // 当前地址连接失败，尝试下一个备用地址
            }
        }
        return null;
    }
}
