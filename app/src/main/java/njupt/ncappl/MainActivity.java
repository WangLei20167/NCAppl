package njupt.ncappl;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import appData.GlobalVar;
import connect.APHelper;
import connect.Constant;
import connect.TcpClient;
import connect.TcpServer;
import connect.WifiAdmin;
import fileSlices.EncodeFile;
import fileSlices.PieceFile;
import msg.MsgValue;
import nc.NCUtils;
import utils.LocalInfor;
import utils.MyFileUtils;

public class MainActivity extends AppCompatActivity {

    private APHelper apHelper;
    private WifiAdmin wifiAdmin;
    TcpServer tcpSvr;
    TcpClient tcpClient;
    //对打开文件选择器时 显示的路径进行控制
    private String startPath;
    //编码数据
    EncodeFile encodeFile;
    private int GenerationSize;

    TextView tv_promptMsg;
    ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化全局变量   文件存放地址
        GlobalVar.initial(this);
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
        //初始化有限域
//        NCUtils.nc_acquire();
//        NCUtils.InitGalois();
//        NCUtils.nc_release();

        tv_promptMsg = (TextView) findViewById(R.id.tv_promptMsg);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        //服务器按钮
        Button bt_openServer = (Button) findViewById(R.id.bt_openServer);
        bt_openServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //
                openServer();
            }
        });
        //客户端按钮
        Button bt_openClient = (Button) findViewById(R.id.bt_openClient);
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
                                //Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
                                String xmlFilePath = GlobalVar.getTempPath() + File.separator + text + File.separator + "xml.txt";
                                encodeFile = EncodeFile.xml2object(xmlFilePath, true);
                                return true;
                            }
                        })
                        .positiveText("选择")
                        .show();
            }
        });
    }

    //测试方法
    public void onTest(View view) {
        if (encodeFile != null) {
            for (final PieceFile pieceFile : encodeFile.getPieceFileList()) {

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pieceFile.re_encodeFile();
                    }
                }).start();
            }
        }
    }

    //运行Server
    public void openServer() {
        //先要执行打开ap操作
        //开启AP
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (encodeFile == null) {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "encodeFile变量为null");
                    return;
                }
                if (apHelper.setWifiApEnabled(APHelper.createWifiCfg(), true)) {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "AP打开成功");
                    //执行打开server的动作
                    tcpSvr.openServerSocket(encodeFile);
                } else {
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0, "AP打开失败");
                }
            }
        }).start();
    }

    //运行client
    public void openClient() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //此处做打开wifi的操作
                wifiAdmin.openWifi();
                String ssid = wifiAdmin.searchWifi(Constant.SSID);
                if (ssid == null) {
                    System.out.println("查找指定ssid失败");
                    return;
                }
                //连接网络的操作
                wifiAdmin.addNetwork(
                        wifiAdmin.CreateWifiInfo(ssid, Constant.AP_PASS_WORD, 3)
                );
                //等待连接的时间为10s
                // Starting time.
                long startMili = System.currentTimeMillis();
                while (!wifiAdmin.isWifiConnected()) {
                    if (wifiAdmin.currentConnectSSID().equals(ssid)) {
                        break;
                    }
                    //等待连接
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // Ending time.
                    long endMili = System.currentTimeMillis();
                    int time = (int) ((endMili - startMili) / 1000);
                    if (time > 10) {
                        SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                                "连接指定wifi失败，请重试"
                        );
                        return;
                    }
                }
                SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                        "连接wifi成功"
                );
                //做连接ServerSocket的动作
                tcpClient.connectServer();
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
                            "正在对"+file.getName()+"进行再编码");
                    encodeFile = new EncodeFile(file.getName(), GenerationSize, null);
                    //编码
                    encodeFile.cutFile(file);
                    //encodeFile.recoveryFile();
                    SendMessage(MsgValue.TELL_ME_SOME_INFOR, 0, 0,
                            "编码完成");
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
            //释放有限域
//            NCUtils.nc_acquire();
//            NCUtils.UninitGalois();
//            NCUtils.nc_release();
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
            case R.id.action_description:
                Toast.makeText(this, "显示软件信息", Toast.LENGTH_SHORT).show();
                break;
            default:
        }
        return true;
    }

}
