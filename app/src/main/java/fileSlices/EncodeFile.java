package fileSlices;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.reflection.FieldDictionary;
import com.thoughtworks.xstream.converters.reflection.SortableFieldKeySorter;
import com.thoughtworks.xstream.converters.reflection.Sun14ReflectionProvider;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import appData.GlobalVar;
import utils.IntAndBytes;
import utils.LocalInfor;
import utils.MyFileUtils;

/**
 * Created by Administrator on 2017/7/22 0022.
 */

@XStreamAlias("EncodeFile")
public class EncodeFile {
    //主属性
    @XStreamAsAttribute
    private String fileName;
    @XStreamAlias("GenerationSize")
    private int nK;    //子代编码文件数目
    //分成的大的部分文件
    @XStreamAlias("involvedSubfile")
    private int CurrentParts = 0;
    @XStreamAlias("totalSubfile")
    private int TotalParts = 0;
    //小的编码文件片
    @XStreamAlias("receivedSegments")
    private int currentSmallPiece = 0;
    @XStreamAlias("totalSegments")
    private int totalSmallPiece = 0;
    //文件校验长度
    @XStreamAlias("segmentSizeA")
    private int rightFileLen1 = 0;
    @XStreamAlias("segmentSizeB")
    private int rightFileLen2 = 0;
    //文件夹名字
    private String folderName;

    //文件源手机的串号
    //标记哪个手机是文件源
    private String IMEI;
    //文件片的信息
    @XStreamAlias("subfileInfor")
    private List<PieceFile> pieceFileList = new ArrayList<PieceFile>();

    //隐藏以下字段
    @XStreamOmitField
    private String storagePath;                    //文件存储路径
    @XStreamOmitField
    private static String xmlFileName = "xml.txt";  //配置文件的名称

    public EncodeFile(String fileName, int nK, String folderName) {
        this.fileName = fileName;
        this.nK = nK;
        if (folderName == null || folderName.equals("")) {
            //获取不含后缀的文件名,作为文件夹名字  后面加时间后缀   保证文件名唯一
            this.folderName = fileName.substring(0, fileName.lastIndexOf(".")) + "_"
                    + LocalInfor.getCurrentTime("MMddHHmmssSSS");
        } else {
            this.folderName = folderName;
        }
        storagePath = MyFileUtils.creatFolder(GlobalVar.getTempPath(), this.folderName);
        IMEI = GlobalVar.getIMEI();
    }

    //对文件进行分片 分成每10M一个部分，在送去编码
    public void cutFile(final File originFile) {
//        System.out.println("对文件进行分片--    " +
//                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
        int fileLen = (int) originFile.length();
        int file_piece_len = 10 * 1024 * 1024;  //若是大于10M的文件，对文件进行分片，每片10M
        int piece_num = fileLen / file_piece_len + (fileLen % file_piece_len != 0 ? 1 : 0);
        int rest_len = fileLen - file_piece_len * (piece_num - 1);  //最后一片的长度
        //获取文件流
        InputStream in;
        try {
            in = new FileInputStream(originFile);
        } catch (FileNotFoundException e) {
            //读文件出错
            e.printStackTrace();
            return;
        }
        //分成的大文件片，暂存地址
        String dataTempPath = MyFileUtils.creatFolder(storagePath, "dataTemp");
        ArrayList<File> temp_files = new ArrayList<File>();
        //读取文件到片文件
        for (int i = 0; i < piece_num; ++i) {
            if (i == (piece_num - 1)) {
                //创建一个文件用于写入这一片数据
                File piece_file = MyFileUtils.splitFile(in, dataTempPath, LocalInfor.getCurrentTime("MMddHHmmssSSS") + ".piece", rest_len);
                temp_files.add(piece_file);
                try {
                    //关闭文件流
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            } else {
                //创建一个文件用于写入这一片数据
                File piece_file = MyFileUtils.splitFile(in, dataTempPath, LocalInfor.getCurrentTime("MMddHHmmssSSS") + ".piece", file_piece_len);
                temp_files.add(piece_file);
            }
        }
//        System.out.println("分片结束，开始编码--    " +
//                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
        //为两个校验长度赋值
        //前几个部分文件校验长度
        int perLen = file_piece_len / nK + (file_piece_len % nK != 0 ? 1 : 0);
        rightFileLen1 = 1 + nK + perLen;
        //最后一个部分文件校验长度
        int rest_perLen = rest_len / nK + (rest_len % nK != 0 ? 1 : 0);
        rightFileLen2 = 1 + nK + rest_perLen;

        //对大文件片再进行分片
        //编码：
        for (int i = 0; i < piece_num; ++i) {
            int rightFileLen;
            if (i == (piece_num - 1)) {
                rightFileLen = rightFileLen2;
            } else {
                rightFileLen = rightFileLen1;
            }
            PieceFile pieceFile = new PieceFile(storagePath, i + 1, nK, rightFileLen);
            File file = temp_files.get(i);
            pieceFile.encodeFile(file);
            pieceFile.re_encodeFile();
            //添加进List
            pieceFileList.add(pieceFile);
        }
//        System.out.println("编码结束开始再编码--    " +
//                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
//        for(PieceFile pieceFile:pieceFileList){
//            pieceFile.re_encodeFile();
//        }
//        System.out.println("再编码结束--    " +
//                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
        MyFileUtils.deleteAllFile(dataTempPath, true);
        //设置变量
        CurrentParts = piece_num;
        TotalParts = piece_num;
        currentSmallPiece = piece_num * nK;
        totalSmallPiece = currentSmallPiece;
        //把配置存入xml文件
        object2xml();
        //把源文件复制到现在的编码文件目录下一份
        new Thread(new Runnable() {
            @Override
            public void run() {
                MyFileUtils.moveFile(originFile, storagePath, false);
            }
        }).start();
    }

    //恢复文件   解码文件
    public boolean recoveryFile() {
        //合并后的文件存储路径
        String outFilePath = storagePath + File.separator + fileName;
        File originFile = new File(outFilePath);
        if (originFile.exists()) {
            //已经解码过了
            return true;
        }
        if (CurrentParts != TotalParts) {
            return false;
        }
        if (currentSmallPiece != totalSmallPiece) {
            return false;
        }
        File[] files = new File[TotalParts];
        for (PieceFile pieceFile : pieceFileList) {
            int pieceNo = pieceFile.getPieceNo();
            File file = pieceFile.getOriginPFile();
            if (file == null) {
                return false;
            } else {
                files[pieceNo - 1] = file;
            }
        }
        for (int i = 0; i < TotalParts; ++i) {
            if (files[i] == null) {
                return false;
            }
        }
        //合并文件
        //String outFilePath = storagePath + File.separator + fileName;
        System.out.println("拼接开始--    " +
                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
        MyFileUtils.mergeFiles(outFilePath, files);
        System.out.println("拼接结束--    " +
                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
        //MyFileUtils.mergeFiles(outFilePath, files);
        //在此删除恢复回来的部分文件
        for (int i = 0; i < files.length; ++i) {
            File file = files[i];
            file.delete();
        }
        return true;
    }

    //把对象保存在xml文件中
    public synchronized void object2xml() {
        //设置xml字段顺序
        SortableFieldKeySorter sorter = new SortableFieldKeySorter();
        sorter.registerFieldOrder(EncodeFile.class,
                new String[]{
                        "fileName",
                        "nK",
                        "CurrentParts",
                        "TotalParts",
                        "currentSmallPiece",
                        "totalSmallPiece",
                        "rightFileLen1",
                        "rightFileLen2",
                        "folderName",
                        "IMEI",
                        "pieceFileList",
                        "storagePath",
                        "xmlFileName"
                });
        sorter.registerFieldOrder(PieceFile.class,
                new String[]{
                        "pieceNo",
                        "currentFileNum",
                        "nK",
                        "rightFileLen",
                        "haveSendFile",
                        "sendFilePath",
                        "coeffMatrix",
                        "pieceFilePath",
                        "encodeFilePath",
                        "re_encodeFilePath",
                        "isReencoding",
                        "sendBufferPath"
                });
        //XStream xStream = new XStream(new DomDriver("UTF-8"));
        XStream xStream = new XStream(new Sun14ReflectionProvider(new FieldDictionary(sorter)));
        xStream.setMode(XStream.NO_REFERENCES);
        //使用注解
        xStream.processAnnotations(EncodeFile.class);
        xStream.processAnnotations(PieceFile.class);
        //转化为String，并保存入文件
        String xml = xStream.toXML(this);
        MyFileUtils.writeToFile(storagePath, xmlFileName, xml.getBytes());
    }

    /**
     * 从xml文件读出object
     *
     * @param xml_obj        xml文件的存储路径或是xml文件对象
     * @param recoverAllPath 是否恢复文件路径的控制
     * @return
     */
    public static EncodeFile xml2object(Object xml_obj, boolean recoverAllPath) {
        //读取xml配置文件
        byte[] bt_xml = MyFileUtils.readFile(xml_obj);
        if (bt_xml == null) {
            return null;
        }
        String xml = new String(bt_xml);
        XStream xStream = new XStream(new DomDriver("UTF-8"));
        //使用注解
        xStream.processAnnotations(EncodeFile.class);
        xStream.processAnnotations(PieceFile.class);
        //这个blog标识一定要和Xml中的保持一直，否则会报错
        xStream.alias("EncodeFile", EncodeFile.class);
        EncodeFile encodeFile = null;
        try {
            encodeFile = (EncodeFile) xStream.fromXML(xml);
        } catch (Exception e) {
            e.printStackTrace();
            //解析出错
            return null;
        }

        //恢复没有写入xml的属性
        //恢复pieceFile的nK值
        for (PieceFile pieceFile : encodeFile.getPieceFileList()) {
            pieceFile.setnK(encodeFile.getnK());
        }
        //是否恢复所有文件路径
        if (recoverAllPath) {
            //恢复总存储路径
            String fileName = encodeFile.fileName;
            String folderName = encodeFile.getFolderName();   //获取不含后缀的文件名,作为文件夹名字
            String storagePath = GlobalVar.getTempPath() + File.separator + folderName;
            encodeFile.setStoragePath(storagePath);
            for (PieceFile pieceFile : encodeFile.getPieceFileList()) {
                //PieceFile的一级目录
                String pieceFilePath = storagePath + File.separator + pieceFile.getPieceNo();
                pieceFile.setPieceFilePath(pieceFilePath);
                //两个二级目录
                String encodeFilePath = pieceFilePath + File.separator + "encodeFiles";
                pieceFile.setEncodeFilePath(encodeFilePath);
                String re_encodeFilePath = pieceFilePath + File.separator + "re_encodeFile";
                pieceFile.setRe_encodeFilePath(re_encodeFilePath);
                String sendBufferPath = pieceFilePath + File.separator + "sendBuffer";
                pieceFile.setSendBufferPath(sendBufferPath);
                //查看是否有再编码文件
//                ArrayList<File> files = MyFileUtils.getList_1_files(pieceFile.getRe_encodeFilePath());
//                if (files.size() == 1) {
//                    pieceFile.setHaveSendFile(true);
//                } else {
//                    pieceFile.setHaveSendFile(false);
//                }
            }
        }
        return encodeFile;
    }

    /**
     * 从已有的EncodeFile变量，clone一个只含基本信息的新对象
     * 当client接收到server数据xml配置信息，发现本地没有任何数据片信息试，调用
     *
     * @param encodeFile
     * @return
     */
    public static EncodeFile clone(EncodeFile encodeFile) {
        //从encodeFile中获取需要clone的信息
        String fileName = encodeFile.fileName;
        int nK = encodeFile.getnK();
        //int TotalParts = encodeFile.TotalParts;
        //int totalSmallPiece = encodeFile.totalSmallPiece;
        //clone
        EncodeFile newEncodeFile = new EncodeFile(fileName, nK, encodeFile.folderName);
        newEncodeFile.TotalParts = encodeFile.TotalParts;
        newEncodeFile.totalSmallPiece = encodeFile.totalSmallPiece;
        newEncodeFile.rightFileLen1 = encodeFile.rightFileLen1;
        newEncodeFile.rightFileLen2 = encodeFile.rightFileLen2;
        newEncodeFile.folderName = encodeFile.folderName;
        newEncodeFile.IMEI = encodeFile.IMEI;   //文件源的标记
        newEncodeFile.object2xml();    //把配置信息写入文件
        return newEncodeFile;
    }

    //找出有用的部分 进行请求
    //让它返回数组
    //返回一个数组   存有partNo 部分文件的编号
    public int[] findUsefulParts(EncodeFile itsEncodeFile) {
        int[] usefulParts = null;
        List<PieceFile> itsPieceFileList = itsEncodeFile.getPieceFileList();
        //如果本地没有任何数据
        if (pieceFileList.size() == 0) {
            int partNum = itsPieceFileList.size();
            for (int i = 0; i < partNum; ++i) {
                PieceFile pieceFile = itsPieceFileList.get(i);
                int pieceNo = pieceFile.getPieceNo();
                usefulParts = IntAndBytes.intArrayGrow(usefulParts, pieceNo);
            }
            return usefulParts;
        }
        for (PieceFile itsPieceFile : itsPieceFileList) {
            int itsPieceNo = itsPieceFile.getPieceNo();
            boolean haveThisPart = false;
            int flag = itsPieceNo - 1; //下标
            for (PieceFile myPieceFile : pieceFileList) {
                if (myPieceFile.getPieceNo() == itsPieceNo) {
                    //判断对方的数据是否对自己有用
                    //usefulParts[flag] = myPieceFile.usefulOrNot(itsPieceFile.getCoeffMatrix());
                    if (myPieceFile.usefulOrNot(itsPieceFile.getCoeffMatrix())) {
                        usefulParts = IntAndBytes.intArrayGrow(usefulParts, itsPieceNo);
                    }
                    haveThisPart = true;
                    break;
                }
            }
            //没有这部分数据
            if (!haveThisPart) {
                usefulParts = IntAndBytes.intArrayGrow(usefulParts, itsPieceNo);
            }
        }
        return usefulParts;
    }

    //统计一共有多少有用的文件
    //暂时没用此方法
    public int calculateUsefulFileNum(EncodeFile itsEncodeFile) {
        //如果本地没有任何数据
        if (currentSmallPiece == 0) {
            return itsEncodeFile.currentSmallPiece;
        }
        //
        int count = 0;
        List<PieceFile> itsPieceFileList = itsEncodeFile.getPieceFileList();
        for (PieceFile itsPieceFile : itsPieceFileList) {
            int itsPieceNo = itsPieceFile.getPieceNo();
            boolean haveThisPart = false;
            for (PieceFile myPieceFile : pieceFileList) {
                if (myPieceFile.getPieceNo() == itsPieceNo) {
                    //判断对方的数据是否对自己有用
                    int temp = myPieceFile.getUserfulFileNum(itsPieceFile.getCoeffMatrix());
                    count += temp;
                    haveThisPart = true;
                    break;
                }
            }
            //没有这部分数据
            if (!haveThisPart) {
                int temp = itsPieceFile.getCurrentFileNum();
                count += temp;
            }
        }
        return count;
    }

    public void updateCurrentSmallPiece() {
        int count = 0;
        for (PieceFile pieceFile : pieceFileList) {
            count += pieceFile.getCurrentFileNum();
        }
        currentSmallPiece = count;
    }

    //判断此设备是不是数据源
    public boolean isDataSource() {
        //根据IMEI进行判断
        if (IMEI.equals(GlobalVar.getIMEI())) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * 以下是自动生成的Getter和Setter方法
     */
    public int getCurrentParts() {
        return CurrentParts;
    }

    public void setCurrentParts(int currentParts) {
        CurrentParts = currentParts;
    }

    public int getCurrentSmallPiece() {
        return currentSmallPiece;
    }

    public void setCurrentSmallPiece(int currentSmallPiece) {
        this.currentSmallPiece = currentSmallPiece;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getnK() {
        return nK;
    }

    public void setnK(int nK) {
        this.nK = nK;
    }

    public List<PieceFile> getPieceFileList() {
        return pieceFileList;
    }

    public void setPieceFileList(List<PieceFile> pieceFileList) {
        this.pieceFileList = pieceFileList;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public int getTotalParts() {
        return TotalParts;
    }

    public void setTotalParts(int totalParts) {
        TotalParts = totalParts;
    }

    public int getTotalSmallPiece() {
        return totalSmallPiece;
    }

    public void setTotalSmallPiece(int totalSmallPiece) {
        this.totalSmallPiece = totalSmallPiece;
    }

    public int getRightFileLen1() {
        return rightFileLen1;
    }

    public void setRightFileLen1(int rightFileLen1) {
        this.rightFileLen1 = rightFileLen1;
    }

    public int getRightFileLen2() {
        return rightFileLen2;
    }

    public void setRightFileLen2(int rightFileLen2) {
        this.rightFileLen2 = rightFileLen2;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public static String getXmlFileName() {
        return xmlFileName;
    }

    public static void setXmlFileName(String xmlFileName) {
        EncodeFile.xmlFileName = xmlFileName;
    }
}
