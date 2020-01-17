package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpRequest
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/9
 */

import java.util.HashMap;
import java.util.Map;

public class HttpRequest extends HttpMessage {

    private HttpMethod httpMethod;
    private String requestUri;// 请求地址
    private Map<String, String> parameters;
    private Map<String, FieldItem> fieldItems;
    private String queryString;


    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
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


    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }


    public Map<String, String> getParameters() {
        return parameters;
    }

    public Map<String, FieldItem> getFieldItems() {
        return fieldItems;
    }
}
