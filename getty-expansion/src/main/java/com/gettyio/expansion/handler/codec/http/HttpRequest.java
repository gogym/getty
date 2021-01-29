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
package com.gettyio.expansion.handler.codec.http;

import java.util.HashMap;
import java.util.Map;

/**
 * HttpRequest.java
 *
 * @description:http请求
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class HttpRequest extends HttpMessage {

    private HttpMethod httpMethod;
    /**
     * 请求地址
     */
    private String requestUri;
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
