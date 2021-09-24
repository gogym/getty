/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class PlatformDependent {

    private final static Logger logger = LoggerFactory.getLogger(PlatformDependent.class);

    private static final Pattern MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN = Pattern.compile("\\s*-XX:MaxDirectMemorySize\\s*=\\s*([0-9]+)\\s*([kKmMgG]?)\\s*$");

    private static final boolean IS_ANDROID = isAndroid0();

    private static final boolean HAS_UNSAFE = hasUnsafe0();
    private static final boolean DIRECT_BUFFER_PREFERRED = HAS_UNSAFE;
    private static final long MAX_DIRECT_MEMORY = maxDirectMemory0();

    private static final long ARRAY_BASE_OFFSET = arrayBaseOffset0();


    static {
        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.noPreferDirect: {}", !DIRECT_BUFFER_PREFERRED);
        }

        if (!hasUnsafe() && !isAndroid()) {
            logger.info(
                    "Your platform does not provide complete low-level API for accessing direct buffers reliably. " +
                            "Unless explicitly requested, heap buffer will always be preferred to avoid potential system " +
                            "unstability.");
        }
    }

    private PlatformDependent() {
        //只支持静态方法
    }


    /**
     * Return {@code true} 如果JVM在Windows上运行
     */
    public static boolean isWindows() {
        boolean windows = SystemPropertyUtil.get("os.name", "").toLowerCase(Locale.US).contains("win");
        if (windows) {
            logger.debug("Platform: Windows");
        }
        return windows;
    }

    /**
     * 当且仅当当前平台是Android时返回{@code true}
     */
    public static boolean isAndroid() {
        return IS_ANDROID;
    }


    /**
     * Return {@code true} 如果找到 {@code sun.misc.Unsafe} 可用于加速直接内存访问。
     */
    public static boolean hasUnsafe() {
        return HAS_UNSAFE;
    }

    /**
     * 如果平台有可靠的底层直接缓存访问API，并且用户指定了{@code-preferdirect}选项，则返回{@code true}。
     */
    public static boolean directBufferPreferred() {
        return DIRECT_BUFFER_PREFERRED;
    }

    /**
     * 返回为直接分配缓冲区预留的最大内存。
     */
    public static long maxDirectMemory() {
        return MAX_DIRECT_MEMORY;
    }


    /**
     * 尝试释放指定的直接{@link ByteBuffer}。
     * 请注意，如果当前平台不支持此操作，或者指定的缓冲区不是直接缓冲区。
     */
    public static void freeDirectBuffer(ByteBuffer buffer) {
        if (hasUnsafe() && !isAndroid()) {
            PlatformDependent0.freeDirectBuffer(buffer);
        }
    }

    public static long directBufferAddress(ByteBuffer buffer) {
        return PlatformDependent0.directBufferAddress(buffer);
    }

    public static int getInt(Object object, long fieldOffset) {
        return PlatformDependent0.getInt(object, fieldOffset);
    }

    public static byte getByte(long address) {
        return PlatformDependent0.getByte(address);
    }

    public static short getShort(long address) {
        return PlatformDependent0.getShort(address);
    }

    public static int getInt(long address) {
        return PlatformDependent0.getInt(address);
    }

    public static long getLong(long address) {
        return PlatformDependent0.getLong(address);
    }


    public static void putByte(long address, byte value) {
        PlatformDependent0.putByte(address, value);
    }

    public static void putShort(long address, short value) {
        PlatformDependent0.putShort(address, value);
    }

    public static void putInt(long address, int value) {
        PlatformDependent0.putInt(address, value);
    }

    public static void putLong(long address, long value) {
        PlatformDependent0.putLong(address, value);
    }

    public static void copyMemory(long srcAddr, long dstAddr, long length) {
        PlatformDependent0.copyMemory(srcAddr, dstAddr, length);
    }

    public static void copyMemory(byte[] src, int srcIndex, long dstAddr, long length) {
        PlatformDependent0.copyMemory(src, ARRAY_BASE_OFFSET + srcIndex, null, dstAddr, length);
    }

    public static void copyMemory(long srcAddr, byte[] dst, int dstIndex, long length) {
        PlatformDependent0.copyMemory(null, srcAddr, dst, ARRAY_BASE_OFFSET + dstIndex, length);
    }


    /**
     * 返回系统 {@link ClassLoader}.
     */
    public static ClassLoader getSystemClassLoader() {
        return PlatformDependent0.getSystemClassLoader();
    }

    /**
     * 是否是安卓
     *
     * @return
     */
    private static boolean isAndroid0() {
        boolean android;
        try {
            Class.forName("android.app.Application", false, getSystemClassLoader());
            android = true;
        } catch (Exception e) {
            // 无法加载Android中唯一可用的类。
            android = false;
        }

        if (android) {
            logger.debug("Platform: Android");
        }
        return android;
    }

    /**
     * 是否是不安全的
     *
     * @return
     */
    private static boolean hasUnsafe0() {
        if (isAndroid()) {
            logger.debug("sun.misc.Unsafe: unavailable (Android)");
            return false;
        }

        try {
            boolean hasUnsafe = PlatformDependent0.hasUnsafe();
            logger.debug("sun.misc.Unsafe: {}", hasUnsafe ? "available" : "unavailable");
            return hasUnsafe;
        } catch (Throwable t) {
            // 可能初始化PlatformDependent0失败。
            return false;
        }
    }

    private static long arrayBaseOffset0() {
        if (!hasUnsafe()) {
            return -1;
        }

        return PlatformDependent0.arrayBaseOffset();
    }

    private static long maxDirectMemory0() {
        long maxDirectMemory = 0;
        try {
            // Try to get from sun.misc.VM.maxDirectMemory() which should be most accurate.
            Class<?> vmClass = Class.forName("sun.misc.VM", true, getSystemClassLoader());
            Method m = vmClass.getDeclaredMethod("maxDirectMemory");
            maxDirectMemory = ((Number) m.invoke(null)).longValue();
        } catch (Throwable t) {
            // Ignore
        }

        if (maxDirectMemory > 0) {
            return maxDirectMemory;
        }

        try {
            // Now try to get the JVM option (-XX:MaxDirectMemorySize) and parse it.
            // Note that we are using reflection because Android doesn't have these classes.
            Class<?> mgmtFactoryClass = Class.forName(
                    "java.lang.management.ManagementFactory", true, getSystemClassLoader());
            Class<?> runtimeClass = Class.forName(
                    "java.lang.management.RuntimeMXBean", true, getSystemClassLoader());

            Object runtime = mgmtFactoryClass.getDeclaredMethod("getRuntimeMXBean").invoke(null);

            @SuppressWarnings("unchecked")
            List<String> vmArgs = (List<String>) runtimeClass.getDeclaredMethod("getInputArguments").invoke(runtime);
            for (int i = vmArgs.size() - 1; i >= 0; i--) {
                Matcher m = MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN.matcher(vmArgs.get(i));
                if (!m.matches()) {
                    continue;
                }

                maxDirectMemory = Long.parseLong(m.group(1));
                switch (m.group(2).charAt(0)) {
                    case 'k':
                    case 'K':
                        maxDirectMemory *= 1024;
                        break;
                    case 'm':
                    case 'M':
                        maxDirectMemory *= 1024 * 1024;
                        break;
                    case 'g':
                    case 'G':
                        maxDirectMemory *= 1024 * 1024 * 1024;
                        break;
                }
                break;
            }
        } catch (Throwable t) {
            // Ignore
        }

        if (maxDirectMemory <= 0) {
            maxDirectMemory = Runtime.getRuntime().maxMemory();
            logger.debug("maxDirectMemory: {} bytes (maybe)", maxDirectMemory);
        } else {
            logger.debug("maxDirectMemory: {} bytes", maxDirectMemory);
        }

        return maxDirectMemory;
    }


}
