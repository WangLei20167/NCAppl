package connect;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.List;

/**
 * 此类用以管理wifi
 * Created by Administrator on 2017/5/26 0026.
 */

public class WifiAdmin {
    // 定义WifiManager对象
    private WifiManager mWifiManager;
    // 定义WifiInfo对象
    private WifiInfo mWifiInfo;
    // 扫描出的网络连接列表
    private List<ScanResult> mWifiList;
    // 网络连接列表
    private List<WifiConfiguration> mWifiConfiguration;
    // 定义一个WifiLock
    private WifiManager.WifiLock mWifiLock;

    private Context context;
    //检查打开app前，wifi的状态
    private boolean beforeWifiState = false;

    // 构造器
    public WifiAdmin(Context context) {
        this.context = context;
        // 取得WifiManager对象
        mWifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        // 取得WifiInfo对象
        mWifiInfo = mWifiManager.getConnectionInfo();
        setWifiState();
    }


    public void setWifiState() {
        this.beforeWifiState = mWifiManager.isWifiEnabled();
    }

    //恢复app打开之前的WiFi状态
    public void resetWifi() {
        if (mWifiManager.isWifiEnabled() != beforeWifiState) {
            mWifiManager.setWifiEnabled(beforeWifiState);
        }
    }

    // 打开WIFI
    public void openWifi() {
        APHelper apHelper = new APHelper(context);
        if (apHelper.isApEnabled()) {
            apHelper.setWifiApEnabled(null, false);
        }
        if (!mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(true);
        }
        //等待开启成功再返回
        while (!mWifiManager.isWifiEnabled()) {

        }
    }

    // 关闭WIFI
    public void closeWifi() {
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }

        //等待关闭后再返回
        while (mWifiManager.isWifiEnabled()) {

        }
    }

    //得到当前WIFI是否可用
    private boolean wifiEnable() {
        return mWifiManager.isWifiEnabled();
    }


    // 得到配置好的网络
    public List<WifiConfiguration> getConfiguration() {
        return mWifiConfiguration;
    }


    // 添加一个网络并连接
    public void addNetwork(WifiConfiguration wcg) {
        int wcgID = mWifiManager.addNetwork(wcg);
        boolean b = mWifiManager.enableNetwork(wcgID, true);
        System.out.println("a--" + wcgID);
        System.out.println("b--" + b);
    }

    // 断开指定ID的网络
    public void disconnectWifi(int netId) {
        mWifiManager.disableNetwork(netId);
        mWifiManager.disconnect();
    }


    //然后是一个实际应用方法，连接wifi：String bssid,
    public WifiConfiguration CreateWifiInfo(String SSID, String Password, int Type) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + SSID + "\"";

        //相同ssid时 用在配置bssid   是否有用？
        //config.BSSID="\"" + bssid + "\"";

        if (Type == 1) //WIFICIPHER_NOPASS
        {
            config.wepKeys[0] = "";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        }
        if (Type == 2) //WIFICIPHER_WEP
        {
            config.hiddenSSID = true;
            config.wepKeys[0] = "\"" + Password + "\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        }
        if (Type == 3) //WIFICIPHER_WPA
        {
            config.preSharedKey = "\"" + Password + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            //config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        }
        //不能使用
//        if (Type == 4) //WIFICIPHER_WPA2
//        {
//            config.preSharedKey = "\"" + Password + "\"";
//            config.hiddenSSID = true;
//            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
//            config.allowedKeyManagement.set(4);
//            config.status = WifiConfiguration.Status.ENABLED;
//        }
        return config;
    }

    //检查指定的SSID信息是否已经存在
    private WifiConfiguration IsExsits(String SSID) {
        try {
            List<WifiConfiguration> existingConfigs = mWifiManager.getConfiguredNetworks();
            for (WifiConfiguration existingConfig : existingConfigs) {
                if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                    return existingConfig;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return null;
        }
    }


    //搜索是否存在指定ssid包含的网络，存在返回对应的ssid
    public String searchWifi(String ssid) {
        //String bssid = "";   //相同ssid按bssid来识别
        //在以保存的wifi信息中找ssid，若存在，则先删除
//        WifiConfiguration tempConfig = this.IsExsits(ssid);
//        if (tempConfig != null) {
//            mWifiManager.removeNetwork(tempConfig.networkId);
//        }
        //标志是否找到指定的ssid
        boolean find = false;
        ScanResult scanResult0 = null;
        do {
            //扫描网络列表
            mWifiManager.startScan();
            //得到扫描结果
            List<ScanResult> mWifiList = mWifiManager.getScanResults();
            for (int i = 0; i < mWifiList.size(); ++i) {
                //检查有没有ssid
//                if (mWifiList.get(i).SSID.equals(ssid)) {
//                    int level = mWifiList.get(i).level;
//                    //level的值为-100到0的值，值越小，信号越差
//                    //0到-50，最佳
//                    //-50到-70,较好
//                    //-70到-80,一般
//                    //-80到-100，最差
//                    if (level > -80) {
//                        //信号大于-80，质量一般
//                        bssid = mWifiList.get(i).BSSID;
//                        find = true;
//                        break;
//                    }
//                }
                ScanResult scanResult = mWifiList.get(i);
                //找到包含 NCSharing的SSID进行连接
                if (scanResult.SSID.indexOf(Constant.SSID) == 0) {
                    //连接最好信号的网络
                    int level = mWifiList.get(i).level;
                    if (scanResult0 == null) {
                        scanResult0 = scanResult;
                    } else if (level > scanResult0.level) {
                        scanResult0 = scanResult;
                    }
                    find = true;
                }
            }
            if (find) {
                break;
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (mWifiManager.isWifiEnabled());
        if (scanResult0 != null) {
            String resultSSID = scanResult0.SSID;
            WifiConfiguration tempConfig = this.IsExsits(resultSSID);
            if (tempConfig != null) {
                mWifiManager.removeNetwork(tempConfig.networkId);
            }
            return resultSSID;
        } else {
            return null;
        }
    }

    //获取当前wifi连接的ssid
    public String currentConnectSSID() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        return wifiInfo.getSSID();
    }

    //是否已连接某个WIFI
    public boolean isWifiConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiNetworkInfo.isConnected()) {
            return true;
        }
        return false;
    }
}
