package fileSlices;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import nc.NCUtils;
import utils.IntAndBytes;
import utils.LocalInfor;
import utils.MyFileUtils;

/**
 * Created by Administrator on 2017/7/22 0022.
 */
@XStreamAlias("Subfile")
public class PieceFile {
    @XStreamAsAttribute
    @XStreamAlias("subfileNo")
    private int pieceNo;
    @XStreamAlias("revSeg")
    private int currentFileNum = 0;
    //是否对每个编码文件设置校对长度？？？
    @XStreamAlias("segmentSize")
    private int rightFileLen = 0;

    //系数矩阵 用int数组来存储
    private int[][] coeffMatrix = new int[0][0];
    //隐藏以下字段
    @XStreamOmitField
    private int nK;
    @XStreamOmitField
    private String pieceFilePath;
    @XStreamOmitField
    private String encodeFilePath;
    @XStreamOmitField
    private String re_encodeFilePath;
    //用来暂存将要发送的文件
    @XStreamOmitField
    private String sendBufferPath;

    //用以标志是否正在再编码
    @XStreamOmitField
    private volatile boolean isReencoding = false;
    //当把准备把文件发送给一个用户时
    //把此位置位false
    //监听此位   如果是false，则重新生成再编码文件
    @XStreamOmitField
    private volatile boolean haveSendFile = true;

//    @XStreamOmitField
//    private volatile String sendFilePath = null;

    public PieceFile(String path, int pieceNo, int nK, int rightFileLen) {
        this.pieceNo = pieceNo;
        this.nK = nK;
        this.rightFileLen = rightFileLen;
        //创建存储路径
        pieceFilePath = MyFileUtils.creatFolder(path, pieceNo + "");
        //两个二级目录
        encodeFilePath = MyFileUtils.creatFolder(pieceFilePath, "encodeFiles");
        re_encodeFilePath = MyFileUtils.creatFolder(pieceFilePath, "re_encodeFile");

        sendBufferPath=MyFileUtils.creatFolder(pieceFilePath,"sendBuffer");
    }

    /**
     * 根据xml文件，看看对方是否拥有对自己有用的数据
     * 有则true，否则false
     *
     * @return
     */
    public boolean usefulOrNot(int[][] itsCoeffMatrix) {
        //本地无此部分文件的编码数据
        if (coeffMatrix.length == 0) {
            return true;
        }
        //与自己的系数矩阵拼起来
        int row = coeffMatrix.length;
        int myRank = row;
        int[][] testMatrix = new int[row + 1][nK];
        //在此使用一维矩阵
        System.arraycopy(coeffMatrix, 0, testMatrix, 0, row);
        //认为对方的系数矩阵的秩也会这个值
        int itsRow = itsCoeffMatrix.length;
        for (int i = 0; i < itsRow; ++i) {
            //拼接在最后一行
            System.arraycopy(itsCoeffMatrix[i], 0, testMatrix[row], 0, nK);
            //计算拼接之后的秩
            NCUtils.nc_acquire();
            int nRank = NCUtils.getRank(IntAndBytes.int2Array_byte1Array(testMatrix), row + 1, nK);
            NCUtils.nc_release();
            if (nRank == (myRank + 1)) {
                return true;
            }
        }
        return false;
    }

    //接收到编码文件   加入操作
    public boolean addEncodeFile(File file) {
        //合法性检验
        //1、文件长度
        int fileLen = (int) file.length();
        if (fileLen != rightFileLen) {
            file.delete();
            return false;
        }
        byte[][] coeff = new byte[1][nK];
        byte[] b = new byte[1];
        try {
            FileInputStream stream = new FileInputStream(file);
            stream.read(b, 0, 1);
            //读入一行系数矩阵
            stream.read(coeff[0], 0, nK);
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //2、文件长度校验
        int k = (int) b[0];
        if (k != nK) {
            file.delete();
            return false;
        }
        //3、检查秩
        int row = coeffMatrix.length;
        if (row == 0) {
            //本地之前无数据
            coeffMatrix = IntAndBytes.byteArray2IntArray(coeff);
            currentFileNum = row + 1;
            //重新生成再编码文件
            //re_encodeFile();
            return true;
        } else {
            byte[][] bt_coefM = IntAndBytes.intArray2byteArray(coeffMatrix);
            int nRank = row;
            byte[][] testCoeff = new byte[row + 1][nK];
            //拼接矩阵
            System.arraycopy(bt_coefM, 0, testCoeff, 0, row);
            System.arraycopy(coeff[0], 0, testCoeff[row], 0, nK);
            //计算秩
            NCUtils.nc_acquire();
            int rank = NCUtils.getRank(IntAndBytes.byte2Array_byte1Array(testCoeff), row + 1, nK);
            NCUtils.nc_release();
            if (rank == nRank) {
                //证明数据没用
                file.delete();
                return false;
            } else if (rank == (nRank + 1)) {
                //数据有用
                //更新系数矩阵
                coeffMatrix = IntAndBytes.byteArray2IntArray(testCoeff);
                currentFileNum = row + 1;

                //重新生成再编码文件
                haveSendFile = false;
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        //没有正在再编码，则开始再编码
//                        if (!isReencoding()) {
//                            re_encodeFile();
//                        }
//                    }
//                }).start();
                return true;
            } else {
                //未知错误
                System.out.println("接收到文件后，在本地进行加入文件操作出错（PieceFile类）");
                return false;
            }
        }

    }

    //获取再编码文件
    public synchronized String getReencodeFile() {
        while (!haveSendFile) {
            System.out.println("获取文件时,haveSendFile为false," + "正在循环等待再编码文件");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ArrayList<File> fileArrayList = MyFileUtils.getListFiles(re_encodeFilePath);
        File file = fileArrayList.get(0);
        System.out.println("已获取到再编码文件,正在剪切");
        File transferFile = MyFileUtils.transferFile(file, sendBufferPath);
        haveSendFile = false;
        return transferFile.getPath();
//        ArrayList<File> files = MyFileUtils.getListFiles(re_encodeFilePath);
//        int size = files.size();
//        if (size == 0) {
//            //生成在编码文件
//
////            if (isReencoding) {
////                //等待再编码结束
////                while (isReencoding) {
////                    try {
////                        Thread.sleep(100);
////                    } catch (InterruptedException e) {
////                        e.printStackTrace();
////                    }
////                }
////                ArrayList<File> fileArrayList = MyFileUtils.getListFiles(re_encodeFilePath);
////                return fileArrayList.get(0).getPath();
////            } else {
////                String filePath = re_encodeFile();
////                return filePath;
////            }
//        }
//        if (size > 1) {
//            System.out.println("再编码文件大于1");
//        }
//        haveSendFile = false;
//        return files.get(0).getPath();
    }

    //检查文件长度是否合法 编码系数是否有重复
    //若要获取encodefile需用此方法获取文件列表
    public ArrayList<File> checkFileLen() {
        ArrayList<File> fileList = MyFileUtils.getList_1_files(encodeFilePath);
        int fileNum = fileList.size();
        for (File file : fileList) {
            int fileLen = (int) file.length();
            if (fileLen != rightFileLen) {
                file.delete();
                System.out.println("找到有文件不符合长度要求，删除");
            }
        }
        //此处应该加大文件合法性的校验
        //1、首字节是否为nK
        //2、编码系数是否在系数矩阵中
        //检查下系数有多少行系数
        ArrayList<File> fileList1 = MyFileUtils.getList_1_files(encodeFilePath);
//        int fileNum1 = fileList1.size();
//        if (currentFileNum != fileNum1) {
//            currentFileNum = fileNum1;
//        }
        //说明文件数目和系数矩阵的行数是否一致
//        if(coeffMatrix.length!=fileNum){
//            //不一致   就说明出现了系数矩阵和文件出现了严重的数据同步错误
//        }
        return fileList1;
    }

    //对文件进行编码，这里其实只是用单位矩阵进行封装
    public void encodeFile(File pfile) {
        int fileLen = (int) pfile.length();
        int perLen = fileLen / nK + (fileLen % nK != 0 ? 1 : 0);  //每个子代编码片长度
        //设置文件校对长度
        // rightFileLen = 1 + nK + perLen;
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(pfile);
        } catch (FileNotFoundException e) {
            //读文件出错
            e.printStackTrace();
            return;
        }
        //对数据封装 K+单位矩阵+数据
        for (int m = 0; m < nK; ++m) {
            byte[] b = new byte[1 + nK];
            b[0] = (byte) nK;
            b[m + 1] = 1;
            // String fileName = (i + 1) + "_" + (m + 1) + ".nc";
            String fileName = pieceNo + "." + LocalInfor.getCurrentTime("MMddHHmmssSSS") + ".nc";
            File piece_file = MyFileUtils.creatFile(encodeFilePath, fileName);
            try {
                FileOutputStream fos = new FileOutputStream(piece_file);
                fos.write(b);    //写入文件
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            MyFileUtils.splitFile(inputStream, encodeFilePath, fileName, perLen);
        }
        //初始化变量
        currentFileNum = nK;   //代表拥有所有的文件
        //系数矩阵
        coeffMatrix = new int[nK][nK];
        for (int i = 0; i < nK; ++i) {
            coeffMatrix[i][i] = 1;
        }
    }

    //对文件进行再编码   需要访问jni
    //需要在一个独立线程中运行
    //因为会分配较大内存，所以规定每次之后一个可以进入
    //返回再编码文件的路径
    public String re_encodeFile() {
        System.out.println(pieceNo + "正在进行再编码");
        isReencoding = true;
        //从编码文件路径中取出文件
        List<File> fileList = checkFileLen();
        if (fileList == null) {
            //检查长度时出错
            //   return null;
        }
        int fileNum = fileList.size();
        //如果只有一个编码文件的话，那就不用再编码
        if (fileNum == 1) {
            File file = fileList.get(0);
            return file.getPath();
        }
        //注意：用于再编码的文件长度必定都是一样的
        int fileLen = (int) (fileList.get(0).length());
        //用于存文件数组  一维数组存储
        byte[] fileData = new byte[fileNum * fileLen];
        for (int i = 0; i < fileNum; ++i) {
            File file = fileList.get(i);
            try {
                FileInputStream fis = new FileInputStream(file);
                //读取fileLen个字节，放入filedata中，从i*fileData位置开始写
                fis.read(fileData, i * fileLen, fileLen);    //读取文件中的内容到b[]数组
                fis.close();
            } catch (IOException e) {
                //Toast.makeText(this, "读取文件异常", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                //  return null;
            }
        }
        //存再编码结果
        NCUtils.nc_acquire();
        byte[] reEncodeData = NCUtils.reencode(fileData, fileNum, fileLen);
        NCUtils.nc_release();
        MyFileUtils.deleteAllFile(re_encodeFilePath, false);
        String fileName = pieceNo + "." + LocalInfor.getCurrentTime("MMddHHmmssSSS") + ".nc"; //pieceNo.time.re  //格式
        File re_encodeFile = MyFileUtils.writeToFile(re_encodeFilePath, fileName, reEncodeData);
        isReencoding = false;
        haveSendFile = true;
        System.out.println(pieceNo + "再编码完成，已返回文件路径");
        return re_encodeFile.getPath();
    }

    //对文件进行解码     需要访问jni
    public boolean decode_pfile() {
        List<File> fileList = checkFileLen();
        if (fileList == null) {
            //检查长度时出错
            return false;
        }
        int fileNum = fileList.size();
        if (fileNum < nK) {
            //文件数目不足，无法解码
            return false;
        }
        int fileLen = (int) fileList.get(0).length();
        //用于存文件数组  存入一维数组
        byte[] fileData = new byte[nK * fileLen];   //如果文件很多，也只需nK个文件
        for (int i = 0; i < nK; ++i) {
            File file = fileList.get(i);
            try {
                InputStream in = new FileInputStream(file);
                //b = new byte[fileLen];
                in.read(fileData, i * fileLen, fileLen);    //读取文件中的内容到b[]数组
                in.close();
            } catch (IOException e) {
                //Toast.makeText(this, "读取文件异常", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return false;
            }
        }
        //int col = fileLen - 1 - nK;
        NCUtils.nc_acquire();
        byte[] origin_data = NCUtils.decode(fileData, nK, fileLen);
        NCUtils.nc_release();
        if (origin_data == null) {
            return false;
        }
        //写入文件
        MyFileUtils.writeToFile(pieceFilePath, pieceNo + ".decode", origin_data);
        return true;
    }

    //检查有没有恢复出来的原文件片
    public File getOriginPFile() {
        String filePath = pieceFilePath + File.separator + pieceNo + ".decode";
        File file = new File(filePath);
        if (file.exists()) {
            return file;
        }
        //等待解码
        if (decode_pfile()) {
            File file1 = new File(filePath);
            return file1;
        } else {
            return null;
        }
    }

    //在socket突然断开时调用
    //目的是检查本地文件是否和变量信息同步
    public void handleDataSynError() {
        ArrayList<File> encodeFiles = MyFileUtils.getListFiles(encodeFilePath);
        //
        if (encodeFiles.size() == currentFileNum) {
            //说明数据是同步的没出错
            return;
        }
        //先检查文件长度
        for (File file : encodeFiles) {
            int fileLen = (int) file.length();
            if (fileLen != rightFileLen) {
                //文件长度不合法，则删除该文件
                file.delete();
            }
        }
        //出现错误
        //与系数矩阵做比对，找出出错的文件

    }

    /**
     * 以下是自动生成的Getter和Setter方法
     */
    public int getCurrentFileNum() {
        return currentFileNum;
    }

    public void setCurrentFileNum(int currentFileNum) {
        this.currentFileNum = currentFileNum;
    }

    public String getEncodeFilePath() {
        return encodeFilePath;
    }

    public void setEncodeFilePath(String encodeFilePath) {
        this.encodeFilePath = encodeFilePath;
    }

    public int getnK() {
        return nK;
    }

    public void setnK(int nK) {
        this.nK = nK;
    }

    public String getPieceFilePath() {
        return pieceFilePath;
    }

    public void setPieceFilePath(String pieceFilePath) {
        this.pieceFilePath = pieceFilePath;
    }

    public int getPieceNo() {
        return pieceNo;
    }

    public void setPieceNo(int pieceNo) {
        this.pieceNo = pieceNo;
    }

    public String getRe_encodeFilePath() {
        return re_encodeFilePath;
    }

    public void setRe_encodeFilePath(String re_encodeFilePath) {
        this.re_encodeFilePath = re_encodeFilePath;
    }

    public int getRightFileLen() {
        return rightFileLen;
    }

    public void setRightFileLen(int rightFileLen) {
        this.rightFileLen = rightFileLen;
    }

    public int[][] getCoeffMatrix() {
        return coeffMatrix;
    }

    public void setCoeffMatrix(int[][] coeffMatrix) {
        this.coeffMatrix = coeffMatrix;
    }

    public boolean isReencoding() {
        return isReencoding;
    }

    public void setReencoding(boolean reencoding) {
        isReencoding = reencoding;
    }

    public boolean isHaveSendFile() {
        return haveSendFile;
    }

    public void setHaveSendFile(boolean haveSendFile) {
        this.haveSendFile = haveSendFile;
    }

    public String getSendBufferPath() {
        return sendBufferPath;
    }

    public void setSendBufferPath(String sendBufferPath) {
        this.sendBufferPath = sendBufferPath;
    }
}
