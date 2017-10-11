package connect;

import android.os.Handler;
import android.os.Message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import appData.GlobalVar;
import fileSlices.EncodeFile;
import fileSlices.PieceFile;
import msg.MsgValue;
import utils.IntAndBytes;
import utils.MyFileUtils;

/**
 * Created by kingstones on 2017/9/27.
 */

public class TcpClient {
    private Socket socket = null;
    private DataInputStream in = null;   //接收
    private DataOutputStream dos = null; //发送文件
    public Semaphore dos_Semaphore = new Semaphore(1);
    //private OutputStream outputstream = null;
    private Handler handler;
    //本地的编码文件
    private EncodeFile localEncodeFile;
    private EncodeFile itsEncodeFile;   //对方的编码数据
    //用来控制第一次获取到一半的数据时
    //切换AP
    private volatile int requestFileNum = 0;
    //再编码线程是否循环执行的标志
    private volatile boolean reencodeFlag = false;

    public TcpClient(Handler handler) {
        this.handler = handler;
    }

    public boolean connectServer() {
        try {
            //若是socket不为空
            if (socket != null) {
                socket.close();
                socket = null;
            }
            localEncodeFile = null;
            //socket = new Socket(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
//            socket = new Socket();
//            SocketAddress endpoint = new InetSocketAddress(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
//            socket.setSendBufferSize(1 * 1024 * 1024);
//            socket.setReceiveBufferSize(1 * 1024 * 1024);
            //实现一个socket创建3秒延迟的作用
            long startTime = System.currentTimeMillis();
            while (true) {
                try {
                    socket = new Socket(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
                    //socket.connect(endpoint);  //连接3秒超时
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    long endTime = System.currentTimeMillis();
                    if ((endTime - startTime) > 3000) {
                        System.out.println("连接socket失败");
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "连接ServerSocket失败");
                        return false;
                    } else {
                        //等待0.1秒重连
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }

            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "连接ServerSocket成功");
            socket.setReceiveBufferSize(1 * 1024 * 1024);
            socket.setSendBufferSize(1 * 1024 * 1024);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(socket.getInputStream());     //接收
            dos = new DataOutputStream(socket.getOutputStream());//发送
            int revSize = socket.getReceiveBufferSize();
            int sendSize = socket.getSendBufferSize();
            System.out.println("接收缓存区" + revSize + "   发送缓存区" + sendSize + " ");
            RevThread revThread = new RevThread();
            revThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //连接成功   循环检测生成再编码文件
        if (!reencodeFlag) {
            reencodeFlag = true;
            reencodeListenerThread();
        }
        return true;
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
                        if (requestFileNum > 0) {
                            serviceAcquire();
                        } else {
                            //这里做关闭socket操作
                            System.out.println("已收到一半的数据，按照策略，开始切换为AP模式");
                            //关闭socket
                            //closeSocket();  在打开server状态时，写有关闭client的操作
                            //返回到主线程   执行开启AP
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "client切换为server");
                            SendMessage(MsgValue.CLIENT_2_SERVER, 0, 0, null);
                            //跳出循环
                            break;
                        }
                        continue;
                    } else if (fileNameOrOrder.contains(",")) {
                        //这个是文件请求信息
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                Constant.TCP_ServerIP + "发起文件请求" + ",请求码" + fileNameOrOrder);
                        //新开的线程
                        //为了保证发送和接收是互不影响的
                        solveFileRequest(fileNameOrOrder);
                        continue;
                    } else if (fileNameOrOrder.contains(".")) {
                        filePath = getEncodeFileStrogePath(fileNameOrOrder);
                    }
                    //获取文件长度
                    int fileLen = in.readInt();
                    File file = new File(filePath);
                    //在此需要对同名文件做处理
//                    if (file.exists()) {
//                        file.delete();
//                        file.createNewFile();
//                    }
                    FileOutputStream fos = new FileOutputStream(file);  //覆盖写
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
                        System.out.println("已获取到对方的xml配置文件，正在解析");
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "已获取到对方的xml配置文件，正在解析");
                        parseXML(file);
                    } else {
                        //处理收到文件后的EncodeFile变量更新
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, fileNameOrOrder +
                                "接收完成");
                        System.out.println(fileNameOrOrder + "接收完成");
                        solveFileChange(file);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("TcpClient Socket已经关闭");
                    //在此启动对本地encodefile变量的异常处理操作
                    socketExceptionHandle();
                    break;
                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                    System.out.println("（client）对方的socket突然关闭，造成的异常退出");
                    //在此启动对本地encodefile变量的异常处理操作
                    socketExceptionHandle();
                    break;
                }
            }
        }
    }

    //关闭socket
    public void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
                System.out.println("(client)关闭socket");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //关闭再编码线程
        reencodeFlag = false;
    }

    //处理文件请求信息   请求信息包含,逗号 , DataOutputStream dos
    public void solveFileRequest(final String requestOrder) {
        Thread solveFileReqThread = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("（client)发送文件线程已经在运行");
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
                                break;
                            }
                        }
                    }
                }
                //一次请求应答完成   告知对方
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                        "应答完成，告知对方");
                try {
                    dos_Semaphore.acquire();
                    dos.writeUTF(Constant.ANSWER_END);
                    dos_Semaphore.release();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        solveFileReqThread.start();
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
            //把值再传回主线程赋给encodeFile变量
            SendMessage(MsgValue.CLIENT_CHANGE_ENCODEFILR, 0, 0, localEncodeFile);
        }
        //获取一半数据
        if (localEncodeFile.getCurrentSmallPiece() == 0) {
            requestFileNum = localEncodeFile.getTotalSmallPiece() / 2;
        } else {
            //无限请求   请求到不能再请求
            requestFileNum = Integer.MAX_VALUE;
        }
        //在此判断对方一共有多少个有用的文件


        //把xml文件发给server
        String xmlFilePath = localEncodeFile.getStoragePath() + File.separator + "xml.txt";
        sendfile(xmlFilePath, false);
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
        int[] usefulParts = localEncodeFile.findUsefulParts(itsEncodeFile);
        if (usefulParts != null) {
            String requestCode = "";
            //在此处对请求文件的数目加入控制
            int requestNum = usefulParts.length;
            if (requestNum > requestFileNum) {
                for (int i = 0; i < requestFileNum; ++i) {
                    requestCode += (usefulParts[i] + ",");
                }
            } else {
                for (int i = 0; i < requestNum; ++i) {
                    requestCode += (usefulParts[i] + ",");
                }
            }
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    "对方拥有对我有用的数据，向对方发送文件请求");
            try {
                dos_Semaphore.acquire();
                dos.writeUTF(requestCode);
                dos_Semaphore.release();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (localEncodeFile.getCurrentSmallPiece() == localEncodeFile.getTotalSmallPiece()) {
                        String filePath = localEncodeFile.getStoragePath() + File.separator + localEncodeFile.getFileName();
                        File file = new File(filePath);
                        if (file.exists()) {
                            //解码文件已经存在
                            return;
                        }
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "本地已拥有所有的编码数据片，正在解码");
                        if (localEncodeFile.recoveryFile()) {
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                    localEncodeFile.getFileName() + "解码成功");
                            //打开
                            //SendMessage(MsgValue.DECODE_SUCCESS_OPEN_FILE, 0, 0, filePath);
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
        int partNo = 0;
        try {
            partNo = Integer.parseInt(strPartNo);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            System.out.println("解析传过来的文件名出错");
        }
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
                    //成功添加了一个文件
                    --requestFileNum;
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
            dos_Semaphore.acquire();
            dos.writeUTF(fileName);
            dos.writeInt((int) file.length());
            sendbytes = new byte[1024];
            while ((nLen = fis.read(sendbytes, 0, sendbytes.length)) > 0) {
                dos.write(sendbytes, 0, nLen);
                dos.flush();
            }
            dos_Semaphore.release();
            if (deleteFile) {
                file.delete();
            }
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    file.getName() + "发送完成");
            //SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "Send finish");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    //一个循环检测线程   当检测到haveSendFile变量为false时，启动再编码
    //注意：TCPClient关闭时，此处需要关闭
    public void reencodeListenerThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //清空发送缓存中没来得及删除的文件
                if (localEncodeFile != null) {
                    for (PieceFile pieceFile : localEncodeFile.getPieceFileList()) {
                        MyFileUtils.deleteAllFile(pieceFile.getSendBufferPath(), false);
                    }
                }
                while (reencodeFlag) {
                    if (localEncodeFile == null) {

                    } else {
                        for (PieceFile pieceFile : localEncodeFile.getPieceFileList()) {
                            if (!pieceFile.isHaveSendFile()) {
                                if (!pieceFile.isReencoding()) {
                                    pieceFile.re_encodeFile();
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    //socket突然断开后，对本地变量做的异常处理
    public synchronized void socketExceptionHandle() {
        if (localEncodeFile == null) {
            return;
        }
        for (PieceFile pieceFile : localEncodeFile.getPieceFileList()) {
            pieceFile.handleDataSynError();
        }
    }

    //本类发送消息的方法
    private void SendMessage(int what, int arg1, int arg2, Object obj) {
        if (handler != null) {
            Message.obtain(handler, what, arg1, arg2, obj).sendToTarget();
        }
    }
}
