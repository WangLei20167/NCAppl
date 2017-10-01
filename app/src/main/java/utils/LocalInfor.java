package utils;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

/**
 * Created by Administrator on 2017/4/28 0028.
 * 用以获取本机信息的类
 */

public class LocalInfor {
    // private Context context;
    /**
     * 获取系统当前的时间
     */
    //public static String oldTime = "";

    public static String getCurrentTime(String dataFormat) {
        SimpleDateFormat format = new SimpleDateFormat(dataFormat);//"yyyy-MM-dd HH:mm:ss:SSS"
        Date curDate = new Date();
        String str = format.format(curDate);
        String s = str;
        //为了保证每次取得值唯一
        //if (str.equals(oldTime)) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //str = str + "a";
        // }
        //oldTime = s;
        return str;
    }


    /**
     * 获取手机的品牌型号
     *
     * @return
     */
    public static String getPhoneModel() {
        // String mtyb= android.os.Build.BRAND;//手机品牌
        String mtype = android.os.Build.MODEL; // 手机型号
        // return mtyb+" "+mtype;
        return mtype;
    }

    /**
     * 获取设备唯一的标志码
     */
    public static String getDeviceID(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = telephonyManager.getDeviceId();
        return imei;
    }


    /**
     * 获取本机ip方法
     */
    public static String getHostIP() {
        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.i("yao", "SocketException");
            e.printStackTrace();
        }
        return hostIp;

    }
}
