package io.jmmym.bedwarspro.auth;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 极简 HTTP POST（HttpURLConnection，兼容 Java 8，无第三方依赖）。 */
public final class Post {

    private Post() {
    }

    /**
     * 发送 JSON 请求体，返回响应体字符串。
     *
     * @throws Exception 网络异常、超时或 HTTP 错误码均向上抛出，由调用方统一处理
     */
    public static String json(String url, String body, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setDoOutput(true);
            c.setDoInput(true);
            c.setUseCaches(false);
            c.setInstanceFollowRedirects(false);
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
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            try {
                while ((n = in.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return bos.toString("UTF-8");
        } finally {
            c.disconnect();
        }
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
