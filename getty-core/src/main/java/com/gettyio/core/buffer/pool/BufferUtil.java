package com.gettyio.core.buffer.pool;

import java.io.*;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * BufferUtil 类提供了一组静态方法，用于操作和管理缓冲区。
 * 该类的设计目的是提高缓冲区的使用效率和安全性。
 */
public class BufferUtil {
    private static final String TAG = BufferUtil.class.getSimpleName();

    /**
     * 定义临时缓冲区的大小，用于各种操作中的临时数据存储
     */
    static final int TEMP_BUFFER_SIZE = 4096;
    /**
     * 定义空格字符的ASCII码
     */
    static final byte SPACE = 0x20;
    /**
     * 定义减号字符的ASCII码
     */
    static final byte MINUS = '-';
    /**
     * 定义一个包含数字和字母的字节数组，用于转换数字和十六进制字符
     */
    static final byte[] DIGIT =
            {
                    (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7', (byte) '8', (byte) '9',
                    (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D',
                    (byte) 'E', (byte) 'F'
            };

    /**
     * 定义一个空的ByteBuffer，用于表示没有数据的情况
     */
    public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.wrap(new byte[0]);

    /**
     * 以刷新模式分配ByteBuffer。
     * 分配的ByteBuffer的位置（position）和限制（limit）都将被设置为零，这表示缓冲区为空，在写入任何数据之前需要进行翻转操作。
     *
     * @param capacity 分配的ByteBuffer的容量
     * @return ByteBuffer对象
     */
    public static ByteBuffer allocate(int capacity) {
        // 分配具有指定容量的ByteBuffer
        ByteBuffer buf = ByteBuffer.allocate(capacity);
        // 将限制设置为0，标记缓冲区当前为空
        buf.limit(0);
        return buf;
    }


    /**
     * 以直接分配模式分配ByteBuffer。
     * 分配的ByteBuffer的位置（position）和限制（limit）都将被设置为零，表示缓冲区为空且处于直接分配模式。
     * 直接分配的ByteBuffer通常用于与本机代码（如JNI）交互。
     *
     * @param capacity 分配的ByteBuffer的容量
     * @return ByteBuffer对象
     */
    public static ByteBuffer allocateDirect(int capacity) {
        // 直接分配指定容量的ByteBuffer
        ByteBuffer buf = ByteBuffer.allocateDirect(capacity);
        // 将限制设置为0，表示缓冲区为空
        buf.limit(0);
        return buf;
    }


    /**
     * 根据指定的容量和类型分配ByteBuffer。
     * 如果指定为直接缓冲区，则分配一个直接ByteBuffer；否则，分配一个非直接ByteBuffer。
     * 分配的ByteBuffer的位置和限制都将被设置为零，这表示缓冲区为空，且在写入任何数据之前需要进行翻转操作。
     *
     * @param capacity 分配的ByteBuffer的容量
     * @param direct   指示是否分配一个直接ByteBuffer的布尔值；true表示直接ByteBuffer，false表示非直接ByteBuffer
     * @return 新分配的ByteBuffer对象
     */
    public static ByteBuffer allocate(int capacity, boolean direct) {
        // 根据direct参数决定是分配直接ByteBuffer还是非直接ByteBuffer
        return direct ? allocateDirect(capacity) : allocate(capacity);
    }


    /**
     * 深拷贝一个ByteBuffer缓冲区
     *
     * @param buffer 需要被拷贝的ByteBuffer对象
     * @return 拷贝后的ByteBuffer对象；如果输入的buffer为null，则返回null
     */
    public static ByteBuffer copy(ByteBuffer buffer) {
        // 检查输入的buffer是否为null
        if (buffer == null) {
            return null;
        }

        // 记录当前buffer的位置
        int p = buffer.position();

        // 根据原buffer的类型（直接缓冲区或非直接缓冲区）创建一个新的buffer进行深拷贝
        ByteBuffer clone = buffer.isDirect() ? ByteBuffer.allocateDirect(buffer.remaining()) : ByteBuffer.allocate(buffer.remaining());

        // 将原buffer的内容拷贝到新buffer中
        clone.put(buffer);

        // 重置新buffer的位置，以便于使用
        clone.flip();

        // 恢复原buffer的位置
        buffer.position(p);

        return clone;
    }


    /**
     * 重置ByteBuffer的字节序为大端字节序，并将其设置为空缓冲区模式。
     * 该方法将缓冲区的位置和限制都设置为0。
     *
     * @param buffer 需要重置的ByteBuffer对象。
     */
    public static void reset(ByteBuffer buffer) {
        // 检查buffer是否为null
        if (buffer != null) {
            // 重置字节序为大端字节序
            buffer.order(ByteOrder.BIG_ENDIAN);
            // 将位置和限制都设置为0，实现缓冲区的清空和重置
            buffer.position(0);
            buffer.limit(0);
        }
    }


    /**
     * 将ByteBuffer缓冲区清空，设置其位置和限制为0。
     * 这种方式等效于将缓冲区重置为刚创建时的状态。
     *
     * @param buffer 需要清空的ByteBuffer对象。
     */
    public static void clear(ByteBuffer buffer) {
        // 检查buffer是否为null
        if (buffer != null) {
            // 将位置和限制设置为0，实现缓冲区的清空
            buffer.position(0);
            buffer.limit(0);
        }
    }


    /**
     * 将ByteBuffer缓冲区清空，切换到填充模式。
     * 位置被设置为0，限制被设置为容量。
     * 这种方式使得缓冲区准备好接受新的数据。
     *
     * @param buffer 需要清空并切换到填充模式的ByteBuffer对象。
     */
    public static void clearToFill(ByteBuffer buffer) {
        // 检查buffer是否为null
        if (buffer != null) {
            // 重置位置，设置限制为容量，准备填充缓冲区
            buffer.position(0);
            buffer.limit(buffer.capacity());
        }
    }


    /**
     * 将缓冲区切换到填充模式。
     * 该方法将位置设置为缓冲区中未使用位置的第一个位置（即旧的限制），将限制设置为容量。
     * 如果缓冲区为空，则此调用实际上等同于 {@link #clearToFill(ByteBuffer)}。
     * 如果没有未使用的空间来填充，则尝试通过 {@link ByteBuffer#compact()} 来创建空间。
     * <p>
     * 此方法用作 {@link ByteBuffer#compact()} 的替代。
     *
     * @param buffer 需要切换到填充模式的缓冲区
     * @return 在切换位置之前有效数据的位置。该值应该传递给后续的 {@link #flipToFlush(ByteBuffer, int)} 调用中。
     */
    public static int flipToFill(ByteBuffer buffer) {
        // 获取当前位置
        int position = buffer.position();
        // 获取当前限制
        int limit = buffer.limit();
        if (position == limit) {
            // 如果当前位置等于限制，即缓冲区为空，则重置位置和限制为容量
            buffer.position(0);
            buffer.limit(buffer.capacity());
            return 0;
        }

        // 获取缓冲区容量
        int capacity = buffer.capacity();
        if (limit == capacity) {
            // 如果限制等于容量，即没有未使用的空间，则进行压缩操作
            buffer.compact();
            return 0;
        }

        // 设置新的位置和限制，以准备填充缓冲区
        buffer.position(limit);
        buffer.limit(capacity);
        return position;
    }


    /**
     * 将缓冲区切换到刷新模式。
     * 限制被设置为第一个未使用的字节（旧的位置），并且位置被设置为传入的位置。
     * <p>
     * 此方法用作{@link Buffer#flip()}的替代。
     *
     * @param buffer   要被切换的缓冲区
     * @param position 切换到的有效数据位置。这应该是之前调用{@link #flipToFill(ByteBuffer)}返回的值。
     */
    public static void flipToFlush(ByteBuffer buffer, int position) {
        // 将限制设置为当前位置，准备读取数据
        buffer.limit(buffer.position());
        // 将位置设置为传入的位置，确定有效数据的开始点
        buffer.position(position);
    }


    /**
     * 将给定的ByteBuffer对象翻转到冲洗模式，准备进行数据的读取或处理。
     * 这是通过调用另一个重载方法flipToFlush(ByteBuffer buffer, int position)实现的，
     * 其中将位置参数设置为0，即将缓冲区的有效数据起始位置重置为开始。
     *
     * @param buffer 需要进行翻转操作的ByteBuffer对象。
     *               注意：此方法不接受任何返回值，因为它是对传入的ByteBuffer对象进行就地修改。
     */
    public static void flipToFlush(ByteBuffer buffer) {
        flipToFlush(buffer, 0); // 使用0作为位置参数，将缓冲区的有效数据起始位置重置为开始处
    }

    /**
     * 将一个整数以小端格式存入ByteBuffer中。
     *
     * @param buffer 存放数据的ByteBuffer对象。
     * @param value  需要存入的整数值。
     */
    public static void putIntLittleEndian(ByteBuffer buffer, int value) {
        // 先将buffer切换到填充模式，以便于写入数据
        int p = flipToFill(buffer);
        // 逐字节存入整数的各个位，采用小端格式

        // 存放最低8位
        buffer.put((byte) (value & 0xFF));
        // 存放次低8位
        buffer.put((byte) ((value >>> 8) & 0xFF));
        // 存放次高8位
        buffer.put((byte) ((value >>> 16) & 0xFF));
        // 存放最高8位
        buffer.put((byte) ((value >>> 24) & 0xFF));
        // 数据写入完成后，切换buffer回刷新模式
        flipToFlush(buffer, p);
    }


    /**
     * 将ByteBuffer转换为byte数组。
     *
     * @param buffer 要转换的ByteBuffer，转换时处于flush模式。该buffer不会被修改。
     * @return 从buffer复制的byte数组。
     */
    public static byte[] toArray(ByteBuffer buffer) {
        // 如果buffer支持直接访问其内部数组
        if (buffer.hasArray()) {
            // 获取buffer的内部数组
            byte[] array = buffer.array();
            // 计算起始复制位置
            int from = buffer.arrayOffset() + buffer.position();
            // 从起始位置复制buffer中剩余的部分到新数组并返回
            return Arrays.copyOfRange(array, from, from + buffer.remaining());
        } else {
            // 如果buffer不支持直接访问其内部数组，则创建一个新数组并逐个元素复制
            // 根据buffer剩余容量创建新数组
            byte[] to = new byte[buffer.remaining()];
            // 从buffer的切片中获取数据到新数组to
            buffer.slice().get(to);
            // 返回复制后的数组
            return to;
        }
    }

    /**
     * 检查传入的ByteBuffer是否等于预定义的EMPTY_BUFFER。
     *
     * @param buf 需要检查的ByteBuffer对象。
     * @return 如果传入的ByteBuffer对象等于EMPTY_BUFFER，则返回true；否则返回false。
     */
    public static boolean isTheEmptyBuffer(ByteBuffer buf) {
        // 由于EMPTY_BUFFER是静态不变的对象，这里直接使用引用比较
        @SuppressWarnings("ReferenceEquality")
        boolean isTheEmptyBuffer = (buf == EMPTY_BUFFER);
        return isTheEmptyBuffer;
    }


    /**
     * 检查ByteBuffer是否为空或null。
     *
     * @param buf 需要检查的ByteBuffer对象。
     * @return 如果ByteBuffer为null或没有剩余元素，则返回true；否则返回false。
     */
    public static boolean isEmpty(ByteBuffer buf) {
        // 检查ByteBuffer是否为null或没有剩余元素
        return buf == null || buf.remaining() == 0;
    }


    /**
     * 检查ByteBuffer数组是否为空或包含空的ByteBuffer。
     *
     * @param buf 需要检查的ByteBuffer数组。
     * @return 如果ByteBuffer数组为null、长度为0或所有元素均为空或没有剩余元素，则返回true；否则返回false。
     */
    public static boolean isEmpty(ByteBuffer[] buf) {
        // 检查数组是否为null或长度为0
        if (buf == null || buf.length == 0) {
            return true;
        }
        // 遍历数组，检查每个ByteBuffer对象是否非空且有剩余元素
        for (ByteBuffer b : buf) {
            if (b != null && b.hasRemaining()) {
                return false;
            }
        }
        return true;
    }


    /**
     * 获取一个或多个ByteBuffer中剩余字节的总数。
     *
     * @param buf 要检查的ByteBuffer数组。
     * @return 所有ByteBuffer中剩余字节的总数。
     */
    public static long remaining(ByteBuffer... buf) {
        // 初始化剩余字节总数为0
        long remaining = 0;
        // 检查传入的ByteBuffer数组是否为null
        if (buf != null) {
            // 遍历ByteBuffer数组
            for (ByteBuffer b : buf) {
                // 检查每个ByteBuffer对象是否为null
                if (b != null) {
                    // 累加每个非null ByteBuffer对象的剩余字节数
                    remaining += b.remaining();
                }
            }
        }
        // 返回累计的剩余字节总数
        return remaining;
    }


    /**
     * 检查ByteBuffer是否非空且含有内容。
     *
     * @param buf 待检查的ByteBuffer对象
     * @return 如果ByteBuffer非空且剩余字节数大于0，则返回true。
     */
    public static boolean hasContent(ByteBuffer buf) {
        // 检查ByteBuffer是否既非null且含有内容
        return buf != null && buf.remaining() > 0;
    }


    /**
     * 检查ByteBuffer是否已满。
     *
     * @param buf 待检查的ByteBuffer对象
     * @return 如果ByteBuffer非空且其限制等于其容量，则返回true。
     */
    public static boolean isFull(ByteBuffer buf) {
        // 检查ByteBuffer是否已满
        return buf != null && buf.limit() == buf.capacity();
    }


    /**
     * 从经过空检查的缓冲区获取剩余字节
     *
     * @param buffer 从中获取剩余字节的缓冲区，处于刷新模式。
     * @return 如果缓冲区为null，则返回0；否则返回缓冲区中剩余的字节数。
     */
    public static int length(ByteBuffer buffer) {
        // 判断缓冲区是否为空，返回相应的剩余字节数
        return buffer == null ? 0 : buffer.remaining();
    }


    /**
     * 获取从限制位置到容量之间的空间大小
     *
     * @param buffer 从中获取空间的缓冲区
     * @return 从限制到容量的空间大小
     */
    public static int space(ByteBuffer buffer) {
        // 如果缓冲区为null，则返回0
        if (buffer == null) {
            return 0;
        }
        // 返回缓冲区的容量减去限制的大小
        return buffer.capacity() - buffer.limit();
    }


    /**
     * 缩小缓冲区
     * <p>
     * 此方法用于将缓冲区中的有效数据向前移动，从而压缩缓冲区，释放后面的空间。
     * 如果操作后缓冲区仍未完全腾空，则返回false；如果操作前缓冲区已满，操作后缓冲区变得未满，则返回true。
     *
     * @param buffer 需要被压缩的缓冲区
     * @return 如果压缩操作前缓冲区是满的，并且操作后缓冲区变得未满，则返回true；否则返回false。
     */
    public static boolean compact(ByteBuffer buffer) {
        // 如果当前位置已经是0，说明缓冲区没有数据需要压缩，直接返回false
        if (buffer.position() == 0) {
            return false;
        }

        // 判断缓冲区是否已满
        boolean full = buffer.limit() == buffer.capacity();

        // 执行压缩操作并翻转缓冲区，使指针指向数据的开始位置
        buffer.compact().flip();

        // 如果操作前缓冲区是满的，并且操作后缓冲区的限制小于容量，说明成功腾出了空间，返回true
        return full && buffer.limit() < buffer.capacity();
    }


    /**
     * 将一个ByteBuffer中的数据安全地复制到另一个ByteBuffer中，避免溢出或下溢。
     *
     * @param from 从中读取数据的ByteBuffer，处于flush模式。
     * @param to   向其中写入数据的ByteBuffer，处于fill模式。
     * @return 成功移动的字节数。
     */
    public static int put(ByteBuffer from, ByteBuffer to) {
        int put;
        // 计算from缓冲区中剩余的字节数
        int remaining = from.remaining();
        if (remaining > 0) {
            // 如果from中剩余的字节数不超过to中的剩余空间，则直接复制
            if (remaining <= to.remaining()) {
                // 直接将from中的数据复制到to中
                to.put(from);
                // 移动的字节数为from中剩余的字节数
                put = remaining;
                // 将from的位置设置为其限制，标记数据已全部读取
                from.position(from.limit());
            } else if (from.hasArray()) {
                // 如果from有数组，并且from中的剩余字节数大于to中的剩余空间
                // 将要移动的字节数设置为to中的剩余空间大小
                put = to.remaining();
                // 通过数组直接复制数据
                to.put(from.array(), from.arrayOffset() + from.position(), put);
                // 更新from的位置
                from.position(from.position() + put);
            } else {
                // 如果from没有数组，但from中的剩余字节数大于to中的剩余空间
                // 将要移动的字节数设置为to中的剩余空间大小
                put = to.remaining();
                // 从from中创建一个视图缓冲区，限制为put
                ByteBuffer slice = from.slice();
                slice.limit(put);
                // 将视图缓冲区的数据复制到to中
                to.put(slice);
                // 更新from的位置
                from.position(from.position() + put);
            }
        } else {
            // 如果from中没有剩余数据，则不进行任何操作
            // 移动的字节数为0
            put = 0;
        }
        // 返回移动的字节数
        return put;
    }


    /**
     * 将字节序列追加到缓冲区中。
     *
     * @param to  待追加数据的目标ByteBuffer，操作前处于flush模式
     * @param b   要追加的字节数组
     * @param off 字节数组中开始追加的偏移量
     * @param len 要追加的字节长度
     * @throws BufferOverflowException 如果由于空间限制无法追加数据到缓冲区时抛出
     */
    public static void append(ByteBuffer to, byte[] b, int off, int len) throws BufferOverflowException {
        // 将目标缓冲区切换到fill模式，准备进行数据追加
        int pos = flipToFill(to);
        try {
            // 执行数据追加操作
            to.put(b, off, len);
        } finally {
            // 不论数据追加操作成功与否，最后都切换回flush模式
            flipToFlush(to, pos);
        }
    }


    /**
     * 将字节数组追加到指定的ByteBuffer中。
     *
     * @param to 指定的ByteBuffer对象，追加数据时其处于flush模式。
     * @param b  要追加的字节数组。
     * @throws BufferOverflowException 如果由于空间限制无法将字节数组追加到ByteBuffer中时抛出。
     */
    public static void append(ByteBuffer to, byte[] b) throws BufferOverflowException {
        // 调用另一个重载方法完成追加操作，起始偏移量为0，长度为字节数组的长度
        append(to, b, 0, b.length);
    }


    /**
     * 将字符串以UTF-8格式追加到指定的ByteBuffer中。
     *
     * @param to 指定的ByteBuffer对象，追加数据时其处于flush模式。
     * @param s  要追加的字符串。
     * @throws BufferOverflowException 如果由于空间限制无法将字符串以UTF-8格式追加到ByteBuffer中时抛出。
     */
    public static void append(ByteBuffer to, String s) throws BufferOverflowException {
        // 将字符串转换为UTF-8格式的字节数组，然后调用另一个重载方法完成追加操作
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        append(to, b, 0, b.length);
    }


    /**
     * 将一个字节追加到缓冲区中。
     *
     * @param to 待追加字节的缓冲区，此缓冲区应处于可填充状态。
     * @param b  要追加的字节。
     * @throws BufferOverflowException 如果由于空间限制无法追加字节到缓冲区时抛出。
     */
    public static void append(ByteBuffer to, byte b) {
        // 将缓冲区切换到填充模式，获取当前位置
        int pos = flipToFill(to);
        try {
            // 将字节b追加到缓冲区中
            to.put(b);
        } finally {
            // 不论操作是否成功，最后都切换缓冲区回冲模式，恢复之前的位置
            flipToFlush(to, pos);
        }
    }


    /**
     * 将一个缓冲区追加到另一个缓冲区中
     *
     * @param to 目标缓冲区，该缓冲区应处于可填充状态。
     * @param b  要追加的源缓冲区。
     * @return 在翻转位置之前的合法数据的位置。
     */
    public static int append(ByteBuffer to, ByteBuffer b) {
        // 将目标缓冲区切换到填充模式，并记录当前位置
        int pos = flipToFill(to);
        try {
            // 将源缓冲区b的内容追加到目标缓冲区to中，并返回追加操作前的有效数据位置
            return put(b, to);
        } finally {
            // 不论操作结果如何，最后都切换目标缓冲区回冲模式，恢复之前的位置
            flipToFlush(to, pos);
        }
    }


    /**
     * 类似于append方法，但不会抛出{@link BufferOverflowException}异常。
     * 该方法会尝试将字节数组中的数据填充到指定的ByteBuffer中，直到ByteBuffer没有剩余空间或达到指定的填充长度。
     *
     * @param to  ByteBuffer对象，即将被填充的目标缓冲区。该缓冲区将在填充过程中被切换到填充模式，填充完成后切换回冲模式。
     * @param b   byte数组，包含要填充的数据。
     * @param off 数据数组中的起始偏移量。
     * @param len 要填充的最大长度。
     * @return 实际从数据数组中取出并填充到缓冲区的字节数。
     */
    public static int fill(ByteBuffer to, byte[] b, int off, int len) {
        // 将目标缓冲区切换到填充模式并记录当前位置
        int pos = flipToFill(to);
        try {
            // 计算可以填充的最大字节数
            int remaining = to.remaining();
            int take = remaining < len ? remaining : len;
            // 将数据从数组填充到缓冲区
            to.put(b, off, take);
            return take;
        } finally {
            // 不论操作结果如何，最后都切换目标缓冲区回冲模式，恢复之前的位置
            flipToFlush(to, pos);
        }
    }


    /**
     * 从指定文件中读取数据到ByteBuffer中。
     *
     * @param file   指定要读取的文件。
     * @param buffer 用于存储从文件中读取的数据的ByteBuffer。
     * @throws IOException 如果在读取文件过程中发生I/O错误。
     */
    public static void readFrom(File file, ByteBuffer buffer) throws IOException {
        // 使用RandomAccessFile以只读模式打开文件
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // 获取文件通道
            FileChannel channel = raf.getChannel();
            // 计算需要读取的字节数，即文件长度
            long needed = raf.length();

            // 循环读取数据，直到读完所有数据或ByteBuffer没有剩余空间
            while (needed > 0 && buffer.hasRemaining()) {
                // 读取数据到buffer，并更新剩余需读取字节数
                needed = needed - channel.read(buffer);
            }
        }
    }

    /**
     * 从输入流中读取指定数量的数据到ByteBuffer中。
     *
     * @param is     输入流，从中读取数据。
     * @param needed 需要读取的数据量。
     * @param buffer 用于存储读取数据的ByteBuffer。
     * @throws IOException 如果在读取过程中发生I/O错误。
     */
    public static void readFrom(InputStream is, int needed, ByteBuffer buffer) throws IOException {
        // 分配一个临时的ByteBuffer，用于从输入流中读取数据
        ByteBuffer tmp = allocate(8192);

        // 循环读取数据，直到满足需要的数量或ByteBuffer没有剩余空间
        while (needed > 0 && buffer.hasRemaining()) {
            // 从输入流读取数据，最多读取8192字节
            int l = is.read(tmp.array(), 0, 8192);
            if (l < 0) {
                // 如果读取到EOF，结束循环
                break;
            }
            // 设置临时ByteBuffer的位置和限制，以反映实际读取的数据量
            tmp.position(0);
            tmp.limit(l);
            // 将读取的数据复制到目标ByteBuffer中
            buffer.put(tmp);
        }
    }

    /**
     * 将ByteBuffer中的数据写入OutputStream。
     *
     * @param buffer ByteBuffer对象，数据将从此对象中读取并写入输出流。
     * @param out    OutputStream对象，数据将被写入此输出流。
     * @throws IOException 如果在写入过程中发生I/O错误。
     */
    public static void writeTo(ByteBuffer buffer, OutputStream out) throws IOException {
        if (buffer.hasArray()) {
            // 如果ByteBuffer支持直接访问其数组，直接写入输出流
            out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            // 更新buffer的位置，类似非数组版本的writeTo
            buffer.position(buffer.position() + buffer.remaining());
        } else {
            // 如果ByteBuffer不支持直接访问数组，需要逐块读取并写入输出流
            byte[] bytes = new byte[Math.min(buffer.remaining(), TEMP_BUFFER_SIZE)];
            while (buffer.hasRemaining()) {
                // 每次读取的数据量，取剩余数据量与临时缓冲区大小的最小值
                int byteCountToWrite = Math.min(buffer.remaining(), TEMP_BUFFER_SIZE);
                buffer.get(bytes, 0, byteCountToWrite);
                // 将读取的数据写入输出流
                out.write(bytes, 0, byteCountToWrite);
            }
        }
    }


    /**
     * 将ByteBuffer中的数据写入到byte数组中。
     *
     * @param buffer 源ByteBuffer，数据将从该buffer中读取并写入到out数组中。
     * @param out    目标byte数组，buffer中的数据将被写入到该数组中。
     * @throws IOException 如果操作过程中发生I/O错误。
     */
    public static void writeTo(ByteBuffer buffer, byte[] out) throws IOException {
        // 检查参数是否合法
        if (buffer == null || out == null) {
            throw new IllegalArgumentException("Buffer and out byte array cannot be null.");
        }

        if (buffer.hasArray()) {
            int position = buffer.position();
            int limit = buffer.limit();

            if (limit - position >= out.length) {
                // 当ByteBuffer剩余空间足够时，直接通过系统数组拷贝方法进行拷贝
                System.arraycopy(buffer.array(), buffer.arrayOffset() + position, out, 0, out.length);
                buffer.position(position + out.length);
            } else {
                // 当ByteBuffer剩余空间不足时，只拷贝剩余部分
                System.arraycopy(buffer.array(), buffer.arrayOffset() + position, out, 0, limit - position);
                buffer.position(limit);
            }
        } else {
            for (int i = 0; i < out.length && buffer.hasRemaining(); i++) {
                out[i] = buffer.get();
            }
        }


//        if (buffer.hasArray()) {
//
//            if (buffer.remaining() >= out.length) {
//                // 当ByteBuffer支持直接访问且剩余空间足够时，直接通过系统数组拷贝方法进行拷贝
//                System.arraycopy(buffer.array(), buffer.arrayOffset() + buffer.position(), out, 0, out.length);
//                // 更新buffer的位置
//                buffer.position(buffer.position() + out.length);
//            } else {
//                // 当ByteBuffer支持直接访问但剩余空间不足时，只拷贝剩余部分
//                System.arraycopy(buffer.array(), buffer.arrayOffset() + buffer.position(), out, 0, buffer.remaining());
//                // 更新buffer的位置
//                buffer.position(buffer.position() + buffer.remaining());
//            }
//        } else {
//            // 当ByteBuffer不支持直接访问数组时，逐个字节读取并写入到out数组中
//            if (buffer.hasRemaining()) {
//                buffer.get(out);
//            }
//        }
    }


    /**
     * 将ByteBuffer转换为ISO-8859-1编码的字符串
     *
     * @param buffer 要转换的ByteBuffer，转换过程中该缓冲区不变
     * @return 转换后的字符串
     */
    public static String toString(ByteBuffer buffer) {
        return toString(buffer, StandardCharsets.ISO_8859_1);
    }


    /**
     * 将缓冲区转换为ISO-8859-1编码的字符串
     *
     * @param buffer  要转换的缓冲区，采用flush模式进行转换。缓冲区本身不会被改变。
     * @param charset 使用的字符集，用来将字节转换为字符串。
     * @return 缓冲区转换后的字符串。
     */
    public static String toString(ByteBuffer buffer, Charset charset) {
        if (buffer == null) {
            return null;
        }
        // 尝试直接从缓冲区的数组中获取数据，如果缓冲区不支持，则手动复制。
        byte[] array = buffer.hasArray() ? buffer.array() : null;
        if (array == null) {
            // 缓冲区不支持直接访问数组，因此需要手动复制数据。
            byte[] to = new byte[buffer.remaining()];
            buffer.slice().get(to);
            return new String(to, 0, to.length, charset);
        }
        // 使用缓冲区的数组直接创建字符串，避免数据复制，提高效率。
        return new String(array, buffer.arrayOffset() + buffer.position(), buffer.remaining(), charset);
    }


    /**
     * 将部分缓冲区转换为字符串。
     *
     * @param buffer   要转换的缓冲区
     * @param position 在缓冲区中开始字符串的位置
     * @param length   缓冲区的长度
     * @param charset  使用的字符集来转换字节
     * @return 缓冲区作为字符串
     */
    public static String toString(ByteBuffer buffer, int position, int length, Charset charset) {
        // 检查缓冲区是否为null
        if (buffer == null) {
            return null;
        }
        byte[] array = buffer.hasArray() ? buffer.array() : null;
        // 如果缓冲区不支持直接访问，通过创建只读缓冲区来处理
        if (array == null) {
            ByteBuffer ro = buffer.asReadOnlyBuffer();
            ro.position(position);
            ro.limit(position + length);
            byte[] to = new byte[length];
            ro.get(to);
            // 使用指定字符集将字节转换为字符串
            return new String(to, 0, to.length, charset);
        }
        // 如果支持直接访问，直接使用指定字符集将字节转换为字符串
        return new String(array, buffer.arrayOffset() + position, length, charset);
    }


    /**
     * 将缓冲区转换为UTF-8编码的字符串
     *
     * @param buffer 要转换的缓冲区，转换过程中缓冲区内容不变
     * @return 缓冲区内容转换后的字符串
     */
    public static String toUTF8String(ByteBuffer buffer) {
        // 使用UTF-8字符集将缓冲区转换为字符串
        return toString(buffer, StandardCharsets.UTF_8);
    }


    /**
     * 将缓冲区中的数据转换为整数。解析过程会持续到遇到第一个非数字字符为止。如果没有发现数字，将会抛出IllegalArgumentException异常。
     *
     * @param buffer 包含一个以冲洗模式存储的整数的缓冲区。缓冲区的位置不会改变。
     * @return 一个int类型的整数
     */
    public static int toInt(ByteBuffer buffer) {
        // 根据当前缓冲区的位置和剩余长度来解析整数
        return toInt(buffer, buffer.position(), buffer.remaining());
    }


    /**
     * 将ByteBuffer中的数据转换为整数。解析到第一个非数字字符为止。如果未找到数字，将抛出IllegalArgumentException。
     *
     * @param buffer   包含整数的ByteBuffer，以冲刷模式。位置不变。
     * @param position 从缓冲区的哪个位置开始读取。
     * @param length   用于转换的缓冲区长度。
     * @return 缓冲区字节表示的整数。
     * @throws NumberFormatException 如果未找到数字或者长度小于等于0。
     */
    public static int toInt(ByteBuffer buffer, int position, int length) {
        // 初始化值、标志位
        int val = 0;
        // 标记是否已经开始解析数字
        boolean started = false;
        // 标记是否为负数
        boolean minus = false;

        // 计算解析的上限位置
        int limit = position + length;

        // 长度校验
        if (length <= 0) {
            throw new NumberFormatException(toString(buffer, position, length, StandardCharsets.UTF_8));
        }

        // 遍历ByteBuffer中的字节，进行解析
        for (int i = position; i < limit; i++) {
            byte b = buffer.get(i);
            // 跳过非数字前导字符
            if (b <= SPACE) {
                if (started) {
                    break;
                }
            }
            // 解析数字
            else if (b >= '0' && b <= '9') {
                val = val * 10 + (b - '0');
                started = true;
            }
            // 标记负数
            else if (b == MINUS && !started) {
                minus = true;
            } else {
                // 遇到非数字字符，终止解析
                break;
            }
        }

        // 如果已开始解析数字，则返回结果，否则抛出异常
        if (started) {
            return minus ? (-val) : val;
        }
        throw new NumberFormatException(toString(buffer));
    }


    /**
     * 将ByteBuffer转换为整数。解析到第一个非数字字符为止。如果没有找到数字，将抛出IllegalArgumentException
     *
     * @param buffer 包含一个以 flush 模式存储的整数的 ByteBuffer。位置会被更新。
     * @return 一个整数
     * @throws IllegalArgumentException 如果没有找到数字
     * @throws NumberFormatException    如果缓冲区不包含有效的数字
     */
    public static int takeInt(ByteBuffer buffer) {
        int val = 0; // 初始化值
        boolean started = false; // 标记是否已经开始解析数字
        boolean minus = false; // 标记是否为负数
        int i;
        // 遍历缓冲区，从当前位置开始到缓冲区的限制
        for (i = buffer.position(); i < buffer.limit(); i++) {
            // 获取当前字节
            byte b = buffer.get(i);
            if (b <= SPACE) {
                // 如果是空格或更小的字符，但已经开始解析数字了，则退出循环
                if (started) {
                    break;
                }
            } else if (b >= '0' && b <= '9') {
                // 如果是数字，解析该数字，并更新started标志
                val = val * 10 + (b - '0');
                started = true;
            } else if (b == MINUS && !started) {
                // 如果是负号且还未开始解析数字，标记为负数
                minus = true;
            } else {
                // 如果遇到非数字且已经开始解析，则退出循环
                break;
            }
        }

        if (started) {
            // 如果已经开始解析数字，更新缓冲区的位置，并根据minus标志返回正负数
            buffer.position(i);
            return minus ? (-val) : val;
        }
        // 如果未开始解析数字，抛出异常
        throw new NumberFormatException(toString(buffer));
    }


    /**
     * 将ByteBuffer转换为long类型。解析到第一个非数字字符为止。如果没有找到数字，将抛出IllegalArgumentException异常。
     *
     * @param buffer 包含一个以 flush 模式存储的整数的 ByteBuffer。该位置不会被改变。
     * @return 一个long类型的整数
     * @throws IllegalArgumentException 如果没有找到数字，则抛出此异常。
     */
    public static long toLong(ByteBuffer buffer) {
        // 初始化值为0
        long val = 0;
        // 标记是否已经开始解析数字
        boolean started = false;
        // 标记是否遇到负号
        boolean minus = false;

        // 遍历buffer中的内容
        for (int i = buffer.position(); i < buffer.limit(); i++) {
            // 获取当前字节
            byte b = buffer.get(i);
            if (b <= SPACE) {
                // 如果是空格或更小的字符，但已经开始解析数字时，则跳出循环
                if (started) {
                    break;
                }
            } else if (b >= '0' && b <= '9') {
                // 如果是数字，将其解析并累加到val中
                val = val * 10L + (b - '0');
                // 标记已经开始解析数字
                started = true;
            } else if (b == MINUS && !started) {
                // 如果是负号且还未开始解析数字，则标记为负数
                minus = true;
            } else {
                // 如果遇到非数字且已经开始解析数字，则跳出循环
                break;
            }
        }

        // 如果已经开始解析数字，根据是否为负数返回结果
        if (started) {
            return minus ? (-val) : val;
        }
        // 如果未开始解析数字，抛出异常
        throw new NumberFormatException(toString(buffer));
    }


    /**
     * 将整数以十六进制形式存入ByteBuffer中。
     *
     * @param buffer 目标ByteBuffer，用于存储转换后的十六进制字符串。
     * @param n      要转换的整数。
     */
    public static void putHexInt(ByteBuffer buffer, int n) {
        if (n < 0) {
            // 标记负数
            buffer.put((byte) '-');

            if (n == Integer.MIN_VALUE) {
                // 处理整数最小值特殊情况
                buffer.put((byte) (0x7f & '8'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));

                return;
            }
            // 转换为正数处理
            n = -n;
        }

        if (n < 0x10) {
            // 如果数值小于16，直接放入对应的字符
            buffer.put(DIGIT[n]);
        } else {
            boolean started = false;
            // 通过除以一系列十六进制数来逐步构造十六进制表示
            for (int hexDivisor : hexDivisors) {
                if (n < hexDivisor) {
                    // 如果当前数值小于除数，追加'0'
                    if (started) {
                        buffer.put((byte) '0');
                    }
                    continue;
                }
                // 标记已经开始写入
                started = true;
                // 计算商
                int d = n / hexDivisor;
                // 放入对应的字符
                buffer.put(DIGIT[d]);
                // 更新剩余值
                n = n - d * hexDivisor;
            }
        }
    }

    /**
     * 将整数以十进制形式存入ByteBuffer中。
     *
     * @param buffer 存放转换后字符串的ByteBuffer对象。
     * @param n      需要转换为字符串形式的整数。
     */
    public static void putDecInt(ByteBuffer buffer, int n) {
        // 处理负数
        if (n < 0) {
            buffer.put((byte) '-');

            // 特殊处理整数最小值
            if (n == Integer.MIN_VALUE) {
                buffer.put((byte) '2');
                // 将Integer.MIN_VALUE转换为对应的正数
                n = 147483648;
            } else
                // 转换为对应的正数
                n = -n;
        }

        // 处理0到9之间的数字
        if (n < 10) {
            // 直接存入对应的字符
            buffer.put(DIGIT[n]);
        } else {
            boolean started = false;
            // 通过除法和取余逐步构建数字的字符串表示
            for (int decDivisor : decDivisors) {
                // 当n小于当前除数时，插入对应的'0'字符
                if (n < decDivisor) {
                    if (started) {
                        buffer.put((byte) '0');
                    }
                    continue;
                }

                started = true;
                // 计算商
                int d = n / decDivisor;
                // 存入商对应的字符
                buffer.put(DIGIT[d]);
                // 更新n为余数
                n = n - d * decDivisor;
            }
        }
    }

    /**
     * 将一个长整型数字以十进制形式存入ByteBuffer中。
     *
     * @param buffer ByteBuffer对象，用于接收转换后的数字字符串。
     * @param n      需要转换的长整型数字。
     */
    public static void putDecLong(ByteBuffer buffer, long n) {
        // 处理负数情况
        if (n < 0) {
            buffer.put((byte) '-');

            // 特殊处理Long.MIN_VALUE，因为直接取反会导致溢出
            if (n == Long.MIN_VALUE) {
                buffer.put((byte) '9');
                // 将Long.MIN_VALUE转换为对应的正数
                n = 223372036854775808L;
            } else
                // 取反转换为正数
                n = -n;
        }

        // 处理0到9之间的数字，直接添加到buffer中
        if (n < 10) {
            buffer.put(DIGIT[(int) n]);
        } else {
            boolean started = false;
            // 使用预定义的除数序列逐步构建数字的字符串表示
            for (long aDecDivisorsL : decDivisorsL) {
                // 当n小于当前除数时，如果已开始构建则补0
                if (n < aDecDivisorsL) {
                    if (started) {
                        buffer.put((byte) '0');
                    }
                    continue;
                }

                started = true;
                // 计算商
                long d = n / aDecDivisorsL;
                // 将商存入buffer
                buffer.put(DIGIT[(int) d]);
                // 更新n为余数
                n = n - d * aDecDivisorsL;
            }
        }
    }

    /**
     * 将整数转换为ByteBuffer。
     *
     * @param value 需要转换的整数。
     * @return 包含整数的ByteBuffer对象。
     */
    public static ByteBuffer toBuffer(int value) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        putDecInt(buf, value);
        return buf;
    }

    /**
     * 将长整数转换为ByteBuffer。
     *
     * @param value 需要转换的长整数。
     * @return 包含长整数的ByteBuffer对象。
     */
    public static ByteBuffer toBuffer(long value) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        putDecLong(buf, value);
        return buf;
    }

    /**
     * 将字符串转换为ByteBuffer，使用ISO-8859-1字符集。
     *
     * @param s 需要转换的字符串。
     * @return 包含字符串的ByteBuffer对象。
     */
    public static ByteBuffer toBuffer(String s) {
        return toBuffer(s, StandardCharsets.ISO_8859_1);
    }

    /**
     * 将字符串按照指定字符集转换为ByteBuffer。
     *
     * @param s       需要转换的字符串。
     * @param charset 指定的字符集。
     * @return 包含字符串的ByteBuffer对象。
     */
    public static ByteBuffer toBuffer(String s, Charset charset) {
        if (s == null) {
            // 返回空的ByteBuffer，应对输入为null的情况
            return EMPTY_BUFFER;
        }
        // 将字符串按照指定字符集转换为字节数组，然后放入ByteBuffer中
        return toBuffer(s.getBytes(charset));
    }

    /**
     * 使用提供的字节数组创建一个新的ByteBuffer。
     *
     * @param array 用于支持缓冲区的字节数组。
     * @return 指定字节数组的ByteBuffer，处于刷新模式。
     */
    public static ByteBuffer toBuffer(byte[] array) {
        if (array == null) {
            return EMPTY_BUFFER;
        }
        return toBuffer(array, 0, array.length);
    }

    /**
     * 使用提供的字节数组创建一个新的ByteBuffer。
     *
     * @param array  要使用的字节数组。
     * @param offset 在字节数组内部开始使用的偏移量。
     * @param length 要使用的字节数组的长度。
     * @return 指定字节数组的ByteBuffer，处于刷新模式。
     */
    public static ByteBuffer toBuffer(byte[] array, int offset, int length) {
        if (array == null) {
            return EMPTY_BUFFER;
        }
        // 封装字节数组到ByteBuffer，从指定的偏移量开始，使用指定的长度
        return ByteBuffer.wrap(array, offset, length);
    }

    /**
     * 使用提供的字符串创建一个直接的ByteBuffer。
     *
     * @param s 要转换为ByteBuffer的字符串。
     * @return 使用指定字符串创建的直接ByteBuffer。
     */
    public static ByteBuffer toDirectBuffer(String s) {
        return toDirectBuffer(s, StandardCharsets.ISO_8859_1);
    }


    /**
     * 将字符串转换为直接的ByteBuffer。
     *
     * @param s       要转换的字符串。
     * @param charset 字符串的字符集。
     * @return 一个直接的ByteBuffer，包含字符串的字节表示。
     */
    public static ByteBuffer toDirectBuffer(String s, Charset charset) {
        if (s == null) {
            return EMPTY_BUFFER;
        }
        // 获取字符串的字节数组
        byte[] bytes = s.getBytes(charset);
        // 分配一个直接的ByteBuffer，与字节数组长度相同
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        // 将字节数组放入ByteBuffer
        buf.put(bytes);
        // 重置ByteBuffer的limit和position，以准备读取
        buf.flip();
        return buf;
    }

    /**
     * 将文件映射为ByteBuffer。
     *
     * @param file 要映射为ByteBuffer的文件。
     * @return 一个ByteBuffer，其内容是文件的部分或全部。
     * @throws IOException 如果打开文件或进行映射时发生错误。
     */
    public static ByteBuffer toMappedBuffer(File file) throws IOException {
        return toMappedBuffer(file.toPath(), 0, file.length());
    }

    /**
     * 将文件路径映射为ByteBuffer。
     *
     * @param filePath 文件的路径。
     * @param pos      ByteBuffer将开始读取的文件位置。
     * @param len      要映射的字节长度。
     * @return 一个ByteBuffer，其内容是文件指定部分的字节。
     * @throws IOException 如果打开文件或进行映射时发生错误。
     */
    public static ByteBuffer toMappedBuffer(Path filePath, long pos, long len) throws IOException {
        // 使用文件通道打开文件，并以只读方式映射指定的部分
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            return channel.map(MapMode.READ_ONLY, pos, len);
        }
    }

    /**
     * 将ByteBuffer对象转换为包含其主要状态信息的字符串摘要。
     *
     * @param buffer 待转换的ByteBuffer对象。如果buffer为null，返回字符串"null"。
     * @return 描述ByteBuffer对象状态的字符串，包括位置（position）、限制（limit）、容量（capacity）和剩余（remaining）信息。
     */
    public static String toSummaryString(ByteBuffer buffer) {
        if (buffer == null) {
            return "null";
        }
        StringBuilder buf = new StringBuilder();
        buf.append("[p=");
        buf.append(buffer.position());
        buf.append(",l=");
        buf.append(buffer.limit());
        buf.append(",c=");
        buf.append(buffer.capacity());
        buf.append(",r=");
        buf.append(buffer.remaining());
        buf.append("]");
        return buf.toString();
    }

    /**
     * 将ByteBuffer数组转换为包含每个数组元素主要状态信息的详细字符串表示。
     *
     * @param buffer 待转换的ByteBuffer数组。每个数组元素的状态信息将被包含在结果字符串中。
     * @return 描述所有ByteBuffer数组元素状态的字符串数组，每个元素的状态信息格式与toSummaryString方法相同。
     */
    public static String toDetailString(ByteBuffer[] buffer) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < buffer.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            // 对每个ByteBuffer元素调用toSummaryString，累加到结果字符串中
            builder.append(toSummaryString(buffer[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    /**
     * 将ByteBuffer转换为包含指针和内容的详细调试字符串
     *
     * @param buffer 生成详细字符串的源ByteBuffer
     * @return 表示ByteBuffer指针和内容的字符串
     */
    public static String toDetailString(ByteBuffer buffer) {
        // 检查buffer是否为null
        if (buffer == null) {
            return "null";
        }

        StringBuilder buf = new StringBuilder();
        // 向StringBuilder中添加ByteBuffer的详细信息
        idString(buffer, buf);
        // 添加ByteBuffer的位置、限制、容量和剩余空间信息
        buf.append("[p=");
        buf.append(buffer.position());
        buf.append(",l=");
        buf.append(buffer.limit());
        buf.append(",c=");
        buf.append(buffer.capacity());
        buf.append(",r=");
        buf.append(buffer.remaining());
        buf.append("]={");

        // 这里本应添加内容的转换，但代码中没有实现具体逻辑
        buf.append("}");
        // 返回构建完成的字符串
        return buf.toString();
    }


    /**
     * 将ByteBuffer转换为与内容无关的字符串ID
     *
     * @param buffer 输入的ByteBuffer
     * @param out    用于存储转换结果的StringBuilder
     */
    private static void idString(ByteBuffer buffer, StringBuilder out) {
        // 根据ByteBuffer的类名和特定的内容生成ID
        out.append(buffer.getClass().getSimpleName());
        out.append("@");
        if (buffer.hasArray() && buffer.arrayOffset() == 4) {
            // 如果ByteBuffer支持数组访问且数组偏移量为4，使用数组的前4个字节生成ID
            out.append('T');
            byte[] array = buffer.array();
            toHex(array[0], out);
            toHex(array[1], out);
            toHex(array[2], out);
            toHex(array[3], out);
        } else {
            // 否则，使用系统身份哈希码生成ID
            out.append(Integer.toHexString(System.identityHashCode(buffer)));
        }
    }

    /**
     * 将一个整数转换为十六进制字符串
     *
     * @param value 需要转换的整数
     * @param buf   存储转换结果的Appendable对象
     * @throws IOException 如果Appendable操作失败抛出
     */
    public static void toHex(int value, Appendable buf) throws IOException {
        // 逐位转换整数的每个十六进制位
        int d = 0xf & ((0xF0000000 & value) >> 28);
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        d = 0xf & ((0x0F000000 & value) >> 24);
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        d = 0xf & ((0x00F00000 & value) >> 20);
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        d = 0xf & ((0x000F0000 & value) >> 16);
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        d = 0xf & ((0x0000F000 & value) >> 12);
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        d = 0xf & ((0x00000F00 & value) >> 8);
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        d = 0xf & ((0x000000F0 & value) >> 4);
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        d = 0xf & value;
        buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));

        // 这里似乎是想将整数转换为36进制字符串，但实际代码未实现
        Integer.toString(0, 36);
    }

    /**
     * 将一个字节转换为十六进制字符串
     *
     * @param b   需要转换的字节
     * @param buf 存储转换结果的Appendable对象
     */
    public static void toHex(byte b, Appendable buf) {
        // 通过位操作将字节转换为两位十六进制字符串
        try {
            int d = 0xf & ((0xF0 & b) >> 4);
            buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
            d = 0xf & b;
            buf.append((char) ((d > 9 ? ('A' - 10) : '0') + d));
        } catch (IOException e) {
            // 抛出运行时异常处理IOException
            throw new RuntimeException(e);
        }
    }

    /**
     * 将ByteBuffer转换为与内容无关的字符串ID
     *
     * @param buffer the buffer to generate a string ID from
     * @return A string showing the buffer ID
     */
    public static String toIDString(ByteBuffer buffer) {
        // 使用StringBuilder构建最终的字符串ID并返回
        StringBuilder buf = new StringBuilder();
        idString(buffer, buf);
        return buf.toString();
    }

    /**
     * 将ByteBuffer转换为十六进制摘要字符串
     *
     * @param buffer the buffer to generate a hex byte summary from
     * @return A string showing a summary of the content in hex
     */
    public static String toHexSummary(ByteBuffer buffer) {
        // 如果buffer为null，直接返回"null"
        if (buffer == null) {
            return "null";
        }
        StringBuilder buf = new StringBuilder();

        // 构建包含buffer剩余长度的摘要字符串
        buf.append("b[").append(buffer.remaining()).append("]=");
        // 遍历buffer，将每个字节转换为十六进制并添加到摘要字符串中
        for (int i = buffer.position(); i < buffer.limit(); i++) {
            toHex(buffer.get(i), buf);
            // 如果已经处理了前24个字节且buffer的长度超过32字节，添加"..."并跳过剩余部分
            if (i == buffer.position() + 24 && buffer.limit() > buffer.position() + 32) {
                buf.append("...");
                i = buffer.limit() - 8;
            }
        }
        return buf.toString();
    }


    //十进制除数数组，用于将整数分解为各个位数
    private static final int[] decDivisors =
            {
                    1000000000, 100000000, 10000000, 1000000, 100000, 10000, 1000, 100, 10, 1
            };

    //十六进制除数数组
    private static final int[] hexDivisors =
            {
                    0x10000000, 0x1000000, 0x100000, 0x10000, 0x1000, 0x100, 0x10, 0x1
            };

    //十进制除数长整型数组，用于更大范围的整数分解
    private static final long[] decDivisorsL =
            {
                    1000000000000000000L, 100000000000000000L, 10000000000000000L, 1000000000000000L, 100000000000000L, 10000000000000L,
                    1000000000000L, 100000000000L,
                    10000000000L, 1000000000L, 100000000L, 10000000L, 1000000L, 100000L, 10000L, 1000L, 100L, 10L, 1L
            };

    /**
     * 向ByteBuffer中添加CRLF（回车换行）序列。
     *
     * @param buffer 目标ByteBuffer，将在此buffer中添加CRLF。
     */
    public static void putCRLF(ByteBuffer buffer) {
        // 添加回车符
        buffer.put((byte) 13);
        // 添加换行符
        buffer.put((byte) 10);
    }

    /**
     * 检查另一个ByteBuffer是否以前缀的形式出现在给定的ByteBuffer中。
     *
     * @param prefix 前缀ByteBuffer，用于比较。
     * @param buffer 目标ByteBuffer，检查是否包含前缀。
     * @return 如果buffer以prefix为前缀，则返回true；否则返回false。
     */
    public static boolean isPrefix(ByteBuffer prefix, ByteBuffer buffer) {
        // 首先检查buffer剩余空间是否足够包含prefix
        if (prefix.remaining() > buffer.remaining()) {
            return false;
        }
        // buffer的当前位置
        int bi = buffer.position();
        // 遍历prefix中的每个字节，与buffer中的对应字节进行比较
        for (int i = prefix.position(); i < prefix.limit(); i++) {
            // 如果有任一字节不匹配，则返回false
            if (prefix.get(i) != buffer.get(bi++)) {
                return false;
            }
        }
        // 所有字节匹配，返回true
        return true;
    }

    /**
     * 确保ByteBuffer具有至少指定的容量。
     *
     * @param buffer   原始ByteBuffer对象。
     * @param capacity 需要保证的最小容量。
     * @return 如果原始buffer容量足够，则返回该buffer；否则返回一个新分配的具有足够容量的ByteBuffer。
     * @throws UnsupportedOperationException 如果原始buffer不支持array操作，抛出此异常。
     */
    public static ByteBuffer ensureCapacity(ByteBuffer buffer, int capacity) {
        // 如果buffer为null，直接分配新buffer
        if (buffer == null) {
            return allocate(capacity);
        }

        // 如果当前容量足够，直接返回原buffer
        if (buffer.capacity() >= capacity) {
            return buffer;
        }

        // 如果buffer支持array操作，创建一个新的buffer，复制原始数据
        if (buffer.hasArray()) {
            return ByteBuffer.wrap(Arrays.copyOfRange(buffer.array(), buffer.arrayOffset(), buffer.arrayOffset() + capacity), buffer.position(), buffer.remaining());
        }

        // 对于不支持array操作的buffer，抛出异常
        throw new UnsupportedOperationException();
    }
}
