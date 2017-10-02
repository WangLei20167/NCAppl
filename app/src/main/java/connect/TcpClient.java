package connect;

import android.os.Handler;
import android.os.Message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import appData.GlobalVar;
import fileSlices.EncodeFile;
import fileSlices.PieceFile;
import msg.MsgValue;
import utils.MyFileUtils;

/**
 * Created by kingstones on 2017/9/27.
 */

public class TcpClient {
    private Socket socket = null;
    private DataInputStream in = null;   //接收
    private DataOutputStream dos = null; //发送文件
    //private OutputStream outputstream = null;
    private Handler handler;
    //本地的编码文件
    private EncodeFile localEncodeFile;
    private EncodeFile itsEncodeFile;   //对方的编码数据

    public TcpClient(Handler handler) {
        this.handler = handler;
    }

    public void connectServer() {
        try {
            //若是socket不为空
            if (socket != null) {
                socket.close();
                socket = null;
            }
            localEncodeFile = null;
            try {
                socket = new Socket(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("connect to AP failed.");
                return;
            }
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "连接ServerSocket成功");
            socket.setTcpNoDelay(true);
            in = new DataInputStream(socket.getInputStream());     //接收
            dos = new DataOutputStream(socket.getOutputStream());//发送
            RevThread revThread = new RevThread();
            revThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //连接成功
    }

    class RevThread extends Thread {
        @Override
        public void run() {
            byte[] getBytes = new byte[1024];
            while (socket.isConnected()) {
                try {
                    //获取文件名称  或是指令   在此做一个判断
                    String fileNameOrOrder = in.readUTF();
                    //创建存储地址
                    String filePath = "";
                    if (fileNameOrOrder.equals("xml.txt")) {
                        filePath = GlobalVar.getTempPath() + File.separator + "xml.txt";
                    } else if (fileNameOrOrder.equals(Constant.ANSWER_END)) {
                        //一次服务请求结束  再次请求服务
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "对方的一次文件请求应答完成");
                        serviceAcquire();
                        continue;
                    } else if (fileNameOrOrder.contains(",")) {
                        //这个是文件请求信息
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                Constant.TCP_ServerIP + "发起文件请求" + ",请求码" + fileNameOrOrder);
                        //新开的线程
                        //为了保证发送和接收是互不影响的
                        solveFileRequest(fileNameOrOrder);
                        continue;
                    } else {
                        filePath = getEncodeFileStrogePath(fileNameOrOrder);
                    }
                    //获取文件长度
                    int fileLen = in.readInt();
                    File file = new File(filePath);
                    //在此需要对同名文件做处理
                    if (file.exists()) {
                        file.delete();
                        file.createNewFile();
                    }
                    FileOutputStream fos = new FileOutputStream(file, true);  //覆盖写
                    //fos = new FileOutputStream(myFile, true);  //续写
                    //BufferedOutputStream bos = new BufferedOutputStream(fos);
                    int nLen = 0;
                    int restLen = fileLen;
                    while (true) {
                        if (restLen >= 1024) {
                            nLen = in.read(getBytes, 0, 1024);
                        } else {
                            nLen = in.read(getBytes, 0, restLen);
                        }
                        fos.write(getBytes, 0, nLen);
                        restLen -= nLen;
                        if (restLen <= 0) {
                            break;
                        }
                    }
                    fos.close();
                    if (fileNameOrOrder.equals("xml.txt")) {
                        //解析xml文件
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "已获取到对方的xml配置文件，正在解析");
                        parseXML(file);
                    } else {
                        //处理收到文件后的EncodeFile变量更新
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, fileNameOrOrder +
                                "接收完成");
                        solveFileChange(file);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("TcpClient Socket已经关闭");
                    break;
                }
            }
        }
    }

    //处理文件请求信息   请求信息包含,逗号 , DataOutputStream dos
    public void solveFileRequest(String requestOrder) {
        String[] strParts = requestOrder.split(",");
        for (String strPart : strParts) {
            if (!strPart.equals("")) {
                int partNo = Integer.parseInt(strPart);
                //在本地找信息
                for (final PieceFile pieceFile : localEncodeFile.getPieceFileList()) {
                    if (pieceFile.getPieceNo() == partNo) {
                        String reEncodeFilePath = pieceFile.getReencodeFile();
                        //发送给用户
                        sendfile(reEncodeFilePath, true);
                        //发送完后，再重新编码文件
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (!pieceFile.isReencoding()) {
                                    pieceFile.re_encodeFile();
                                }
                            }
                        }).start();
                        break;
                    }
                }
            }
        }
        //一次请求应答完成   告知对方
        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                "应答完成，告知对方");
        try {
            dos.writeUTF(Constant.ANSWER_END);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void parseXML(File file) {
        itsEncodeFile = EncodeFile.xml2object(file, false);
        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                "解析完成，正在查看是否拥有对自己有用的数据");
        String fileName = itsEncodeFile.getFileName();
        String folderName = itsEncodeFile.getFolderName();
        ArrayList<File> folders = MyFileUtils.getListFolders(GlobalVar.getTempPath());
        for (File folder : folders) {
            if (folder.getName().equals(folderName)) {
                String xmlFilePath = folder.getPath() + File.separator + "xml.txt";
                localEncodeFile = EncodeFile.xml2object(xmlFilePath, true);
                break;
            }
        }
        //如果本地没有此文件的编码文件
        if (localEncodeFile == null ||
                (!localEncodeFile.getFileName().equals(fileName))) {
            localEncodeFile = EncodeFile.clone(itsEncodeFile);
        }
        //把xml文件发给server
        String xmlFilePath = localEncodeFile.getStoragePath() + File.separator + "xml.txt";
        sendfile(xmlFilePath,false);
        //根据对方的xml文件查看是否拥有对自己有用的数据
        //如果有的话，则请求
        // 给服务器端发送文件请求
        serviceAcquire();
    }

    //服务请求
    public void serviceAcquire() {
        if (itsEncodeFile == null) {
            return;
        }
        String usefulParts = localEncodeFile.findUsefulParts(itsEncodeFile);
        if (!usefulParts.equals("")) {
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    "对方拥有对我有用的数据，向对方发送文件请求");
            try {
                dos.writeUTF(usefulParts);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (localEncodeFile.getCurrentSmallPiece() == localEncodeFile.getTotalSmallPiece()) {
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "本地已拥有所有的编码数据片，正在解码");
                        if (localEncodeFile.recoveryFile()) {
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                    localEncodeFile.getFileName() + "解码成功");
                        } else {
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                    "解码失败");
                        }
                    }
                }
            }).start();
        }
    }

    //获取文件存储地址
    public String getEncodeFileStrogePath(String encodeFileName) {
        String filePath = "";
        String strPartNo = encodeFileName.substring(0, encodeFileName.indexOf("."));
        int partNo = Integer.parseInt(strPartNo);
        //构建文件存储路径
        for (PieceFile pieceFile : localEncodeFile.getPieceFileList()) {
            if (pieceFile.getPieceNo() == partNo) {
                filePath = pieceFile.getEncodeFilePath() + File.separator + encodeFileName;
                break;
            }
        }
        //本地编码数据中没有此部分文件
        if (filePath.equals("")) {
            int rightFileLen = 0;
            if (partNo == localEncodeFile.getTotalParts()) {
                rightFileLen = localEncodeFile.getRightFileLen2();
            } else {
                rightFileLen = localEncodeFile.getRightFileLen1();
            }
            PieceFile pieceFile = new PieceFile(
                    localEncodeFile.getStoragePath(),
                    partNo,
                    localEncodeFile.getnK(),
                    rightFileLen
            );
            //添加进列表
            localEncodeFile.getPieceFileList().add(pieceFile);
            localEncodeFile.setCurrentParts(localEncodeFile.getCurrentParts() + 1);
            filePath = pieceFile.getEncodeFilePath() + File.separator + encodeFileName;
        }
        return filePath;
    }

    //接收到文件后   改变EncodeFile变量
    public void solveFileChange(File file) {
        String fileName = file.getName();
        String strPartNo = fileName.substring(0, fileName.indexOf("."));
        int partNo = Integer.parseInt(strPartNo);
        for (PieceFile pieceFile : localEncodeFile.getPieceFileList()) {
            if (pieceFile.getPieceNo() == partNo) {
                if (pieceFile.addEncodeFile(file)) {
                    //更改已收到的编码数据片的个数
                    localEncodeFile.updateCurrentSmallPiece();
                    //写入xml配置文件
                    localEncodeFile.object2xml();
                }
                break;
            }
        }
    }

    public void sendfile(String filepath, boolean deleteFile) {
        int nLen = 0;
        byte[] sendbytes = null;
        try {
            in = new DataInputStream(socket.getInputStream());     //接收
            File file = new File(filepath);
            FileInputStream fis = new FileInputStream(file);
            String fileName = file.getName();
            //outputstream.write(fileName.getBytes());
            dos.writeUTF(fileName);
            dos.writeInt((int) file.length());
            sendbytes = new byte[1024];
            while ((nLen = fis.read(sendbytes, 0, sendbytes.length)) > 0) {
                dos.write(sendbytes, 0, nLen);
                dos.flush();
            }
            if (deleteFile) {
                file.delete();
            }
            //SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "Send finish");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //本类发送消息的方法
    private void SendMessage(int what, int arg1, int arg2, Object obj) {
        if (handler != null) {
            Message.obtain(handler, what, arg1, arg2, obj).sendToTarget();
        }
    }
}
