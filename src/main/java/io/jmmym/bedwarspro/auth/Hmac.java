package io.jmmym.bedwarspro.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 签名（纯 JDK，兼容 Java 8）。 */
public final class Hmac {

    private Hmac() {
    }

    /** 返回小写十六进制 HMAC-SHA256。 */
    public static String sha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
            return Md5.hex(mac.doFinal(data.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 失败", e);
        }
    }
}
