package utils;

/**
 * Created by kingstones on 2017/10/20.
 */

public class StringUtil {
    /**
     * s1>s2,返回true，否则返回false
     *
     * @param s1
     * @param s2
     * @return
     */
    public static boolean strCompare(String s1, String s2) {
//        String t1 = "20131011";
//        String t2 = "20131030";
        int result = s1.compareTo(s2);
        return (result > 0);
    }
}
