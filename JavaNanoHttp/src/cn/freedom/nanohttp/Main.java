
package cn.freedom.nanohttp;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import cn.freedom.nano.config.Config;
import cn.freedom.nano.core.AppControllerManager;
import cn.freedom.nano.core.HttpServ;
import cn.freedom.nanohttp.context.FreedomApplication;
import cn.freedom.nanohttp.control.VideoUrlListController;
import cn.freedom.nanohttp.view.TextUdpSenderHelper;

public class Main {

    private static JTextField textField;
    private static LayoutManager layoutmanager = new FlowLayout();


    public static void main(String[] args) {
        MFrame frame = new MFrame();
        frame.pack();
        frame.setVisible(true);
    }
}

class MFrame extends JFrame {

    private JTextField jt_name;
    private HttpServ httpServ;

    public MFrame() {
        JLabel jl = new JLabel("欢迎使用共享软件", SwingUtilities.CENTER);
        Font font = new Font("宋体", Font.BOLD, 24);
        jl.setFont(font);
        jl.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        this.add(jl, BorderLayout.NORTH);

        font = new Font("宋体", Font.PLAIN, 12);

        JLabel jl_name = new JLabel("共享目录：", SwingUtilities.RIGHT);
        jl_name.setFont(font);

        JPanel jp_center_left = new JPanel();
        jp_center_left.setLayout(new GridLayout(1, 1));
        jp_center_left.add(jl_name);

        jt_name = new JTextField(50);

        JPanel jp_center_right = new JPanel();
        jp_center_right.setLayout(new GridLayout(1, 1));
        jp_center_right.add(jt_name);
        // jp_center_right.add(jt_id);
        // jp_center_right.add(jt_pass1);
        // jp_center_right.add(jt_pass2);
        // jp_center_right.add(jt_count);

        JPanel jp_center = new JPanel();
        // jp_center.setLayout(new GridLayout(1, 2));
        jp_center.setLayout(new FlowLayout());
        jp_center.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 60));
        jp_center.add(jp_center_left);
        jp_center.add(jp_center_right);

        JButton jb1 = new JButton("选择要共享的目录");
        JButton jb2 = new JButton("结束");

        jb1.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                fileChooser();
            }
        });

        JPanel jp_south = new JPanel();
        jp_south.add(jb1);
        jp_south.add(jb2);
        jp_south.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        this.add(jp_center);
        this.add(jp_south, BorderLayout.SOUTH);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
        this.setSize(1024, 768);
        this.setResizable(false);
        this.setLocationRelativeTo(null);

        Config.setLogger(TextLogger.getLogger(Main.class));
        
        AppControllerManager.adduserControl(new VideoUrlListController());
        
        httpServ = new HttpServ(FreedomApplication.getMacName(),"/home/kenny/code_path");
        String myService = httpServ.startServer();
        System.out.println("myService " + myService);// TODO Auto-generated
                                                     // method stub);
        TextUdpSenderHelper helper = new TextUdpSenderHelper(httpServ);
        helper.join();
    }

    public void fileChooser() {
        JFileChooser chooser = new JFileChooser();

        // private JFileChooser fc=new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);// 只能选择目录
        // FileNameExtensionFilter filter = new FileNameExtensionFilter(
        // "JPG & GIF Images", "jpg", "gif");
        // 设置文件类型
        // chooser.setFileFilter(filter);
        // 打开选择器面板
        int returnVal = chooser.showOpenDialog(new JPanel());
        // 保存文件从这里入手，输出的是文件名
        if (returnVal != JFileChooser.CANCEL_OPTION) {
            System.out.println("你打开的文件是: " + chooser.getSelectedFile().getName());
            jt_name.setText("正在共享的目录: " + chooser.getSelectedFile().getAbsolutePath());
            httpServ.resetRootPath(chooser.getSelectedFile().getAbsolutePath());
        }
    }
}
