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
package com.gettyio.core.pipeline;

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

/**
 * 默认空处理器。
 * <p>
 * 用作管道 head 和 tail 哨兵节点的处理器。继承 {@link ChannelHandlerAdapter}，
 * 对所有事件均执行默认的透传行为，不参与任何业务处理。
 * </p>
 * <p>
 * <b>异常终止点：</b>当异常沿管道传播到尾部哨兵时，{@link #exceptionCaught} 会记录日志
 * 并不再向后传播，从而终止异常传播链，避免未被处理的异常被静默吞掉。
 * </p>
 */
class DefaultChannelHandler extends ChannelHandlerAdapter {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DefaultChannelHandler.class);

    /**
     * 异常传播终点：记录未处理的异常并终止传播链。
     * <p>
     * 与父类默认实现不同，此方法不调用 {@code ctx.fireChannelProcess}，
     * 从而阻止异常继续向后续节点传播。当异常到达尾部哨兵时，
     * 由此方法统一记录日志。
     * </p>
     *
     * @param ctx   通道上下文
     * @param cause 异常原因
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("an exception was thrown by a pipeline handler (uncaught)", cause);
    }
}
