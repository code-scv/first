
package cn.freedom.nano.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * HTTP response. Return one of these from serve().
 */
public class Response {
    public static Hashtable theMimeTypes = new Hashtable();

    static {
        StringTokenizer st = new StringTokenizer("css        text/css " + "htm        text/html " + "html       text/html " + "xml        text/xml " + "txt        text/plain "
                + "asc        text/plain " + "gif        image/gif " + "jpg        image/jpeg " + "jpeg       image/jpeg " + "png        image/png " + "mp3        audio/mpeg "
                + "m3u        audio/mpeg-url " + "mp4        video/mp4 " + "ogv        video/ogg " + "flv        video/x-flv " + "mov        video/quicktime "
                + "swf        application/x-shockwave-flash " + "js         application/javascript " + "pdf        application/pdf " + "doc        application/msword "
                + "ogg        application/x-ogg " + "zip        application/octet-stream " + "exe        application/octet-stream " + "class      application/octet-stream ");
        while (st.hasMoreTokens())
            theMimeTypes.put(st.nextToken(), st.nextToken());
    }
    /**
     * Some HTTP response status codes
     */
    public static final String HTTP_OK = "200 OK", HTTP_PARTIALCONTENT = "206 Partial Content",
            HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable",
            HTTP_REDIRECT = "301 Moved Permanently", HTTP_NOTMODIFIED = "304 Not Modified",
            HTTP_FORBIDDEN = "403 Forbidden", HTTP_NOTFOUND = "404 Not Found",
            HTTP_BADREQUEST = "400 Bad Request", HTTP_INTERNALERROR = "500 Internal Server Error",
            HTTP_NOTIMPLEMENTED = "501 Not Implemented";
    
    /**
     * Common mime types for dynamic content
     */
    public static final String MIME_PLAINTEXT = "text/plain", MIME_HTML = "text/html",
            MIME_JSOUP = "application/x-javascript",
            MIME_DEFAULT_BINARY = "application/octet-stream",
            MIME_JSON = "application/json", MIME_XML = "text/xml";
    /**
     * Default constructor: response = HTTP_OK, data = mime = 'null'
     */
    public Response() {
        this.status = HTTP_OK;
    }

    /**
     * Basic constructor.
     */
    public Response(String status, String mimeType, InputStream data) {
        this.status = status;
        this.mimeType = mimeType;
        this.data = data;
    }

    /**
     * Convenience method that makes an InputStream out of given text.
     */
    public Response(String status, String mimeType, String txt) {
        this.status = status;
        this.mimeType = mimeType;
        try {
            this.data = new ByteArrayInputStream(txt.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException uee) {
            uee.printStackTrace();
        }
    }

    /**
     * Adds given line to the header.
     */
    public void addHeader(String name, String value) {
        header.put(name, value);
    }

    /**
     * HTTP status code after processing, e.g. "200 OK", HTTP_OK
     */
    public String status;

    /**
     * MIME type of content, e.g. "text/html"
     */
    public String mimeType;

    /**
     * Data of the response, may be null.
     */
    public InputStream data;

    /**
     * Headers for the HTTP response. Use addHeader() to add lines.
     */
    public Properties header = new Properties();

    public String getHeader() {
        StringBuffer str = new StringBuffer();

        str.append(getStatusLineString());
        str.append(getHeaderString());

        return str.toString();
    }

    // 状态返回码
    public String getStatusLineString() {
        return "HTTP/1.1" + " " + status + HTTP.CRLF;
    }

    public String getHeaderString() {
        StringBuffer str = new StringBuffer();

        if (header != null) {
            Enumeration e = header.keys();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                String value = header.getProperty(key);
                str.append(key + ": " + value + HTTP.CRLF);
            }
        }
        return str.toString();
    }
    
    public static class ERROR_MSG{
        public static final String NEXT_CHUNK_DOES_NOT_START_WITH_BOUNDARY = "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html";
        public static final String GIVEN_HOMEDIR_IS_NOT_A_DIRECTORY = "INTERNAL ERRROR: given homeDir is not a directory.";
        public static final String FORBIDDEN_PARAMETER_ERROR = "FORBIDDEN: Parameter ERROR.";
        public static final String BOUNDARY_MISSING = "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html";
        public static final String BOUNDARY_SYNTAX_ERROR = "BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html";
        public static final String NOT_FOUND = "Error 404, file not found.";
        
    }
}
