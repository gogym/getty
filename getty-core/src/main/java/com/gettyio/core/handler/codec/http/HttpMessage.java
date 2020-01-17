package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpMessage
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/17
 */

import java.util.List;
import java.util.Map;

public class HttpMessage {

    protected HttpVersion httpVersion;
    protected HttpHeaders httpHeaders = new HttpHeaders();
    protected HttpBody httpBody = new HttpBody();

    private int readStatus;

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(HttpHeaders httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public String getHeader(String name) {
        return this.httpHeaders.getHeader(name);
    }

    public List<String> getHeaders(String name) {
        return this.httpHeaders.getHeaders(name);
    }

    public List<Map.Entry<String, String>> getHeaders() {
        return this.httpHeaders.getHeaders();
    }


    public void setHeader(String name, Object value) {
        this.httpHeaders.setHeader(name, value);
    }

    public void setHeader(final String name, final Iterable<?> values) {
        this.httpHeaders.setHeader(name, values);
    }

    public void addHeader(final String name, final Object value) {
        this.httpHeaders.addHeader(name, value);
    }

    public void removeHeader(final String name) {
        this.httpHeaders.removeHeader(name);
    }

    public HttpBody getHttpBody() {
        return httpBody;
    }

    public void setHttpBody(HttpBody httpBody) {
        this.httpBody = httpBody;
    }

    public int getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(int readStatus) {
        this.readStatus = readStatus;
    }
}
