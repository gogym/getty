package com.gettyio.core.handler.codec.http;/*
 * 类名：HttpResponse
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2020/1/16
 */

import com.gettyio.core.util.DateTimeUtil;

public class HttpResponse extends HttpMessage {

    private HttpResponseStatus httpResponseStatus;

    public HttpResponse() {

    }

    public HttpResponse(HttpVersion httpVersion, HttpResponseStatus httpResponseStatus) {
        super.httpVersion = httpVersion;
        this.httpResponseStatus = httpResponseStatus;
        addHeader(HttpHeaders.Names.SERVER, "getty");
        addHeader(HttpHeaders.Names.DATE, DateTimeUtil.getCurrentTime());
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    public void setHttpResponseStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }

}
