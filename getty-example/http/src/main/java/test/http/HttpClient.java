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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 测试客户端。
 * <p>
 * 使用原始 Socket 发送 HTTP 请求，覆盖以下测试场景：
 * <ul>
 *   <li>基本 GET 请求</li>
 *   <li>带查询参数的 GET 请求</li>
 *   <li>POST 表单提交（application/x-www-form-urlencoded）</li>
 *   <li>POST 文件上传（multipart/form-data）</li>
 *   <li>自定义头部请求</li>
 *   <li>Keep-Alive 连接多次请求</li>
 *   <li>自定义状态码请求</li>
 *   <li>JSON 响应请求</li>
 *   <li>大响应体请求</li>
 * </ul>
 * 需要先启动 {@link HttpTestServer}。
 * </p>
 *
 * @author gogym
 */
public class HttpClient {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8888;

    /**
     * 运行所有 HTTP 测试场景。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  HTTP 测试客户端 - 开始执行测试场景");
        System.out.println("==============================================\n");

        try {
            // 场景1：基本 GET 请求
            testBasicGet();

            // 场景2：带查询参数的 GET 请求
            testGetWithParams();

            // 场景3：POST 表单提交
            testFormPost();

            // 场景4：POST 文件上传（multipart/form-data）
            testFileUpload();

            // 场景5：自定义头部
            testCustomHeaders();

            // 场景6：Keep-Alive 连接多次请求
            testKeepAlive();

            // 场景7：自定义状态码
            testCustomStatus();

            // 场景8：JSON 响应
            testJsonResponse();

            // 场景9：大响应体
            testLargeResponse();

            // 场景10：404 未找到
            testNotFound();

        } catch (Exception e) {
            System.err.println("测试执行异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n==============================================");
        System.out.println("  所有测试场景执行完毕");
        System.out.println("==============================================");
    }

    /**
     * 场景1：基本 GET 请求。
     * <p>
     * 发送 GET / 请求，验证服务器能正常响应。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testBasicGet() throws Exception {
        System.out.println("--- 场景1：基本 GET 请求 ---");
        String request = "GET / HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "User-Agent: Getty-Test-Client/1.0\r\n"
                + "\r\n";
        sendAndReceive(request);
    }

    /**
     * 场景2：带查询参数的 GET 请求。
     * <p>
     * 发送 GET /params?id=123&name=getty&age=1 请求，
     * 验证 URL 查询参数的解析。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testGetWithParams() throws Exception {
        System.out.println("--- 场景2：带查询参数的 GET 请求 ---");
        String request = "GET /params?id=123&name=getty&age=1 HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "User-Agent: Getty-Test-Client/1.0\r\n"
                + "\r\n";
        sendAndReceive(request);
    }

    /**
     * 场景3：POST 表单提交（application/x-www-form-urlencoded）。
     * <p>
     * 发送 POST /form 请求，Body 为表单数据，
     * 验证 decodeFormBody 的正确性。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testFormPost() throws Exception {
        System.out.println("--- 场景3：POST 表单提交 ---");
        String body = "username=admin&password=123456&remember=true";
        String request = "POST /form HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "\r\n"
                + body;
        sendAndReceive(request);
    }

    /**
     * 场景4：POST 文件上传（multipart/form-data）。
     * <p>
     * 发送 POST /upload 请求，包含一个文本字段和一个文件字段，
     * 验证 readMultipart 和 FieldItem 的正确性。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testFileUpload() throws Exception {
        System.out.println("--- 场景4：POST 文件上传 (multipart/form-data) ---");
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"description\"\r\n"
                + "\r\n"
                + "这是一个测试文件\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "Hello, this is the file content!\r\n"
                + "Line 2 of the file.\r\n"
                + "--" + boundary + "--\r\n";

        String request = "POST /upload HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "Content-Type: multipart/form-data; boundary=" + boundary + "\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "\r\n"
                + body;
        sendAndReceive(request);
    }

    /**
     * 场景5：自定义头部请求。
     * <p>
     * 发送 GET /headers 请求，携带多个自定义头部，
     * 验证头部解析和回显的正确性。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testCustomHeaders() throws Exception {
        System.out.println("--- 场景5：自定义头部回显 ---");
        String request = "GET /headers HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "X-Custom-Header: custom-value\r\n"
                + "X-Request-Id: 12345\r\n"
                + "Authorization: Bearer token123\r\n"
                + "Accept: text/html,application/json\r\n"
                + "Accept-Language: zh-CN,zh;q=0.9\r\n"
                + "\r\n";
        sendAndReceive(request);
    }

    /**
     * 场景6：Keep-Alive 连接多次请求。
     * <p>
     * 在同一个 TCP 连接上发送两次请求，
     * 验证 Keep-Alive 长连接的正确性。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testKeepAlive() throws Exception {
        System.out.println("--- 场景6：Keep-Alive 连接测试 ---");
        String request1 = "GET /keep-alive HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "Connection: keep-alive\r\n"
                + "\r\n";
        String request2 = "GET /keep-alive HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 第一次请求
            System.out.println("[发送第1次请求]");
            out.write(request1.getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("[第1次响应]");
            readResponse(in);

            System.out.println();

            // 第二次请求（同一连接）
            System.out.println("[发送第2次请求 - 同一连接]");
            out.write(request2.getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("[第2次响应]");
            readResponse(in);
        }
    }

    /**
     * 场景7：自定义状态码。
     * <p>
     * 分别请求 404、500、301 状态码，验证不同状态的编码。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testCustomStatus() throws Exception {
        System.out.println("--- 场景7：自定义状态码 ---");
        String[] statusCodes = {"404", "500", "301"};
        for (String code : statusCodes) {
            System.out.println("  请求 /status/" + code + ":");
            String request = "GET /status/" + code + " HTTP/1.1\r\n"
                    + "Host: localhost:8888\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            sendAndReceive(request);
        }
    }

    /**
     * 场景8：JSON 响应。
     * <p>
     * 请求 JSON 格式的响应，验证 application/json Content-Type 的编码。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testJsonResponse() throws Exception {
        System.out.println("--- 场景8：JSON 响应 ---");
        String request = "GET /json HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "Accept: application/json\r\n"
                + "\r\n";
        sendAndReceive(request);
    }

    /**
     * 场景9：大响应体。
     * <p>
     * 请求大响应体（约 100KB），验证大数据量的传输。
     * 只打印前 200 字符和总字节数。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testLargeResponse() throws Exception {
        System.out.println("--- 场景9：大响应体测试 ---");
        String request = "GET /large HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 读取响应
            InputStream in = socket.getInputStream();

            // 读取头部，直到遇到空行
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int prev1 = -1, prev2 = -1, prev3 = -1;
            int b;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && b == '\n') {
                    break;
                }
                prev3 = prev2;
                prev2 = prev1;
                prev1 = b;
            }

            // 解析头部，找到 Content-Length
            String headerStr = new String(headerBuf.toByteArray(), StandardCharsets.UTF_8);
            String[] lines = headerStr.split("\r\n");
            int contentLength = -1;
            for (String line : lines) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            // 读取消息体（按字节读取）
            if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = in.read(bodyBytes, totalRead, contentLength - totalRead);
                    if (read < 0) break;
                    totalRead += read;
                }
                String bodyStr = new String(bodyBytes, 0, totalRead, StandardCharsets.UTF_8);
                System.out.println("  响应体大小: " + totalRead + " 字节");
                System.out.println("  响应体前200字符: " + bodyStr.substring(0, Math.min(200, bodyStr.length())) + "...");
            }
        }
    }

    /**
     * 场景10：404 未找到。
     * <p>
     * 请求不存在的路径，验证 404 响应。
     * </p>
     *
     * @throws Exception IO 异常
     */
    private static void testNotFound() throws Exception {
        System.out.println("--- 场景10：404 未找到 ---");
        String request = "GET /nonexistent/path HTTP/1.1\r\n"
                + "Host: localhost:8888\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        sendAndReceive(request);
    }

    /**
     * 发送 HTTP 请求并打印响应。
     * <p>
     * 每次调用创建新的 TCP 连接，发送请求后读取完整响应并打印。
     * </p>
     *
     * @param request HTTP 请求字符串
     * @throws Exception IO 异常
     */
    private static void sendAndReceive(String request) throws Exception {
        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 发送请求
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 读取并打印响应
            readResponse(in);
        }
        System.out.println();
    }

    /**
     * 从 BufferedReader 读取并打印 HTTP 响应。
     * <p>
     * 先读取状态行和头部，再根据 Content-Length 从原始输入流读取字节消息体，
     * 避免 BufferedReader 字符解码导致字节数与字符数不匹配的问题。
     * </p>
     *
     * @param reader 输入流读取器（用于读取状态行和头部）
     * @param in     原始输入流（用于按字节读取消息体）
     * @throws Exception IO 异常
     */
    private static void readResponse(InputStream in) throws Exception {
        // 读取头部，直到遇到空行 (\r\n\r\n)
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int prev1 = -1, prev2 = -1, prev3 = -1;
        int b;
        while ((b = in.read()) != -1) {
            headerBuf.write(b);
            // 检测 \r\n\r\n
            if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && b == '\n') {
                break;
            }
            prev3 = prev2;
            prev2 = prev1;
            prev1 = b;
        }

        // 解析头部
        String headerStr = new String(headerBuf.toByteArray(), StandardCharsets.UTF_8);
        String[] lines = headerStr.split("\r\n");
        int contentLength = -1;
        for (int i = 0; i < lines.length; i++) {
            System.out.println("  " + lines[i]);
            if (i > 0 && lines[i].toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(lines[i].substring(15).trim());
            }
        }

        // 读取消息体（按字节读取）
        if (contentLength > 0) {
            byte[] bodyBytes = new byte[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = in.read(bodyBytes, totalRead, contentLength - totalRead);
                if (read < 0) break;
                totalRead += read;
            }
            System.out.println("  [消息体]");
            System.out.println("  " + new String(bodyBytes, 0, totalRead, StandardCharsets.UTF_8));
        }
    }
}
