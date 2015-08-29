
package cn.freedom.nano.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.freedom.nano.control.IUserController;
import cn.freedom.nano.util.ILogger;

public class NanoHttpManager {

    public static enum HttpMethod {
        GET, POST, PUT, DELETE
    }

    public static List<IUserController> controllers = new ArrayList<IUserController>();

    public static void adduserControl(IUserController controller) {
        if (!controllers.contains(controller)) {
            controllers.add(controller);
        }
    }

    public static void configMethodType(HttpMethod... method) {
        if (method == null && method.length > 0)
            return;
        canUseMethod = new HashSet<String>();
        for (HttpMethod m : method) {
            canUseMethod.add(m.name().toLowerCase());
        }
    }

    public static void configMappingFileType(String... type) {
        if (type == null && type.length > 0)
            return;
        canUseFileType = new HashSet<String>();
        for (String string : type) {
            canUseFileType.add(string.toLowerCase());

        }
    }

    private static Set<String> canUseMethod;
    private static Set<String> canUseFileType;

    public static boolean canMappingFileType(String type) {
        if (canUseFileType == null) {
            return false;
        }
        return (canUseFileType.contains(type.toLowerCase()));
    }

    public static boolean canUseMethod(String method) {
        if (canUseMethod == null) {
            return false;
        }
        return (canUseMethod.contains(method.toLowerCase()));
    }

    public static <T extends IUserController> T getControl(String uri) {
        for (IUserController ctrl : controllers) {
            if (ctrl.match(uri)) {
                try {
                    return (T) ctrl;
                } catch (Exception e) {
                }
            }
        }
        return null;
    }

    private static Class<? extends ILogger> loggerClazz = cn.freedom.nano.core.DefaultLogger.class;

    public static void setLoggerClass(Class<? extends ILogger> clazz) {
        loggerClazz = clazz;
    }

    public static ILogger getLogger(Class clazz) {
        return getLogger(clazz.getName());
    }

    public static ILogger getLogger(String clazz) {
        ILogger logger;
        try {
            logger = loggerClazz.newInstance();
            logger.setTag(clazz);
            return logger;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new DefaultLogger();
    }

}
