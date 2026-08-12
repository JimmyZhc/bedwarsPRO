package io.jmmym.bedwarspro.auth;

/**
 * 运行时字符串解密器（配合混淆使用）。
 * 算法：cipher[i] = plain[i] + ((i * 31) % 97) + 3
 * 与 auth-tools/StrGen.java 的加密算法严格一致。
 */
public final class Str {

    private Str() {
    }

    public static String s(char[] c) {
        char[] r = new char[c.length];
        for (int i = 0; i < c.length; i++) {
            r[i] = (char) (c[i] - ((i * 31) % 97) - 3);
        }
        return new String(r);
    }
}
