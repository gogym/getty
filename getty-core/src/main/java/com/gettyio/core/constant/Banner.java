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
package com.gettyio.core.constant;

import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;

/**
 * Getty 框架启动横幅（Banner）。
 * <p>
 * 服务器启动时打印框架 Logo、版本号以及运行时环境信息，
 * 方便运维人员快速确认框架版本和 JVM / 操作系统配置。
 * <p>
 * 调用入口：{@link #printBanner()}，由 {@code AioServerStarter} / {@code NioServerStarter} 在启动时自动调用。
 *
 * @author gogym.ggj
 * @see Version
 */
public final class Banner {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(Banner.class);

    /**
     * "getty" ASCII Art Logo（使用 '#' 块字符绘制）。
     * <p>
     * 每个字母高 5 行、宽 5~7 列，整体对齐后形成统一的品牌标识。
     */
    public static final String BANNER =
            "                                                          \n" +
            "   ####   ####  #####  #####   #   #                      \n" +
            "  #      #        #      #      # #                       \n" +
            "  # ###  ###      #      #       #                        \n" +
            "  #   #  #        #      #       #                        \n" +
            "   ####   ####    #      #       #                        \n" +
            "                                                          \n";

    /** 分隔线宽度（与 Banner 等宽） */
    private static final String SEPARATOR =
            "----------------------------------------------------------";

    /**
     * 打印启动横幅。
     * <p>
     * 输出内容包括：
     * <ul>
     *   <li>ASCII Art Logo</li>
     *   <li>框架版本号</li>
     *   <li>JDK 版本</li>
     *   <li>操作系统与 CPU 核心数</li>
     * </ul>
     */
    public static void printBanner() {
        String javaVersion = System.getProperty("java.version", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        int processors = Runtime.getRuntime().availableProcessors();

        String info = "\n"
                + SEPARATOR + "\n"
                + "\n"
                + BANNER
                + "  :: Getty ::    (v" + Version.VERSION + ")\n"
                + "\n"
                + "  JDK         : " + javaVersion + "\n"
                + "  OS          : " + osName + " (" + osArch + ")\n"
                + "  Processors  : " + processors + "\n"
                + "\n"
                + SEPARATOR;

        LOGGER.info(info);
    }

    private Banner() {
    }
}
