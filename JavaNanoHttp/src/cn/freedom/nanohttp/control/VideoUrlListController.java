
package cn.freedom.nanohttp.control;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import cn.freedom.nano.control.BaseJsonController;
import cn.freedom.nanohttp.context.FreedomApplication;

@SuppressWarnings("deprecation")
public class VideoUrlListController extends BaseJsonController {

    @Override
    public boolean match(String uri) {
        return "/videoList.json".equals(uri);
    }

    @Override
    public String getResult(String uri, Properties parms, File servRootPath) {
        File file = servRootPath;
        List<File> medias = showAllFiles(file);
        StringBuffer result = new StringBuffer("{");
        long start = System.currentTimeMillis();
        try {
            result.append("\"code\":").append(1).append(",");
            result.append("\"macName\":").append("\"").append(FreedomApplication.getMacName()).append("\"").append(",");
            result.append("\"medias\":").append("[");
            boolean isNotFirst = false;
            for (File media : medias) {
                if (isNotFirst) {
                    result.append(",");
                } else {
                    isNotFirst = true;
                }
                String mediaPath = media.getAbsolutePath();
                String mediaUrl = mediaPath.replace(file.getAbsolutePath() + "/", "");
                mediaUrl = "/" + URLEncoder.encode(mediaUrl);
                String name = media.getName();
                
                
                StringBuffer mediaJson = new StringBuffer("{");
                try {
                    mediaJson.append("\"name\":").append("\"").append(name).append("\"").append(",");
                    mediaJson.append("\"mediaUrl\":").append("\"").append(mediaUrl).append("\"").append("}");
                } catch (Exception e) {
                }
                
                result.append(mediaJson.toString());

            }
            result.append("]").append("}");
            System.out.println("获取json 耗时" + (System.currentTimeMillis() - start));
        } catch (Exception e) {
        }
        return result.toString();
    }

    private static List<File> showAllFiles(File dir) {
        List<File> result = new ArrayList<File>();
        File[] fs = dir.listFiles();
        for (int i = 0; i < fs.length; i++) {
            //System.out.println(fs[i].getAbsolutePath());
            if (fs[i].isDirectory()) {
                if (fs[i].getName().contains(".thumbnails") || fs[i].getName().contains(".emoji")) {
                    continue;
                }

                try {
                    List<File> medias = showAllFiles(fs[i]);
                    result.addAll(medias);
                } catch (Exception e) {
                }
            } else {
                if (isMedia(fs[i])) {
                    result.add(fs[i]);
                }
            }
        }
        return result;
    }

    private static boolean isMedia(File f) {
        try {
            int dot = f.getCanonicalPath().lastIndexOf('.');
            if (dot >= 0) {
                return mediaMimeTypes.containsKey(f.getCanonicalPath().substring(dot + 1).toLowerCase());
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public static Hashtable mediaMimeTypes = new Hashtable();
    public static Hashtable picMimeTypes = new Hashtable();

    static {
        StringTokenizer mediast = new StringTokenizer("mp3        audio/mpeg " + "m3u        audio/mpeg-url " + "mp4        video/mp4 " + "ogv        video/ogg " + "flv        video/x-flv "
                + "mov        video/quicktime " + "swf        application/x-shockwave-flash ");
        while (mediast.hasMoreTokens()) {
            mediaMimeTypes.put(mediast.nextToken(), mediast.nextToken());
        }
        StringTokenizer st = new StringTokenizer("gif        image/gif " + "jpg        image/jpeg " + "jpeg       image/jpeg " + "png        image/png ");
        while (st.hasMoreTokens())
            picMimeTypes.put(st.nextToken(), st.nextToken());
    }

}
