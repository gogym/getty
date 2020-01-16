package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpRequest
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/9
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequest {

    private HttpMethod httpMethod;
    private String requestUri;// 请求地址
    private HttpVersion httpVersion;
    private HttpHeaders httpHeaders = new HttpHeaders();
    private HttpBody httpBody = new HttpBody();
    private Map<String, String> parameters;
    private Map<String, FieldItem> fieldItems;
    private String queryString;


    private int readStatus;


    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

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

    public String getParameter(String name) {
        return parameters == null ? null : parameters.get(name);
    }

    public void addParameter(String key, String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(key, value);
    }


    public FieldItem getFieldItem(String name) {
        return fieldItems == null ? null : fieldItems.get(name);
    }

    public void addFieldItem(String key, FieldItem value) {
        if (fieldItems == null) {
            fieldItems = new HashMap<>();
        }
        fieldItems.put(key, value);
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public int getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(int readStatus) {
        this.readStatus = readStatus;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }


}
