package com.gettyio.protobuf.client;

import com.gettyio.core.channel.AbstractSocketChannel;
import com.gettyio.core.channel.starter.AioClientStarter;
import com.gettyio.core.channel.starter.ConnectHandler;
import com.gettyio.core.pipeline.ChannelPipeline;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufEncoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import com.gettyio.expansion.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import com.gettyio.core.handler.ssl.SSLConfig;
import com.gettyio.core.handler.ssl.SSLHandler;
import com.gettyio.core.pipeline.ChannelInitializer;
import com.gettyio.protobuf.packet.MessageClass;

import java.util.UUID;

/**
 * Protobuf IM 客户端全场景测试。
 * <p>
 * 连接成功后依次模拟各种 IM 业务场景：
 * <ol>
 *   <li>连接请求（reqType=1）</li>
 *   <li>心跳（reqType=2）</li>
 *   <li>单聊文本消息（reqType=10）</li>
 *   <li>单聊中文消息</li>
 *   <li>单聊长文本消息（测试大负载）</li>
 *   <li>群聊消息（reqType=11）</li>
 *   <li>ACK 确认（reqType=20）</li>
 *   <li>全字段消息（填充所有 25 个字段）</li>
 *   <li>连续快速发送（测试粘包/半包）</li>
 *   <li>批量发送（性能测试）</li>
 * </ol>
 * </p>
 */
public class ImClient {

    /** 请求类型常量 */
    private static final int REQ_CONNECT = 1;
    private static final int REQ_HEARTBEAT = 2;
    private static final int REQ_SINGLE = 10;
    private static final int REQ_GROUP = 11;
    private static final int REQ_ACK = 20;

    public static void main(String[] args) throws Exception {
        test(9999);
    }

    private static void test(int port) {
        AioClientStarter client = new AioClientStarter("127.0.0.1", port);
        client.channelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(AbstractSocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.getChannelPipeline();

                // ----如需 SSL，取消以下注释----
                // String pkPath = getClass().getClassLoader().getResource("clientStore.jks").getPath();
                // SSLConfig sslConfig = new SSLConfig();
                // sslConfig.setKeyFile(pkPath);
                // sslConfig.setKeyPassword("123456");
                // sslConfig.setKeystorePassword("123456");
                // sslConfig.setTrustFile(pkPath);
                // sslConfig.setTrustPassword("123456");
                // sslConfig.setClientMode(true);
                // pipeline.addFirst(new SSLHandler(sslConfig));
                // ----SSL END----

                // Protobuf 编码器
                pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                pipeline.addLast(new ProtobufEncoder());

                // Protobuf 解码器
                pipeline.addLast(new ProtobufVarint32FrameDecoder());
                pipeline.addLast(new ProtobufDecoder(MessageClass.Message.getDefaultInstance()));

                // 业务处理器
                pipeline.addLast(new SimpleHandler());
            }
        });

        client.start(new ConnectHandler() {
            @Override
            public void onCompleted(final AbstractSocketChannel channel) {
                try {
                    // 等待连接稳定
                    Thread.sleep(500);

                    System.out.println("\n========== 开始 Protobuf IM 全场景测试 ==========\n");

                    // ---- 1. 连接请求 ----
                    sendAndPause("1. 连接请求");
                    MessageClass.Message connectMsg = MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setReqType(REQ_CONNECT)
                            .setMsgTime(System.currentTimeMillis())
                            .setSenderId("user-001")
                            .setSenderName("张三")
                            .setIdentify("IM")
                            .setVersion("1.0")
                            .build();
                    channel.writeAndFlush(connectMsg);
                    Thread.sleep(500);

                    // ---- 2. 心跳 ----
                    sendAndPause("2. 心跳");
                    channel.writeAndFlush(MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setReqType(REQ_HEARTBEAT)
                            .setMsgTime(System.currentTimeMillis())
                            .setSenderId("user-001")
                            .build());
                    Thread.sleep(500);

                    // ---- 3. 单聊英文消息 ----
                    sendAndPause("3. 单聊英文消息");
                    channel.writeAndFlush(MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setReqType(REQ_SINGLE)
                            .setMsgTime(System.currentTimeMillis())
                            .setSenderId("user-001")
                            .setSenderName("张三")
                            .setReceiverId("user-002")
                            .setReceiverName("李四")
                            .setBody("Hello, this is a test message!")
                            .setBodyType(1)
                            .build());
                    Thread.sleep(500);

                    // ---- 4. 单聊中文消息 ----
                    sendAndPause("4. 单聊中文消息");
                    channel.writeAndFlush(MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setReqType(REQ_SINGLE)
                            .setMsgTime(System.currentTimeMillis())
                            .setSenderId("user-001")
                            .setSenderName("张三")
                            .setReceiverId("user-002")
                            .setReceiverName("李四")
                            .setBody("你好，这是一条中文测试消息！支持各种特殊字符：@#$%^&*()")
                            .setBodyType(1)
                            .build());
                    Thread.sleep(500);

                    // ---- 5. 单聊长文本消息 ----
                    sendAndPause("5. 单聊长文本(5000字)");
                    StringBuilder sb = new StringBuilder(5000);
                    for (int i = 0; i < 500; i++) {
                        sb.append("第").append(i + 1).append("行消息内容。");
                    }
                    channel.writeAndFlush(MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setReqType(REQ_SINGLE)
                            .setMsgTime(System.currentTimeMillis())
                            .setSenderId("user-001")
                            .setSenderName("张三")
                            .setReceiverId("user-002")
                            .setReceiverName("李四")
                            .setBody(sb.toString())
                            .setBodyType(1)
                            .build());
                    Thread.sleep(500);

                    // ---- 6. 群聊消息 ----
                    sendAndPause("6. 群聊消息");
                    channel.writeAndFlush(MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setReqType(REQ_GROUP)
                            .setMsgTime(System.currentTimeMillis())
                            .setSenderId("user-001")
                            .setSenderName("张三")
                            .setGroupId("group-100")
                            .setGroupName("Getty技术交流群")
                            .setBody("大家好，这是群聊测试消息！")
                            .setBodyType(1)
                            .build());
                    Thread.sleep(500);

                    // ---- 7. ACK 确认 ----
                    sendAndPause("7. ACK 确认");
                    channel.writeAndFlush(MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setReqType(REQ_ACK)
                            .setMsgTime(System.currentTimeMillis())
                            .setAck("msg-20240101-001")
                            .setSenderId("user-001")
                            .build());
                    Thread.sleep(500);

                    // ---- 8. 全字段消息（填充所有 25 个字段）----
                    sendAndPause("8. 全字段消息");
                    channel.writeAndFlush(MessageClass.Message.newBuilder()
                            .setId(newId())
                            .setIdentify("IM")
                            .setVersion("2.0")
                            .setReqType(REQ_SINGLE)
                            .setMsgTime(System.currentTimeMillis())
                            .setServerId("server-001")
                            .setAck("ack-full")
                            .setSenderId("user-001")
                            .setSenderName("张三")
                            .setSenderHeadImgUrl("http://example.com/avatar/zhangsan.jpg")
                            .setReceiverId("user-002")
                            .setReceiverName("李四")
                            .setReceiverHeadImgUrl("http://example.com/avatar/lisi.jpg")
                            .setGroupId("group-100")
                            .setGroupName("Getty技术交流群")
                            .setGroupHeadImgUrl("http://example.com/avatar/group.jpg")
                            .setAtUserId("user-003")
                            .setBody("这是一条包含全部字段的消息，用于验证序列化/反序列化的完整性。")
                            .setBodyType(2)
                            .setBodyLength(100)
                            .setStatus(0)
                            .setResult(0)
                            .setField1("扩展字段1")
                            .setField2("扩展字段2")
                            .setField3("扩展字段3")
                            .build());
                    Thread.sleep(500);

                    // ---- 9. 连续快速发送（粘包场景）----
                    sendAndPause("9. 连续快速发送 20 条（粘包测试）");
                    for (int i = 0; i < 20; i++) {
                        channel.writeAndFlush(MessageClass.Message.newBuilder()
                                .setId(newId())
                                .setReqType(REQ_SINGLE)
                                .setMsgTime(System.currentTimeMillis())
                                .setSenderId("user-001")
                                .setSenderName("张三")
                                .setReceiverId("user-002")
                                .setReceiverName("李四")
                                .setBody("快速消息-" + i)
                                .setBodyType(1)
                                .build());
                    }
                    Thread.sleep(3000);

                    // ---- 10. 批量发送（性能测试）----
                    sendAndPause("10. 批量发送 100 条（性能测试）");
                    long startTime = System.currentTimeMillis();
                    for (int i = 0; i < 100; i++) {
                        channel.writeAndFlush(MessageClass.Message.newBuilder()
                                .setId(newId())
                                .setReqType(REQ_SINGLE)
                                .setMsgTime(System.currentTimeMillis())
                                .setSenderId("user-001")
                                .setSenderName("张三")
                                .setReceiverId("user-002")
                                .setReceiverName("李四")
                                .setBody("性能测试消息-" + i)
                                .setBodyType(1)
                                .build());
                    }
                    long sendTime = System.currentTimeMillis() - startTime;
                    System.out.println("[客户端] 100 条消息发送耗时: " + sendTime + "ms");
                    Thread.sleep(5000);

                    // ---- 11. 心跳连续发送（保活测试）----
                    sendAndPause("11. 连续 5 次心跳（保活测试）");
                    for (int i = 0; i < 5; i++) {
                        channel.writeAndFlush(MessageClass.Message.newBuilder()
                                .setId(newId())
                                .setReqType(REQ_HEARTBEAT)
                                .setMsgTime(System.currentTimeMillis())
                                .setSenderId("user-001")
                                .build());
                        Thread.sleep(1000);
                    }

                    System.out.println("\n========== 全部场景测试完成 ==========");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailed(Throwable exc) {
                System.out.println("[客户端] 连接失败: " + exc.getMessage());
                exc.printStackTrace();
            }
        });
    }

    /** 生成唯一消息 ID */
    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 打印场景标题 */
    private static void sendAndPause(String title) throws InterruptedException {
        System.out.println("\n---- " + title + " ----");
    }
}
