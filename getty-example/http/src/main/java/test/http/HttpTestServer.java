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
package test.http;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.config.GettyConfig;
import com.gettyio.core.channel.starter.AioServerStarter;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.http.request.HttpRequestDecoder;
import com.gettyio.expansion.handler.codec.http.response.HttpResponseEncoder;

/**
 * HTTP 测试服务器。
 * <p>
 * 启动 AIO 服务器，监听 8888 端口，用于测试 HTTP 编解码各场景。
 * 支持以下测试路径：
 * <ul>
 *   <li>GET / — 基本 GET 请求</li>
 *   <li>GET /params?id=1&name=test — URL 查询参数</li>
 *   <li>POST /form — application/x-www-form-urlencoded 表单</li>
 *   <li>POST /upload — multipart/form-data 文件上传</li>
 *   <li>GET /headers — 自定义头部回显</li>
 *   <li>GET /keep-alive — Keep-Alive 连接测试</li>
 *   <li>GET /status/404 — 自定义状态码</li>
 *   <li>GET /json — JSON 响应</li>
 *   <li>GET /large — 大响应体测试</li>
 * </ul>
 * </p>
 *
 * @author gogym
 */
public class HttpTestServer {

    /**
     * 启动 HTTP 测试服务器。
     *
     * @param args 命令行参数（未使用）
     * @throws Exception 启动异常
     */
    public static void main(String[] args) throws Exception {

        // 初始化配置对象
        GettyConfig config = new GettyConfig();
        config.setHost("127.0.0.1");
        config.setPort(8888);

        AioServerStarter server = new AioServerStarter(8888);
        server.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                // 获取责任链对象
                ChannelPipeline pipeline = channel.getChannelPipeline();

                // 添加 HTTP 响应编码器
                pipeline.addLast(new HttpResponseEncoder());
                // 添加 HTTP 请求解码器
                pipeline.addLast(new HttpRequestDecoder());
                // 添加测试处理器
                pipeline.addLast(new HttpTestHandler());
            }
        }).start();

        System.out.println("==============================================");
        System.out.println("  HTTP 测试服务器已启动: http://localhost:8888");
        System.out.println("==============================================");
        System.out.println("可用测试路径：");
        System.out.println("  GET  /                    — 基本 GET 请求");
        System.out.println("  GET  /params?id=1&name=x  — URL 查询参数");
        System.out.println("  POST /form                — 表单提交");
        System.out.println("  POST /upload              — 文件上传 (multipart)");
        System.out.println("  GET  /headers             — 自定义头部回显");
        System.out.println("  GET  /keep-alive          — Keep-Alive 测试");
        System.out.println("  GET  /status/404          — 自定义状态码");
        System.out.println("  GET  /json                — JSON 响应");
        System.out.println("  GET  /large               — 大响应体测试");
        System.out.println("==============================================");
    }
}
