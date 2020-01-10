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
    private String queryString;


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

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    //------------------------------------------------------------------------------------------------

    private String host;// 机器地址
    private String origin;// 源地址
    private String cookie;// cookie
    private Boolean upgrade = false;// 是否更新
    private Boolean connection = false;// 是否保存链接
    private Long key1;// key1
    private Long key2;// key2
    private String digest;// 签名
    private Integer secVersion = 0;//版本，默认为0

    public Boolean getConnection() {
        return connection;
    }

    public void setConnection(Boolean connection) {
        this.connection = connection;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Long getKey1() {
        return key1;
    }

    public void setKey1(Long key1) {
        this.key1 = key1;
    }

    public Long getKey2() {
        return key2;
    }

    public void setKey2(Long key2) {
        this.key2 = key2;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public Boolean getUpgrade() {
        return upgrade;
    }

    public void setUpgrade(Boolean upgrade) {
        this.upgrade = upgrade;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public Integer getSecVersion() {
        return secVersion;
    }

    public void setSecVersion(Integer secVersion) {
        this.secVersion = secVersion;
    }
}
