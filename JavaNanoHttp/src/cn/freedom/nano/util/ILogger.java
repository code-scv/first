
package cn.freedom.nano.util;

public interface ILogger {

    public abstract void println(String... message);

    public abstract void printStackTrace(Throwable e);

    public abstract void printStackTrace(String messge, Throwable e);

    public abstract void printStackTrace(String messge);

}
