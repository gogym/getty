
/**
 * MQTT 协议编解码器及相关消息类型定义。
 * <p>
 * 本包实现了 MQTT v3.1 和 v3.1.1 协议的消息编码和解码，包括：
 * </p>
 * <ul>
 *   <li>{@link com.gettyio.expansion.handler.codec.mqtt.MqttDecoder} — MQTT 消息解码器（状态机模式）</li>
 *   <li>{@link com.gettyio.expansion.handler.codec.mqtt.MqttEncoder} — MQTT 消息编码器（单例模式）</li>
 *   <li>{@link com.gettyio.expansion.handler.codec.mqtt.MqttMessageBuilders} — 消息构建器工厂（Builder 模式）</li>
 *   <li>各种消息类型：CONNECT、CONNACK、PUBLISH、SUBSCRIBE、UNSUBSCRIBE 等</li>
 * </ul>
 * <p>基于 Netty MQTT Codec 改造，适配 Getty 框架。</p>
 */
package com.gettyio.expansion.handler.codec.mqtt;
