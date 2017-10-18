package appData;

import android.content.Context;
import android.os.Environment;

import java.io.File;

import connect.Constant;
import fileSlices.EncodeFile;
import utils.LocalInfor;
import utils.MyFileUtils;


/**
 * 用以构建app运行文件夹
 * 全是static类和方法，方便全局调用
 * Created by Administrator on 2017/7/22 0022.
 */

public class GlobalVar {
    private static String dataFolderPath;
    //二级文件夹
    private static String FileRevPath;
    private static String TempPath;
    private static String CrashPath;

    //用来记录文件的发送和接收情况
    private static String LogPath;
    public final static String sendLogName = "sendLog.txt";
    public final static String revLogName = "revLog.txt";

    private static String IMEI;
    private static String SSID_IMEI;

    //全局encodeFile变量
    public static volatile EncodeFile g_ef = null;

    //初始化变量
    public static void initial(Context context) {
        dataFolderPath = MyFileUtils.creatFolder(Environment.getExternalStorageDirectory().getPath(), "1NCSharing");
        TempPath = MyFileUtils.creatFolder(dataFolderPath, "Temp");  //创建文件暂存的目录
        FileRevPath = MyFileUtils.creatFolder(dataFolderPath, "FileRev");
        CrashPath = MyFileUtils.creatFolder(dataFolderPath, "Crash");
        LogPath = MyFileUtils.creatFolder(dataFolderPath, "Log");
        //如果异常日志大于20M，则删除
        File folder = new File(CrashPath);
        int folderLen = (int) folder.length();
        if (folderLen > 20 * 1024 * 1024) {
            MyFileUtils.deleteAllFile(CrashPath, false);
        }
        //设备IMEI码
        IMEI = LocalInfor.getDeviceID(context);
        SSID_IMEI = Constant.SUB_SSID + IMEI;
    }

    //以下是Getter和Setter方法
    public static String getDataFolderPath() {
        return dataFolderPath;
    }

    public static String getFileRevPath() {
        return FileRevPath;
    }

    public static String getTempPath() {
        return TempPath;
    }

    public static String getCrashPath() {
        return CrashPath;
    }

    public static String getSsidImei() {
        return SSID_IMEI;
    }
    public static String getLogPath() {
        return LogPath;
    }

    public static String getIMEI() {
        return IMEI;
    }
}
