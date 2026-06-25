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
package com.gettyio.core.handler.codec;

/**
 * 解码操作的结果容器。
 * <p>
 * 采用不可变设计，支持三种状态：{@link #UNFINISHED}（解码尚未完成）、
 * {@link #SUCCESS}（解码成功）、{@link #failure(Throwable)}（解码失败）。
 * </p>
 *
 * <p>使用哨兵 {@link Throwable} 实例区分内部状态，避免额外的枚举对象分配。</p>
 *
 * <p><b>典型用法：</b></p>
 * <pre>{@code
 * DecoderResult result = decoder.decode(buffer);
 * if (result.isSuccess()) {
 *     // 处理解码成功
 * } else if (result.isFailure()) {
 *     logger.error("解码失败", result.cause());
 * }
 * }</pre>
 */
public class DecoderResult {

    /** 哨兵：解码未完成（数据不足，需要更多字节） */
    protected static final Throwable SIGNAL_UNFINISHED = new Throwable("UNFINISHED");

    /** 哨兵：解码成功 */
    protected static final Throwable SIGNAL_SUCCESS = new Throwable("SUCCESS");

    /** 单例：表示"解码未完成" */
    public static final DecoderResult UNFINISHED = new DecoderResult(SIGNAL_UNFINISHED);

    /** 单例：表示"解码成功" */
    public static final DecoderResult SUCCESS = new DecoderResult(SIGNAL_SUCCESS);

    /**
     * 创建一个表示解码失败的结果。
     *
     * @param cause 失败原因，不能为 null
     * @return 失败结果实例
     * @throws NullPointerException 如果 cause 为 null
     */
    public static DecoderResult failure(Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        return new DecoderResult(cause);
    }

    /** 失败原因或哨兵信号 */
    private final Throwable cause;

    protected DecoderResult(Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        this.cause = cause;
    }

    /**
     * 判断解码是否已完成（包括成功和失败）。
     * 未完成表示数据不足，需要更多输入。
     */
    public boolean isFinished() {
        return cause != SIGNAL_UNFINISHED;
    }

    /**
     * 判断解码是否成功。
     */
    public boolean isSuccess() {
        return cause == SIGNAL_SUCCESS;
    }

    /**
     * 判断解码是否失败。
     * 失败时可通过 {@link #cause()} 获取具体原因。
     */
    public boolean isFailure() {
        return cause != SIGNAL_SUCCESS && cause != SIGNAL_UNFINISHED;
    }

    /**
     * 获取失败原因。仅当 {@link #isFailure()} 为 true 时返回非 null 值。
     *
     * @return 失败原因，成功或未完成时返回 null
     */
    public Throwable cause() {
        return isFailure() ? cause : null;
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "success";
        }
        if (!isFinished()) {
            return "unfinished";
        }
        String msg = cause.toString();
        return new StringBuilder(msg.length() + 17)
                .append("failure(")
                .append(msg)
                .append(')')
                .toString();
    }
}
