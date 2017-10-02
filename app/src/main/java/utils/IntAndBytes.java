package utils;

import java.lang.reflect.Array;

/**
 * Created by Administrator on 2017/5/6 0006.
 * 这是一个int和byte[]数组相互转化的工具
 */

public class IntAndBytes {
    //int转byte[]
    public static byte[] int2byte(int res) {
        byte[] targets = new byte[4];

        targets[0] = (byte) (res & 0xff);// 最低位
        targets[1] = (byte) ((res >> 8) & 0xff);// 次低位
        targets[2] = (byte) ((res >> 16) & 0xff);// 次高位
        targets[3] = (byte) (res >>> 24);// 最高位,无符号右移。
        return targets;
    }

    //byte[]转int
    public static int byte2int(byte[] res) {
        // 一个byte数据左移24位变成0x??000000，再右移8位变成0x00??0000
        int targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00) // | 表示安位或
                | ((res[2] << 24) >>> 8) | (res[3] << 24);
        return targets;
    }

    //java byte范围 -128到127，若是存的值为负返回int型正数
    public static int negByte2int(byte bt) {
        if (bt < 0) {
            int abs = Math.abs(bt);
            return 256 - abs;
        } else {
            return bt;
        }
    }


    //java中byte的范围为-128到127
    //把int数组转化为byte，其中int数组每个元素在-128到255，否则转化失败
    public static byte[][] intArray2byteArray(int[][] matrix) {
        int row = matrix.length;
        int col = matrix[0].length;
        byte[][] bt_matrix = new byte[row][col];
        for (int i = 0; i < row; ++i) {
            for (int j = 0; j < col; ++j) {
                int element = matrix[i][j];
                if (element < -128 || element > 255) {
                    //转化失败
                    return null;
                }
                //超过127的int会转化为负值
                bt_matrix[i][j] = (byte) element;
            }
        }
        return bt_matrix;
    }

    //把二维的int数组转化为一维的byte[]数组
    public static byte[] int2Array_byte1Array(int[][] matrix) {
        int row = matrix.length;
        int col = matrix[0].length;
        byte[] bt_matrix = new byte[row * col];
        for (int i = 0; i < row; ++i) {
            for (int j = 0; j < col; ++j) {
                int element = matrix[i][j];
                if (element < -128 || element > 255) {
                    //转化失败
                    return null;
                }
                //超过127的int会转化为负值
                bt_matrix[i * col + j] = (byte) element;
            }
        }
        return bt_matrix;
    }

    //把二维的byte数组转化为一维的byte[]数组
    public static byte[] byte2Array_byte1Array(byte[][] origin) {
        int row = origin.length;
        int col = origin[0].length;
        byte[] result = new byte[row * col];
        for (int i = 0; i < row; ++i) {
            for (int j = 0; j < col; ++j) {
                result[i * col + j] = origin[i][j];
            }
        }
        return result;
    }

    // byte数组转化为int数组
    public static int[][] byteArray2IntArray(byte[][] b) {
        int row = b.length;
        int col = b[0].length;
        int[][] result = new int[row][col];
        for (int i = 0; i < row; ++i) {
            for (int j = 0; j < col; ++j) {
                int temp = b[i][j];
                if (temp < 0) {
                    result[i][j] = 256 - Math.abs(temp);
                } else {
                    result[i][j] = temp;
                }
            }
        }
        return result;
    }


    /**
     * 把指令和待发送的长度封入byte[5]数组
     *
     * @param instruction
     * @param len
     * @return
     */
    public static byte[] send_instruction_len(int instruction, int len) {
        byte[] bt_len = IntAndBytes.int2byte(len);
        byte[] bt_send = new byte[5];
        bt_send[0] = (byte) instruction;
        for (int i = 1; i < 5; ++i) {
            bt_send[i] = bt_len[i - 1];
        }
        return bt_send;
    }

    /**
     * 自增后原数据保存在新数组中
     *
     * @param o        需要自增的对象
     * @param increase 自增几个单位长度
     * @return
     */
    public static Object arrayGrow(Object o, int increase) {
        Class cl = o.getClass();
        if (!cl.isArray()) return null;
        Class componentType = cl.getComponentType();
        int length = Array.getLength(o);
        int newLength = length + increase;
        Object newArray = Array.newInstance(componentType, newLength);
        System.arraycopy(o, 0, newArray, 0, length);
        return newArray;
    }

}
