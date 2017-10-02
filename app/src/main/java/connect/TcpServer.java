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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import appData.GlobalVar;
import fileSlices.EncodeFile;
import fileSlices.PieceFile;
import msg.MsgValue;

/**
 * Created by kingstones on 2017/9/27.
 */

public class TcpServer {
    private List<Socket> socketList = new ArrayList<Socket>();
    public ServerSocket svrSock;
    private ExecutorService mExecutorService = null;   //线程池
    private Handler handler;
    private EncodeFile encodeFile;

    public TcpServer(Handler handler) {
        this.handler = handler;
    }

    public void openServerSocket(EncodeFile encodeFile) {
        this.encodeFile = encodeFile;
        try {
            svrSock = new ServerSocket(Constant.TCP_ServerPORT);
            svrSock.setReuseAddress(true);   //设置上一个关闭的超时状态下可连接
        } catch (IOException e) {
            //失败
            e.printStackTrace();
        }
        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "绑定端口成功");
        // 创建线程池
        mExecutorService = Executors.newCachedThreadPool();
        Socket client = null;
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
            //启动一个线程处理与client的对话
            try {
                mExecutorService.execute(new ClientThread(client)); //启动一个新的线程来处理连接
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //处理与client的socket对话
    class ClientThread extends Thread {
        private Socket socket;
        private String client_ip;
        private String partner_phoneName = "";
        private DataInputStream in = null;   //接收
        private DataOutputStream dos = null; //发送
        private InputStream inputStream;
        //

        public ClientThread(Socket socket) {
            this.socket = socket;
            client_ip = socket.getInetAddress().toString();
            this.setName(client_ip + " 服务线程");
            System.out.println(this.getName() + "已启动");
            initialize();
        }

        private void initialize() {
            try {
                socket.setTcpNoDelay(true); //设置直接发送
                inputStream = socket.getInputStream();
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
            sendFiles(xmlFilePath, dos, false);
            byte[] getBytes = new byte[1024];
            while (socket.isConnected()) {
                try {
                    //获取文件名称  或是指令   在此做一个判断
                    final String fileNameOrOrder = in.readUTF();
                    //创建存储地址
                    String filePath = "";
                    if (fileNameOrOrder.equals("xml.txt")) {
                        filePath = GlobalVar.getTempPath() + File.separator + "xml.txt";
                    } else if (fileNameOrOrder.contains(",")) {
                        //这个是文件请求信息
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                client_ip + "发起文件请求" + ",请求码" + fileNameOrOrder);
                        //新开的线程
                        //为了保证发送和接收是互不影响的
                        solveFileRequest(fileNameOrOrder, dos);
                        continue;
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
                        // parseXML(file);
                    }
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, fileNameOrOrder + "接收完成");
                } catch (IOException e) {
                    e.printStackTrace();
                    socketList.remove(socket);
                    break;
                }
            }
        }
    }

    /**
     * @param filepath
     * @param dos
     * @param deleteFile 发送完成后是否删除文件
     */
    public void sendFiles(String filepath, DataOutputStream dos, boolean deleteFile) {
        try {
            File file = new File(filepath);
            FileInputStream fis = new FileInputStream(file);
            String fileName = file.getName();
            //outputstream.write(fileName.getBytes());
            dos.writeUTF(fileName);
            dos.writeInt((int) file.length());

            byte[] sendbytes = new byte[1024];
            int nLen = 0;
            while ((nLen = fis.read(sendbytes, 0, sendbytes.length)) > 0) {
                dos.write(sendbytes, 0, nLen);
                dos.flush();
            }
            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                    file.getName() + "发送完成");
            if (deleteFile) {
                file.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //处理文件请求信息   请求信息包含,逗号
    public void solveFileRequest(String requestOrder, DataOutputStream dos) {
        String[] strParts = requestOrder.split(",");
        for (String strPart : strParts) {
            if (!strPart.equals("")) {
                int partNo = Integer.parseInt(strPart);
                //在本地找信息
                for (final PieceFile pieceFile : encodeFile.getPieceFileList()) {
                    if (pieceFile.getPieceNo() == partNo) {
                        String reEncodeFilePath = pieceFile.getReencodeFile();
                        //发送给用户
                        sendFiles(reEncodeFilePath, dos, true);
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

    //本类发送消息的方法
    private void SendMessage(int what, int arg1, int arg2, Object obj) {
        if (handler != null) {
            Message.obtain(handler, what, arg1, arg2, obj).sendToTarget();
        }
    }
}
