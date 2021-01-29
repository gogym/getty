/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.core.util;


import java.util.Formatter;

/**
 * String utility class.
 */
public final class StringUtil {

    public static final String NEWLINE;
    public static final String EMPTY_STRING = "";
    private static final String[] BYTE2HEX_PAD = new String[256];
    private static final String[] BYTE2HEX_NOPAD = new String[256];

    private static final char PACKAGE_SEPARATOR_CHAR = '.';


    static {
        // Determine the newline character of the current platform.
        String newLine;

        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            // Should not reach here, but just in case.
            newLine = "\n";
        }

        NEWLINE = newLine;

        // Generate the lookup table that converts a byte into a 2-digit hexadecimal integer.
        int i;
        for (i = 0; i < 10; i++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append('0');
            buf.append(i);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(i);
        }
        for (; i < 16; i++) {
            StringBuilder buf = new StringBuilder(2);
            char c = (char) ('a' + i - 10);
            buf.append('0');
            buf.append(c);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(c);
        }
        for (; i < BYTE2HEX_PAD.length; i++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append(Integer.toHexString(i));
            String str = buf.toString();
            BYTE2HEX_PAD[i] = str;
            BYTE2HEX_NOPAD[i] = str;
        }
    }


    /**
     * The shortcut to {@link #simpleClassName(Class) simpleClassName(o.getClass())}.
     *
     * @param o obj
     * @return String
     */
    public static String simpleClassName(Object o) {
        if (o == null) {
            return "null_object";
        } else {
            return simpleClassName(o.getClass());
        }
    }

    /**
     * Generates a simplified name from a {@link Class}.  Similar to {@link Class#getSimpleName()}, but it works fine
     * with anonymous classes.
     *
     * @param clazz class
     * @return String
     */
    public static String simpleClassName(Class<?> clazz) {
        String className = ObjectUtil.checkNotNull(clazz, "clazz").getName();
        final int lastDotIdx = className.lastIndexOf(PACKAGE_SEPARATOR_CHAR);
        if (lastDotIdx > -1) {
            return className.substring(lastDotIdx + 1);
        }
        return className;
    }


    public static boolean isEmpty(String str) {

        if (null == str || str.length() == 0) {
            return true;
        }
        return false;
    }
}
