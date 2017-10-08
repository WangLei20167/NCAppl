package utils;

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
     * @param file_source
     * @param targetPath
     */
    public static File transferFile(File file_source, String targetPath) {
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
        file_source.delete();
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
}
