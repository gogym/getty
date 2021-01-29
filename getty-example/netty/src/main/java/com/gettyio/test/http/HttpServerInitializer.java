package com.gettyio.test.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import io.netty.handler.logging.LoggingHandler;


/**
 * netty处理器配置
 *
 * @author kokJuis
 * @version 1.0
 * @date 2016-9-30
 */
public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {


    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();


        ch.pipeline().addLast("logging",new LoggingHandler("DEBUG"));
        ch.pipeline().addLast(new HttpResponseEncoder());
        ch.pipeline().addLast(new HttpRequestDecoder());
        ch.pipeline().addLast(new HttpServerSimpleHandler());



    }
}
