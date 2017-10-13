package wifi;

/**
 * 用来存储网络的ssid，有用数据的个数
 * 对ssid添加权重，以用来控制连接哪个wifi
 * Created by kingstones on 2017/10/9.
 */

public class SSIDInfor {
    private String strSSID;
    private int usefulFileNum;
    //如果之前的ssid列表，没有此ssid，权重50作为基数
    public static int weightBase = 50;
    private int weight;
    private int signalWeight;

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

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getSignalWeight() {
        return signalWeight;
    }

    public void setSignalWeight(int signalWeight) {
        this.signalWeight = signalWeight;
    }
}
