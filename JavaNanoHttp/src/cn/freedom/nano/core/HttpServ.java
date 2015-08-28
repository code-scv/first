
package cn.freedom.nano.core;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import cn.freedom.nano.config.Config;
import cn.freedom.nano.util.ILogger;

public class HttpServ {
    private ILogger logger = Config.getLogger();;
    private static String macName;

    public static String getMacName() {
        return macName;
    }

    public static void setMacName(String macName) {
        HttpServ.macName = macName;
    }

    public HttpServ(String macName, String wwwroot) {

        super();
        HttpServ.macName = macName;
        this.wwwroot = new File(wwwroot);
    }

    public HttpServ(File wwwroot) {
        super();
        this.wwwroot = wwwroot;
    }

    NanoHTTPD nanoHTTPD;
    int port = 50080;
    File wwwroot = new File("/mnt/sdcard/apk");
    String hostaddres;
    private String serHost;

    public String getSerHost() {
        return serHost;
    }

    public void resetRootPath(String path) {
        this.nanoHTTPD.setMyRootDir(new File(path));
    }

    public String startServer() {

        try {
            while (true) {
                try {
                    nanoHTTPD = new NanoHTTPD(port, wwwroot);
                } catch (BindException e) {
                    port++;
                    continue;
                }
                break;
            }
        } catch (IOException ioe) {
            logger.printStackTrace(ioe.getMessage(), ioe);
        }
        String ip = getLocalIpAddress();

        serHost = "http://" + ip + ":" + port;
        //AppControllerManager.serHost = serHost;
        return serHost;
    }

    public void stopServer() {
        if (nanoHTTPD != null)
            nanoHTTPD.stop();
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> infos = NetworkInterface.getNetworkInterfaces();
            while (infos.hasMoreElements()) {
                NetworkInterface niFace = infos.nextElement();
                Enumeration<InetAddress> enumIpAddr = niFace.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    InetAddress mInetAddress = enumIpAddr.nextElement();
                    if (!mInetAddress.isLoopbackAddress() && isIPv4Address(mInetAddress.getHostAddress())) {
                        System.out.println(mInetAddress.getHostAddress());
                        return mInetAddress.getHostAddress().toString();
                    }
                }

            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isIPv4Address(String hostAddress) {
        return ipCheck(hostAddress);
    }

    public static boolean ipCheck(String text) {
        if (text != null && text.length() > 0) {
            String regex = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\." + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\." + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$";
            if (text.matches(regex)) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }
}
