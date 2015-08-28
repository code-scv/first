
package cn.freedom.nanohttp.context;

import java.util.Properties;
import java.util.Set;

public class FreedomApplication {

    public static String getMacName() {
        Properties properties = System.getProperties();
        Set<String> set = properties.stringPropertyNames(); // 获取java虚拟机和系统的信息。
        return properties.getProperty("user.name");
    }
}
