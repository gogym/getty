package com.gettyio.test.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author gogym.ggj
 * @version 1.0.0
 * @ClassName HttpServerSimpleHandler.java
 * @email gongguojun.ggj@alibaba-inc.com
 * @Description TODO
 * @createTime 2020/12/21/ 10:56:00
 */
public class HttpServerSimpleHandler extends SimpleChannelInboundHandler<HttpMessage> {


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        System.out.println("http连接过来");

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("http连接关闭");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpMessage httpMessage) throws Exception {

        System.out.println(httpMessage.protocolVersion());


        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, OK, Unpooled.wrappedBuffer("OK OK OK OK"
                .getBytes()));
        response.headers().set(CONTENT_TYPE, "text/plain");
        response.headers().set(CONTENT_LENGTH,
                response.content().readableBytes());
        response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        channelHandlerContext.write(response);
        channelHandlerContext.flush();

    }




}
