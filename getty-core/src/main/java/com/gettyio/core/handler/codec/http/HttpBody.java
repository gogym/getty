package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpBody
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/10
 */

import java.io.InputStream;
import java.io.OutputStream;

public class HttpBody {

    private String contentType;

    private long contentLength;

    private byte[] content;



    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
