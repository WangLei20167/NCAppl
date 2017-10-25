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
import utils.LocalInfor;
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
    //private volatile EncodeFile  GlobalVar.g_ef;
    private EncodeFile itsEncodeFile;   //对方的编码数据
    //用来控制第一次获取到一半的数据时
    //切换AP
    private volatile int requestFileNum = 0;
    //再编码线程是否循环执行的标志
    private volatile boolean reencodeFlag = false;
    //判断什么时候离开
    //初始状态0，发送完或是接收完+1
    //当为2时，离开
    private int leaveFlag = 0;

    //记录连接socket的状态   防止重复连接的情况
    //
    public volatile boolean connectState = false;

    public TcpClient(Handler handler) {
        this.handler = handler;
    }

    public boolean connectServer() {
        if (connectState) {
            return true;
        }
        try {
            //若是socket不为空
            if (socket != null) {
                socket.close();
                socket = null;
                if (dos != null) {
                    dos_Semaphore.release();
                    dos = null;
                }
            }
            //GlobalVar.g_ef = null;
            //socket = new Socket(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
//            socket = new Socket();
//            SocketAddress endpoint = new InetSocketAddress(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
//            socket.setSendBufferSize(1 * 1024 * 1024);
//            socket.setReceiveBufferSize(1 * 1024 * 1024);
            //实现一个socket创建3秒延迟的作用
            //long startTime = System.currentTimeMillis();
            //去掉循环连接socket
            try {
                socket = new Socket(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
                //socket.connect(endpoint);  //连接3秒超时
                //设置读超时 10s
                socket.setSoTimeout(20 * 1000);

            } catch (IOException e) {
                System.out.println("连接socket失败");
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "连接ServerSocket失败");
                return false;
            }

//            while (true) {
//                try {
//                    socket = new Socket(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
//                    //socket.connect(endpoint);  //连接3秒超时
//                    break;
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    long endTime = System.currentTimeMillis();
//                    if ((endTime - startTime) > 1000) {
//                        System.out.println("连接socket失败");
//                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "连接ServerSocket失败");
//                        return false;
//                    } else {
//                        //等待0.1秒重连
//                        try {
//                            Thread.sleep(100);
//                        } catch (InterruptedException e1) {
//                            e1.printStackTrace();
//                        }
//                    }
//                }
//            }

            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "连接ServerSocket成功");
            connectState = true;
            leaveFlag = 0;
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
        //
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
            while (socket != null && socket.isConnected()) {
                try {
                    //获取文件名称  或是指令   在此做一个判断
                    String fileNameOrOrder = in.readUTF();
                    //创建存储地址
                    String filePath = "";
                    //用来标记获取文件存储地址时，这个变量是否为空
                    boolean g_efNullFlag = false;
                    if (fileNameOrOrder.equals("xml.txt")) {
                        filePath = GlobalVar.getTempPath() + File.separator + "xml.txt";
                    } else if (fileNameOrOrder.equals(Constant.REQUEST_END)) {
                        //离开标志
                        ++leaveFlag;
                        //
                        if (leaveFlag == 2) {
                            //执行离开操作
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                    "与server的交互结束，按照策略，开始切换为AP模式");
                            SendMessage(MsgValue.CLIENT_2_SERVER, 0, 0, null);
                        }
                        continue;
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
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                    "已收到一半的数据，按照策略，开始切换为AP模式");
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
                        if (GlobalVar.g_ef != null) {
                            filePath = getEncodeFileStrogePath(fileNameOrOrder);
                            g_efNullFlag = false;
                        } else {
                            g_efNullFlag = true;
                            System.out.println("client接收到文件时，GlobalVar.g_ef 为 null");
                        }
                    }
                    //获取文件长度
                    int fileLen = in.readInt();
                    if (g_efNullFlag) {
                        in.skipBytes(fileLen);
                    }
//                    if(filePath.equals("")){
//                        in.skipBytes(fileLen);
//                    }
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
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("TcpClient Socket异常");
                    //在此启动对本地encodefile变量的异常处理操作
                    socketExceptionHandle();
                    closeSocket();
                    SendMessage(MsgValue.CLIENT_2_SERVER, 0, 0, null);
                    break;
                }
//                catch (ArrayIndexOutOfBoundsException e) {
//                    e.printStackTrace();
//                    System.out.println("（client）对方的socket突然关闭，造成的异常退出");
//                    //在此启动对本地encodefile变量的异常处理操作
//                    socketExceptionHandle();
//                    closeSocket();
//                    SendMessage(MsgValue.CLIENT_2_SERVER, 0, 0, null);
//                    break;
//                } catch (Exception e) {
//                    //SocketTimeoutException
//                    e.printStackTrace();
//                    //在此启动对本地encodefile变量的异常处理操作
//                    socketExceptionHandle();
//                    closeSocket();
//                    SendMessage(MsgValue.CLIENT_2_SERVER, 0, 0, null);
//                }
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
        connectState = false;
        //退出前更新下xml文件
        if (GlobalVar.g_ef != null) {
            GlobalVar.g_ef.object2xml();
        }
    }

    //处理文件请求信息   请求信息包含,逗号 , DataOutputStream dos
    public void solveFileRequest(final String requestOrder) {
        Thread solveFileReqThread = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("（client)发送文件线程已经在运行");
                String[] strParts = requestOrder.split(",");
                //取出申请放入int数组
                int[] intParts = null;
                for (String strPart : strParts) {
                    if (!strPart.equals("")) {
                        int partNo = Integer.parseInt(strPart);
                        intParts = IntAndBytes.intArrayGrow(intParts, partNo);
                    }
                }
                //请求出错
                //没能解析出文件pieceNo
                if (intParts == null) {
                    return;
                }
                while (intParts != null) {
                    int length = intParts.length;
                    int[] newIntArray = new int[length];
                    System.arraycopy(intParts, 0, newIntArray, 0, length);
                    for (int i = 0; i < length; ++i) {
                        int partNo = newIntArray[i];
                        for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
                            //找到了分片
                            String reEncodeFilePath = null;
                            if (pieceFile.getPieceNo() == partNo) {
                                //有再编码文件
                                if (pieceFile.isHaveSendFile()) {
                                    //打印信息
                                    System.out.println("(" + LocalInfor.getCurrentTime("HH:mm:ss:SSS") + ")" +
                                            "正在获取" + partNo + "部分再编码文件");
                                    reEncodeFilePath = pieceFile.getReencodeFile();
                                } else {
                                    //没有再编码文件
                                    //且只有一个文件部分请求，则进入等待
                                    if (length == 1) {
                                        System.out.println("(" + LocalInfor.getCurrentTime("HH:mm:ss:SSS") + ")" +
                                                "正在获取" + partNo + "部分再编码文件");
                                        reEncodeFilePath = pieceFile.getReencodeFile();
                                    } else {
                                        break;
                                    }
                                }
                            }
                            if (reEncodeFilePath != null) {
                                //发送给用户
                                System.out.println("(" + LocalInfor.getCurrentTime("HH:mm:ss:SSS") + ")" +
                                        "获取到再编码文件" + reEncodeFilePath);
                                sendfile(reEncodeFilePath, true);
                                intParts = IntAndBytes.intArrayReduce(intParts, partNo);
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
                } finally {
                    dos_Semaphore.release();
                }
            }
        });
        solveFileReqThread.start();
    }


    public void parseXML(final File file) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
        itsEncodeFile = EncodeFile.xml2object(file, false);

        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                "解析完成对方xml完成");
        //此log之后出现卡死的情况
        String fileName = itsEncodeFile.getFileName();
        String folderName = itsEncodeFile.getFolderName();
        ArrayList<File> folders = MyFileUtils.getListFolders(GlobalVar.getTempPath());
        for (File folder : folders) {
            if (folder.getName().equals(folderName)) {
                String xmlFilePath = folder.getPath() + File.separator + "xml.txt";
                GlobalVar.g_ef = EncodeFile.xml2object(xmlFilePath, true);
                break;
            }
        }

        //如果本地没有此文件的编码文件
        if (GlobalVar.g_ef == null ||
                (!GlobalVar.g_ef.getFolderName().equals(folderName))) {
            GlobalVar.g_ef = EncodeFile.clone(itsEncodeFile);
            //把值再传回主线程赋给encodeFile变量
            // SendMessage(MsgValue.CLIENT_CHANGE_ENCODEFILR, 0, 0,  GlobalVar.g_ef);
        }
        //更新进度球
        SendMessage(MsgValue.UPDATE_WAVE_PROGRESS, 0, 0, null);
        //获取一半数据
        if (GlobalVar.g_ef.getCurrentSmallPiece() == 0) {
            requestFileNum = GlobalVar.g_ef.getTotalSmallPiece() / 2;
        } else {
            //无限请求   请求到不能再请求
            requestFileNum = Integer.MAX_VALUE;
        }
        //在此判断对方一共有多少个有用的文件


        //把xml文件发给server
//        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
//                "向对方发送自己的xml文件");
        String xmlFilePath = GlobalVar.g_ef.getStoragePath() + File.separator + "xml.txt";
        sendfile(xmlFilePath, false);
//        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
//                "xml文件发送完成");
        //根据对方的xml文件查看是否拥有对自己有用的数据
        //如果有的话，则请求
        // 给服务器端发送文件请求
        //之后出现卡死的情况
        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                "正在查看对方是否有对自己有用的数据");
        serviceAcquire();
//            }
//        }).start();

    }

    //服务请求
    public void serviceAcquire() {
        if (itsEncodeFile == null) {
            return;
        }
        int[] usefulParts = GlobalVar.g_ef.findUsefulParts(itsEncodeFile);
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
            //此log之后出现卡死的情况
            try {
                dos_Semaphore.acquire();
                dos.writeUTF(requestCode);
                dos_Semaphore.release();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                dos_Semaphore.release();
            }
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    "文件请求发送完成");
        } else {
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    "对方没有对我有用的数据");
            //离开标志
            ++leaveFlag;
            //
            if (leaveFlag == 2) {
                //执行离开操作
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                        "与server的交互结束，按照策略，开始切换为AP模式");
                SendMessage(MsgValue.CLIENT_2_SERVER, 0, 0, null);
            }

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
        for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
            if (pieceFile.getPieceNo() == partNo) {
                filePath = pieceFile.getEncodeFilePath() + File.separator + encodeFileName;
                break;
            }
        }
        //本地编码数据中没有此部分文件
        if (filePath.equals("")) {
            int rightFileLen = 0;
            if (partNo == GlobalVar.g_ef.getTotalParts()) {
                rightFileLen = GlobalVar.g_ef.getRightFileLen2();
            } else {
                rightFileLen = GlobalVar.g_ef.getRightFileLen1();
            }
            PieceFile pieceFile = new PieceFile(
                    GlobalVar.g_ef.getStoragePath(),
                    partNo,
                    GlobalVar.g_ef.getnK(),
                    rightFileLen
            );
            //添加进列表
            GlobalVar.g_ef.getPieceFileList().add(pieceFile);
            GlobalVar.g_ef.setCurrentParts(GlobalVar.g_ef.getCurrentParts() + 1);
            filePath = pieceFile.getEncodeFilePath() + File.separator + encodeFileName;
        }
        return filePath;
    }

    //接收到文件后   改变EncodeFile变量
    public void solveFileChange(File file) {
        //
        System.out.println("正在处理文件更改");
        String fileName = file.getName();
        String strPartNo = fileName.substring(0, fileName.indexOf("."));
        int partNo = Integer.parseInt(strPartNo);
        for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
            if (pieceFile.getPieceNo() == partNo) {
                if (pieceFile.addEncodeFile(file)) {
                    System.out.println("添加文件成功");
                    //成功添加了一个文件
                    --requestFileNum;
                    //更改已收到的编码数据片的个数
                    GlobalVar.g_ef.updateCurrentSmallPiece();
                    //写入xml配置文件
                    GlobalVar.g_ef.object2xml();
                    //更新进度球
                    SendMessage(MsgValue.UPDATE_WAVE_PROGRESS, 0, 0, null);
                }
                break;
            }
        }
        //尝试解码
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (GlobalVar.g_ef.getCurrentSmallPiece() == GlobalVar.g_ef.getTotalSmallPiece()) {
                    String filePath = GlobalVar.g_ef.getStoragePath() + File.separator + GlobalVar.g_ef.getFileName();
                    File file = new File(filePath);
                    if (file.exists()) {
                        //解码文件已经存在
                        return;
                    }
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                            "本地已拥有所有的编码数据片，正在解码");
                    if (GlobalVar.g_ef.recoveryFile()) {
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                GlobalVar.g_ef.getFileName() + "解码成功");
                        String time = LocalInfor.getCurrentTime("yy-MM-dd HH:mm:ss");
                        String fileName = GlobalVar.g_ef.getFileName();
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                time + "  " + fileName + " " + "接收完成");
                        MyFileUtils.writeLog(MyFileUtils.REV_TYPE, time, fileName);
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
        } catch (NullPointerException e) {
            e.printStackTrace();
        } finally {
            dos_Semaphore.release();
        }
    }

    //一个循环检测线程   当检测到haveSendFile变量为false时，启动再编码
    //注意：TCPClient关闭时，此处需要关闭
    public void reencodeListenerThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (GlobalVar.g_ef != null) {
                    //清空发送缓存中没来得及删除的文件
                    System.out.println("再编码线程已开启");
                    //清空发送缓存中没来得及删除的文件
                    try {
                        for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
                            MyFileUtils.deleteAllFile(pieceFile.getSendBufferPath(), false);
                            //删除多余的再编码文件
                            String sendFilePath = pieceFile.getSendFilePath();
                            ArrayList<File> files = MyFileUtils.getList_1_files(pieceFile.getRe_encodeFilePath());
                            for (File file : files) {
                                //如果记录中有可供发送的文件
                                if (pieceFile.isHaveSendFile()) {
                                    //
                                    String filePath = file.getPath();
                                    if (!(filePath.equals(sendFilePath))) {
                                        file.delete();
                                    }
                                } else {
                                    file.delete();
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                while (reencodeFlag) {
                    if (GlobalVar.g_ef == null) {

                    } else {
                        //
                        int size = GlobalVar.g_ef.getPieceFileList().size();
                        try {
                            for (int i = 0; i < size; ++i) {
                                final PieceFile pieceFile = GlobalVar.g_ef.getPieceFileList().get(i);
                                if (!pieceFile.isHaveSendFile()) {
                                    //System.out.println("没有再编码文件可供发送");
                                    if (!pieceFile.isReencoding()) {
                                        //在这里重开一个子线程执行
                                        //System.out.println("开启再编码线程");
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                pieceFile.re_encodeFile();
                                                //更改xml文件
                                                //再编码之后为何要更新xml文件？？？
                                                // GlobalVar.g_ef.object2xml();
                                            }
                                        }).start();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
//                        for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
//                            if (!pieceFile.isHaveSendFile()) {
//                                if (!pieceFile.isReencoding()) {
//                                    pieceFile.re_encodeFile();
//                                }
//                            }
//                        }
                    }
//                    try {
//                        Thread.sleep(30);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }
            }
        }).start();
    }

    //socket突然断开后，对本地变量做的异常处理
    public synchronized void socketExceptionHandle() {
        if (GlobalVar.g_ef == null) {
            return;
        }
        for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
            pieceFile.handleDataSynError();
        }
        //更新下xml文件

    }

    //本类发送消息的方法
    private void SendMessage(int what, int arg1, int arg2, Object obj) {
        if (handler != null) {
            Message.obtain(handler, what, arg1, arg2, obj).sendToTarget();
        }
    }
}
