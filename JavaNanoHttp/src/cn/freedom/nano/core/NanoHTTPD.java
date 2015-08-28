
package cn.freedom.nano.core;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;

import cn.freedom.nano.config.Config;
import cn.freedom.nano.control.IJsonController;
import cn.freedom.nano.util.ILogger;

public class NanoHTTPD {

    public Response serve(String uri, String method, Properties header, Properties parms,
            Properties files) {
        if ("/favicon.ico".equals(uri)) {
            return new Response(Response.HTTP_OK, Response.MIME_PLAINTEXT, "");
        }
        printQueryParms(uri, method, header, parms, files);

        // action 请求 json等响应
        if (AppControllerManager.getControl(uri) != null) {
            return userControllerServ(uri, header, parms, myRootDir);
        }
        // 本地文件传输
        return serveFile(uri, header, myRootDir, false);
    }

    // ==================================================
    // Socket & server code
    // ==================================================

    /**
     * Starts a HTTP server to given port.
     * <p>
     * Throws an IOException if the socket is already in use
     */
    public NanoHTTPD(int port, File wwwroot) throws IOException {
        myTcpPort = port;
        this.myRootDir = wwwroot;
        myServerSocket = new ServerSocket(myTcpPort);
        myThread = new Thread(new Runnable() {
            public void run() {
                try {
                    myOut.println("start wait for request!");
                    while (true)
                        new HTTPSession(myServerSocket.accept());
                } catch (IOException ioe) {
                    myOut.printStackTrace(ioe.getMessage(), ioe);
                }
            }
        });
        myThread.setDaemon(true);
        myThread.start();
    }

    /**
     * Stops the server.
     */
    public void stop() {
        try {
            myServerSocket.close();
            myThread.join();
        } catch (IOException ioe) {
        } catch (InterruptedException e) {
        }
    }

    /**
     * Starts as a standalone file server and waits for Enter.
     */
    public static void main(String[] args) {
        myOut.println("NanoHTTPD 1.25 (C) 2001,2005-2011 Jarno Elonen and (C) 2010 Konstantinos Togias\n"
                + "(Command line options: [-p port] [-d root-dir] [--licence])\n");

        // Defaults
        int port = 80;
        File wwwroot = new File(".").getAbsoluteFile();

        // Show licence if requested
        for (int i = 0; i < args.length; ++i)
            if (args[i].equalsIgnoreCase("-p"))
                port = Integer.parseInt(args[i + 1]);
            else if (args[i].equalsIgnoreCase("-d"))
                wwwroot = new File(args[i + 1]).getAbsoluteFile();
            else if (args[i].toLowerCase().endsWith("licence")) {
                myOut.println(LICENCE + "\n");
                break;
            }

        try {
            new NanoHTTPD(port, wwwroot);
        } catch (IOException ioe) {
            myOut.printStackTrace("Couldn't start server:\n" + ioe);
            System.exit(-1);
        }

        myOut.println("Now serving files in port " + port + " from \"" + wwwroot + "\"");
        myOut.println("Hit Enter to stop.\n");

        try {
            System.in.read();
        } catch (Throwable t) {
        }
    }

    /**
     * Handles one session, i.e. parses the HTTP request and returns the
     * response.
     */
    private class HTTPSession implements Runnable {
        public HTTPSession(Socket s) {
            mySocket = s;
            myOut.println("start session thread");
            Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
        }

        public void run() {
            try {
                // 开始读取 request 请求内容
                InputStream is = mySocket.getInputStream();
                if (is == null) {
                    return;
                }
                // Read the first 8192 bytes.
                // The full header should fit in here.
                // Apache's default header limit is 8KB.
                // Do NOT assume that a single read will get the entire header
                // at once!
                final int bufsize = 8192;
                byte[] buf = new byte[bufsize];
                int splitbyte = 0;
                int rlen = 0;
                {
                    int read = is.read(buf, 0, bufsize);
                    while (read > 0) {
                        rlen += read;
                        splitbyte = findHeaderEnd(buf, rlen);
                        if (splitbyte > 0)
                            break;
                        read = is.read(buf, rlen, bufsize - rlen);
                    }
                }

                // Create a BufferedReader for parsing the header.
                ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, rlen);
                BufferedReader hin = new BufferedReader(new InputStreamReader(hbis));
                Properties pre = new Properties();
                Properties parms = new Properties();
                // 注意 decode 方法已经将header 的 key 全部转换为小写
                Properties header = new Properties();
                Properties files = new Properties();

                // Decode the header into parms and header java properties
                decodeHeader(hin, pre, parms, header);
                String method = pre.getProperty("method");
                if (method == null) {
                    method = "GET";
                }
                String uri = pre.getProperty("uri");
                System.out.println("url " + uri);

                long size = 0x7FFFFFFFFFFFFFFFl;
                String contentLength = header.getProperty("content-length");
                if (contentLength != null) {
                    try {
                        size = Integer.parseInt(contentLength);
                    } catch (NumberFormatException ex) {
                    }
                }

                // Write the part of body already read to ByteArrayOutputStream
                // f
                ByteArrayOutputStream f = new ByteArrayOutputStream();
                if (splitbyte < rlen)
                    f.write(buf, splitbyte, rlen - splitbyte);

                // While Firefox sends on the first read all the data fitting
                // our buffer, Chrome and Opera send only the headers even if
                // there is data for the body. We do some magic here to find
                // out whether we have already consumed part of body, if we
                // have reached the end of the data to be sent or we should
                // expect the first byte of the body at the next read.
                if (splitbyte < rlen)
                    size -= rlen - splitbyte + 1;
                else if (splitbyte == 0 || size == 0x7FFFFFFFFFFFFFFFl)
                    size = 0;

                // Now read all the body and write it to f
                buf = new byte[512];
                while (rlen >= 0 && size > 0) {
                    rlen = is.read(buf, 0, 512);
                    size -= rlen;
                    if (rlen > 0)
                        f.write(buf, 0, rlen);
                }

                // Get the raw body as a byte []
                byte[] fbuf = f.toByteArray();

                // Create a BufferedReader for easily reading it as string.
                ByteArrayInputStream bin = new ByteArrayInputStream(fbuf);
                BufferedReader in = new BufferedReader(new InputStreamReader(bin));

                // If the method is POST, there may be parameters
                // in data section, too, read it:
                if (method.equalsIgnoreCase("POST")) {
                    String contentType = "";
                    String contentTypeHeader = header.getProperty("content-type");
                    StringTokenizer st = new StringTokenizer(contentTypeHeader, "; ");
                    if (st.hasMoreTokens()) {
                        contentType = st.nextToken();
                    }

                    if (contentType.equalsIgnoreCase("multipart/form-data")) {
                        // Handle multipart/form-data
                        if (!st.hasMoreTokens())
                            sendError(Response.HTTP_BADREQUEST, Response.ERROR_MSG.BOUNDARY_MISSING);
                        String boundaryExp = st.nextToken();
                        st = new StringTokenizer(boundaryExp, "=");
                        if (st.countTokens() != 2)
                            sendError(Response.HTTP_BADREQUEST,
                                    Response.ERROR_MSG.BOUNDARY_SYNTAX_ERROR);
                        st.nextToken();
                        String boundary = st.nextToken();

                        decodeMultipartData(boundary, fbuf, in, parms, files);
                    } else {
                        // Handle application/x-www-form-urlencoded
                        String postLine = "";
                        char pbuf[] = new char[512];
                        int read = in.read(pbuf);
                        while (read >= 0 && !postLine.endsWith("\r\n")) {
                            postLine += String.valueOf(pbuf, 0, read);
                            read = in.read(pbuf);
                        }
                        postLine = postLine.trim();
                        decodeParms(postLine, parms);
                    }
                }

                if (method.equalsIgnoreCase("PUT"))
                    files.put("content", saveTmpFile(fbuf, 0, f.size()));

                Response r = null;

                if (r == null) {
                    // Ok, now do the serve()
                    r = serve(uri, method, header, parms, files);
                }
                if (r == null) {
                    sendError(Response.HTTP_INTERNALERROR,
                            "SERVER INTERNAL ERROR: Serve() returned a null response.");
                } else {
                    // 流媒体写入socket
                    sendResponse(r.status, r.mimeType, r.header, r.data);
                }

                in.close();
                is.close();
            } catch (IOException ioe) {
                try {
                    sendError(Response.HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: "
                            + ioe.getMessage());
                } catch (Throwable t) {
                }
            } catch (InterruptedException ie) {
                // Thrown by sendError, ignore and exit the thread.
            }
        }

        /**
         * Decodes the sent headers and loads the data into java Properties' key
         * - value pairs
         **/
        private void decodeHeader(BufferedReader in, Properties pre, Properties parms,
                Properties header) throws InterruptedException {
            try {
                // Read the request line
                String inLine = in.readLine();
                if (inLine == null)
                    return;
                StringTokenizer st = new StringTokenizer(inLine);
                if (!st.hasMoreTokens())
                    sendError(Response.HTTP_BADREQUEST,
                            "BAD REQUEST: Syntax error. Usage: GET /example/file.html");

                String method = st.nextToken();
                pre.put("method", method);

                if (!st.hasMoreTokens())
                    sendError(Response.HTTP_BADREQUEST,
                            "BAD REQUEST: Missing URI. Usage: GET /example/file.html");

                String uri = st.nextToken();

                // Decode parameters from the URI
                int qmi = uri.indexOf('?');
                if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms);
                    uri = decodePercent(uri.substring(0, qmi));
                } else
                    uri = decodePercent(uri);

                // If there's another token, it's protocol version,
                // followed by HTTP headers. Ignore version but parse headers.
                // NOTE: this now forces header names lowercase since they are
                // case insensitive and vary by client.
                if (st.hasMoreTokens()) {
                    String line = in.readLine();
                    while (line != null && line.trim().length() > 0) {
                        int p = line.indexOf(':');
                        if (p >= 0)
                            header.put(line.substring(0, p).trim().toLowerCase(),
                                    line.substring(p + 1).trim());
                        line = in.readLine();
                    }
                }

                pre.put("uri", uri);
            } catch (IOException ioe) {
                sendError(Response.HTTP_INTERNALERROR,
                        "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
            }
        }

        /**
         * Decodes the Multipart Body data and put it into java Properties' key
         * - value pairs.
         **/
        private void decodeMultipartData(String boundary, byte[] fbuf, BufferedReader in,
                Properties parms, Properties files) throws InterruptedException {
            try {
                int[] bpositions = getBoundaryPositions(fbuf, boundary.getBytes());
                int boundarycount = 1;
                String mpline = in.readLine();
                while (mpline != null) {
                    if (mpline.indexOf(boundary) == -1)
                        sendError(Response.HTTP_BADREQUEST,
                                Response.ERROR_MSG.NEXT_CHUNK_DOES_NOT_START_WITH_BOUNDARY);
                    boundarycount++;
                    Properties item = new Properties();
                    mpline = in.readLine();
                    while (mpline != null && mpline.trim().length() > 0) {
                        int p = mpline.indexOf(':');
                        if (p != -1)
                            item.put(mpline.substring(0, p).trim().toLowerCase(),
                                    mpline.substring(p + 1).trim());
                        mpline = in.readLine();
                    }
                    if (mpline != null) {
                        String contentDisposition = item.getProperty("content-disposition");
                        if (contentDisposition == null) {
                            sendError(
                                    Response.HTTP_BADREQUEST,
                                    "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html");
                        }
                        StringTokenizer st = new StringTokenizer(contentDisposition, "; ");
                        Properties disposition = new Properties();
                        while (st.hasMoreTokens()) {
                            String token = st.nextToken();
                            int p = token.indexOf('=');
                            if (p != -1)
                                disposition.put(token.substring(0, p).trim().toLowerCase(), token
                                        .substring(p + 1).trim());
                        }
                        String pname = disposition.getProperty("name");
                        pname = pname.substring(1, pname.length() - 1);

                        String value = "";
                        if (item.getProperty("content-type") == null) {
                            while (mpline != null && mpline.indexOf(boundary) == -1) {
                                mpline = in.readLine();
                                if (mpline != null) {
                                    int d = mpline.indexOf(boundary);
                                    if (d == -1)
                                        value += mpline;
                                    else
                                        value += mpline.substring(0, d - 2);
                                }
                            }
                        } else {
                            if (boundarycount > bpositions.length)
                                sendError(Response.HTTP_INTERNALERROR, "Error processing request");
                            int offset = stripMultipartHeaders(fbuf, bpositions[boundarycount - 2]);
                            String path = saveTmpFile(fbuf, offset, bpositions[boundarycount - 1]
                                    - offset - 4);
                            files.put(pname, path);
                            value = disposition.getProperty("filename");
                            value = value.substring(1, value.length() - 1);
                            do {
                                mpline = in.readLine();
                            } while (mpline != null && mpline.indexOf(boundary) == -1);
                        }
                        parms.put(pname, value);
                    }
                }
            } catch (IOException ioe) {
                sendError(Response.HTTP_INTERNALERROR,
                        "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
            }
        }

        /**
         * Find byte index separating header from body. It must be the last byte
         * of the first two sequential new lines.
         **/
        private int findHeaderEnd(final byte[] buf, int rlen) {
            int splitbyte = 0;
            while (splitbyte + 3 < rlen) {
                if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n'
                        && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n')
                    return splitbyte + 4;
                splitbyte++;
            }
            return 0;
        }

        /**
         * Find the byte positions where multipart boundaries start.
         **/
        public int[] getBoundaryPositions(byte[] b, byte[] boundary) {
            int matchcount = 0;
            int matchbyte = -1;
            Vector matchbytes = new Vector();
            for (int i = 0; i < b.length; i++) {
                if (b[i] == boundary[matchcount]) {
                    if (matchcount == 0)
                        matchbyte = i;
                    matchcount++;
                    if (matchcount == boundary.length) {
                        matchbytes.addElement(new Integer(matchbyte));
                        matchcount = 0;
                        matchbyte = -1;
                    }
                } else {
                    i -= matchcount;
                    matchcount = 0;
                    matchbyte = -1;
                }
            }
            int[] ret = new int[matchbytes.size()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = ((Integer) matchbytes.elementAt(i)).intValue();
            }
            return ret;
        }

        /**
         * Retrieves the content of a sent file and saves it to a temporary
         * file. The full path to the saved file is returned.
         **/
        private String saveTmpFile(byte[] b, int offset, int len) {
            String path = "";
            if (len > 0) {
                String tmpdir = System.getProperty("java.io.tmpdir");
                try {
                    File temp = File.createTempFile("NanoHTTPD", "", new File(tmpdir));
                    OutputStream fstream = new FileOutputStream(temp);
                    fstream.write(b, offset, len);
                    fstream.close();
                    path = temp.getAbsolutePath();
                } catch (Exception e) { // Catch exception if any
                    myOut.printStackTrace("Error: " + e.getMessage());
                }
            }
            return path;
        }

        private String decodePercent(String str) throws InterruptedException {
            // System.out.println(str);

            try {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < str.length(); i++) {
                    char c = str.charAt(i);
                    if (c == '+')
                        sb.append(' ');
                    else
                        sb.append(c);

                }

                return URLDecoder.decode(sb.toString(), "utf-8");

            } catch (Exception e) {
                sendError(Response.HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding.");
                return null;
            }
        }

        /**
         * It returns the offset separating multipart file headers from the
         * file's data.
         **/
        private int stripMultipartHeaders(byte[] b, int offset) {
            int i = 0;
            for (i = offset; i < b.length; i++) {
                if (b[i] == '\r' && b[++i] == '\n' && b[++i] == '\r' && b[++i] == '\n')
                    break;
            }
            return i + 1;
        }

        /**
         * Decodes parameters in percent-encoded URI-format ( e.g.
         * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
         * Properties. NOTE: this doesn't support multiple identical keys due to
         * the simplicity of Properties -- if you need multiples, you might want
         * to replace the Properties with a Hashtable of Vectors or such.
         */
        private void decodeParms(String parms, Properties p) throws InterruptedException {
            if (parms == null)
                return;

            StringTokenizer st = new StringTokenizer(parms, "&");
            while (st.hasMoreTokens()) {
                String e = st.nextToken();
                int sep = e.indexOf('=');
                if (sep >= 0)
                    p.put(decodePercent(e.substring(0, sep)).trim(),
                            decodePercent(e.substring(sep + 1)));
            }
        }

        /**
         * 向用户写出error message
         */
        private void sendError(String status, String msg) throws InterruptedException {
            sendResponse(status, Response.MIME_PLAINTEXT, null,
                    new ByteArrayInputStream(msg.getBytes()));
            throw new InterruptedException();
        }

        /**
         * Sends given response to the socket.
         */
        private void sendResponse(String status, String mime, Properties header, InputStream data) {
            try {
                if (status == null)
                    throw new Error("sendResponse(): Status can't be null.");

                OutputStream out = mySocket.getOutputStream();
                PrintWriter pw = new PrintWriter(out);
                pw.print("HTTP/1.0 " + status + " \r\n");

                if (mime != null)
                    pw.print("Content-Type: " + mime + "\r\n");

                if (header == null || header.getProperty("Date") == null)
                    pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");

                if (header != null) {
                    Enumeration e = header.keys();
                    while (e.hasMoreElements()) {
                        String key = (String) e.nextElement();
                        String value = header.getProperty(key);
                        pw.print(key + ": " + value + "\r\n");
                    }
                }

                pw.print("\r\n");
                pw.flush();

                if (data != null) {
                    int pending = data.available(); // This is to support
                                                    // partial sends, see
                                                    // serveFile()
                    byte[] buff = new byte[theBufferSize];
                    while (pending > 0) {
                        int read = data.read(buff, 0, ((pending > theBufferSize) ? theBufferSize
                                : pending));
                        if (read <= 0)
                            break;
                        out.write(buff, 0, read);
                        pending -= read;
                    }
                }
                out.flush();
                out.close();
                if (data != null)
                    data.close();
            } catch (IOException ioe) {
                // Couldn't write? No can do.
                try {
                    mySocket.close();
                } catch (Throwable t) {
                }
            }
        }

        private void sendResponseInputStream(String status, String mime, Properties header,
                InputStream data, long contentOffset) {
            myOut.println("返回 resqones  流");
            printQueryParms(null, null, header, null, null);
            try {
                if (status == null)
                    throw new Error("sendResponse(): Status can't be null.");

                OutputStream out = mySocket.getOutputStream();
                PrintWriter pw = new PrintWriter(out);
                pw.print("HTTP/1.0 " + status + " \r\n");

                if (mime != null)
                    pw.print("Content-Type: " + mime + "\r\n");

                if (header == null || header.getProperty("Date") == null)
                    pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");

                if (header != null) {
                    Enumeration e = header.keys();
                    while (e.hasMoreElements()) {
                        String key = (String) e.nextElement();
                        String value = header.getProperty(key);
                        pw.print(key + ": " + value + "\r\n");
                    }
                }

                pw.print("\r\n");
                pw.flush();

                if (data != null) {
                    if (0 < contentOffset) {
                        // 控制开始位置
                        data.skip(contentOffset);
                    }
                    int pending = data.available(); // This is to support
                    // partial sends, see
                    // serveFile()
                    byte[] buff = new byte[theBufferSize];
                    while (pending > 0) {
                        int read = data.read(buff, 0, ((pending > theBufferSize) ? theBufferSize
                                : pending));
                        if (read <= 0)
                            break;
                        out.write(buff, 0, read);
                        pending -= read;
                    }
                }
                out.flush();
                out.close();
                if (data != null)
                    data.close();
            } catch (IOException ioe) {
                // Couldn't write? No can do.
                try {
                    mySocket.close();
                } catch (Throwable t) {
                }
            }
        }

        private Socket mySocket;
    }

    /**
     * URL-encodes everything between "/"-characters. Encodes spaces as '%20'
     * instead of '+'.
     */
    private String encodeUri(String uri) {
        String newUri = "";
        StringTokenizer st = new StringTokenizer(uri, "/ ", true);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            if (tok.equals("/"))
                newUri += "/";
            else if (tok.equals(" "))
                newUri += "%20";
            else {
                newUri += URLEncoder.encode(tok);
                // For Java 1.4 you'll want to use this instead:
                // try { newUri += URLEncoder.encode( tok, "UTF-8" ); } catch (
                // java.io.UnsupportedEncodingException uee ) {}
            }
        }
        return newUri;
    }

    private int myTcpPort;
    private final ServerSocket myServerSocket;
    private Thread myThread;
    private File myRootDir;

    // ==================================================
    // File server code
    // ==================================================

    public File getMyRootDir() {
        return myRootDir;
    }

    public void setMyRootDir(File myRootDir) {
        this.myRootDir = myRootDir;
    }

    /**
     * Serves file from homeDir and its' subdirectories (only). Uses only URI,
     * ignores all headers and HTTP parameters.
     */
    public Response serveFile(String uri, Properties header, File homeDir,
            boolean allowDirectoryListing) {
        myOut.println("响应 文件传输 请求");
        Response res = null;

        // Make sure we won't die of an exception later

        if (!homeDir.isDirectory())
            res = new Response(Response.HTTP_INTERNALERROR, Response.MIME_PLAINTEXT,
                    Response.ERROR_MSG.GIVEN_HOMEDIR_IS_NOT_A_DIRECTORY);
        if (uri == null) {
            res = new Response(Response.HTTP_FORBIDDEN, Response.MIME_PLAINTEXT,
                    Response.ERROR_MSG.FORBIDDEN_PARAMETER_ERROR);
            return res;
        }
        if (res == null) {
            // Remove URL arguments
            uri = uri.trim().replace(File.separatorChar, '/');
            if (uri.indexOf('?') >= 0)
                uri = uri.substring(0, uri.indexOf('?'));

            // Prohibit getting out of current directory
            if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
                res = new Response(Response.HTTP_FORBIDDEN, Response.MIME_PLAINTEXT,
                        "FORBIDDEN: Won't serve ../ for security reasons.");
        }

        File f = new File(homeDir, uri);
        if (res == null && !f.exists())
            res = new Response(Response.HTTP_NOTFOUND, Response.MIME_PLAINTEXT,
                    "Error 404, file not found.");

        // List the directory, if necessary
        if (res == null && f.isDirectory()) {
            // Browsers get confused without '/' after the
            // directory, send a redirect.
            if (!uri.endsWith("/")) {
                uri += "/";
                res = new Response(
                        Response.HTTP_REDIRECT,
                        Response.MIME_HTML,
                        "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><title>uri</title></head><body>Redirected: <a href=\""
                                + uri + "\">" + uri + "</a></body></html>");
                res.addHeader("Location", uri);
            }

            if (res == null) {
                // First try index.html and index.htm
                if (new File(f, "index.html").exists())
                    f = new File(homeDir, uri + "/index.html");
                else if (new File(f, "index.htm").exists())
                    f = new File(homeDir, uri + "/index.htm");
                // No index file, list the directory if it is readable
                else if (allowDirectoryListing && f.canRead()) {
                    String[] files = f.list();
                    String msg = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><title>welcome</title></head><body><h1>Directory "
                            + uri + "</h1><br/>";

                    if (uri.length() > 1) {
                        String u = uri.substring(0, uri.length() - 1);
                        int slash = u.lastIndexOf('/');
                        if (slash >= 0 && slash < u.length())
                            msg += "<b><a href=\"" + uri.substring(0, slash + 1)
                                    + "\">..</a></b><br/>";
                    }

                    if (files != null) {
                        for (int i = 0; i < files.length; ++i) {
                            File curFile = new File(f, files[i]);
                            boolean dir = curFile.isDirectory();
                            if (dir) {
                                msg += "<b>";
                                files[i] += "/";
                            }

                            msg += "<a href=\"" + encodeUri(uri + files[i]) + "\">" + files[i]
                                    + "</a>";

                            // Show file size
                            if (curFile.isFile()) {
                                long len = curFile.length();
                                msg += " &nbsp;<font size=2>(";
                                if (len < 1024)
                                    msg += len + " bytes";
                                else if (len < 1024 * 1024)
                                    msg += len / 1024 + "." + (len % 1024 / 10 % 100) + " KB";
                                else
                                    msg += len / (1024 * 1024) + "." + len % (1024 * 1024) / 10
                                            % 100 + " MB";

                                msg += ")</font>";
                            }
                            msg += "<br/>";
                            if (dir)
                                msg += "</b>";
                        }
                    }
                    msg += "</body></html>";
                    res = new Response(Response.HTTP_OK, Response.MIME_HTML, msg);
                } else {
                    res = new Response(Response.HTTP_FORBIDDEN, Response.MIME_PLAINTEXT,
                            "FORBIDDEN: No directory listing.");
                }
            }
        }

        try {
            if (res == null) {
                // Get MIME type from file name extension, if possible
                String mime = null;
                int dot = f.getCanonicalPath().lastIndexOf('.');
                if (dot >= 0)
                    mime = (String) Response.theMimeTypes.get(f.getCanonicalPath()
                            .substring(dot + 1).toLowerCase());
                if (mime == null)
                    mime = Response.MIME_DEFAULT_BINARY;

                // Calculate etag
                String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f
                        .length()).hashCode());

                // Support (simple) skipping:
                long startFrom = 0;
                long endAt = -1;
                String range = header.getProperty("range");
                if (range != null) {
                    if (range.startsWith("bytes=")) {
                        range = range.substring("bytes=".length());
                        int minus = range.indexOf('-');
                        try {
                            if (minus > 0) {
                                startFrom = Long.parseLong(range.substring(0, minus));
                                endAt = Long.parseLong(range.substring(minus + 1));
                            }
                        } catch (NumberFormatException nfe) {
                        }
                    }
                }

                // Change return code and add Content-Range header when skipping
                // is requested
                long fileLen = f.length();
                if (range != null && startFrom >= 0) {
                    if (startFrom >= fileLen) {
                        res = new Response(Response.HTTP_RANGE_NOT_SATISFIABLE,
                                Response.MIME_PLAINTEXT, "");
                        res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
                        res.addHeader("ETag", etag);
                    } else {
                        if (endAt < 0)
                            endAt = fileLen - 1;
                        long newLen = endAt - startFrom + 1;
                        if (newLen < 0)
                            newLen = 0;

                        final long dataLen = newLen;
                        FileInputStream fis = new FileInputStream(f) {
                            public int available() throws IOException {
                                return (int) dataLen;
                            }
                        };
                        fis.skip(startFrom);

                        res = new Response(Response.HTTP_PARTIALCONTENT, mime, fis);
                        res.addHeader("Content-Length", "" + dataLen);
                        res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/"
                                + fileLen);
                        res.addHeader("ETag", etag);
                    }
                } else {
                    if (etag.equals(header.getProperty("if-none-match")))
                        res = new Response(Response.HTTP_NOTMODIFIED, mime, "");
                    else {
                        res = new Response(Response.HTTP_OK, mime, new FileInputStream(f));
                        res.addHeader("Content-Length", "" + fileLen);
                        res.addHeader("ETag", etag);
                    }
                }
            }
        } catch (IOException ioe) {
            res = new Response(Response.HTTP_FORBIDDEN, Response.MIME_PLAINTEXT,
                    "FORBIDDEN: Reading file failed.");
        }

        res.addHeader("Accept-Ranges", "bytes"); // Announce that the file
                                                 // server accepts
                                                 // partial content requestes
        return res;
    }

    public Response userControllerServ(String uri, Properties header, Properties parms,
            File servRootPath) {
        myOut.println("响应 user 自定义 请求");
        Response res = null;
        if (uri == null) {
            res = new Response(Response.HTTP_FORBIDDEN, Response.MIME_PLAINTEXT,
                    "FORBIDDEN: Parameter ERROR.");
            return res;
        }
        if (res == null) {
            // Remove URL arguments
            uri = uri.trim().replace(File.separatorChar, '/');
            if (uri.indexOf('?') >= 0)
                uri = uri.substring(0, uri.indexOf('?'));

            // Prohibit getting out of current directory
            if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
                res = new Response(Response.HTTP_FORBIDDEN, Response.MIME_PLAINTEXT,
                        "FORBIDDEN: Won't serve ../ for security reasons.");
        }

        IJsonController controller = AppControllerManager.getControl(uri);

        if (res == null && controller == null) {
            res = new Response(Response.HTTP_NOTFOUND, Response.MIME_PLAINTEXT,
                    "Error 404, file not found.");
        } else {
            res = controller.server(uri, header, parms, servRootPath);
        }
        return res;
    }

    /**
     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
     */

    private static int theBufferSize = 128 * 1024;

    // Change these if you want to log to somewhere else than stdout
    protected static ILogger myOut = Config.getLogger();;

    /**
     * GMT date formatter
     */
    private static java.text.SimpleDateFormat gmtFrmt;

    static {
        gmtFrmt = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * The distribution licence
     */
    private static final String LICENCE = "Copyright (C) 2001,2005-2011 by Jarno Elonen <elonen@iki.fi>\n"
            + "and Copyright (C) 2010 by Konstantinos Togias <info@ktogias.gr>\n"
            + "\n"
            + "Redistribution and use in source and binary forms, with or without\n"
            + "modification, are permitted provided that the following conditions\n"
            + "are met:\n"
            + "\n"
            + "Redistributions of source code must retain the above copyright notice,\n"
            + "this list of conditions and the following disclaimer. Redistributions in\n"
            + "binary form must reproduce the above copyright notice, this list of\n"
            + "conditions and the following disclaimer in the documentation and/or other\n"
            + "materials provided with the distribution. The name of the author may not\n"
            + "be used to endorse or promote products derived from this software without\n"
            + "specific prior written permission. \n"
            + " \n"
            + "THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n"
            + "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n"
            + "OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n"
            + "IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n"
            + "INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n"
            + "NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n"
            + "DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n"
            + "THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n"
            + "(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n"
            + "OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
    /**
     * The distribution licence
     */
    private static final String LICENCE_CHINESE = "\r\n\r\n 感谢 Jarno Elonen \r\n 2015-8 基于原作者的代码修改后支持 逻辑action处理 与 流媒体请求处理";

    /** 打印請求參數 */
    private void printQueryParms(String uri, String method, Properties header, Properties parms,
            Properties files) {
        try {
            myOut.println(method + " '" + uri + "' ");
            Enumeration e = null;
            if (header != null) {
                e = header.propertyNames();
                while (e.hasMoreElements()) {
                    String value = (String) e.nextElement();
                    myOut.println("  HDR: '" + value + "' = '" + header.getProperty(value) + "'");
                }
            }
            if (parms != null) {
                e = parms.propertyNames();
                while (e.hasMoreElements()) {
                    String value = (String) e.nextElement();
                    myOut.println("  PRM: '" + value + "' = '" + parms.getProperty(value) + "'");
                }
            }
            if (files != null) {
                e = files.propertyNames();
                while (e.hasMoreElements()) {
                    String value = (String) e.nextElement();
                    myOut.println("  UPLOADED: '" + value + "' = '" + files.getProperty(value)
                            + "'");
                }
            }
        } catch (Exception e) {
            myOut.printStackTrace(e);
        }

    }

    public boolean hasHeader(Properties header, String name) {
        return header.containsKey(name);
    }

    public boolean hasContentRange(Properties header) {
        return (hasHeader(header, HTTP.CONTENT_RANGE.toLowerCase()) || hasHeader(header,
                HTTP.RANGE.toLowerCase()));
    }

    public String getHeaderValue(Properties header, String name) {
        if (header.containsKey(name)) {
            return header.getProperty(name);
        } else {
            return "";
        }
    }

    /** 获取Range long[] */
    public long[] getContentRange(Properties header) {
        long range[] = new long[3];
        range[0] = range[1] = range[2] = 0;
        if (hasContentRange(header) == false) {
            return range;
        }
        // 注 header 所有key 都已转化为小写
        String rangeLine = getHeaderValue(header, HTTP.CONTENT_RANGE.toLowerCase());
        // Thanks for Brent Hills (10/20/04)
        if (rangeLine.length() <= 0) {
            rangeLine = getHeaderValue(header, HTTP.RANGE.toLowerCase());
        }
        if (rangeLine.length() <= 0) {
            return range;
        }

        try {
            String str[] = rangeLine.split(" |=|-|/");

            if (2 <= str.length) {
                range[0] = Long.parseLong(str[1]);
            }

            if (3 <= str.length) {
                range[1] = Long.parseLong(str[2]);
            }

            if (4 <= str.length) {
                range[2] = Long.parseLong(str[3]);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return range;
    }

    public long getContentRangeFirstPosition(Properties header) {
        long range[] = getContentRange(header);
        return range[0];
    }

    public long getContentRangeLastPosition(Properties header) {
        long range[] = getContentRange(header);
        return range[1];
    }

    public long getContentRangeInstanceLength(Properties header) {
        long range[] = getContentRange(header);
        return range[2];
    }
}
