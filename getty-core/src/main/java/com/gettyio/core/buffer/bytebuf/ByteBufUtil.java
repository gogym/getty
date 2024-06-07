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
package com.gettyio.core.buffer.bytebuf;


import java.nio.ByteOrder;

/**
 * 与处理{@link ByteBuf}相关的实用程序方法集合。
 */
public final class ByteBufUtil {


    private ByteBufUtil() {
    }

    /**
     * 生成给定 ByteBuf 对象的哈希码。
     * 该方法考虑了 ByteBuf 的字节序（BIG_ENDIAN 或 LITTLE_ENDIAN），以及其可读字节的长度，通过一定的计算策略计算出哈希码。
     *
     * @param buffer 输入的 ByteBuf 对象，不可为 null。
     * @return 计算得到的哈希码，保证不为 null。
     */
    public static int hashCode(ByteBuf buffer) {
        // 计算可读字节的长度，并换算成整数和字节的处理数量
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2; // 整数数量
        final int byteCount = aLen & 3; // 余下的字节数量

        // 初始化哈希码
        int hashCode = 1;
        int arrayIndex = buffer.readerIndex(); // 设置数组索引为当前读取索引

        // 处理整数部分
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            // 如果是大端字节序，直接获取并累加整数的哈希码
            for (int i = intCount; i > 0; i--) {
                hashCode = 31 * hashCode + buffer.getInt(arrayIndex);
                arrayIndex += 4;
            }
        } else {
            // 如果是小端字节序，需要反转整数的字节顺序后再累加哈希码
            for (int i = intCount; i > 0; i--) {
                hashCode = 31 * hashCode + swapInt(buffer.getInt(arrayIndex));
                arrayIndex += 4;
            }
        }

        // 处理剩余的字节部分
        for (int i = byteCount; i > 0; i--) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex++);
        }

        // 如果最终的哈希码为 0，则将其设置为 1，避免哈希冲突
        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }


    /**
     * 切换指定的32位整数的字节顺序。
     * 该方法将输入的整数的字节顺序进行反转，适用于需要在不同字节序环境下进行数据交换的场景。
     *
     * @param value 需要进行字节顺序交换的32位整数。
     * @return 完成字节顺序交换后的32位整数。
     */
    public static int swapInt(int value) {
        // 使用Java提供的Integer类的reverseBytes方法直接完成字节顺序的反转
        return Integer.reverseBytes(value);
    }


}
