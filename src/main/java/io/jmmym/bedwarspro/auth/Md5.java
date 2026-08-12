package io.jmmym.bedwarspro.auth;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** 计算文件 MD5（纯 JDK，兼容 Java 8）。 */
public final class Md5 {

    private Md5() {
    }

    /** 计算文件完整 MD5，返回 32 位小写十六进制。 */
    public static String of(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return hex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("计算文件 MD5 失败: " + f.getName(), e);
        }
    }

    /** 字节数组转小写十六进制。 */
    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }
}
