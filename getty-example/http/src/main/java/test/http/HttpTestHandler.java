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
import com.gettyio.core.pipeline.ChannelHandlerContext;
import com.gettyio.core.pipeline.in.SimpleChannelInboundHandler;
import com.gettyio.expansion.handler.codec.http.FieldItem;
import com.gettyio.expansion.handler.codec.http.HttpHeaders;
import com.gettyio.expansion.handler.codec.http.HttpMethod;
import com.gettyio.expansion.handler.codec.http.HttpVersion;
import com.gettyio.expansion.handler.codec.http.request.HttpRequest;
import com.gettyio.expansion.handler.codec.http.response.HttpResponse;
import com.gettyio.expansion.handler.codec.http.response.HttpResponseStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 测试处理器。
 * <p>
 * 根据请求路径分发到不同的测试场景，覆盖 HTTP 编解码的各种场景：
 * <ul>
 *   <li>基本 GET 请求</li>
 *   <li>URL 查询参数解析</li>
 *   <li>表单提交（application/x-www-form-urlencoded）</li>
 *   <li>文件上传（multipart/form-data）</li>
 *   <li>自定义头部回显</li>
 *   <li>Keep-Alive 连接测试</li>
 *   <li>自定义状态码</li>
 *   <li>JSON 响应</li>
 *   <li>大响应体测试</li>
 * </ul>
 * </p>
 *
 * @author gogym
 */
public class HttpTestHandler extends SimpleChannelInboundHandler<HttpRequest> {

    @Override
    public void channelAdded(ChannelHandlerContext ctx) {
        System.out.println("[连接建立] " + ctx.channel());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx) {
        System.out.println("[连接关闭] " + ctx.channel());
    }

    @Override
    public void channelRead0(AbstractSocketChannel channel, HttpRequest request) {
        //channel.setKeepAlive(false);
        String uri = request.getRequestUri();
        // 去掉查询参数部分，只保留路径
        String path = uri;
        int qmark = uri.indexOf('?');
        if (qmark > 0) {
            path = uri.substring(0, qmark);
        }

        System.out.println("[请求] " + request.getHttpMethod() + " " + uri);

        try {
            // 根据路径分发到不同的测试场景
            if ("/".equals(path)) {
                handleBasicGet(channel, request);
            } else if ("/params".equals(path)) {
                handleQueryParams(channel, request);
            } else if ("/form".equals(path)) {
                handleFormPost(channel, request);
            } else if ("/upload".equals(path)) {
                handleFileUpload(channel, request);
            } else if ("/headers".equals(path)) {
                handleHeaderEcho(channel, request);
            } else if ("/keep-alive".equals(path)) {
                handleKeepAlive(channel, request);
            } else if (path.startsWith("/status/")) {
                handleCustomStatus(channel, request, path);
            } else if ("/json".equals(path)) {
                handleJsonResponse(channel, request);
            } else if ("/large".equals(path)) {
                handleLargeResponse(channel, request);
            } else {
                handleNotFound(channel, request);
            }
        } catch (Exception e) {
            e.printStackTrace();
            handleError(channel, request, e);
        }
    }

    /**
     * 场景1：基本 GET 请求。
     * <p>
     * 返回简单的文本响应，验证基本的 HTTP 编解码流程。
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleBasicGet(AbstractSocketChannel channel, HttpRequest request) {
        String body = "Hello, Getty HTTP Server!";
        HttpResponse response = createResponse(HttpResponseStatus.OK, "text/plain;charset=utf-8", body);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景2：URL 查询参数解析。
     * <p>
     * 解析 URL 中的查询参数并回显，验证 decodeQueryString 和 getParameter 的正确性。
     * 测试示例: GET /params?id=123&name=getty&age=1
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleQueryParams(AbstractSocketChannel channel, HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== URL 查询参数测试 ===\n");
        sb.append("请求 URI: ").append(request.getRequestUri()).append("\n");
        sb.append("QueryString: ").append(request.getQueryString()).append("\n");
        sb.append("参数列表:\n");

        Map<String, String> params = request.getParameters();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
        } else {
            sb.append("  (无参数)\n");
        }

        String bodyStr = sb.toString();
        byte[] bodyBytes = bodyStr.getBytes(StandardCharsets.UTF_8);
        HttpResponse response = createResponse(HttpResponseStatus.OK, "text/plain;charset=utf-8", bodyStr);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景3：表单提交（application/x-www-form-urlencoded）。
     * <p>
     * 解析 POST 表单数据并回显，验证 decodeFormBody 的正确性。
     * 测试示例: POST /form，Body: username=admin&password=123456
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleFormPost(AbstractSocketChannel channel, HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 表单提交测试 ===\n");
        sb.append("Method: ").append(request.getHttpMethod()).append("\n");
        sb.append("Content-Type: ").append(request.getHeader(HttpHeaders.Names.CONTENT_TYPE)).append("\n");
        sb.append("参数列表:\n");

        Map<String, String> params = request.getParameters();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
        } else {
            sb.append("  (无参数)\n");
        }

        HttpResponse response = createResponse(HttpResponseStatus.OK, "text/plain;charset=utf-8", sb.toString());
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景4：文件上传（multipart/form-data）。
     * <p>
     * 解析 multipart 请求，回显字段信息和文件元数据，
     * 验证 readMultipart 和 FieldItem 的正确性。
     * 测试示例: POST /upload，Content-Type: multipart/form-data; boundary=xxx
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleFileUpload(AbstractSocketChannel channel, HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 文件上传测试 ===\n");
        sb.append("Content-Type: ").append(request.getHeader(HttpHeaders.Names.CONTENT_TYPE)).append("\n");

        // 回显普通参数
        Map<String, String> params = request.getParameters();
        if (params != null && !params.isEmpty()) {
            sb.append("\n普通参数:\n");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
        }

        // 回显文件字段信息
        Map<String, FieldItem> fieldItems = request.getFieldItems();
        if (fieldItems != null && !fieldItems.isEmpty()) {
            sb.append("\n文件字段:\n");
            for (Map.Entry<String, FieldItem> entry : fieldItems.entrySet()) {
                FieldItem item = entry.getValue();
                sb.append("  字段名: ").append(item.getName()).append("\n");
                sb.append("  文件名: ").append(item.getFilename()).append("\n");
                sb.append("  Content-Type: ").append(item.getContentType()).append("\n");
                sb.append("  是否表单字段: ").append(item.isFormField()).append("\n");
                if (item.getFile() != null) {
                    sb.append("  文件大小: ").append(item.getFile().length).append(" 字节\n");
                }
                sb.append("\n");
            }
        } else {
            sb.append("\n(无文件字段)\n");
        }

        HttpResponse response = createResponse(HttpResponseStatus.OK, "text/plain;charset=utf-8", sb.toString());
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景5：自定义头部回显。
     * <p>
     * 将请求中的所有头部原样返回，验证头部解析和编码的正确性。
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleHeaderEcho(AbstractSocketChannel channel, HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 头部回显测试 ===\n");
        sb.append("请求头部:\n");

        for (Map.Entry<String, String> header : request.getHeaderEntries()) {
            sb.append("  ").append(header.getKey()).append(": ").append(header.getValue()).append("\n");
        }

        HttpResponse response = createResponse(HttpResponseStatus.OK, "text/plain;charset=utf-8", sb.toString());
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景6：Keep-Alive 连接测试。
     * <p>
     * 设置 Connection: keep-alive，验证长连接场景下多次请求的正确性。
     * 响应体中包含当前时间，方便观察每次请求的响应。
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleKeepAlive(AbstractSocketChannel channel, HttpRequest request) {
        String body = "Keep-Alive 测试成功！连接保持中。\n"
                + "当前时间: " + System.currentTimeMillis();

        HttpResponse response = createResponse(HttpResponseStatus.OK, "text/plain;charset=utf-8", body);
        // 保持连接
        response.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
        response.setHeader("X-Keep-Alive", "true");
        // 不关闭连接
        channel.setKeepAlive(true);
        channel.writeAndFlush(response);
    }

    /**
     * 场景7：自定义状态码。
     * <p>
     * 根据路径中的状态码返回对应的 HTTP 状态，验证各种状态码的编码。
     * 测试示例: GET /status/404、GET /status/500、GET /status/301
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     * @param path    请求路径
     */
    private void handleCustomStatus(AbstractSocketChannel channel, HttpRequest request, String path) {
        // 从路径中提取状态码: /status/404 -> 404
        String statusStr = path.substring("/status/".length());
        int statusCode;
        try {
            statusCode = Integer.parseInt(statusStr);
        } catch (NumberFormatException e) {
            statusCode = 400;
        }

        HttpResponseStatus status = HttpResponseStatus.valueOf(statusCode);
        String body = "状态码: " + statusCode + " " + status.getReasonPhrase();

        HttpResponse response = createResponse(status, "text/plain;charset=utf-8", body);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景8：JSON 响应。
     * <p>
     * 返回 JSON 格式的响应体，验证 Content-Type 为 application/json 的场景。
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleJsonResponse(AbstractSocketChannel channel, HttpRequest request) {
        String json = "{\n"
                + "  \"code\": 200,\n"
                + "  \"message\": \"success\",\n"
                + "  \"data\": {\n"
                + "    \"method\": \"" + request.getHttpMethod() + "\",\n"
                + "    \"uri\": \"" + request.getRequestUri() + "\",\n"
                + "    \"version\": \"" + request.getHttpVersion() + "\",\n"
                + "    \"timestamp\": " + System.currentTimeMillis() + "\n"
                + "  }\n"
                + "}";

        HttpResponse response = createResponse(HttpResponseStatus.OK, "application/json;charset=utf-8", json);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景9：大响应体测试。
     * <p>
     * 返回一个较大的响应体（约 100KB），验证大数据量的编码和传输。
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleLargeResponse(AbstractSocketChannel channel, HttpRequest request) {
        StringBuilder sb = new StringBuilder(102400);
        sb.append("=== 大响应体测试 ===\n");
        sb.append("以下内容为重复数据，用于测试大数据量传输：\n\n");

        // 生成约 100KB 的数据
        String line = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz\n";
        int count = 0;
        while (sb.length() < 102400) {
            sb.append(String.format("%05d: %s", count++, line));
        }

        byte[] bodyBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain;charset=utf-8");
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, bodyBytes.length);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        response.getHttpBody().setContent(bodyBytes);
        channel.writeAndFlush(response);
    }

    /**
     * 场景10：404 未找到。
     * <p>
     * 请求路径不匹配任何已注册路由时，返回 404。
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     */
    private void handleNotFound(AbstractSocketChannel channel, HttpRequest request) {
        String body = "404 Not Found: " + request.getRequestUri();
        HttpResponse response = createResponse(HttpResponseStatus.NOT_FOUND, "text/plain;charset=utf-8", body);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 场景11：服务器内部错误。
     * <p>
     * 处理过程中发生异常时，返回 500 错误。
     * </p>
     *
     * @param channel 通道
     * @param request 请求对象
     * @param e       异常对象
     */
    private void handleError(AbstractSocketChannel channel, HttpRequest request, Exception e) {
        String body = "500 Internal Server Error: " + e.getMessage();
        HttpResponse response = createResponse(
                HttpResponseStatus.INTERNAL_SERVER_ERROR, "text/plain;charset=utf-8", body);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        channel.writeAndFlush(response);
    }

    /**
     * 创建 HTTP 响应的通用方法。
     *
     * @param status      响应状态
     * @param contentType 内容类型
     * @param body        响应体文本
     * @return HTTP 响应对象
     */
    private HttpResponse createResponse(HttpResponseStatus status, String contentType, String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1, status);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, bodyBytes.length);
        response.getHttpBody().setContent(bodyBytes);
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[异常] " + cause.getMessage());
        cause.printStackTrace();
    }
}
