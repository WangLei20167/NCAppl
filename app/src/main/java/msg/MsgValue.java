package msg;

/**
 * 消息常量
 * Created by kingstones on 2017/9/26.
 */

public class MsgValue {
    public final static int TELL_ME_SOME_INFOR = 0;

    //更新进度球显示
    public final static int UPDATE_WAVE_PROGRESS = 1;
    //public final static int CLIENT_CHANGE_ENCODEFILR = 1;
    //client转server
    public final static int CLIENT_2_SERVER = 2;
    //解码成功，打开文件
    public final static int DECODE_SUCCESS_OPEN_FILE = 3;
    //server转client
    public final static int SERVER_2_CLIENT = 4;
    //改变按钮字体颜色作为状态标志
    public final static int SERVER_FLAG=5;
    public final static int CLIENT_FLAG=6;
}
