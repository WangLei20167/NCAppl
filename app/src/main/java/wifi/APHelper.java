package wifi;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


import appData.GlobalVar;
import connect.Constant;

import static android.content.Context.WIFI_SERVICE;

/**
 * 本类用以管理AP 热点
 * Created by Vision on 15/6/24.<br>
 * Email:Vision.lsm.2012@gmail.com
 */
public class APHelper {
    private static final String TAG = APHelper.class.getName();

    public static final int WIFI_AP_STATE_ENABLING = 12;
    public static final int WIFI_AP_STATE_ENABLED = 13;
    public static final int WIFI_AP_STATE_FAILED = 14;
    private final WifiManager mWifiManager;

    public APHelper(Context context) {
        mWifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
    }

    //判断AP是否可用
    public boolean isApEnabled() {
        int state = getWifiApState();
        return WIFI_AP_STATE_ENABLING == state || WIFI_AP_STATE_ENABLED == state;
    }

    public int getWifiApState() {
        try {
            Method method = mWifiManager.getClass().getMethod("getWifiApState");
            int i = (Integer) method.invoke(mWifiManager);
            return i;
        } catch (Exception e) {
            Log.i(TAG, "Cannot get WiFi AP state" + e);
            return WIFI_AP_STATE_FAILED;
        }
    }

    //可用于打开或关闭热点
    public boolean setWifiApEnabled(WifiConfiguration wifiConfig, boolean enabled) {
        boolean result = false;
        //关闭热点
        if (mWifiManager.isWifiEnabled()) {
            //wifi和热点不能同时打开，所以打开热点的时候需要关闭wifi
            mWifiManager.setWifiEnabled(false);
        }
        //等待wifi关闭
        while (mWifiManager.isWifiEnabled()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            //使用反射机制打开热点
            Method method = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            result = (boolean) (Boolean) method.invoke(mWifiManager, wifiConfig, enabled);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return result;
    }

    //配置wifi
    public static WifiConfiguration createWifiCfg() {
        WifiConfiguration wifiCfg = new WifiConfiguration();

//设置无密码模式
//        wifiCfg.SSID = Constant.HOST_SPOT_SSID;
//        wifiCfg.wepKeys[0] = "";
//        wifiCfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
//        wifiCfg.wepTxKeyIndex = 0;

        wifiCfg.SSID = GlobalVar.getSsidImei();
        wifiCfg.preSharedKey = Constant.AP_PASS_WORD;
        wifiCfg.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        //WPA
        wifiCfg.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiCfg.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiCfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK); //WPA
        wifiCfg.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiCfg.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiCfg.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wifiCfg.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        //WPA2 不能使用
        //wifiCfg.allowedKeyManagement.set(4);  //WifiConfiguration.KeyMgmt.WPA2_PSK
        return wifiCfg;
    }


}

