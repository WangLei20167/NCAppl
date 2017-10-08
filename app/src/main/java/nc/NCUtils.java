package nc;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Semaphore;

import utils.IntAndBytes;

/**
 * 矩阵乘法耗时太大  使用的jni函数执行
 */
public class NCUtils {
    //    public static GF gf;
//    //    public NCUtils() {
////        gf = new GF();
////        // 初始化有限域
////        // 这里采用m=8，多项式值为391
////        gf.init(8, 0x00000187);
////    }
    //为了避免两个线程同时进入jni操作，这里需要引入锁 信号量机制
    //这里的jni编解码只支持单线程
    public final static Semaphore NC_SEMAPHORE = new Semaphore(1);

    //申请使用nc
    public static void nc_acquire() {
        try {
            NC_SEMAPHORE.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //使用完毕释放掉
    public static void nc_release() {
        NC_SEMAPHORE.release();
    }

    //在此封装编码，再编码，解码
    //在此只生成一个再编码数据
    public static byte[] reencode(byte[] encodeData, int row, int col) {
        byte[] randomMatrix = new byte[row];
        Random random = new Random();
        for (int i = 0; i < row; ++i) {
            randomMatrix[i] = (byte) (random.nextInt(256));
        }
        //nc_acquire();
        byte[] result = Multiply(randomMatrix, 1, row, encodeData, row, col);
        // nc_release();
        //为了避免矩阵的来回复制，
        //再编码没有取出首字节K值，
        //再编码矩阵与编码数据相乘后，在把再编码结果首位置为K值
        result[0] = encodeData[0];
        return result;
    }

    //解码
    //编码数据的格式 K+编码系数+编码后的数据
    public static byte[] decode(byte[] encodeData, int row, int col) {
        //取出编码系数
        //这里认为nK == row，就是拥有的数据量刚好可以解码
        int nK = encodeData[0];
        byte[] coefMatrix = new byte[nK * nK];
        for (int i = 0; i < nK; ++i) {
            for (int j = 0; j < nK; ++j) {
                coefMatrix[i * nK + j] = encodeData[i * col + j + 1];
            }
        }
        //求编码矩阵的逆矩阵
        //nc_acquire();
        byte[] invCoefMatrix = InverseMatrix(coefMatrix, nK);
        if (invCoefMatrix == null) {
            //麻烦啦，编码系数不满秩，无法解码数据
            //nc_release();
            return null;
        }
        byte[] data = Multiply(invCoefMatrix, nK, nK, encodeData, row, col);
        //nc_release();

        //编码数据的数据量过大，（10M）
        //没把编码系数与编码后的数据做分离操作，直接相乘后，取出原数据
        int row0 = row;
        int col0 = col - 1 - nK;
        byte[] originData = new byte[row0 * col0];
        for (int i = 0; i < row0; ++i) {
            System.arraycopy(data, i * col + 1 + nK, originData, i * col0, col0);
        }
        return originData;
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    //初始化有限域
    //public static native void InitGalois();

    //public static native void UninitGalois();
    public static native byte[] Multiply(byte[] matrix1, int row1, int col1, byte[] matrix2, int row2, int col2);

    public static native byte[] InverseMatrix(byte[] arrayData, int nK);

    public static native int getRank(byte[] matrix, int nRow, int nCol);

}
