package wifi;

/**
 * 用来存储网络的ssid，有用数据的个数
 * 对ssid添加权重，以用来控制连接哪个wifi
 * Created by kingstones on 2017/10/9.
 */

public class SSIDInfor {
    private String strSSID;
    private int usefulFileNum;
    private int connectWeight = 0;

    public String getStrSSID() {
        return strSSID;
    }

    public void setStrSSID(String strSSID) {
        this.strSSID = strSSID;
    }

    public int getUsefulFileNum() {
        return usefulFileNum;
    }

    public void setUsefulFileNum(int usefulFileNum) {
        this.usefulFileNum = usefulFileNum;
    }

    public int getConnectWeight() {
        return connectWeight;
    }

    public void setConnectWeight(int connectWeight) {
        this.connectWeight = connectWeight;
    }
}
