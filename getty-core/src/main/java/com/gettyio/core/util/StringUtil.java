package com.gettyio.core.util;


import java.util.Formatter;


public final class StringUtil {

    public static final String NEWLINE;
    public static final String EMPTY_STRING = "";

    static {
        //确定当前平台的换行符。
        String newLine;
        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            newLine = "\n";
        }
        NEWLINE = newLine;
    }


    public static String simpleClassName(Object o) {
        if (o == null) {
            return "null_object";
        } else {
            return simpleClassName(o.getClass());
        }
    }

    public static String simpleClassName(Class<?> clazz) {
        if (clazz == null) {
            return "null_class";
        }

        Package pkg = clazz.getPackage();
        if (pkg != null) {
            return clazz.getName().substring(pkg.getName().length() + 1);
        } else {
            return clazz.getName();
        }
    }


    public static boolean isEmpty(String str) {

        if (null == str || str.length() == 0) {
            return true;
        }
        return false;
    }
}
