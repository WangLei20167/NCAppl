package connect;

/**
 * Created by Administrator on 2017/7/24 0024.
 */

public class Constant {
    //3套方案都有的SSID子串
    public final static String GENERAL_SUB_SSID = "Sharing";
    //ssid的格式是 NCSharing+手机唯一识别码
    public final static String SUB_SSID = "NCSharing";
    //定义AP密码
    public final static String AP_PASS_WORD = "123456789";
    //作为Server时的监听地址与端口
    public final static String TCP_ServerIP = "192.168.43.1";

    public final static int TCP_ServerPORT = 9000;
    //指令
    public final static String ANSWER_END = "answerEnd";  //一次应答结束

    public final static String REQUEST_END = "requestEnd"; //不再进行文件请求了

}
