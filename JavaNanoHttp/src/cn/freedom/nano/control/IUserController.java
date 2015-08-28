
package cn.freedom.nano.control;

import java.io.File;
import java.util.Properties;

import cn.freedom.nano.core.Response;

public interface IUserController {
    public boolean match(String uri);
    public Response server(String uri, Properties header,  Properties parms, File servRootPath);
}
