#include <jni.h>
#include <string>
#include "gf.c"

extern "C" {
//矩阵相乘
JNIEXPORT jobjectArray JNICALL
Java_nc_NCUtils_Multiply(JNIEnv *env, jobject instance,
                        jobjectArray matrix1, jobjectArray matrix2);

}
//在有限域上的矩阵乘法
JNIEXPORT jobjectArray JNICALL
Java_nc_NCUtils_Multiply(JNIEnv *env, jobject instance,
                        jobjectArray matrix1, jobjectArray matrix2) {
    //获取数组1
    jint row1 = env->GetArrayLength(matrix1);//获得行数
    jarray myarray = (jarray) env->GetObjectArrayElement(matrix1, 0);
    jint col1 = env->GetArrayLength(myarray);
    env->DeleteLocalRef(myarray);//释放内存，防止内存泄漏

    jbyte **buffer1 = new jbyte *[row1];
    for (int i = 0; i < row1; ++i) {
        buffer1[i] = new jbyte[col1];
        jbyteArray bytedata = (jbyteArray) env->GetObjectArrayElement(matrix1, i);
        buffer1[i] = env->GetByteArrayElements(bytedata, 0);
        env->DeleteLocalRef(bytedata);//释放内存，防止内存泄漏
    }
    //获取到数据
    //jbyte变为jboolean
    jboolean **Mat1 = (jboolean **) buffer1;

    //获取数组2
    jint row2 = env->GetArrayLength(matrix2);//获得行数
    myarray = (jarray) env->GetObjectArrayElement(matrix2, 0);
    jint col2 = env->GetArrayLength(myarray);
    env->DeleteLocalRef(myarray);//释放内存，防止内存泄漏

    jbyte **buffer2 = new jbyte *[row2];
    for (int i = 0; i < row2; ++i) {
        buffer2[i] = new jbyte[col2];
        jbyteArray bytedata = (jbyteArray) env->GetObjectArrayElement(matrix2, i);
        buffer2[i] = env->GetByteArrayElements(bytedata, 0);
        env->DeleteLocalRef(bytedata);//释放内存，防止内存泄漏
    }
    //获取到数据
    //jbyte变为jboolean
    jboolean **Mat2 = (jboolean **) buffer2;

    if (col1 != row2) {
        return NULL;
    }
    //进行相乘
    gf_init(8, 0x00000187);                //初始化域
    //存储结果
    jboolean **result = new jboolean *[row1];
    for (int i = 0; i < row1; i++) {
        result[i] = new jboolean[col2];
    }
    unsigned int temp;
    for (int i = 0; i < row1; i++) {            //两矩阵相乘
        for (int j = 0; j < col2; j++) {
            temp = 0;
            for (int k = 0; k < col1; k++) {
                temp = gf_add(temp, gf_mul(Mat1[i][k], Mat2[k][j]));
            }
            result[i][j] = (jboolean) temp;
        }
    }
    gf_uninit();

    //jboolean变为jbyte
    jbyte **bt_result = (jbyte **) result;
    jobjectArray resultArray = env->NewObjectArray(row1, env->FindClass("[B"), NULL);
    for (int i = 0; i < row1; i++) {
        jbyteArray byteArray = env->NewByteArray(col2);
        env->SetByteArrayRegion(byteArray, 0, col2, bt_result[i]);
        env->SetObjectArrayElement(resultArray, i, byteArray);
        env->DeleteLocalRef(byteArray);
    }

    //释放数组
    for (int i = 0; i < row1; ++i) {
        delete[] buffer1[i];
        delete[] result[i];
    }
    delete[] buffer1;
    delete[] result;
    for (int i = 0; i < row2; ++i) {
        delete[] buffer2[i];
    }
    delete[] buffer2;

    return resultArray;
}