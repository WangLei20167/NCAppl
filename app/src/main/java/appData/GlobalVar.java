package appData;

import android.content.Context;
import android.os.Environment;

import connect.Constant;
import utils.LocalInfor;
import utils.MyFileUtils;


/**
 * 用以构建app运行文件夹
 * 全是static类和方法，方便全局调用
 * Created by Administrator on 2017/7/22 0022.
 */

public class GlobalVar {
    private static String dataFolderPath;
    private static String FileRevPath;
    private static String TempPath;

    private static String SSID_IMEI;

    //初始化变量
    public static void initial(Context context) {
        dataFolderPath = MyFileUtils.creatFolder(Environment.getExternalStorageDirectory().getPath(), "1NCSharing");
        TempPath = MyFileUtils.creatFolder(dataFolderPath, "Temp");  //创建文件暂存的目录
        FileRevPath = MyFileUtils.creatFolder(dataFolderPath, "FileRev");
        SSID_IMEI = Constant.SSID + LocalInfor.getDeviceID(context);
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

    public static String getSsidImei() {
        return SSID_IMEI;
    }
}
