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
//
//    //在此封装再编码，解码
//    //随机编码文件   数组过大，则释放参数数组
//    public static byte[][] encode(byte[][] originData) {
//        int K = originData.length;
//        int col = originData[0].length;
//        byte[][] randomMatrix = new byte[K][K];
//        Random random = new Random();
//        for (int i = 0; i < K; ++i) {
//            for (int j = 0; j < K; ++j) {
//                randomMatrix[i][j] = (byte) (random.nextInt(256));
//            }
//        }
//        //编码  N*col的矩阵
//        nc_acquire();
//        byte[][] result = Multiply(randomMatrix, originData);
//        nc_release();
//        //释放originData
//        originData = null;
//        System.gc();
//        int Col = 1 + K + col;
//        byte[][] encodeResult = new byte[K][Col];
//        for (int i = 0; i < K; ++i) {
//            encodeResult[i][0] = (byte) K;
//            System.arraycopy(randomMatrix[i], 0, encodeResult[i], 1, K);
//            System.arraycopy(result[i], 0, encodeResult[i], 1 + K, col);
//        }
//        return encodeResult;
//    }
//
//    //再编码  生成的文件数目outputNum
//    //传入数据过大时    对参数数组使用完，进行了释放
//    public byte[][] reencode(byte[][] encodeData, int outputNum) {
//

//        int row = encodeData.length;
//        //int col = encodeData[0].length;
//        byte K = encodeData[0][0];
//        byte[][] randomMatrix = new byte[outputNum][row];
//        Random random = new Random();
//        for (int i = 0; i < outputNum; ++i) {
//            for (int j = 0; j < row; ++j) {
//                randomMatrix[i][j] = (byte) (random.nextInt(256));
//            }
//        }
//        //报错   内存分配过大
////        byte[][] data = new byte[row][col - 1];
////        for (int i = 0; i < row; ++i) {
////            System.arraycopy(encodeData[i], 1, data[i], 0, col - 1);
////        }
//        //释放之前的内存  通知gc回收
////        encodeData = null;
////        System.gc();
//        //outputNum * (col-1)
//        nc_acquire();
//        byte[][] result = Multiply(randomMatrix, encodeData);
//        nc_release();
//        //释放data
//        //data = null;
//        //System.runFinalization();
//        //System.gc();
//        //封装数据
//        //报错 这里申请数组过大
//        //byte[][] reencodeeResult = new byte[outputNum][col];
//  /*      for (int i = 0; i < outputNum; ++i) {
//            result[i][0] = K;
//            // System.arraycopy(result[i], 0, result[i], 1, col - 1);
//        }
//
//        encodeData = null;
//  */
//       //
//        // byte[][] res = new byte[1][1];
//        //res[0][0] = 0;
//        return encodeData;
//    }
//
//    //解码   释放了参数数组
//    public static byte[][] decode(byte[][] encodeData) {
//        int row = encodeData.length;
//        int col = encodeData[0].length;
//        int K = encodeData[0][0];
//        //拆分出系数矩阵  和编码后的数据
//        byte[][] coefMatrix = new byte[row][row];
//        byte[][] data = new byte[row][col];
//        for (int i = 0; i < row; ++i) {
//            System.arraycopy(encodeData[i], 1, coefMatrix[i], 0, K);
//            System.arraycopy(encodeData[i], 1 + K, data[i], 0, col - 1 - K);
//        }
//        //通知回收
//        encodeData = null;
//        System.gc();
//        byte[][] invCoef = Inverse(IntAndBytes.byteArray2IntArray(coefMatrix), row);
//        nc_acquire();
//        byte[][] originData = Multiply(invCoef, data);
//        nc_release();
//        return originData;
//    }


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
