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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZip 压缩/解压工具类。
 * <p>
 * 使用 try-with-resources 确保流资源正确关闭，
 * 缓冲区大小为 4KB 以获得更好的 I/O 性能。
 * </p>
 *
 * @author gogym
 * @date 2020/4/9
 */
public final class GZipUtil {

    /** 解压缓冲区大小（4KB），平衡内存占用与 I/O 吞吐 */
    private static final int BUFFER_SIZE = 4096;

    private GZipUtil() {
    }

    /**
     * GZip 压缩
     *
     * @param data 原始字节数据
     * @return 压缩后的字节数据，输入为 null 或空时返回 null
     * @throws IOException 压缩过程异常
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    /**
     * GZip 解压
     *
     * @param data 压缩的字节数据
     * @return 解压后的字节数据，输入为 null 或空时原样返回
     * @throws IOException 解压过程异常
     */
    public static byte[] uncompress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 2);
        try (ByteArrayInputStream in = new ByteArrayInputStream(data);
             GZIPInputStream gunzip = new GZIPInputStream(in)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = gunzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        }
        return out.toByteArray();
    }
}
