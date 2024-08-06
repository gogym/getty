package test.http;


import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.expansion.handler.codec.http.*;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.expansion.handler.codec.http.request.HttpRequest;
import com.gettyio.expansion.handler.codec.http.response.HttpResponse;
import com.gettyio.expansion.handler.codec.http.response.HttpResponseStatus;

public class SimpleHandler extends SimpleChannelInboundHandler<HttpRequest> {
    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("连接过来了");
        //设置keep-alive为false
        ctx.channel().setKeepAlive(false);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AbstractSocketChannel aioChannel, HttpRequest request) {
        System.out.println("version:" + request.getHttpVersion()+"---header:"+request.getHeader("Connection")+"---body:"+ request.getParameter("id"));
        //设置keep-alive为false
        //aioChannel.setKeepAlive(false);

        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.getHttpHeaders().setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        response.getHttpHeaders().setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain;charset=utf-8");
        //response.getHttpHeaders().setHeader(HttpHeaders.Names.TRANSFER_ENCODING,"chunked");
        //response.getHttpHeaders().setHeader(HttpHeaders.Names.CONTENT_ENCODING,HttpHeaders.Values.GZIP);
        response.getHttpHeaders().setHeader(HttpHeaders.Names.CONTENT_LENGTH,"111".getBytes().length);
        response.getHttpBody().setContent("111".getBytes());
        aioChannel.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        System.out.println("出错了");
    }
}
