package utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import appData.GlobalVar;
import njupt.ncappl.FilesListViewActivity;

/**
 * Created by Administrator on 2017/5/14 0014.
 */

public class MyFileUtils {
    /**
     * 将byte流写入指定文件，文件若是不存在，先创建再写
     *
     * @param path      路径
     * @param fileName  文件名
     * @param inputData
     */
    public static File writeToFile(String path, String fileName, byte[] inputData) {
        File myFile = new File(path + File.separator + fileName);
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        if (!myFile.exists()) {   //不存在则创建
            try {
                myFile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        try {
            //传递一个true参数，代表不覆盖已有的文件。并在已有文件的末尾处进行数据续写,false表示覆盖写
            //FileWriter fw = new FileWriter(myFile, false);
            //BufferedWriter bw = new BufferedWriter(fw);
            fos = new FileOutputStream(myFile);  //覆盖写
            //fos = new FileOutputStream(myFile, true);  //续写
            bos = new BufferedOutputStream(fos);
            bos.write(inputData);
            //bw.write("测试文本");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.flush();
                    bos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }

        return myFile;
    }


    /**
     * 从路径文件中读取数据放入byte[]
     *
     * @param obj 传入文件对象，或是文件路径
     * @return byte[]
     */
    public static byte[] readFile(Object obj) {
        File file = null;
        if (obj instanceof File) {
            file = (File) obj;
        } else {
            file = new File(obj.toString());
        }
        if (!file.exists()) {
            try {
                //不存在的话，创建新文件并退出
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            System.out.println("file too big...");
            return null;
        }

        byte[] buffer = null;
        try {
            FileInputStream fi = new FileInputStream(file);
            buffer = new byte[(int) fileSize];
            int offset = 0;
            int numRead = 0;
            while (offset < buffer.length
                    && (numRead = fi.read(buffer, offset, buffer.length - offset)) >= 0) {
                offset += numRead;
            }
            // 确保所有数据均被读取
            if (offset != buffer.length) {
                throw new IOException("Could not completely read file "
                        + file.getName());
            }
            fi.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return buffer;

    }


    /**
     * 在指定路径下创建File，并返回File对象
     *
     * @param path
     * @param fileName
     * @return
     */
    public static File creatFile(String path, String fileName) {
        String toFile = path + File.separator + fileName;
        File myFile = new File(toFile);
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        if (!myFile.exists()) {   //不存在则创建
            try {
                myFile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return myFile;
    }


    /**
     * 在指定目录下创建文件夹
     *
     * @param path       路径
     * @param folderName 文件夹名
     * @return 返回创建的文件夹路径
     */
    public static String creatFolder(String path, String folderName) {
        String pathFolder = path + File.separator + folderName;
        File tempFolder = new File(pathFolder);
        if (!tempFolder.exists()) {
            //若不存在，则创建
            tempFolder.mkdir();
        }

        return pathFolder;
    }


    /***
     * 获取指定目录下的所有的文件（不包括文件夹），采用了递归
     *
     * @param obj
     * @return
     */
    public static ArrayList<File> getListFiles(Object obj) {
        File directory = null;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        ArrayList<File> files = new ArrayList<File>();
        if (directory.isFile()) {
            files.add(directory);
            return files;
        } else if (directory.isDirectory()) {
            File[] fileArr = directory.listFiles();
            for (int i = 0; i < fileArr.length; i++) {
                File fileOne = fileArr[i];
                files.addAll(getListFiles(fileOne));
            }
        }
        return files;
    }

    /**
     * 获取指定目录下文件夹,只找一级目录下
     */
    public static ArrayList<File> getListFolders(Object obj) {
        File directory = null;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        ArrayList<File> folderList = new ArrayList<File>();
        if (directory.isDirectory()) {
            File[] fileArr = directory.listFiles();
            for (int i = 0; i < fileArr.length; ++i) {
                File fileOne = fileArr[i];
                if (fileOne.isDirectory()) {
                    folderList.add(fileOne);
                }
            }
        }
        return folderList;
    }

    /**
     * 获取指定目录下文件,只找一级目录下
     */
    public static ArrayList<File> getList_1_files(Object obj) {
        File directory = null;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        ArrayList<File> fileList = new ArrayList<File>();
        if (directory.isDirectory()) {
            File[] fileArr = directory.listFiles();
            for (int i = 0; i < fileArr.length; ++i) {
                File fileOne = fileArr[i];
                if (fileOne.isFile()) {
                    fileList.add(fileOne);
                }
            }
        }
        return fileList;
    }


    /**
     * 获取一级目录下所有文件和文件夹
     *
     * @param obj
     * @return
     */
    public static ArrayList<File> getList(Object obj) {
        File directory = null;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        ArrayList<File> folderList = new ArrayList<File>();
        if (directory.isDirectory()) {
            File[] fileArr = directory.listFiles();
            for (int i = 0; i < fileArr.length; ++i) {
                File fileOne = fileArr[i];
                folderList.add(fileOne);
            }
        }
        return folderList;
    }

    /**
     * 获取一个目录下文件的数目，不包含文件夹，只查找一级目录
     */
    public static int getFileNum(Object obj) {
        File directory = null;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        int fileNum = 0;
        if (directory.isDirectory()) {
            File[] fileArr = directory.listFiles();
            for (int i = 0; i < fileArr.length; ++i) {
                File fileOne = fileArr[i];
                if (fileOne.isFile()) {
                    ++fileNum;
                }
            }
        }
        return fileNum;
    }

    /**
     * 删除指定目录下所有文件  注意：删除文件夹时，是先删除其中所有文件，再删除文件夹
     * 不删除指定的路径
     *
     * @param obj
     * @param deletePath 是否删除路径（文件夹）
     * @return
     */
    public static void deleteAllFile(Object obj, boolean deletePath) {
        File directory = null;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        if (directory.isFile()) {
            directory.delete();
            return;
        } else if (directory.isDirectory()) {
            File[] fileArr = directory.listFiles();
            for (int i = 0; i < fileArr.length; i++) {
                File fileOne = fileArr[i];
                if (fileOne.isDirectory()) {
                    deleteAllFile(fileOne, false);
                }
                fileOne.delete();
            }
            //这句加上的话  指定路径也会被删除
            if (deletePath) {
                directory.delete();
            }
        }
    }


    /**
     * 合并文件
     *
     * @param outFile 输出路径,含文件名
     * @param files   需要合并的文件路径 可是File[]，也可是String[]
     */
    public static final int BUFSIZE = 1024 * 8;

    public static void mergeFiles(String outFile, File[] files) {
        FileChannel outChannel = null;
        try {
            outChannel = new FileOutputStream(outFile).getChannel();
            for (File f : files) {
                FileChannel fc = new FileInputStream(f).getChannel();
                ByteBuffer bb = ByteBuffer.allocate(BUFSIZE);
                while (fc.read(bb) != -1) {
                    bb.flip();
                    outChannel.write(bb);
                    bb.clear();
                }
                fc.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                if (outChannel != null) {
                    outChannel.close();
                }
            } catch (IOException ignore) {
            }
        }
    }


    /**
     * //把文件复制进指定的路径
     * 转移文件
     *
     * @param file_source
     * @param targetPath
     * @param deleteOriginFile 是否删除原文件
     */
    public static File moveFile(File file_source, String targetPath, boolean deleteOriginFile) {
        String fileName = file_source.getName();

        File file_target = new File(targetPath + File.separator + fileName);
        if (!file_target.exists()) {   //不存在则创建
            try {
                file_target.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        InputStream fis = null;
        OutputStream fos = null;
        try {
            fis = new FileInputStream(file_source);
            fos = new FileOutputStream(file_target);
            byte[] buf = new byte[4096];
            int i;
            while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fis.close();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //删除源文件
        if (deleteOriginFile) {
            file_source.delete();
        }
        return file_target;
    }


    /**
     * 从一个文件读取一定字节到另一个文件，续写
     *
     * @param in       输入文件
     * @param path
     * @param fileName
     * @param bytes    字节数
     */
    public static File splitFile(InputStream in, String path, String fileName, int bytes) {
        File out_file = creatFile(path, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out_file, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        int limitRead = bytes;
        if (limitRead > 1024) {
            limitRead = 1024;
        }
        byte[] temp = new byte[1024];
        int readBytes = 0;
        while (true) {
            try {
                int len = in.read(temp, 0, limitRead);
                if (len == -1) {
                    //已经读完了数据，剩余部分填充0
                    //因为网络编码处理，需要每一段都是相同长度
                    byte[] bt_0 = new byte[limitRead];
                    fos.write(bt_0, 0, limitRead);
                    fos.close();
                    break;
                }
                fos.write(temp, 0, len);    //写入文件
                readBytes += len;
                limitRead = bytes - readBytes;
                if (limitRead > 1024) {
                    limitRead = 1024;
                } else if (limitRead <= 0) {
                    fos.close();
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return out_file;

    }


    //写发送和接收日志
    //把发送和接收完成的时间写入日志
    public final static int SEND_TYPE = 0;
    public final static int REV_TYPE = 1;

    /**
     * @param type     0代表是开始分享的发送时间，1代表是接受完成的时间
     * @param fileName 分享的文件名
     * @param time     分享的时间
     */
    public static void writeLog(int type, String time, String fileName) {
        String filePath;
        String description;
        if (type == 0) {
            filePath = GlobalVar.getLogPath() + File.separator + GlobalVar.sendLogName;
            description = "开始分享";
        } else {
            filePath = GlobalVar.getLogPath() + File.separator + GlobalVar.revLogName;
            description = "接收完成";
        }
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        byte[] inputData = (time + "  " + fileName + " " + description + "\n\n").getBytes();
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        try {
            //传递一个true参数，代表不覆盖已有的文件。并在已有文件的末尾处进行数据续写,false表示覆盖写
            //FileWriter fw = new FileWriter(myFile, false);
            //BufferedWriter bw = new BufferedWriter(fw);
            //fos = new FileOutputStream(myFile);  //覆盖写
            fos = new FileOutputStream(file, true);  //续写
            bos = new BufferedOutputStream(fos);
            bos.write(inputData);
            //bw.write("测试文本");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.flush();
                    bos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
    /**
     * 根据文件路径或是File对象打开文件
     *
     * @param objFile File对象，或是文件路径
     * @param context
     * @return 打开成功返回true，否则false
     */
    public static boolean openFile(Object objFile, Context context) {
        File file = null;
        if (objFile instanceof File) {
            file = (File) objFile;
        } else {
            file = new File(objFile.toString());
        }
        //文件不存在  或者路径指向的不是一个文件
        if ((!file.exists()) || (!file.isFile())) {

            return false;
        }
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //设置intent的Action属性
        intent.setAction(Intent.ACTION_VIEW);
        //获取文件file的MIME类型
        String type = getMIMEType(file);
        //设置intent的data和Type属性。
        intent.setDataAndType(Uri.fromFile(file), type);
        //跳转
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            //logger.error("FileUtil", e);
            return false;
            //Toast.makeText(FilesListViewActivity.this, "找不到打开此文件的应用！", Toast.LENGTH_SHORT).show();
        }
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
