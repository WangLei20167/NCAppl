package njupt.ncappl;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.mauiie.aech.AECHConfiguration;
import com.mauiie.aech.AECrashHelper;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import appData.GlobalVar;
import cn.fanrunqi.waveprogress.WaveProgressView;
import nc.NCUtils;
import runtimepermissions.PermissionsManager;
import runtimepermissions.PermissionsResultAction;
import utils.LocalInfor;
import wifi.APHelper;
import connect.Constant;
import connect.TcpClient;
import connect.TcpServer;
import wifi.WifiAdmin;
import fileSlices.EncodeFile;
import fileSlices.PieceFile;
import msg.MsgValue;
import utils.MyFileUtils;

public class MainActivity extends AppCompatActivity {

    private APHelper apHelper;
    private WifiAdmin wifiAdmin;
    TcpServer tcpSvr;
    TcpClient tcpClient;

    //一个定时器   client长时间无法连接上socket时调用
    Timer timer;
    volatile boolean needLeave;

    //对打开文件选择器时 显示的路径进行控制
    private String startPath;
    //编码数据
    //注意无论时server状态还是client状态
    //操作的都是这一个变量
    //private volatile EncodeFile encodeFile;
    private int GenerationSize;

    TextView tv_promptMsg;
    ScrollView scrollView;
    //进度球布局
    LinearLayout layout_wavePro;
    //当新的显示进度小于老进度时，要启用重绘
    int oldProgress = 0;
    WaveProgressView waveProgressView;
    TextView tv_fileName;
    Button bt_modeSwitch;

    Button bt_openServer;
    Button bt_openClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //
        requestPermission();
        //初始化全局变量   文件存放地址
        GlobalVar.initial(this);
        //初始化有限域
        NCUtils.InitGalois();
        //全局抓取异常   世界警察
        AECrashHelper.initCrashHandler(getApplication(),
                new AECHConfiguration.Builder()
                        .setLocalFolderPath(GlobalVar.getCrashPath()) //配置日志信息存储的路径
                        .setSaveToLocal(true).build()); //开启存储在本地功能
        //文件选择器开始目录   取
        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
        startPath = pref.getString("startPath", Environment.getExternalStorageDirectory().getPath());
        GenerationSize = pref.getInt("GenerationSize", 4);
        //AP和wifi的管理类
        apHelper = new APHelper(this);
        wifiAdmin = new WifiAdmin(this);
        //TCPServer和TCPClient的管理类
        tcpSvr = new TcpServer(handler);
        tcpClient = new TcpClient(handler);
        timer = new Timer();

        tv_promptMsg = (TextView) findViewById(R.id.tv_promptMsg);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        layout_wavePro = (LinearLayout) findViewById(R.id.layout_wavePro);
        waveProgressView = (WaveProgressView) findViewById(R.id.waveProgressbar);
        waveProgressView.setWaveColor("#5b9ef4");
        waveProgressView.setText("#FFFF00", 50);

        tv_fileName = (TextView) findViewById(R.id.tv_fileName);
        bt_modeSwitch = (Button) findViewById(R.id.bt_modeSwitch);

        //服务器按钮
        bt_openServer = (Button) findViewById(R.id.bt_openServer);
        bt_openServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //在这里要对手动server启动 和 自动启动 做区分
                //手动启动的话，开启server，必须是client离开时，启动定时向普通节点转化
                openServer(false);
            }
        });
        //客户端按钮
        bt_openClient = (Button) findViewById(R.id.bt_openClient);
        bt_openClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //
                openClient();
            }
        });
        //选择文件按钮
        Button bt_selectFile = (Button) findViewById(R.id.bt_selectFile);
        bt_selectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectFile();
            }
        });

        //选择本地已有的编码文件按钮
        Button bt_selectEncodeFile = (Button) findViewById(R.id.bt_selectEncodeFile);
        bt_selectEncodeFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ArrayList<File> folders = MyFileUtils.getListFolders(GlobalVar.getTempPath());
                int size = folders.size();
                if (size == 0) {
                    //本地无编码文件
                    Toast.makeText(MainActivity.this, "本地无编码文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] folderNameArray = new String[size];
                for (int i = 0; i < size; ++i) {
                    folderNameArray[i] = folders.get(i).getName();
                }
                new MaterialDialog.Builder(MainActivity.this)
                        .title("选择编码文件")
                        .items(folderNameArray)
                        .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                /**
                                 * If you use alwaysCallSingleChoiceCallback(), which is discussed below,
                                 * returning false here won't allow the newly selected radio button to actually be selected.
                                 **/
                                String xmlFilePath = GlobalVar.getTempPath() + File.separator + text + File.separator + "xml.txt";
                                GlobalVar.g_ef = EncodeFile.xml2object(xmlFilePath, true);
                                if (GlobalVar.g_ef != null) {
                                    SendMessage(MsgValue.UPDATE_WAVE_PROGRESS, 0, 0, null);
                                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                            "已选择" + GlobalVar.g_ef.getFileName());
                                }
                                return true;
                            }
                        })
                        .positiveText("选择")
                        .show();
            }
        });

        File folder = new File(GlobalVar.getTempPath());
        if (folder.exists()) {
            int length = (int) folder.length();
            if (length > (Math.pow(2, 30) / 2)) {
                //如果文件夹下大于512M则提示删除
                new MaterialDialog.Builder(this)
                        .title("提示")
                        .content("Temp文件夹大于512M，是否清空Temp目录？")
                        .positiveText("确认")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                // TODO
                                MyFileUtils.deleteAllFile(GlobalVar.getTempPath(), false);
                                Toast.makeText(MainActivity.this, "Temp目录下文件已清空", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .negativeText("取消")
                        .show();
            }
        }
    }

    //测试方法
    public void onTest(View view) {
        if (GlobalVar.g_ef != null) {
            GlobalVar.g_ef.recoveryFile();
//            PieceFile pieceFile=GlobalVar.g_ef.getPieceFileList().get(0);
//            pieceFile.decode_pfile();
//            for (final PieceFile pieceFile : GlobalVar.g_ef.getPieceFileList()) {
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        pieceFile.re_encodeFile();
//                    }
//                }).start();
//            }
        }
    }

    public void onModeSwitch(View view) {
        String mode = bt_modeSwitch.getText().toString();
        //1、按钮上是运行模式
        //点击后切换向运行模式
        //2、按钮上是调试模式
        //点击后就切换向调试模式
        if (mode.equals(getString(R.string.run_mode))) {
            //切换向运行模式
            scrollView.setVisibility(View.GONE);
            layout_wavePro.setVisibility(View.VISIBLE);
            bt_modeSwitch.setText(getString(R.string.debug_mode));
        } else if (mode.equals(getString(R.string.debug_mode))) {
            //切换向调试模式
            layout_wavePro.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
            bt_modeSwitch.setText(getString(R.string.run_mode));
        }
    }

    //运行Server server状态锁
    public volatile boolean serverLocked = false;

    public void openServer(final boolean auto) {
//        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
//                "普通2AP开始" + "    " +
//                        LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
        if (serverLocked) {
            return;
        }
        SendMessage(MsgValue.SERVER_FLAG, 0, 0, null);
        //先要执行打开ap操作
        //开启AP
        new Thread(new Runnable() {
            @Override
            public void run() {
                //锁上
                serverLocked = true;
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "server上锁");
                //关闭client的重试连接
                needLeave = true;
                timer.cancel();
                //关闭处理client的操作
                tcpClient.closeSocket();
                //关闭server的操作
                tcpSvr.closeServer();
                //等待client开锁后在向下执行
                if (clientLocked) {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "需等待client状态关闭后继续");
                }
                while (clientLocked) {

                }
                if (GlobalVar.g_ef == null) {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "GlobalVar.g_ef变量为null");
                    //指令更为开启client状态
                    if (auto) {
                        openClient();
                    }
                    serverLocked = false;
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "server开锁");
                    return;
                } else {
                    int currentSmallPieceNum = GlobalVar.g_ef.getCurrentSmallPiece();
                    if (currentSmallPieceNum < 1) {
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "GlobalVar.g_ef变量为null");
                        //指令更为开启client状态
                        if (auto) {
                            openClient();
                        }
                        serverLocked = false;
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "server开锁");
                        return;
                    }
                }
                if (apHelper.setWifiApEnabled(APHelper.createWifiCfg(), true)) {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "AP打开成功");
                    //执行打开server的动作
                    //等待打开
//                    while ((!apHelper.isApEnabled())) {
//
//                    }
//                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
//                            "普通2AP开始" + "    " +
//                                    LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
                    tcpSvr.openServerSocket(auto);
                } else {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "AP打开失败");
                }
                //开锁
                serverLocked = false;
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "server开锁");
            }
        }).start();
    }

    //运行client
    public volatile boolean clientLocked = false;

    public void openClient() {
//        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
//                "AP2普通开始" + "    " +
//                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));
        if (clientLocked) {
            return;
        }
        SendMessage(MsgValue.CLIENT_FLAG, 0, 0, null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                //锁上
                clientLocked = true;
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "client上锁");
                timer.cancel();
                //关闭server的操作
                tcpSvr.closeServer();
                //防止client状态重开
                tcpClient.closeSocket();
                //在此开启一个定时任务
                needLeave = false;
                //如果一段时间没有连接上wifi,则尝试切换为server状态
                TimerTask mTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        // task to run goes here
                        //尝试切换向AP节点
                        if (GlobalVar.g_ef != null) {
                            needLeave = true;
                            SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "client切换向server");
                            SendMessage(MsgValue.CLIENT_2_SERVER, 0, 0, null);
                        }
                    }
                };
                Random random = new Random(System.currentTimeMillis());
                int diff = random.nextInt(10);
                long delay = (20 + diff) * 1000;
                //如果delay秒后还是无法连接wifi，则尝试切换向AP
                timer = new Timer();
                timer.schedule(mTimerTask, delay);
                //打开wifi
                wifiAdmin.openWifi();
//                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
//                        "AP2普通结束" + "    " +
//                                LocalInfor.getCurrentTime("HH:mm:ss:SSS"));

                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "正在搜索连接指定wifi");
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "连接超时" + delay / 1000 + "秒将尝试切换为AP");
                while (!needLeave) {
                    //此处做打开wifi的操作
                    //wifiAdmin.openWifi();
                    String ssid = wifiAdmin.searchWifi();
                    if (ssid == null) {
                        System.out.println("查找指定ssid失败");
                        //return;
                        if (needLeave) {
                            break;
                        }
                        continue;
                    }
                    //如果当前wifi是连接状态，则断开当前连接
                    if (wifiAdmin.isWifiConnected()) {
                        if (!(wifiAdmin.currentConnectSSID().equals(ssid))) {
                            wifiAdmin.disconnectWifi();
                            //连接网络的操作
                            wifiAdmin.addNetwork(ssid);
                        }
                    } else {
                        //连接网络的操作
                        wifiAdmin.addNetwork(ssid);
                    }
                    //等待连接的时间为5s
                    // Starting time.
                    long startMili = System.currentTimeMillis();
                    boolean timeOut = false;
                    while (!wifiAdmin.isWifiConnected()) {
                        //wifiAdmin.currentConnectSSID().equals(ssid)
                        //等待连接
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // Ending time.
                        long endMili = System.currentTimeMillis();
                        int time = (int) ((endMili - startMili) / 1000);
                        //超时未连接
                        if (time > 5) {
                            if (!needLeave) {
                                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                        "连接指定wifi失败，正在重试"
                                );
                                System.out.println("连接指定wifi失败，正在重试");
                            }
                            //openClient(); //重试
                            timeOut = true;
                            break;  //重试连接
                            //return;
                        }
                    }
                    if (timeOut) {
                        continue;
                    }
                    if (wifiAdmin.currentConnectSSID().equals(ssid)) {
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "连接wifi成功"
                        );
                    }
                    //做连接ServerSocket的动作
                    if (tcpClient.connectServer()) {
                        //连接wifi和socket成功
                        //取消定时切换任务
                        break;
                    }
                }
                timer.cancel();
                //开锁
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "client开锁");
                clientLocked = false;
            }
        }).start();
    }


    //用来处理文件选择器
    private static final int FILE_CODE = 0;

    //打开文件选择器   选择文件
    public void selectFile() {
        //打开文件选择器
        Intent i = new Intent(this, FilePickerActivity.class);
        //单选
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        //多选
        //i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, true);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
        //设置开始时的路径
        i.putExtra(FilePickerActivity.EXTRA_START_PATH, startPath);
        //i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
        startActivityForResult(i, FILE_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == FILE_CODE && resultCode == Activity.RESULT_OK) {
            // Use the provided utility method to parse the result
            List<Uri> files = Utils.getSelectedFilesFromResult(intent);
            ArrayList<File> fileList = new ArrayList<File>();
            for (Uri uri : files) {
                File file = Utils.getFileForUri(uri);
                // Do something with the result...
                fileList.add(file);
            }

            final File file = fileList.get(0);
            String s = file.getParent();
            if (!s.equals(startPath)) {
                //存
                SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
                editor.putString("startPath", s);
                editor.apply();
                startPath = s;
            }
            //编码现实
            new Thread(new Runnable() {
                @Override
                public void run() {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                            "正在对" + file.getName() + "进行编码");
                    GlobalVar.g_ef = new EncodeFile(file.getName(), GenerationSize, null);
                    //编码
                    GlobalVar.g_ef.cutFile(file);
                    //encodeFile.recoveryFile();
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                            "编码完成");

                    SendMessage(MsgValue.UPDATE_WAVE_PROGRESS, 0, 0, null);
                }
            }).start();
        }
    }

    /**
     * 处理各个类发来的UI请求消息
     */
    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MsgValue.TELL_ME_SOME_INFOR:
                    String infor = msg.obj.toString();
                    //Toast.makeText(MainActivity.this, infor, Toast.LENGTH_SHORT).show();
                    tv_promptMsg.append(infor + "\n");
                    //滚动到最下面
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    break;
                case MsgValue.UPDATE_WAVE_PROGRESS:
                    if (GlobalVar.g_ef != null) {
                        int currentNum = GlobalVar.g_ef.getCurrentSmallPiece();
                        int totalNum = GlobalVar.g_ef.getTotalSmallPiece();
                        String fileName = GlobalVar.g_ef.getFileName();
                        //进度
                        int progress = (int) (((float) currentNum / totalNum) * 100);
                        //
                        if (progress < oldProgress) {
                            //重绘
                            waveProgressView.requestLayout();
                            waveProgressView.invalidate();
                        }
                        oldProgress = progress;
                        waveProgressView.setCurrent(progress, currentNum + "/" + totalNum);
                        tv_fileName.setText(fileName);
                    }
                    break;
                //client切换向server
                case MsgValue.CLIENT_2_SERVER:
                    //测试encodeFile变量
                    System.out.println("client向server切换");
                    //EncodeFile encodeFile0 = encodeFile;
                    openServer(true);
                    break;
                //server切换向client
                case MsgValue.SERVER_2_CLIENT:
                    System.out.println("server向client切换");
                    //EncodeFile encodeFile0 = encodeFile;
                    openClient();
                    break;
                case MsgValue.DECODE_SUCCESS_OPEN_FILE:
                    System.out.println("正在打开文件");
                    MyFileUtils.openFile(msg.obj, MainActivity.this);
                    break;
                case MsgValue.SERVER_FLAG:
                    bt_openClient.setTextColor(Color.BLACK);
                    bt_openServer.setTextColor(Color.BLUE);
                    break;
                case MsgValue.CLIENT_FLAG:
                    bt_openServer.setTextColor(Color.BLACK);
                    bt_openClient.setTextColor(Color.BLUE);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };


    //本类发送消息的方法
    private void SendMessage(int what, int arg1, int arg2, Object obj) {
        if (handler != null) {
            Message.obtain(handler, what, arg1, arg2, obj).sendToTarget();
        }
    }

    //点击两次back退出程序
    private long mExitTime;

    @Override
    public void onBackPressed() {
        if ((System.currentTimeMillis() - mExitTime) > 2000) {
            Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
            mExitTime = System.currentTimeMillis();

        } else {
            //退出前的处理操作
            //关闭
            tcpSvr.closeServer();
            tcpClient.closeSocket();
            //释放有限域
            try {
                NCUtils.UninitGalois();
            } catch (Throwable e) {
                //可catch的最大类
                e.printStackTrace();
            }
            //关闭AP
            if (apHelper.isApEnabled()) {
                apHelper.setWifiApEnabled(null, false);
            }
            //删除wifi配置
            wifiAdmin.deleteContainSSid();
            //执行退出操作,并释放资源
            finish();
            //Dalvik VM的本地方法完全退出app
            Process.killProcess(Process.myPid());    //获取PID
            System.exit(0);   //常规java、c#的标准退出法，返回值为0代表正常退出
        }
    }

    /**
     * 以下处理菜单选项
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_setKValue:
                new MaterialDialog.Builder(this)
                        .title("GenerationSize")
                        .inputType(InputType.TYPE_CLASS_NUMBER)
                        .input(GenerationSize + "", GenerationSize + "", new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                // Do something
                                try {
                                    int k = Integer.parseInt(input.toString());
                                    if (k > 1 && k < 11) {
                                        GenerationSize = k;
                                        //存
                                        SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
                                        editor.putInt("GenerationSize", k);
                                        editor.apply();
                                    } else {
                                        Toast.makeText(MainActivity.this, "GenerationSize在2与10之间取值", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (NumberFormatException e) {
                                    e.printStackTrace();
                                }

                            }
                        }).show();
                break;
            case R.id.action_openAppFolder:
                //打开应用文件夹
                //String path="/storage/emulated/0/DCIM";
                Intent intent = new Intent(MainActivity.this, FilesListViewActivity.class);
                //intent.putExtra("data_path",path);
                intent.putExtra("data_path", GlobalVar.getDataFolderPath());
                startActivity(intent);
                break;
            case R.id.action_clearTemp:

                new MaterialDialog.Builder(this)
                        .title("提示")
                        .content("是否清空Temp目录？")
                        .positiveText("确认")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                // TODO
                                MyFileUtils.deleteAllFile(GlobalVar.getTempPath(), false);
                                Toast.makeText(MainActivity.this, "Temp目录下文件已清空", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .negativeText("取消")
                        .show();
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 适配android6.0以上权限                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         =
     */
    private void requestPermission() {
        /**
         * 请求所有必要的权限
         */
        PermissionsManager.getInstance().requestAllManifestPermissionsIfNecessary(this, new PermissionsResultAction() {
            @Override
            public void onGranted() {
                //1、获取IMEI的权限
                //2、访问文件的权限
                //3、(wifi扫描)需要的手机定位权限
//				Toast.makeText(MainActivity.this, "All permissions have been granted", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDenied(String permission) {
                //Toast.makeText(MainActivity.this, "Permission " + permission + " has been denied", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
