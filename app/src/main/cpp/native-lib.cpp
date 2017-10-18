#include <jni.h>
#include <string>
//#include "gf.c"
//return NULL 时，用到了string库？？？
//把有限域上的库直接整合在这里
//不再使用 gf.c
//有限域上的加减乘法指数运算宏
#define  gf_add(a, b)    (a^b)
#define  gf_sub(a, b)    (a^b)

#define  gf_mul(a, b)    (table_mul[a][b])
#define  gf_div(a, b)    (table_div[a][b])

typedef unsigned int GFType;
//有限域
int gFieldSize;
//
//乘法表和除法表
GFType *table_alpha;
GFType *table_index;
GFType **table_mul;
GFType **table_div;
//有限域表
GFType prim_poly[13] =
        {
/*	0 */    0x00000000,
/*  1 */    0x00000001,
/*  2 */    0x00000007,
/*  3 */    0x0000000b,
/*  4 */    0x00000013,
/*  5 */    0x00000025,
/*  6 */    0x00000043,
/*  7 */    0x00000089,
/*  8 */    0x00000187,
/*  9 */    0x00000211,
/* 10 */    0x00000409,
/* 11 */    0x00000805,
/* 12 */    0x00001053,
        };

extern "C" {
//乘法
GFType gfmul(GFType a, GFType b);
//除法
GFType gfdiv(GFType a, GFType b);
//指数
GFType gf_exp(GFType a, GFType n);
//初始化有限域
void gf_init(unsigned int m, unsigned int prim);
//释放有限域
void gf_uninit();

//1、初始化有限域
JNIEXPORT void JNICALL
        Java_nc_NCUtils_InitGalois(JNIEnv *env, jobject instance);
//2、释放有限域
JNIEXPORT void JNICALL
        Java_nc_NCUtils_UninitGalois(JNIEnv *env, jobject instance);

//3、矩阵相乘
JNIEXPORT jbyteArray JNICALL
        Java_nc_NCUtils_Multiply(JNIEnv *env, jobject instance,
                                 jbyteArray matrix1, jint row1, jint col1,
                                 jbyteArray matrix2, jint row2, jint col2);

//4、矩阵求逆
//先是用到了求矩阵的秩，满秩则继续求逆，不满秩则返回NULL
JNIEXPORT jbyteArray JNICALL
        Java_nc_NCUtils_InverseMatrix(JNIEnv *env, jobject thiz,
                                      jbyteArray arrayData, jint nK);
//5、求秩
JNIEXPORT jint JNICALL
        Java_nc_NCUtils_getRank(JNIEnv *env, jobject instance, jbyteArray matrix, jint nRow,
                                jint nCol);
}

//初始化有限域
JNIEXPORT void
JNICALL Java_nc_NCUtils_InitGalois(JNIEnv *env, jobject thiz) {
    // Initialize the Galois field.
    gf_init(8, 0x00000187);
    // return env->NewStringUTF("初始化有限域");
}
//释放有限域
JNIEXPORT void JNICALL
Java_nc_NCUtils_UninitGalois(JNIEnv *env, jobject thiz) {
    //释放有限域
    gf_uninit();
    //return env->NewStringUTF("释放有限域");
}


//矩阵相乘
JNIEXPORT jbyteArray JNICALL
Java_nc_NCUtils_Multiply(JNIEnv *env, jobject instance,
                         jbyteArray matrix1, jint row1, jint col1,
                         jbyteArray matrix2, jint row2, jint col2) {
    //gs_jvm->AttachCurrentThread(&env, NULL);
    //矩阵1
    jbyte *olddata1 = (jbyte *) env->GetByteArrayElements(matrix1, 0);
    jsize oldsize1 = env->GetArrayLength(matrix1);
    unsigned char *pData1 = (unsigned char *) olddata1;

    //矩阵2
    jbyte *olddata2 = (jbyte *) env->GetByteArrayElements(matrix2, 0);
    jsize oldsize2 = env->GetArrayLength(matrix2);
    unsigned char *pData2 = (unsigned char *) olddata2;

    // unsigned char pResult[row1 * col2];
    unsigned char *pResult = new unsigned char[row1 * col2];
    //gf_init(8, 0x00000187);
    //相乘
    unsigned char temp = 0;
    for (int i = 0; i < row1; ++i) {
        for (int j = 0; j < col2; ++j) {
            temp = 0;
            for (int k = 0; k < col1; ++k) {
                temp = gf_add(temp, gf_mul(pData1[i * col1 + k], pData2[k * col2 + j]));
            }
            pResult[i * col2 + j] = temp;
        }
    }
    //gf_uninit();
    //转化数组
    jsize myLen = row1 * col2;
    jbyteArray jarrResult = env->NewByteArray(myLen);
    jbyte *jbyte1 = (jbyte *) pResult;
    env->SetByteArrayRegion(jarrResult, 0, myLen, jbyte1);
    //释放空间
    delete[] pResult;
    env->ReleaseByteArrayElements(matrix1, olddata1, 0);
    env->ReleaseByteArrayElements(matrix2, olddata2, 0);

    //gs_jvm->DetachCurrentThread(); //使用完成后
    return jarrResult;
}

//矩阵求逆
//先是用到了求矩阵的秩，满秩则继续求逆，不满秩则返回NULL
JNIEXPORT jbyteArray JNICALL
Java_nc_NCUtils_InverseMatrix(JNIEnv *env, jobject thiz,
                              jbyteArray arrayData, jint nK) {

    jbyte *olddata = (jbyte *) env->GetByteArrayElements(arrayData, 0);
    jsize oldsize = env->GetArrayLength(arrayData);
    unsigned char *pData = (unsigned char *) olddata;
    //判断下秩 若是不满秩，则返回NULL
    jint rank = Java_nc_NCUtils_getRank(env, thiz, arrayData, nK, nK);
    if (rank != nK) {
        env->ReleaseByteArrayElements(arrayData, olddata, 0);
        return NULL;
    }
    int k = nK;
    int nCol = nK;
    //初始化有限域
    //gf_init(8, 0x00000187);
    //unsigned int M[k][k];
    unsigned int **M = new unsigned int *[k];
    for (int i = 0; i < k; ++i) {
        M[i] = new unsigned int[k];
    }
    // k = nCol = nRow;
    for (int i = 0; i < k; i++) {
        for (int j = 0; j < k; j++) {
            M[i][j] = pData[i * k + j];  // Copy the coefficient to M.
        }
    }

    //unsigned int IM[k][k];
    unsigned int **IM = new unsigned int *[k];
    for (int i = 0; i < k; ++i) {
        IM[i] = new unsigned int[k];
    }
    // Init
    for (int i = 0; i < k; i++) {
        for (int j = 0; j < k; j++) {
            if (i == j) {
                IM[i][j] = 1;
            }
            else {
                IM[i][j] = 0;
            }
        }
    }
    /************************************************************************/
    /* Step 1. Change to a lower triangle matrix.                           */
    /************************************************************************/
    for (int i = 0; i < nCol; i++) {
        for (int j = i + 1; j < nCol; j++) {
            // Now, the main element must be nonsingular.
            GFType temp = gf_div(M[j][i], M[i][i]);

            for (int z = 0; z < nCol; z++) {
                M[j][z] = gf_add(M[j][z], gf_mul(temp, M[i][z]));
                IM[j][z] = gf_add(IM[j][z], gf_mul(temp, IM[i][z]));
            }
        }
    }
    /************************************************************************/
    /* Step 2. Only the elements on the diagonal are non-zero.                  */
    /************************************************************************/
    for (int i = 1; i < nCol; i++) {
        for (int j = 0; j < i; j++) {
            GFType temp = gf_div(M[j][i], M[i][i]);
            for (int z = 0; z < nCol; z++) {
                M[j][z] = gf_add(M[j][z], gf_mul(temp, M[i][z]));
                IM[j][z] = gf_add(IM[j][z], gf_mul(temp, IM[i][z]));
            }
        }
    }
    /************************************************************************/
    /* Step 3. The elements on the diagonal are 1.                  */
    /************************************************************************/
    for (int i = 0; i < nCol; i++) {
        if (M[i][i] != 1) {
            GFType temp = M[i][i];
            for (int z = 0; z < nCol; z++) {
                M[i][z] = gf_div(M[i][z], temp);
                IM[i][z] = gf_div(IM[i][z], temp);
            }
        }
    }
/*
	LOGD("2Coeff, %d,  %d,  %d",IM[0][0],IM[0][1],IM[0][2]);
	LOGD("2Coeff, %d,  %d,  %d",IM[1][0],IM[1][1],IM[1][2]);
	LOGD("2Coeff, %d,  %d,  %d",IM[2][0],IM[2][1],IM[2][2]);
*/

    //unsigned char IMCopy[k * k];
    //这个误写成unsigned int就会导致求逆出错
    unsigned char *IMCopy = new unsigned char[k * k];
    for (int i = 0; i < k; i++) {
        for (int j = 0; j < k; j++) {
            IMCopy[i * k + j] = IM[i][j];
        }
    }
    //清空有限域
    //gf_uninit();

    jbyteArray jarrRV = env->NewByteArray(k * k);
    jsize myLen = k * k;
    jbyte *jby = (jbyte *) IMCopy;
    env->SetByteArrayRegion(jarrRV, 0, myLen, jby);

    env->ReleaseByteArrayElements(arrayData, olddata, 0);
    //释放数组
    for (int i = 0; i < k; ++i) {
        delete[] M[i];
        delete[] IM[i];
    }
    delete[] M;
    delete[] IM;
    delete[] IMCopy;
    return jarrRV;
}

//求秩
JNIEXPORT jint JNICALL
Java_nc_NCUtils_getRank(JNIEnv *env, jobject instance, jbyteArray matrix, jint nRow, jint nCol) {

    jbyte *olddata = (jbyte *) env->GetByteArrayElements(matrix, 0);
    jsize oldsize = env->GetArrayLength(matrix);
    unsigned char *pData = (unsigned char *) olddata;
    //初始化有限域
    //gf_init(8, 0x00000187);

    //
    //  unsigned int M[nRow][nCol];  这种写法会造成多线程时出错
    unsigned int **M = new unsigned int *[nRow];
    for (int i = 0; i < nRow; ++i) {
        M[i] = new unsigned int[nCol];
    }

    unsigned int test = 0;
    for (int i = 0; i < nRow; i++) {
        for (int j = 0; j < nCol; j++) {
            test = pData[i * nCol + j];
            M[i][j] = pData[i * nCol + j];
        }
    }

    // Define a variable to record the position of the main element.
    int yPos = 0;

    for (int i = 0; i < nRow; i++) {
        // Find the main element which must be non-zero.
        bool bFind = false;
        for (int x = yPos; x < nCol; x++) {
            for (int k = i; k < nRow; k++) {
                if (M[k][x] != 0) {
                    // Exchange the two vectors.
                    for (int x = 0; x < nCol; x++) {
                        jboolean nVal = M[i][x];
                        M[i][x] = M[k][x];
                        M[k][x] = nVal;
                    }                                        // We have exchanged the two vectors.
                    bFind = true;
                    break;
                }
            }
            if (bFind == true) {
                yPos = x;
                break;
            }
        }

        for (int j = i + 1; j < nRow; j++) {
            // Now, the main element must be nonsingular.
            unsigned int temp = gf_div(M[j][yPos], M[i][yPos]);
            for (int z = 0; z < nCol; z++) {
                M[j][z] = (jboolean) (gf_add(M[j][z], gf_mul(temp, M[i][z])));
            }
        }
        //
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
            unsigned int temp = gf_div(M[k][yPos], M[i][yPos]);
            for (int z = 0; z < nCol; z++) {
                M[k][z] = (jboolean) (gf_add(M[k][z], gf_mul(temp, M[i][z])));
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
    //清空内存
    //gf_uninit();
    //释放内存
    for (int i = 0; i < nRow; ++i) {
        delete[] M[i];
    }
    delete[] M;
    env->ReleaseByteArrayElements(matrix, olddata, 0);
    return nRank;
}


//初始化有限域
void gf_init(unsigned int m, unsigned int prim)// GF(2^m), primitive polymonial
{
    int i = 0, j = 0;

    if (m > 12)    // the field size is supported from GF(2^1) to GF(2^12).
        return;

    gFieldSize = 1 << m;

    if (0 == prim)
        prim = prim_poly[m];


    table_alpha = (GFType *) malloc(sizeof(GFType) * gFieldSize);
    table_index = (GFType *) malloc(sizeof(GFType) * gFieldSize);
    table_mul = (GFType **) malloc(sizeof(GFType *) * gFieldSize);
    table_div = (GFType **) malloc(sizeof(GFType *) * gFieldSize);
    for (i = 0; i < gFieldSize; i++) {
        table_mul[i] = (GFType *) malloc(sizeof(GFType) * gFieldSize);
        table_div[i] = (GFType *) malloc(sizeof(GFType) * gFieldSize);
    }


    table_alpha[0] = 1;
    table_index[0] = -1;

    for (i = 1; i < gFieldSize; i++) {
        table_alpha[i] = table_alpha[i - 1] << 1;
        if (table_alpha[i] >= gFieldSize) {
            table_alpha[i] ^= prim;
        }

        table_index[table_alpha[i]] = i;
    }

    table_index[1] = 0;

    // create the tables of mul and div
    for (i = 0; i < gFieldSize; i++)
        for (j = 0; j < gFieldSize; j++) {
            table_mul[i][j] = gfmul(i, j);
            table_div[i][j] = gfdiv(i, j);

        }
}

//释放有限域
void gf_uninit() {
    int i = 0;

    free(table_alpha);
    free(table_index);

    for (i = 0; i < gFieldSize; i++) {
        free(table_mul[i]);
        free(table_div[i]);
    }
    free(table_mul);
    free(table_div);
}

//乘法
GFType gfmul(GFType a, GFType b) {
    if (0 == a || 0 == b)
        return 0;

    return table_alpha[(table_index[a] + table_index[b]) % (gFieldSize - 1)];
}

//除法
GFType gfdiv(GFType a, GFType b) {
    if (0 == a || 0 == b)
        return 0;

    return table_alpha[(table_index[a] - table_index[b] + (gFieldSize - 1)) % (gFieldSize - 1)];
}

//指数
GFType gf_exp(GFType a, GFType n) {
    if (a == 0 && n == 0) {
        return 1;
    }
    if (a == 0 && n != 0) {
        return 0;
    }
    return table_alpha[table_index[a] * n % (gFieldSize - 1)];
}

