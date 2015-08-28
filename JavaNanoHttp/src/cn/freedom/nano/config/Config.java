
package cn.freedom.nano.config;

import cn.freedom.nano.util.ILogger;

public class Config {
    static ILogger logger;

    public static ILogger getLogger() {
        return logger;
    }

    public static void setLogger(ILogger logger) {
        Config.logger = logger;
    }
}
