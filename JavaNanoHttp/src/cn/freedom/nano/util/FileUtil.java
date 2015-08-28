
package cn.freedom.nano.util;

import java.io.File;

public class FileUtil {
    private static String type = "*/*";
    public static String ip = "127.0.0.1";
    public static String deviceDMRUDN = "0";
    public static String deviceDMSUDN = "0";
    public static int port = 5977;

    public static String getFileType(String uri) {
        if (uri == null) {
            return type;
        }

        if (isAudio(uri)) {
            return "audio/mpeg";
        }

        if (isVideo(uri)) {
            return "video/mp4";
        }
        return type;
    }

    public static String getDeviceDMRUDN() {
        return deviceDMRUDN;
    }

    public static String getDeviceDMSUDN() {
        return deviceDMSUDN;
    }

    public static boolean isVideo(String url) {
        if (null == url)
            return false;
        return url.endsWith("mp4") || url.endsWith("mkv");

    }

    public static boolean isAudio(String url) {
        if (null == url)
            return false;
        return url.endsWith("mp3");
    }

    public static boolean exists(String path) {
        File file = new File(path);
        if (file.exists()) {
            return true;
        } else {
            return false;
        }
    }

}
