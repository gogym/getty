package com.gettyio.core.buffer;/*
 * 类名：BufferWriter
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/6/17
 */

import java.io.IOException;
import java.io.OutputStream;

public abstract class BufferWriter extends OutputStream {

    @Override
    public void write(int b) throws IOException {
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
    }
}
