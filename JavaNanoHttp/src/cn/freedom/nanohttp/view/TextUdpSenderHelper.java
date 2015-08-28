
package cn.freedom.nanohttp.view;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import cn.freedom.nano.config.Config;
import cn.freedom.nano.core.HttpServ;
import cn.freedom.nano.util.ILogger;

public class TextUdpSenderHelper {
    private ILogger myOut = Config.getLogger();

    /**
     * @param args
     * @throws Exception
     */

    // 广播地址
    private static final String BROADCAST_IP = "230.0.0.1";// 广播IP
    private static int BROADCAST_INT_PORT = 58071; // 不同的port对应不同的socket发送端和接收端

    MulticastSocket broadSocket;// 用于接收广播信息

    InetAddress broadAddress;// 广播地址

    DatagramSocket sender;// 数据流套接字 相当于码头，用于发送信息
    HttpServ serv ;
    public TextUdpSenderHelper( HttpServ serv ) {
        BROADCAST_INT_PORT = 58071;
        this.serv = serv ;
        try {
            // 初始化
            broadSocket = new MulticastSocket(BROADCAST_INT_PORT);
            broadAddress = InetAddress.getByName(BROADCAST_IP);
            sender = new DatagramSocket();
        } catch (Exception e) {
            // TODO: handle exception
            myOut.println("*****lanSend初始化失败*****" + e.toString());
        }
    }

    public void join() {
        try {
            broadSocket.joinGroup(broadAddress); // 加入到组播地址，这样就能接收到组播信息
            new Thread(new GetPacket()).start(); // 新建一个线程，用于循环侦听端口信息
        } catch (Exception e) {
            // TODO: handle exception
            myOut.println("*****加入组播失败*****");
        }
    }

    // 广播发送查找在线用户
    public void sendGetUserMsg() {
        byte[] b = new byte[1024];
        DatagramPacket packet; // 数据包，相当于集装箱，封装信息
        try {
            b = ("find@" + getMessage()).getBytes();
            packet = new DatagramPacket(b, b.length, broadAddress, BROADCAST_INT_PORT); // 广播信息到指定端口
            sender.send(packet);
            myOut.println("*****已发送请求*****");
        } catch (Exception e) {
            myOut.printStackTrace("*****查找出错*****", e);
        }
    }

    private String getMessage() {
        String ip = HttpServ.getLocalIpAddress();
        String serverHost = serv.getSerHost();
        String macName = HttpServ.getMacName();
        return  macName + "@" + ip + "@" + serverHost;
    }

    // 当局域网内的在线机子收到广播信息时响应并向发送广播的ip地址主机发送返还信息，达到交换信息的目的
    void returnUserMsg(String ip) {
        byte[] b = new byte[1024];
        DatagramPacket packet;
        try {
            b = ("retn@" + getMessage()).getBytes();
            packet = new DatagramPacket(b, b.length, InetAddress.getByName(ip), BROADCAST_INT_PORT);
            sender.send(packet);
            myOut.println("发送信息成功！");
        } catch (Exception e) {
            // TODO: handle exception
            myOut.printStackTrace("*****发送返还信息失败*****", e);
        }
    }

    // 当局域网某机子下线是需要广播发送下线通知
    void offLine() {
        byte[] b = new byte[1024];
        DatagramPacket packet;
        try {
            b = ("offl@" + getMessage()).getBytes();
            packet = new DatagramPacket(b, b.length, broadAddress, BROADCAST_INT_PORT);
            sender.send(packet);
            myOut.println("*****已离线*****");
        } catch (Exception e) {
            // TODO: handle exception
            myOut.println("*****离线异常*****");
        }
    }

    class GetPacket implements Runnable { // 新建的线程，用于侦听
        public void run() {
            DatagramPacket inPacket;

            String message;
            while (true) {
                try {
                    inPacket = new DatagramPacket(new byte[1024], 1024);
                    broadSocket.receive(inPacket); // 接收广播信息并将信息封装到inPacket中
                    message = new String(inPacket.getData(), 0, inPacket.getLength()); // 获取信息，并切割头部，判断是何种信息（find--上线，retn--回答，offl--下线）
                    myOut.println(message);
                    String [] ms = message.split("@");
                    // if
                    // (device.getMacName().equals(FreedomApplication.getMacName()))
                    // {
                    // myOut.println("忽略本机请求!");
                    // continue; // 忽略自身
                    // }
                    if (message.startsWith("find")) { // 如果是请求信息
                        myOut.println("新上线主机：" + " ip：" + ms[1] + " 主机：");
                        returnUserMsg(ms[1]);
                    } else if (message.startsWith("retn")) { // 如果是返回信息
                        myOut.println("找到新主机：" + " ip：" + ms[1]+ " 主机：");
                    } else if (message.startsWith("offl")) { // 如果是离线信息
                       // myOut.println("主机下线：" + " ip：" + device.getIp() + " 主机：" + device.getServerHost());
                    }

                } catch (Exception e) {
                    myOut.printStackTrace("获取在线主机线程出错 " + e.getMessage(), e);
                }
            }
        }
    }
}
