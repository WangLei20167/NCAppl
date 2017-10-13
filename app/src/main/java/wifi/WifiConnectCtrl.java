package wifi;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 用来维护一个已连接的ssid信息表
 * 作为wifi连接选择的判断
 * Created by kingstones on 2017/10/9.
 */

public class WifiConnectCtrl {
    private static List<SSIDInfor> ssidInforList = new ArrayList<>();

    //返回一个ssid
    public static String selectSSID(List<ScanResult> usefulWifiList, WifiManager mWifiManager) {
//        String ssid = "";
        List<SSIDInfor> currentSSIDList = new ArrayList<>();
        int size = usefulWifiList.size();
        if (size == 0) {
            return null;
        }
        //int level = Integer.MIN_VALUE;
        for (int i = 0; i < size; ++i) {
            ScanResult scanResult = usefulWifiList.get(i);
            String ssid = scanResult.SSID;
            //int lv0 = scanResult.level;
            int signalLevel = mWifiManager.calculateSignalLevel(scanResult.level, 5);
            boolean have = false;
            for (SSIDInfor ssidInfor0 : ssidInforList) {
                if (ssidInfor0.getStrSSID().equals(ssid)) {
                    ssidInfor0.setSignalWeight(signalLevel);
                    currentSSIDList.add(ssidInfor0);
                    have = true;
                    break;
                }
            }
            if (!have) {
                //之前没有此ssid
                SSIDInfor ssidInfor = new SSIDInfor();
                ssidInfor.setStrSSID(ssid);
                ssidInfor.setSignalWeight(signalLevel);
                ssidInfor.setWeight(SSIDInfor.weightBase);
                currentSSIDList.add(ssidInfor);
            }
        }
        //选择一个ssid
        SSIDInfor selectSSIDInfor = null;
        for (SSIDInfor ssidInfor : currentSSIDList) {
            if (selectSSIDInfor == null) {
                selectSSIDInfor = ssidInfor;
            } else {
                //先判断权重，在判断信号强度
                int w0 = ssidInfor.getWeight();
                int w1 = selectSSIDInfor.getWeight();
                if (ssidInfor.getWeight() > selectSSIDInfor.getWeight()) {
                    selectSSIDInfor = ssidInfor;
                } else if (ssidInfor.getWeight() == selectSSIDInfor.getWeight()) {
                    int s0 = ssidInfor.getSignalWeight();
                    int s1 = selectSSIDInfor.getSignalWeight();
                    if (ssidInfor.getSignalWeight() > selectSSIDInfor.getSignalWeight()) {
                        selectSSIDInfor = ssidInfor;
                    }
                }
            }
        }
        selectSSIDInfor.setWeight(0);
        //相当于连过一次之后，在之后的5次尝试连接中，处于权重劣势
        for (SSIDInfor ssidInfor0 : ssidInforList) {
            if (!ssidInfor0.equals(selectSSIDInfor)) {
                ssidInfor0.setWeight(ssidInfor0.getWeight() + 10);
            }
        }
        //添加进列表
        if (!ssidInforList.contains(selectSSIDInfor)) {
            //对之前连过的，这次没连的ssid增加权重10
            //selectSSIDInfor.setWeight(0);
            ssidInforList.add(selectSSIDInfor);
        }
        return selectSSIDInfor.getStrSSID();
    }
}


