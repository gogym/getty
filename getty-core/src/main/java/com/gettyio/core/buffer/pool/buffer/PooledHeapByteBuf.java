/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file tothe License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.gettyio.core.buffer.pool.buffer;


import com.gettyio.core.buffer.buffer.ByteBuf;
import com.gettyio.core.buffer.pool.Recycler;
import com.gettyio.core.util.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;

public final class PooledHeapByteBuf extends PooledByteBuf<byte[]> {

    private static final Recycler<PooledHeapByteBuf> RECYCLER = new Recycler<PooledHeapByteBuf>() {
        @Override
        protected PooledHeapByteBuf newObject(Handle<PooledHeapByteBuf> handle) {
            return new PooledHeapByteBuf(handle, 0);
        }
    };

    public static PooledHeapByteBuf newInstance(int maxCapacity) {
        PooledHeapByteBuf buf = RECYCLER.get();
        buf.setRefCnt(1);
        buf.maxCapacity(maxCapacity);
        return buf;
    }

    private PooledHeapByteBuf(Recycler.Handle<PooledHeapByteBuf> recyclerHandle, int maxCapacity) {
        super(recyclerHandle, maxCapacity);
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    protected byte _getByte(int index) {
        return memory[idx(index)];
    }


    @Override
    protected int _getInt(int index) {
        index = idx(index);
        return (memory[index] & 0xff) << 24 |
                (memory[index + 1] & 0xff) << 16 |
                (memory[index + 2] & 0xff) << 8 |
                memory[index + 3] & 0xff;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(memory, idx(index), dst.memoryAddress() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, memory, idx(index), length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(memory, idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index);
        dst.put(memory, idx(index), Math.min(capacity() - index, dst.remaining()));
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        out.write(memory, idx(index), length);
        return this;
    }


    private int getBytes(int index, GatheringByteChannel out, int length, boolean internal) throws IOException {
        checkIndex(index, length);
        index = idx(index);
        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = internalNioBuffer();
        } else {
            tmpBuf = ByteBuffer.wrap(memory);
        }
        return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length));
    }


    @Override
    protected void _setByte(int index, int value) {
        memory[idx(index)] = (byte) value;
    }


    @Override
    protected void _setInt(int index, int value) {
        index = idx(index);
        memory[index] = (byte) (value >>> 24);
        memory[index + 1] = (byte) (value >>> 16);
        memory[index + 2] = (byte) (value >>> 8);
        memory[index + 3] = (byte) value;
    }


    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        if (src.hasMemoryAddress()) {
            PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, memory, idx(index), length);
        } else {
            src.getBytes(srcIndex, memory, idx(index), length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        System.arraycopy(src, srcIndex, memory, idx(index), length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        int length = src.remaining();
        checkIndex(index, length);
        src.get(memory, idx(index), length);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex(index, length);
        return in.read(memory, idx(index), length);
    }


    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        ByteBuf copy = alloc().heapBuffer(length, maxCapacity());
        copy.writeBytes(memory, idx(index), length);
        return copy;
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[]{nioBuffer(index, length)};
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        index = idx(index);
        ByteBuffer buf = ByteBuffer.wrap(memory, index, length).slice();
        byteBuffer = buf;
        return buf;
    }

    @Override
    public boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(byte[] memory) {
        return ByteBuffer.wrap(memory);
    }
}
