package test.http;


import com.gettyio.core.channel.AioChannel;
import com.gettyio.core.handler.codec.http.*;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;

public class SimpleHandler extends SimpleChannelInboundHandler<HttpRequest> {
    @Override
    public void channelAdded(AioChannel aioChannel) {
        System.out.println("连接过来了");
    }

    @Override
    public void channelClosed(AioChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AioChannel aioChannel, HttpRequest request) {
        System.out.println("读取消息了:" + request.getHttpVersion());
        //设置keep-alive为false
        aioChannel.setKeepAlive(false);

        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.getHttpHeaders().setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        response.getHttpHeaders().setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain;charset=utf-8");
        //response.getHttpHeaders().setHeader(HttpHeaders.Names.TRANSFER_ENCODING,"chunked");
        //response.getHttpHeaders().setHeader(HttpHeaders.Names.CONTENT_ENCODING,HttpHeaders.Values.GZIP);
        //response.getHttpHeaders().setHeader(HttpHeaders.Names.CONTENT_LENGTH,"111".getBytes().length);
        response.getHttpBody().setContent("111".getBytes());
        aioChannel.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause) throws Exception {
        cause.printStackTrace();
        System.out.println("出错了");
    }
}
