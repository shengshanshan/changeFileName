package com.doriginvision;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainFrame extends JFrame{
    private static final int W=800;
    private static final int H=600;
    private JButton pickFileButton;
    private JButton executeButton;
    private String currentDir=System.getProperty("user.home");
    private JTextField filePath;
    private JTextArea textArea;
    private java.util.List<Path> paths;
    private boolean isImage(String fileName){
        if(fileName!=null&&fileName.length()>0){
            int index = fileName.lastIndexOf(".");
            if(index>0){
                String suffix = fileName.substring(index+1).toLowerCase();
                return suffix.equals("jpg") || suffix.equals("png") || suffix.equals("jpeg");
            }
        }
        return false;
    }
    private static final String regex = "([\u4e00-\u9fa5]+)";
    private static final Pattern pattern = Pattern.compile(regex);
    private String getChinese(String input){
        StringBuilder sb = new StringBuilder();
        if(input!=null&&input.length()>0) {
            Matcher matcher = pattern.matcher(input);
            while(matcher.find()){
                sb.append(matcher.group(0));
            }
        }
        return sb.toString();
    }
    public static String getStackTrace(Throwable throwable){
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        try{
            throwable.printStackTrace(pr);
            return sw.toString();
        }finally {
            pr.close();
        }
    }
    public void initUI(){
        JPanel top = new JPanel();
        JPanel mid = new JPanel();
        JPanel bottom = new JPanel();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(mid, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        filePath = new JTextField(currentDir);
        filePath.setEnabled(false);
        filePath.setDropTarget(new DropTarget(){
            @Override
            public synchronized void drop(DropTargetDropEvent evt) {
                evt.acceptDrop(DnDConstants.ACTION_COPY);
                try {
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    droppedFiles.forEach(file -> {
                        if(file.isDirectory()){
                            currentDir = file.getAbsolutePath();
                        }
                    });
                    changeDirectory();
                } catch (UnsupportedFlavorException e) {
                    textArea.append(getStackTrace(e));
                } catch (IOException e) {
                    textArea.append(getStackTrace(e));
                }
            }
        });
        pickFileButton = new JButton("预览");
        top.setLayout(new BorderLayout());
        top.add(new JLabel("目录："), BorderLayout.WEST);
        top.add(filePath, BorderLayout.CENTER);
        top.add(pickFileButton, BorderLayout.EAST);

        pickFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser(currentDir);
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int ret = chooser.showOpenDialog(MainFrame.this);
                if (ret == JFileChooser.APPROVE_OPTION){
                    currentDir = chooser.getSelectedFile().getAbsolutePath();
                    changeDirectory();
                }
            }
        });

        mid.setLayout(new BorderLayout());
        textArea = new JTextArea(10, 30);
        textArea.setEnabled(false);
        JScrollPane scroll = new JScrollPane(textArea);
        mid.add(scroll, BorderLayout.CENTER);

        executeButton = new JButton("确认修改");
        bottom.add(executeButton);
        executeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(paths==null||paths.size()<=0){
                    JOptionPane.showMessageDialog(MainFrame.this, "未找到需要修改的文件。");
                }else{
                    int ret = JOptionPane.showConfirmDialog(MainFrame.this, "确认修改这些文件？（此操作不可恢复）", "请确认", JOptionPane.YES_NO_OPTION);
                    if(ret==0){
                        moveAllFiles();
                    }
                }
            }
        });
    }
    private void moveAllFiles(){
        paths.forEach(path -> {
            File old = new File(path.toString());
            File newFile = new File(getMvToFileName(path));
            System.out.println(old.getAbsolutePath()+"->"+newFile.getAbsolutePath());
            old.renameTo(newFile);
        });
        changeDirectory();
    }
    private String getMvToFileName(Path path){
        StringBuilder sb = new StringBuilder();
        sb.append(path.getParent()).append("/").append(getParentFileChinese(path)).append("_").append(path.getFileName());
        return sb.toString();
    }
    private String getParentFileChinese(Path path){
        return getChinese(path.getName(path.getNameCount()-2).toString());
    }
    private String getFileChinese(Path path){
        return getChinese(path.getFileName().toString());
    }
    private String getOutput(Path path){
        StringBuilder sb = new StringBuilder();
        sb.append("需要将 [")
                .append(path)
                .append("] 更名为 [")
                .append(getMvToFileName(path)).append("]\n");
        return sb.toString();
    }
    private void getAllFiles(){
        try(Stream<Path> stream = Files.walk(Paths.get(currentDir))) {
            paths = stream.filter(Files::isRegularFile)
                    .filter(path -> isImage(path.getFileName().toString()))
                    .filter(path -> getParentFileChinese(path).length()>0)
                    .filter(path -> getFileChinese(path).length()==0)
                    .collect(Collectors.toList());
            paths.forEach(filePath->{
                textArea.append(getOutput(filePath));
            });
        }catch (Exception e){
            textArea.append(getStackTrace(e));
            paths = null;
        }
    }
    private void changeDirectory(){
        filePath.setText(currentDir);
        textArea.setText("需要处理的文件如下：\n----\n");

        getAllFiles();
    }
    public MainFrame(){
        initUI();
        setTitle("Change File Name");
        setSize(W, H);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
}
