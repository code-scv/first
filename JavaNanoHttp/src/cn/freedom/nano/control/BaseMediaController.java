
package cn.freedom.nano.control;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import cn.freedom.nano.core.Response;

public abstract class BaseMediaController implements IUserController {
    public abstract MediaRes getMediaRes(String uri, Properties header, Properties parms,
            File servRootPath);

    @Override
    public Response server(String uri, Properties header, Properties parms, File servRootPath) {
        Response res = null;
        MediaRes mediaRes = getMediaRes(uri, header, parms, servRootPath);

        try {
            if (res == null) {
                // Get MIME type from file name extension, if possible
                String mime = mediaRes.MediaType;
                if (mime == null)
                    mime = Response.MIME_DEFAULT_BINARY;

                // Calculate etag
                String etag = Integer.toHexString((uri + mediaRes.name + "" + mediaRes.len)
                        .hashCode());

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

                // Change return code and add Content-Range header when
                // skipping
                // is requested

                if (range != null && startFrom >= 0) {
                    if (startFrom >= mediaRes.len) {
                        res = new Response(Response.HTTP_RANGE_NOT_SATISFIABLE,
                                Response.MIME_PLAINTEXT, "");
                        res.addHeader("Content-Range", "bytes 0-0/" + mediaRes.len);
                        res.addHeader("ETag", etag);
                    } else {
                        if (endAt < 0)
                            endAt = mediaRes.len - 1;
                        long newLen = endAt - startFrom + 1;
                        if (newLen < 0)
                            newLen = 0;

                        final long dataLen = newLen;
                        mediaRes.in.skip(startFrom);

                        res = new Response(Response.HTTP_PARTIALCONTENT, mime, mediaRes.in);
                        res.addHeader("Content-Length", "" + dataLen);
                        res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/"
                                + mediaRes.len);
                        res.addHeader("ETag", etag);
                    }
                } else {
                    if (etag.equals(header.getProperty("if-none-match")))
                        res = new Response(Response.HTTP_NOTMODIFIED, mime, "");
                    else {
                        res = new Response(Response.HTTP_OK, mime, mediaRes.in);
                        res.addHeader("Content-Length", "" + mediaRes.len);
                        res.addHeader("ETag", etag);
                    }
                }
            }
        } catch (IOException ioe) {
            res = new Response(Response.HTTP_FORBIDDEN, Response.MIME_PLAINTEXT,
                    "FORBIDDEN: Reading file failed.");
        }
        res.addHeader("Accept-Ranges", "bytes");
        return res;

    }

    private class MediaRes {
        InputStream in;
        long len;
        String MediaType;
        String name;
    }
}
