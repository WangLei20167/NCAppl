package connect;

import android.os.Handler;
import android.os.Message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import appData.GlobalVar;
import fileSlices.EncodeFile;
import fileSlices.PieceFile;
import msg.MsgValue;
import utils.LocalInfor;
import utils.MyFileUtils;

/**
 * Created by kingstones on 2017/9/27.
 */

public class TcpServer {
    private List<Socket> socketList = new ArrayList<Socket>();
    public ServerSocket svrSock;
    private ExecutorService mExecutorService = null;   //线程池
    private Handler handler;
    // private EncodeFile encodeFile;

    private EncodeFile itsEncodeFile;
    //再编码线程是否循环执行的标志
    private volatile boolean reencodeFlag = false;

    //用于定时切换AP与普通节点
    private Timer mTimer;
    //private TimerTask mTimerTask;
    private volatile boolean taskState;  //定时任务是否启动的状态字
    private long baseDelay = 10 * 1000;
    //表示即将拥有数据   为了防止数据的重复请求
    //private volatile boolean[] willHave;
    //检查server状态是否打开
    private volatile boolean serverState = false;
    //解码锁   防止多次进入解锁
    public volatile boolean decoding = false;

    public TcpServer(Handler handler) {
        this.handler = handler;

        mTimer = new Timer();
        //long delay = 0;
        // schedules the task to be run in an interval
        //定时任务的启动代码
        //mTimer.schedule(mTimerTask, delay);
        //定时任务取消的代码
        //mTimer.cancel();
    }

    public void openServerSocket(final boolean auto) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (serverState) {
                    return;
                }
                //this.encodeFile = encodeFile;
                //willHave = new boolean[GlobalVar.g_ef.getTotalParts()];
                //Arrays.fill(willHave, false);
                try {
                    svrSock = new ServerSocket(Constant.TCP_ServerPORT);
                    svrSock.setReuseAddress(true);   //设置上一个关闭的超时状态下可连接
                } catch (IOException e) {
                    //失败
                    System.out.println("绑定端口失败");
                    e.printStackTrace();
                }
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "绑定端口成功");

                decoding = false;
                serverState = true;
                // 创建线程池
                mExecutorService = Executors.newCachedThreadPool();
                Socket client = null;
                //监听需要再编码的时机
                if (!reencodeFlag) {
                    reencodeFlag = true;
                    reencodeListenerThread();
                }
                //定时任务的启动代码
                //如果是由代码自动启动的server，则在此开启定时
                //若是手动点击的，这里不做启动处理
                if (auto) {
                    //自动启动的  开启定时转换
                    startTimerTask();
                } else {
                    //如果是手动的点的  则写入日志
                    String time = LocalInfor.getCurrentTime("yy-MM-dd HH:mm:ss");
                    String fileName = GlobalVar.g_ef.getFileName();
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                            time + "  " + fileName + " " + "开始分享");
                    MyFileUtils.writeLog(MyFileUtils.SEND_TYPE, time, fileName);
                }
                System.out.println("(Server)定时任务已经启动");
                //等待client连接
                while (true) {
                    try {
                        //阻塞等待连接
                        client = svrSock.accept();
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                    String client_ip = client.getInetAddress().toString();
                    for (int i = 0; i < socketList.size(); ++i) {
                        Socket s = socketList.get(i);
                        if (s.getInetAddress().toString().equals(client_ip)) {
                            try {
                                s.close();
                                socketList.remove(i);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (IndexOutOfBoundsException e) {
                                //List机制
                                //循环执行后，出现的未知异常
                                e.printStackTrace();
                            }
                            continue;
                        }
                        //删除无效socket
                        if(!s.isConnected()){
                            socketList.remove(i);
                        }

                    }

                    socketList.add(client);
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                            client.getInetAddress() + "已连接");
                    //启动一个线程处理与client的对话
                    try {
                        //设置发送缓存区大小
                        client.setSendBufferSize(1 * 1024 * 1024);
                        client.setSendBufferSize(1 * 1024 * 1024);
                        client.setTcpNoDelay(true);
                        int revSize = client.getReceiveBufferSize();
                        int sendSize = client.getSendBufferSize();
                        System.out.println("接收缓存区" + revSize + "   发送缓存区" + sendSize + " ");
                        mExecutorService.execute(new ClientThread(client)); //启动一个新的线程来处理连接
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void closeServer() {
        //在这里不关闭ServerSocket
        //只关闭和client连接的socket
        //和再编码线程
        serverState = false;
        int size = socketList.size();
        for (int i = 0; i < size; ++i) {
            try {
                Socket socket = socketList.get(i);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        socketList.clear();
        //关闭再编码
        reencodeFlag = false;
        //此方法调用后，再执行转化为Client状态
        //确保定时任务关闭
        cancelTimerTask();
    }

    //处理与client的socket对话
    class ClientThread extends Thread {
        private Socket socket;
        private String client_ip;
        //private String partner_phoneName = "";
        private DataInputStream in = null;   //接收
        //对其添加同步锁
        //用前申请  用后释放
        private DataOutputStream dos = null; //发送
        public Semaphore dos_Semaphore = new Semaphore(1);

        public volatile long startTime = 0;//接收到文件请求的时间
        public volatile long serverDelay = 0;//从发送文件请求到返回给它文件的延迟

        //private InputStream inputStream;

        public ClientThread(Socket socket) {
            this.socket = socket;
            //this.socket.setTcpNoDelay(true);
            client_ip = socket.getInetAddress().toString();
            this.setName(client_ip + " 服务线程");
            System.out.println(this.getName() + "已启动");
            initialize();
        }

        private void initialize() {
            try {
                socket.setTcpNoDelay(true); //设置直接发送
                //inputStream = socket.getInputStream();
                in = new DataInputStream(socket.getInputStream());     //接收
                dos = new DataOutputStream(socket.getOutputStream());//发送

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            //发送给client xml文件
            String xmlFilePath = GlobalVar.g_ef.getStoragePath() + File.separator + "xml.txt";
            sendFiles(xmlFilePath, false);
            byte[] getBytes = new byte[1024];
            while (socket.isConnected()) {
                try {
                    //获取文件名称  或是指令   在此做一个判断
                    final String fileNameOrOrder = in.readUTF();
                    //创建存储地址
                    String filePath = "";
                    if (fileNameOrOrder.equals("xml.txt")) {
                        //改写存储的xml名字  用来避免多个client时，对同一个xml.txt操作
                        //TCPClient中此处就不需要再添加，因为它只会收到server的xml.txt
                        filePath = GlobalVar.getTempPath() + File.separator + "xml" +
                                LocalInfor.getCurrentTime("MMddHHmmssSSS") + ".txt";
                    } else if (fileNameOrOrder.equals(Constant.ANSWER_END)) {
                        //一次服务请求结束  再次请求服务
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "对方的一次文件请求应答完成");
                        serviceAcquire();
                        continue;
                    } else if (fileNameOrOrder.contains(",")) {
                        //这个是文件请求信息
                        startTime = System.currentTimeMillis();
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                client_ip + "发起文件请求" + ",请求码" + fileNameOrOrder);
                        //新开的线程
                        //为了保证发送和接收是互不影响的
                        System.out.println("收到文件请求" + fileNameOrOrder);

                        //表示将要发生实际的数据交换，此时要关闭定时任务
                        //定时任务取消的代码
                        cancelTimerTask();

                        solveFileRequest(fileNameOrOrder);
                        continue;
                    } else if (fileNameOrOrder.contains(".")) {
                        filePath = getEncodeFileStrogePath(fileNameOrOrder);
                    }
                    //获取文件长度
                    int fileLen = in.readInt();
                    File file = new File(filePath);
//                    if (file.exists()) {
//                        file.delete();
//                        file.createNewFile();
//                    }
                    //FileOutputStream fos = new FileOutputStream(file, true);  //覆盖写
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
                    if (fileNameOrOrder.equals("xml.txt")) {
                        //解析xml文件
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
                    //SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, fileNameOrOrder + "接收完成");
                } catch (IOException e) {
                    e.printStackTrace();
                    socketList.remove(socket);
                    socketExceptionHandle();
                    if (socketList.size() == 0) {
                        if (!taskState) {
                            //定时任务的启动代码
                            startTimerTask();
                        }
                    }
                    break;
                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                    System.out.println("(server)对方的socket突然关闭，造成的异常退出");
                    socketList.remove(socket);
                    socketExceptionHandle();
                    if (socketList.size() == 0) {
                        if (!taskState) {
                            //定时任务的启动代码
                            startTimerTask();
                        }
                    }
                    break;
                }
            }
        }

        /**
         * @param filepath
         * @param deleteFile 发送完成后是否删除文件
         */
        public void sendFiles(String filepath, boolean deleteFile) {
            try {
                File file = new File(filepath);
                FileInputStream fis = new FileInputStream(file);
                String fileName = file.getName();
                //outputstream.write(fileName.getBytes());
                dos_Semaphore.acquire();
                dos.writeUTF(fileName);
                dos.writeInt((int) file.length());

                byte[] sendbytes = new byte[1024];
                int nLen = 0;
                while ((nLen = fis.read(sendbytes, 0, sendbytes.length)) > 0) {
                    dos.write(sendbytes, 0, nLen);
                    dos.flush();
                }
                dos_Semaphore.release();
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                        file.getName() + "发送完成");
                fis.close();
                if (deleteFile) {
                    file.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        public void parseXML(File file) {
            itsEncodeFile = EncodeFile.xml2object(file, false);
            file.delete();
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    "解析完成，正在查看是否拥有对自己有用的数据");
            //根据对方的xml文件查看是否拥有对自己有用的数据
            //如果有的话，则请求
            // 给服务器端发送文件请求
            serviceAcquire();
        }

        //服务请求  向对方发送文件请求
        public void serviceAcquire() {
//            synchronized (ClientThread.class) {
            if (itsEncodeFile == null) {
                return;
            }
            int[] usefulParts = GlobalVar.g_ef.findUsefulParts(itsEncodeFile);
            //String requestCode = "";
            if (usefulParts != null) {
                String requestCode = "";
                int requestNum = usefulParts.length;
                //
                int[] changeWillHaveFlag = null;
                for (int i = 0; i < requestNum; ++i) {
                    //看下这部分是否即将拥有所有的文件
                    int partNo = usefulParts[i];
//                        if (willHave[partNo - 1]) {
//                            //说明这部分文件即将拥有所有的文件，则不再请求
//                            continue;
//                        }
                    requestCode += (usefulParts[i] + ",");
                    //如果请求后，发现所有文件都已经满足，则把willHave位置位true
//                        for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
//                            if (pieceFile.getPieceNo() == partNo) {
//                                if (pieceFile.getCurrentFileNum() == (pieceFile.getnK() - 1)) {
//                                    willHave[partNo - 1] = true;
//                                    if (changeWillHaveFlag == null) {
//                                        changeWillHaveFlag = new int[1];
//                                        changeWillHaveFlag[i] = partNo;
//                                    } else {
//                                        int len = changeWillHaveFlag.length;
//                                        int[] newArray = new int[len + 1];
//                                        newArray[len] = partNo;
//                                    }
//                                }
//                            }
                }
                //}
                //启动一个定时任务    10s后清除 即将拥有标志
//                    if (changeWillHaveFlag != null) {
//                        cleanWillHaveFlag(changeWillHaveFlag);
//                    }
                //没有要请求的文件
//                    if (requestCode.equals("")) {
//                        //没有可供请求的文件了
//                        //告诉client不再请求数据了
//                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
//                                "(server）本地不再向" + client_ip + "请求数据");
//                        try {
//                            dos_Semaphore.acquire();
//                            dos.writeUTF(Constant.REQUEST_END);
//                            dos_Semaphore.release();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                        return;
//                    }

                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                        "对方拥有对我有用的数据，向对方发送文件请求");
                //表示将要发生实际的数据交换，此时要关闭定时任务
                //定时任务取消的代码
                cancelTimerTask();
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
                //没有可供请求的文件了
                //告诉client不再请求数据了
                try {
                    dos_Semaphore.acquire();
                    dos.writeUTF(Constant.REQUEST_END);
                    dos_Semaphore.release();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                        "(server）本地不再向" + client_ip + "请求数据");
            }
        }

        //处理文件请求信息   请求信息包含,逗号
        public void solveFileRequest(final String requestOrder) {
            Thread solveFileReqThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("（server)发送文件线程已经在运行");
                    String[] strParts = requestOrder.split(",");
                    //只测第一个文件获取的时延
                    boolean first = true;
                    for (String strPart : strParts) {
                        if (!strPart.equals("")) {
                            int partNo = Integer.parseInt(strPart);
                            //在本地找信息
                            for (final PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
                                if (pieceFile.getPieceNo() == partNo) {
                                    //打印信息
                                    System.out.println("("+LocalInfor.getCurrentTime("HH:mm:ss:SSS")+")"+
                                            "正在获取" + partNo + "部分再编码文件");
//                                    while (!pieceFile.isHaveSendFile()) {
//                                        System.out.println("获取文件时,haveSendFile为false," + "正在循环等待再编码文件");
//                                    }

                                    //String reEncodeFilePath = pieceFile.getReencodeFile();
                                    //String s = pieceFile.getReencodeFile();
                                    //String[] split = s.split("#");
                                    //long delay0 = Integer.parseInt(split[0]);
                                    String reEncodeFilePath = pieceFile.getReencodeFile();
                                    if (first) {
                                        long endTime = System.currentTimeMillis();
                                        serverDelay = endTime - startTime;
                                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                                client_ip + "文件获取时延为" + serverDelay + " ms");
                                        first = false;
                                    }
                                    //发送给用户
                                    System.out.println("("+LocalInfor.getCurrentTime("HH:mm:ss:SSS")+")"+
                                            "获取到再编码文件" + reEncodeFilePath);
                                    sendFiles(reEncodeFilePath, true);
                                    //发送完后，再重新编码文件
                                    //此位置位false，当检测到时，启动再编码
                                    //pieceFile.setHaveSendFile(false);
//                        new Thread(new Runnable() {
//                            @Override
//                            public void run() {
//                                if (!pieceFile.isReencoding()) {
//                                    pieceFile.re_encodeFile();
//                                }
//                            }
//                        }).start();
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


        //获取文件存储地址
        public String getEncodeFileStrogePath(String encodeFileName) {
            String filePath = "";
            String strPartNo = encodeFileName.substring(0, encodeFileName.indexOf("."));
            int partNo = Integer.parseInt(strPartNo);
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

        //socket突然断开后，对本地变量做的异常处理
        public synchronized void socketExceptionHandle() {
            if (GlobalVar.g_ef == null) {
                return;
            }
            for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
                pieceFile.handleDataSynError();
            }
        }

    }

    //接收到文件后   改变EncodeFile变量
    //在服务器端这里需要引入同步
    //这个方法对encodeFile进行了更改
    //因此需要互斥的进入执行
    //需要加入类锁

    public void solveFileChange(File file) {
        //类锁
        synchronized (TcpServer.class) {
            String fileName = file.getName();
            String strPartNo = fileName.substring(0, fileName.indexOf("."));
            int partNo = Integer.parseInt(strPartNo);
            for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
                if (pieceFile.getPieceNo() == partNo) {
                    if (pieceFile.addEncodeFile(file)) {
                        //更改已收到的编码数据片的个数
                        GlobalVar.g_ef.updateCurrentSmallPiece();
                        //写入xml配置文件
                        GlobalVar.g_ef.object2xml();
                    }
                    break;
                }
            }

            //尝试解码
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (GlobalVar.g_ef.getCurrentSmallPiece() == GlobalVar.g_ef.getTotalSmallPiece()) {
                        //访问解码锁
                        //正在解码，则不访问
                        if (decoding) {
                            return;
                        }
                        //上锁 不允许别的线程再进入
                        decoding = true;
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
                        //开锁  允许进入
                        decoding = false;
                    }
                }
            }).start();
        }
    }
    //删除部分文件即将拥有标志
//    public void cleanWillHaveFlag(final int[] changeWillHaveFlag) {
//        //初始化mTimer和mTimerTask
//        TimerTask timerTask = new TimerTask() {
//            @Override
//            public void run() {
//                // task to run goes here
//                int len = changeWillHaveFlag.length;
//                for (int i = 0; i < len; ++i) {
//                    int partNo = changeWillHaveFlag[i];
//                    if (willHave[partNo - 1]) {
//                        willHave[partNo - 1] = false;
//                    }
//                }
//            }
//        };
//        Timer timer = new Timer();
//        //mTimer = new Timer();
//        //long delay = 0;
//        // schedules the task to be run in an interval
//        //定时任务的启动代码
//        long delay = 10 * 1000;
//        timer.schedule(timerTask, delay);
//    }

    public void startTimerTask() {
        //
        if (taskState) {
            return;
        }

        //初始化mTimer和mTimerTask
        TimerTask mTimerTask = new TimerTask() {
            @Override
            public void run() {
                // task to run goes here
                System.out.println("定时任务运行");
                //这里做切换向普通节点的动作
                //SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "定时任务运行");
                SendMessage(MsgValue.SERVER_2_CLIENT, 0, 0, null);
                //定时任务执行完，处于关闭状态
                taskState = false;
            }
        };
        mTimer = new Timer();
        //long delay = 0;
        // schedules the task to be run in an interval
        //定时任务的启动代码
        Random random = new Random(System.currentTimeMillis());
        int diff = random.nextInt(20);
        long delay = baseDelay + diff * 1000;
        mTimer.schedule(mTimerTask, delay);

        taskState = true;
        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                "(server）定时任务已经开启，" + (delay / 1000) + "秒后切换状态");
        //定时任务取消的代码
        //mTimer.cancel();
    }

    public void cancelTimerTask() {
        //
        if (taskState) {
            mTimer.cancel();
            taskState = false;
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    "(server）定时任务被取消");
        }
    }

    //一个循环检测线程   当检测到haveSendFile变量为false时，启动再编码
    //注意：TCPServer关闭时，此处需要关闭
    public void reencodeListenerThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (GlobalVar.g_ef != null) {
                    //删除之前没删掉的xml.txt
                    ArrayList<File> xmlFiles = MyFileUtils.getList_1_files(GlobalVar.getTempPath());
                    for (File file : xmlFiles) {
                        file.delete();
                    }
                    //清空发送缓存中没来得及删除的文件
                    for (PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
                        MyFileUtils.deleteAllFile(pieceFile.getSendBufferPath(), false);
                        //
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
                }
                while (reencodeFlag) {
                    if (GlobalVar.g_ef == null) {

                    } else {
                        int size = GlobalVar.g_ef.getPieceFileList().size();
                        for (int i = 0; i < size; ++i) {
                            final PieceFile pieceFile = GlobalVar.g_ef.getPieceFileList().get(i);
                            if (!pieceFile.isHaveSendFile()) {
                                boolean isReencoding = pieceFile.isReencoding();
                                if (!isReencoding) {
                                    //在这里重开一个子线程执行
//                                    pieceFile.re_encodeFile();
//                                    //更改xml文件
//                                    GlobalVar.g_ef.object2xml();
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            pieceFile.re_encodeFile();
                                            //更改xml文件
                                            GlobalVar.g_ef.object2xml();
                                        }
                                    }).start();
                                }
                            }
                        }
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


    //本类发送消息的方法
    private void SendMessage(int what, int arg1, int arg2, Object obj) {
        if (handler != null) {
            Message.obtain(handler, what, arg1, arg2, obj).sendToTarget();
        }
    }
}
