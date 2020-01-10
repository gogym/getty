package com.gettyio.core.handler.codec.http;


public class HttpException extends Exception {
    private final HttpResponseStatus status;
    private final String message;

    public HttpException(HttpResponseStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpException(HttpResponseStatus status) {
        this(status, null);
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

}
