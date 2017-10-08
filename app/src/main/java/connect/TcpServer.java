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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
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
    private EncodeFile encodeFile;

    private EncodeFile itsEncodeFile;

    public TcpServer(Handler handler) {
        this.handler = handler;
    }

    public void openServerSocket(EncodeFile encodeFile) {
        this.encodeFile = encodeFile;
        try {
            svrSock = new ServerSocket(Constant.TCP_ServerPORT);
            svrSock.setReuseAddress(true);   //设置上一个关闭的超时状态下可连接
//            InetSocketAddress endpoint = new InetSocketAddress(Constant.TCP_ServerIP, Constant.TCP_ServerPORT);
//            svrSock = new ServerSocket();
//            svrSock.setReceiveBufferSize(1 * 1024 * 1024);
//            svrSock.bind(endpoint);
        } catch (IOException e) {
            //失败
            e.printStackTrace();
        }
        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "绑定端口成功");
        // 创建线程池
        mExecutorService = Executors.newCachedThreadPool();
        Socket client = null;
        //监听需要再编码的时机
        reencodeListenerThread();
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
                    }
                    break;
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

    public void closeServer() {

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
            String xmlFilePath = encodeFile.getStoragePath() + File.separator + "xml.txt";
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
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                client_ip + "发起文件请求" + ",请求码" + fileNameOrOrder);
                        //新开的线程
                        //为了保证发送和接收是互不影响的
                        System.out.println("收到文件请求" + fileNameOrOrder);
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
                    break;
                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                    System.out.println("(server)对方的socket突然关闭，造成的异常退出");
                    socketList.remove(socket);
                    socketExceptionHandle();
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
            if (itsEncodeFile == null) {
                return;
            }
            String usefulParts = encodeFile.findUsefulParts(itsEncodeFile);
            if (!usefulParts.equals("")) {
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                        "对方拥有对我有用的数据，向对方发送文件请求");
                try {
                    dos_Semaphore.acquire();
                    dos.writeUTF(usefulParts);
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
                        if (encodeFile.getCurrentSmallPiece() == encodeFile.getTotalSmallPiece()) {
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                    "本地已拥有所有的编码数据片，正在解码");
                            if (encodeFile.recoveryFile()) {
                                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                        encodeFile.getFileName() + "解码成功");
                            } else {
                                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                        "解码失败");
                            }
                        }
                    }
                }).start();
            }
        }

        //处理文件请求信息   请求信息包含,逗号
        public void solveFileRequest(final String requestOrder) {
            Thread solveFileReqThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("（server)发送文件线程已经在运行");
                    String[] strParts = requestOrder.split(",");
                    for (String strPart : strParts) {
                        if (!strPart.equals("")) {
                            int partNo = Integer.parseInt(strPart);
                            //在本地找信息
                            for (final PieceFile pieceFile : encodeFile.getPieceFileList()) {
                                if (pieceFile.getPieceNo() == partNo) {
                                    //打印信息
                                    System.out.println("正在获取" + partNo + "部分再编码文件");
                                    String reEncodeFilePath = pieceFile.getReencodeFile();
                                    //发送给用户
                                    System.out.println("获取到再编码文件" + reEncodeFilePath);
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
                for (PieceFile pieceFile : encodeFile.getPieceFileList()) {
                    if (pieceFile.getPieceNo() == partNo) {
                        if (pieceFile.addEncodeFile(file)) {
                            //更改已收到的编码数据片的个数
                            encodeFile.updateCurrentSmallPiece();
                            //写入xml配置文件
                            encodeFile.object2xml();
                        }
                        break;
                    }
                }
            }
        }

        //获取文件存储地址
        public String getEncodeFileStrogePath(String encodeFileName) {
            String filePath = "";
            String strPartNo = encodeFileName.substring(0, encodeFileName.indexOf("."));
            int partNo = Integer.parseInt(strPartNo);
            //构建文件存储路径
            for (PieceFile pieceFile : encodeFile.getPieceFileList()) {
                if (pieceFile.getPieceNo() == partNo) {
                    filePath = pieceFile.getEncodeFilePath() + File.separator + encodeFileName;
                    break;
                }
            }
            //本地编码数据中没有此部分文件
            if (filePath.equals("")) {
                int rightFileLen = 0;
                if (partNo == encodeFile.getTotalParts()) {
                    rightFileLen = encodeFile.getRightFileLen2();
                } else {
                    rightFileLen = encodeFile.getRightFileLen1();
                }
                PieceFile pieceFile = new PieceFile(
                        encodeFile.getStoragePath(),
                        partNo,
                        encodeFile.getnK(),
                        rightFileLen
                );
                //添加进列表
                encodeFile.getPieceFileList().add(pieceFile);
                encodeFile.setCurrentParts(encodeFile.getCurrentParts() + 1);
                filePath = pieceFile.getEncodeFilePath() + File.separator + encodeFileName;
            }
            return filePath;
        }

        //socket突然断开后，对本地变量做的异常处理
        public synchronized void socketExceptionHandle() {
            if (encodeFile == null) {
                return;
            }
            for (PieceFile pieceFile : encodeFile.getPieceFileList()) {
                pieceFile.handleDataSynError();
            }
        }

    }


    //一个循环检测线程   当检测到haveSendFile变量为false时，启动再编码
    //注意：TCPServer关闭时，此处需要关闭
    public void reencodeListenerThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (encodeFile != null) {
                    //删除之前没删掉的xml.txt
                    ArrayList<File> xmlFiles = MyFileUtils.getList_1_files(GlobalVar.getTempPath());
                    for (File file : xmlFiles) {
                        file.delete();
                    }
                    //清空发送缓存中没来得及删除的文件
                    for (PieceFile pieceFile : encodeFile.getPieceFileList()) {
                        MyFileUtils.deleteAllFile(pieceFile.getSendBufferPath(), false);
                    }
                }
                while (true) {
                    if (encodeFile == null) {

                    } else {
                        for (PieceFile pieceFile : encodeFile.getPieceFileList()) {
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


    //本类发送消息的方法
    private void SendMessage(int what, int arg1, int arg2, Object obj) {
        if (handler != null) {
            Message.obtain(handler, what, arg1, arg2, obj).sendToTarget();
        }
    }
}
