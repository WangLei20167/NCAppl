package nc;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Semaphore;

import utils.IntAndBytes;

/**
 * 矩阵乘法耗时太大  使用的jni函数执行
 */
public class NCUtils {
    public static GF gf;
    //    public NCUtils() {
//        gf = new GF();
//        // 初始化有限域
//        // 这里采用m=8，多项式值为391
//        gf.init(8, 0x00000187);
//    }
    //为了避免两个线程同时进入jni操作，这里需要引入锁 信号量机制
    public final static Semaphore NC_SEMAPHORE = new Semaphore(1);
    //使用jni方法前
    //.acquire();
    //使用后
    //.release();

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

    //在此封装再编码，解码
    //随机编码文件   数组过大，则释放参数数组
    public static byte[][] encode(byte[][] originData) {
        int K = originData.length;
        int col = originData[0].length;
        byte[][] randomMatrix = new byte[K][K];
        Random random = new Random();
        for (int i = 0; i < K; ++i) {
            for (int j = 0; j < K; ++j) {
                randomMatrix[i][j] = (byte) (random.nextInt(256));
            }
        }
        //编码  N*col的矩阵
      //  nc_acquire();
        byte[][] result = Multiply(randomMatrix, originData);
     //   nc_release();
        //释放originData
        originData=null;
        System.gc();
        int Col = 1 + K + col;
        byte[][] encodeResult = new byte[K][Col];
        for (int i = 0; i < K; ++i) {
            encodeResult[i][0] = (byte) K;
            System.arraycopy(randomMatrix[i], 0, encodeResult[i], 1, K);
            System.arraycopy(result[i], 0, encodeResult[i], 1 + K, col);
        }
        return encodeResult;
    }

    //再编码  生成的文件数目outputNum
    //传入数据过大时    对参数数组使用完，进行了释放
    public static byte[][] reencode(byte[][] encodeData, int outputNum) {
        int row = encodeData.length;
        int col = encodeData[0].length;
        byte K = encodeData[0][0];
        byte[][] randomMatrix = new byte[outputNum][row];
        Random random = new Random();
        for (int i = 0; i < outputNum; ++i) {
            for (int j = 0; j < row; ++j) {
                randomMatrix[i][j] = (byte) (random.nextInt(256));
            }
        }
        //报错   内存分配过大
        byte[][] data = new byte[row][col - 1];
        for (int i = 0; i < row; ++i) {
            System.arraycopy(encodeData[i], 1, data[i], 0, col - 1);
        }
        //释放之前的内存  通知gc回收
        encodeData = null;
        System.gc();
        //outputNum * (col-1)
        // nc_acquire();
        byte[][] result = Multiply(randomMatrix, data);
        //nc_release();

        //释放data
        data = null;
        System.gc();
        //封装数据
        //报错 这里申请数组过大
        byte[][] reencodeeResult = new byte[outputNum][col];
        for (int i = 0; i < outputNum; ++i) {
            reencodeeResult[i][0] = K;
            System.arraycopy(result[i], 0, reencodeeResult[i], 1, col - 1);
        }
        return reencodeeResult;
    }

    //解码   释放了参数数组
    public static byte[][] decode(byte[][] encodeData) {
        int row = encodeData.length;
        int col = encodeData[0].length;
        int K = encodeData[0][0];
        //拆分出系数矩阵  和编码后的数据
        byte[][] coefMatrix = new byte[row][row];
        byte[][] data = new byte[row][col];
        for (int i = 0; i < row; ++i) {
            System.arraycopy(encodeData[i], 1, coefMatrix[i], 0, K);
            System.arraycopy(encodeData[i], 1 + K, data[i], 0, col - 1 - K);
        }
        //通知回收
        encodeData = null;
        System.gc();
        byte[][] invCoef = Inverse(IntAndBytes.byteArray2IntArray(coefMatrix), row);
        //  nc_acquire();
        byte[][] originData = Multiply(invCoef, data);
        //  nc_release();
        return originData;
    }

    // 矩阵求逆
    public static byte[][] Inverse(int[][] Mat, int n) {
        if (gf == null) {
            gf = new GF();
            // 初始化有限域
            // 这里采用m=8，多项式值为391
            gf.init(8, 0x00000187);
        }
        int nRow = n;
        int nCol = n;
        int nRank = getRank(Mat, n, n);
        //求逆
        int bRet = nRank;
        if (bRet != nRow) {
            return null;
        } else {
            /************************************************************************/
            /**Start to get the inverse matrix!                                     */
            /************************************************************************/

            int[][] N = new int[nCol][2 * nCol];
            for (int i = 0; i < nCol; i++) {
                for (int j = 0; j < nCol; j++) {
                    N[i][j] = Mat[i][j];
                }
                for (int j = nCol; j < 2 * nCol; j++) {
                    if (i == j - nCol) {
                        N[i][j] = 1;
                    } else {
                        N[i][j] = 0;
                    }
                }
            }
            /************************************************************************/
            /** Step 1. Change to a lower triangle matrix.                           */
            /************************************************************************/
            for (int i = 0; i < nCol; i++) {
                // There must exist a non-zero mainelement.
                if (N[i][i] == 0) {
                    // Record this line.
                    int[] temp = new int[200];
                    Arrays.fill(temp, 0);
                    for (int k = 0; k < 2 * nCol; k++) {
                        temp[k] = N[i][k];
                    }
                    // Exchange
                    int Row = nCol;                    // They are the same in essensial.
                    for (int z = i + 1; z < Row; z++) {
                        if (N[z][i] != 0) {
                            for (int x = 0; x < 2 * nCol; x++) {
                                N[i][x] = N[z][x];
                                N[z][x] = temp[x];
                            }
                            break;
                        }
                    }
                }

                for (int j = i + 1; j < nCol; j++) {
                    // Now, the main element must be nonsingular.
                    int temp = gf.div(N[j][i], N[i][i]);
                    for (int z = 0; z < 2 * nCol; z++) {
                        N[j][z] = gf.add(N[j][z], gf.mul(temp, N[i][z]));
                    }
                }
            }
            /************************************************************************/
            /** Step 2. Only the elements on the diagonal are non-zero.                  */
            /************************************************************************/
            for (int i = 1; i < nCol; i++) {
                for (int k = 0; k < i; k++) {
                    int temp = gf.div(N[k][i], N[i][i]);
                    for (int z = 0; z < 2 * nCol; z++) {
                        N[k][z] = gf.add(N[k][z], gf.mul(temp, N[i][z]));
                    }
                }
            }
            /************************************************************************/
            /* Step 3. The elements on the diagonal are 1.                  */
            /************************************************************************/
            for (int i = 0; i < nCol; i++) {
                if (N[i][i] != 1) {
                    int temp = N[i][i];
                    for (int z = 0; z < 2 * nCol; z++) {
                        N[i][z] = gf.div(N[i][z], temp);
                    }
                }
            }
            /************************************************************************/
            /**Get the new matrix.                                                  */
            /************************************************************************/

            int[][] CM = new int[nCol][nCol];
            for (int i = 0; i < nCol; i++) {
                for (int j = 0; j < nCol; j++) {
                    CM[i][j] = N[i][j + nCol];
                }
            }

            return IntAndBytes.intArray2byteArray(CM);
        }
    }

    // 求秩
    public static int getRank(int[][] Mat, int nRow, int nCol) {
        if (gf == null) {
            gf = new GF();
            // 初始化有限域
            // 这里采用m=8，多项式值为391
            gf.init(8, 0x00000187);
        }
        // Define a variable to record the position of the main element.
        int[][] M = new int[nRow][nCol];
        for (int i = 0; i < nRow; ++i) {
            for (int j = 0; j < nCol; ++j) {
                M[i][j] = Mat[i][j];
            }
        }

        int yPos = 0;
        for (int i = 0; i < nRow; i++) {
            // Find the main element which must be non-zero.
            // 按列选取第一个非零元素作为主元素，然后交换所在行和主元素所在行
            boolean bFind = false;
            for (int x = yPos; x < nCol; x++) {
                for (int k = i; k < nRow; k++) {
                    if (M[k][x] != 0) {
                        // Exchange the two vectors.

                        /** wx */
                        if (k != i) {
                            for (int m = 0; m < nCol; m++) {
                                int nVal = M[i][m];
                                M[i][m] = M[k][m];
                                M[k][m] = nVal;
                            }
                        }
                        /** wx */
                        bFind = true;
                        break;
                    }
                }
                if (bFind == true) {
                    yPos = x;
                    break;
                }
                /** wx */
                //return -1;
                /** wx */
            }
            // 所在行位置以下的各行按序消元
            for (int j = i + 1; j < nRow; j++) {
                // Now, the main element must be nonsingular.
                int temp = gf.div(M[j][yPos], M[i][yPos]);
                for (int z = 0; z < nCol; z++) {
                    M[j][z] = gf.add(M[j][z], gf.mul(temp, M[i][z]));// 模二加等价于模二减
                }
            }
            yPos++;
        }

        // The matrix becomes a scalar matrix. we need to make more elements become 0 with elementary transformations.
        yPos = 0;
        for (int i = 1; i < nRow; i++) {
            for (int j = 0; j < nCol; j++) {
                if (M[i][j] != 0) {
                    // the main element is found.
                    yPos = j;
                    break;
                }
            }
            for (int k = 0; k < i; k++) {
                int temp = gf.div(M[k][yPos], M[i][yPos]);
                for (int z = 0; z < nCol; z++) {
                    M[k][z] = gf.add(M[k][z], gf.mul(temp, M[i][z]));
                }
            }
        }

        int nRank = 0;
        // Get the rank.
        for (int i = 0; i < nRow; i++) {
            int nNonzero = 0;
            for (int j = 0; j < nCol; j++) {
                if (M[i][j] != 0) {
                    nNonzero++;
                }
            }
            // If there is only one nonzero element in the new matrix, it is concluded an original packet is leaked.
            if (nNonzero > 0) {
                // Leaked.
                nRank++;
            }
        }
        return nRank;
    }


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public static native byte[][] Multiply(byte[][] matrix1, byte[][] matrix2);

    // 矩阵相乘
//	public byte[][] Multiply(byte[][] mat1, byte[][] mat2) {
//    if (gf == null) {
//        gf = new GF();
//        // 初始化有限域
//        // 这里采用m=8，多项式值为391
//        gf.init(8, 0x00000187);
//    }
//		int row = mat1.length;
//		int k = mat1[0].length;
//		int k1 = mat2.length;
//		// 第一个矩阵列不等于第二个矩阵的行，不能相乘
//		if (k != k1) {
//			return null;
//		}
//		int col = mat2[0].length;
//		byte[][] reusult = new byte[row][col];
//		int temp;
//		for (int i = 0; i < row; ++i) {
//			for (int j = 0; j < col; ++j) {
//				temp = 0;
//				for (int n = 0; n < k; ++n) {
//					int a=mat1[i][n];
//					if(a<0){
//						a=256-Math.abs(a);
//					}
//					int b=mat2[n][j];
//					if(b<0){
//						b=256-Math.abs(b);
//					}
//					temp = gf.add(temp, gf.mul(a, b));
//				}
//				reusult[i][j] = (byte)temp;
//			}
//		}
//		return reusult;
//	}
}
