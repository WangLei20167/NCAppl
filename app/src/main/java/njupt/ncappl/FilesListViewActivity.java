package njupt.ncappl;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import utils.MyFileUtils;

public class FilesListViewActivity extends AppCompatActivity {

    private String dataPath;
    private String currentPath;
    ArrayList<File> files = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_files_list_view);
        //从上一个活动获取文件地址
        Intent intent = getIntent();
        dataPath = intent.getStringExtra("data_path");
        //显示文件目录
        showFileList(dataPath);
    }

    /**
     * 显示path路径下的文件目录
     * @param path
     */
    public void showFileList(final String path) {
        currentPath = path;
        //文件夹名作为标题
        int index = path.lastIndexOf("/");
        String folderName = path.substring(index + 1);
        setTitle(folderName);

        //从路径中获取所有文件和文件夹
        files = MyFileUtils.getList(path);
        int item_num;
        if (path.equals(dataPath)) {
            if(files.size()==0){
                Toast.makeText(this, "app目录下没有文件", Toast.LENGTH_SHORT).show();
            }
            item_num = files.size();
        } else {
            item_num = files.size() + 1;
        }
        final String[] fileList = new String[item_num];

        //fileList[0]="共"+files.size()+"个对象，点击刷新";
        int iFileList = 0;
        if (!path.equals(dataPath)) {
            fileList[0] = "...";
            ++iFileList;
        }
        for (File file : files) {
            fileList[iFileList] = file.getName();
            ++iFileList;
        }

        File folder = new File(path);
        final String parent_path = folder.getParent();

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(FilesListViewActivity.this, R.layout.file_item, fileList);

        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int file_position = position - 1;
                if (path.equals(dataPath)) {
                    file_position = position;
                }
                if (position == 0) {
                    //刷新列表，并返回
                    if (path.equals(dataPath)) {

                    } else {
                        showFileList(parent_path);
                        return;
                    }
                }
                final File file_select = files.get(file_position);
                if (file_select.isDirectory()) {
                    showFileList(file_select.getPath());
                    return;
                }

                long fileLen = file_select.length();
                String str_length;
                DecimalFormat format = new DecimalFormat("###.##");
                if (fileLen > Math.pow(2, 30)) {
                    str_length = format.format((float) fileLen / Math.pow(2, 30)) + "GB";
                } else if (fileLen > Math.pow(2, 20)) {
                    str_length = format.format((float) fileLen / Math.pow(2, 20)) + "MB";
                } else if (fileLen > Math.pow(2, 10)) {
                    str_length = format.format((float) fileLen / Math.pow(2, 10)) + "KB";
                } else {
                    str_length = fileLen + "B";
                }
                long time = file_select.lastModified();
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(time);
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                new AlertDialog.Builder(FilesListViewActivity.this)
                        .setTitle(file_select.getName())
                        .setMessage("Length: " + str_length + "\n" + "LastModified: \n" + formatter.format(cal.getTime()))
                        .setNegativeButton("打开", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent();
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                //设置intent的Action属性
                                intent.setAction(Intent.ACTION_VIEW);
                                //获取文件file的MIME类型
                                String type = getMIMEType(file_select);
                                //设置intent的data和Type属性。
                                intent.setDataAndType(Uri.fromFile(file_select), type);
                                //跳转
                                try {
                                    startActivity(intent);
                                } catch (Exception e) {
                                    //logger.error("FileUtil", e);
                                    Toast.makeText(FilesListViewActivity.this, "找不到打开此文件的应用！", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setPositiveButton("确定", null)
                        .show();
            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_refresh:
                showFileList(currentPath);
                Toast.makeText(this, "刷新成功", Toast.LENGTH_SHORT).show();
                break;
            case R.id.item_deleteAllFiles:
                if (files.size() == 0 || files == null) {
                    Toast.makeText(this, "此目录没有文件", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (currentPath.equals(dataPath)) {
                    Toast.makeText(this, "不可删除app文件根目录", Toast.LENGTH_SHORT).show();
                    return true;
                }
//                for (File file : files) {
//                    file.delete();
//                }
                MyFileUtils.deleteAllFile(currentPath,false);
                showFileList(currentPath);
                Toast.makeText(this, "此目录所有文件已删除", Toast.LENGTH_SHORT).show();
                break;
            default:
        }
        return true;
    }


    /***
     * 根据文件后缀回去MIME类型
     ****/
    private static String getMIMEType(File file) {
        String type = "*/*";
        String fName = file.getName();
        //获取后缀名前的分隔符"."在fName中的位置。
        int dotIndex = fName.lastIndexOf(".");
        if (dotIndex < 0) {
            return type;
        }
        /* 获取文件的后缀名*/
        String end = fName.substring(dotIndex, fName.length()).toLowerCase();
        if (end == "") return type;
        //在MIME和文件类型的匹配表中找到对应的MIME类型。
        for (int i = 0; i < MIME_MapTable.length; i++) { //MIME_MapTable??在这里你一定有疑问，这个MIME_MapTable是什么？
            if (end.equals(MIME_MapTable[i][0]))
                type = MIME_MapTable[i][1];
        }
        return type;
    }

    private static final String[][] MIME_MapTable = {
            // {后缀名，MIME类型}
            {".3gp", "video/3gpp"},
            {".apk", "application/vnd.android.package-archive"},
            {".asf", "video/x-ms-asf"},
            {".avi", "video/x-msvideo"},
            {".bin", "application/octet-stream"},
            {".bmp", "image/bmp"},
            {".c", "text/plain"},
            {".class", "application/octet-stream"},
            {".conf", "text/plain"},
            {".cpp", "text/plain"},
            {".doc", "application/msword"},
            {".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {".xls", "application/vnd.ms-excel"},
            {".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {".exe", "application/octet-stream"},
            {".gif", "image/gif"},
            {".gtar", "application/x-gtar"},
            {".gz", "application/x-gzip"},
            {".h", "text/plain"},
            {".htm", "text/html"},
            {".html", "text/html"},
            {".jar", "application/java-archive"},
            {".java", "text/plain"},
            {".jpeg", "image/jpeg"},
            {".jpg", "image/jpeg"},
            {".js", "application/x-javascript"},
            {".log", "text/plain"},
            {".m3u", "audio/x-mpegurl"},
            {".m4a", "audio/mp4a-latm"},
            {".m4b", "audio/mp4a-latm"},
            {".m4p", "audio/mp4a-latm"},
            {".m4u", "video/vnd.mpegurl"},
            {".m4v", "video/x-m4v"},
            {".mov", "video/quicktime"},
            {".mp2", "audio/x-mpeg"},
            {".mp3", "audio/x-mpeg"},
            {".mp4", "video/mp4"},
            {".mpc", "application/vnd.mpohun.certificate"},
            {".mpe", "video/mpeg"},
            {".mpeg", "video/mpeg"},
            {".mpg", "video/mpeg"},
            {".mpg4", "video/mp4"},
            {".mpga", "audio/mpeg"},
            {".msg", "application/vnd.ms-outlook"},
            {".ogg", "audio/ogg"},
            {".pdf", "application/pdf"},
            {".png", "image/png"},
            {".pps", "application/vnd.ms-powerpoint"},
            {".ppt", "application/vnd.ms-powerpoint"},
            {".pptx",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {".prop", "text/plain"}, {".rc", "text/plain"},
            {".rmvb", "audio/x-pn-realaudio"}, {".rtf", "application/rtf"},
            {".sh", "text/plain"}, {".tar", "application/x-tar"},
            {".tgz", "application/x-compressed"}, {".txt", "text/plain"},
            {".wav", "audio/x-wav"}, {".wma", "audio/x-ms-wma"},
            {".wmv", "audio/x-ms-wmv"},
            {".wps", "application/vnd.ms-works"}, {".xml", "text/plain"},
            {".z", "application/x-compress"},
            {".zip", "application/x-zip-compressed"}, {"", "*/*"}
    };
}
