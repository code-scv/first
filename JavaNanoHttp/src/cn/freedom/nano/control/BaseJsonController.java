
package cn.freedom.nano.control;

import java.io.File;
import java.util.Properties;

import cn.freedom.nano.core.Response;

public abstract class BaseJsonController implements IJsonController {

    @Override
    public Response server(String uri, Properties header, Properties parms, File servRootPath) {
        String mime = Response.MIME_JSON;
        String result = getResult(uri, parms, servRootPath);
        // js跨域支持
        if (parms.getProperty("jsoncallback") != null) {
            mime = Response.MIME_JSOUP;
            result = parms.getProperty("jsoncallback") + "(" + result + ")";
        }
        Response res = new Response(Response.HTTP_OK, mime, result);
        // res.addHeader("Content-Length", "" + result.length());
        return res;
    }

    public abstract String getResult(String uri, Properties parms, File servRootPath);

}
