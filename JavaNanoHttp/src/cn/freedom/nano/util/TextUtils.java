package cn.freedom.nano.util;

public class TextUtils {

    public static boolean isEmpty(String serverHost) {
        return serverHost == null || "".equals(serverHost.trim());
    }

}
