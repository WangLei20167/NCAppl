package wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;

import connect.Constant;

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
        //等待ap关闭
        while (apHelper.isApEnabled()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
    public void addNetwork(String ssid) {
        WifiConfiguration tempConfig = isExsits(ssid);

        if (tempConfig != null) {
            // wifiManager.removeNetwork(tempConfig.networkId);
            boolean b = mWifiManager.enableNetwork(tempConfig.networkId,
                    true);
        } else {
            WifiConfiguration wifiConfig = CreateWifiInfo(ssid, Constant.AP_PASS_WORD, 3);
            //
            if (wifiConfig == null) {
                return;
            }
            int netID = mWifiManager.addNetwork(wifiConfig);
            boolean enabled = mWifiManager.enableNetwork(netID, true);
            //Log.d(TAG, "enableNetwork status enable=" + enabled);
            boolean connected = mWifiManager.reconnect();
           // Log.d(TAG, "enableNetwork connected=" + connected);
            System.out.println("a--" + netID);
            System.out.println("b--" + enabled);
        }
//        int wcgID = mWifiManager.addNetwork(wcg);
//        boolean b = mWifiManager.enableNetwork(wcgID, true);
//        System.out.println("a--" + wcgID);
//        System.out.println("b--" + b);
    }

    // 断开指定ID的网络
    //断开当前的wifi连接
    public void disconnectWifi() {
        int netId = mWifiInfo.getNetworkId();
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


    //删除之前以后保存过的网络信息
    public void deleteContainSSid() {
        try {
            List<WifiConfiguration> existingConfigs = mWifiManager.getConfiguredNetworks();
            for (WifiConfiguration existingConfig : existingConfigs) {
                String ssid = existingConfig.SSID;
                if (ssid.contains(Constant.GENERAL_SUB_SSID)) {
                    int networkId = existingConfig.networkId;
                    mWifiManager.removeNetwork(networkId);
                    mWifiManager.saveConfiguration();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    // 查看以前是否也配置过这个网络
    private WifiConfiguration isExsits(String SSID) {
        try {
            List<WifiConfiguration> existingConfigs = mWifiManager
                    .getConfiguredNetworks();
            for (WifiConfiguration existingConfig : existingConfigs) {
                if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                    return existingConfig;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //搜索是否存在指定ssid包含的网络，存在返回对应的ssid
    public String searchWifi() {
        //删除之前已经保存过得网络信息
        //避免其自动连接之前的ssid
        //让其能够根据策略连接指定的ssid
        //安卓6.0以上无法删除之前保存的网络
        //只能删除掉由本app创建的连接配置
        deleteContainSSid();

        List<WifiConfiguration> existingConfigs = mWifiManager.getConfiguredNetworks();
        //String bssid = "";   //相同ssid按bssid来识别
        //在以保存的wifi信息中找ssid，若存在，则先删除
        //WifiConfiguration tempConfig = this.IsExsits(ssid);
//        WifiConfiguration tempConfig = this.IsExsits(ssid);
//        if (tempConfig != null) {
//            mWifiManager.removeNetwork(tempConfig.networkId);
//        }
        //从已保存网络中删除包含SSID中包含有ssid子串的网络

        //标志是否找到指定的ssid
        boolean find = false;
        ScanResult scanResult0 = null;
        //取出有用的wifi列表
        List<ScanResult> usefulWifiList = new ArrayList<>();
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
//                    //0到-50，最佳 4
//                    //-50到-70,较好 3
//                    //-70到-80,一般 2
//                    //-80到-100，最差 1
//                    if (level > -80) {
//                        //信号大于-80，质量一般
//                        bssid = mWifiList.get(i).BSSID;
//                        find = true;
//                        break;
//                    }
//                }
                ScanResult scanResult = mWifiList.get(i);
                //找到包含 NCSharing的SSID进行连接
                if (scanResult.SSID.indexOf(Constant.SUB_SSID) == 0) {
                    usefulWifiList.add(scanResult);
                    //连接最好信号的网络
                    int level = mWifiList.get(i).level;
//                    int num = mWifiManager.calculateSignalLevel(scanResult.level, 5);
//                    int num0=mWifiManager.calculateSignalLevel(-1, 5);
//                    int num1=mWifiManager.calculateSignalLevel(-50, 5);
//                    int num3=mWifiManager.calculateSignalLevel(-70, 5);
//                    int num4=mWifiManager.calculateSignalLevel(-100, 5);
//                    int num5=mWifiManager.calculateSignalLevel(-60, 5);
//                    int num6=mWifiManager.calculateSignalLevel(0, 5);
//                    int num7=mWifiManager.calculateSignalLevel(-80, 5);
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
        //在此usefulWifiList列表进行wifi选择
        //选择后，返回一个ScanResult类型的值
        String resultSSID = WifiConnectCtrl.selectSSID(usefulWifiList, mWifiManager);
        return resultSSID;
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
