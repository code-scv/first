
package cn.freedom.nano.core;

import java.util.ArrayList;
import java.util.List;

import cn.freedom.nano.control.IUserController;

public class AppControllerManager {

    public static List<IUserController> controllers = new ArrayList<IUserController>();

    public static void adduserControl(IUserController controller) {
        if (!controllers.contains(controller)) {
            controllers.add(controller);
        }
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

}
