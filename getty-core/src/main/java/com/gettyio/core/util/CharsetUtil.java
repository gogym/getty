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


import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Charset工具类
 *
 * @author gogym.ggj
 * @date 2023/6/9
 */
public final class CharsetUtil {

    private CharsetUtil() {
    }

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is identified by
     * an optional byte-order mark
     */
    public static final Charset UTF_16 = StandardCharsets.UTF_16;

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is big-endian
     */
    public static final Charset UTF_16BE = StandardCharsets.UTF_16BE;

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is little-endian
     */
    public static final Charset UTF_16LE = StandardCharsets.UTF_16LE;

    /**
     * 8-bit UTF (UCS Transformation Format)
     */
    public static final Charset UTF_8 = StandardCharsets.UTF_8;

    /**
     * ISO Latin Alphabet No. 1, as known as <tt>ISO-LATIN-1</tt>
     */
    public static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

    /**
     * 7-bit ASCII, as known as ISO646-US or the Basic Latin block of the
     * Unicode character set
     */
    public static final Charset US_ASCII = StandardCharsets.US_ASCII;

    private static final Charset[] CHARSETS = new Charset[]{UTF_16, UTF_16BE, UTF_16LE, UTF_8, ISO_8859_1, US_ASCII};

    public static Charset[] values() {
        return CHARSETS;
    }


}
