
package cn.freedom.nanohttp;

import cn.freedom.nano.util.ILogger;

public class TextLogger implements ILogger {
    private String TAG;
    private boolean isDebug = true;
    StringBuffer sb = new StringBuffer();

    public void println(String... message) {
        sb.setLength(0);
        for (String string : message) {
            sb.append(string).append("|");
        }
        if (isDebug) {
            System.err.println(TAG + sb.toString());
        } else {
            System.err.println(TAG + sb.toString());
        }

    }

    public void printStackTrace(Throwable e) {
        System.err.println(TAG + e.getMessage());
        e.printStackTrace();
    }

    public void printStackTrace(String messge, Throwable e) {
        System.err.println(TAG + messge);
        e.printStackTrace();
    }

    public void printStackTrace(String messge) {
        System.err.println(TAG + messge);
    }

    @Override
    public void setTag(String tag) {
        if (isDebug) {
            this.TAG = "ss_" + tag;
        } else {
            this.TAG = tag;
        }
    }
}
