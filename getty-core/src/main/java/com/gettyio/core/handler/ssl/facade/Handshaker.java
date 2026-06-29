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
package com.gettyio.core.handler.ssl.facade;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

/**
 * SSL 握手管理器。
 * <p>
 * 负责驱动 SSL 握手的完整流程。根据 {@link javax.net.ssl.SSLEngine} 返回的握手状态，
 * 依次处理 NEED_WRAP（生成握手数据）、NEED_UNWRAP（等待对端握手数据）、
 * NEED_TASK（执行委托任务）和 FINISHED（握手完成）。
 * </p>
 *
 * <p>握手任务直接在当前线程中同步执行，避免了外部任务调度器的开销。</p>
 */
class Handshaker {

    private final Worker worker;
    private final Runnable completionCallback;
    private boolean finished;

    /**
     * @param worker             执行 wrap/unwrap 操作的工作器
     * @param completionCallback 握手完成时的回调
     */
    Handshaker(Worker worker, Runnable completionCallback) {
        this.worker = worker;
        this.completionCallback = completionCallback;
    }

    /**
     * 发起握手。调用 SSLEngine.beginHandshake() 并开始握手状态机。
     */
    void begin() throws SSLException {
        worker.beginHandshake();
        processHandshake();
    }

    /**
     * 处理 unwrap 操作的结果，推进握手状态机。
     *
     * @param result SSLEngine.unwrap() 的返回结果
     */
    void handleDecrypt(SSLEngineResult result) throws SSLException {
        // 优先检查：握手可能已在递归 unwrap 中完成（processHandshake 内部触发了 finishHandshake）
        if (isFinished()) {
            return;
        }
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
            finishHandshake();
        } else {
            processHandshake();
        }
    }

    /**
     * 查询握手是否已完成。
     */
    boolean isFinished() {
        return finished;
    }

    // ---- 握手状态机 ----

    /**
     * 根据当前握手状态执行对应的操作，递归驱动握手直到需要等待外部数据。
     */
    private void processHandshake() throws SSLException {
        switch (worker.getHandshakeStatus()) {
            case NOT_HANDSHAKING:
                // 握手已结束（正常或异常），不再处理
                break;

            case FINISHED:
                finishHandshake();
                break;

            case NEED_TASK:
                // 同步执行所有委托任务，然后继续握手
                executeDelegatedTasks();
                processHandshake();
                break;

            case NEED_WRAP:
                // 生成握手数据发送给对端
                SSLEngineResult wrapResult = worker.wrap((ByteBuffer) null);
                if (wrapResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                    finishHandshake();
                } else {
                    processHandshake();
                }
                break;

            case NEED_UNWRAP:
                // 需要等待对端数据，如果有缓存的数据则继续处理
                if (worker.hasPendingData()) {
                    SSLEngineResult unwrapResult = worker.unwrap((ByteBuffer) null);
                    if (unwrapResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                        finishHandshake();
                    } else if (unwrapResult.getStatus() == SSLEngineResult.Status.OK) {
                        processHandshake();
                    }
                }
                // 否则等待下一次 channelRead 带来更多数据
                break;
        }
    }

    /**
     * 同步执行 SSLEngine 的所有委托任务。
     * <p>这些任务通常包含耗时的密钥生成操作，此处直接在当前线程执行以简化架构。</p>
     */
    private void executeDelegatedTasks() {
        Runnable task;
        while ((task = worker.getDelegatedTask()) != null) {
            task.run();
        }
    }

    /**
     * 标记握手完成并触发回调。
     */
    private void finishHandshake() {
        if (!finished) {
            finished = true;
            completionCallback.run();
        }
    }
}
