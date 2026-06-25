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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Getty 框架版本信息。
 * <p>
 * 版本号从 Maven 构建时自动注入到 {@code getty.properties} 中（通过 resource filtering），
 * 运行时从 classpath 读取，确保与 {@code pom.xml} 中的 {@code <version>} 始终一致。
 * <p>
 * 修改版本号只需更新根 pom.xml，无需手动同步 Java 代码。
 *
 * @author gogym.ggj
 * @see Banner
 */
public final class Version {

    /** properties 文件在 classpath 中的路径 */
    private static final String PROPERTIES_PATH = "/getty.properties";

    /** properties 文件中版本号的 key */
    private static final String VERSION_KEY = "getty.version";

    /** 当无法读取 properties 时的默认值 */
    private static final String UNKNOWN = "unknown";

    /** 当前框架版本号（启动时从 getty.properties 加载，仅读取一次） */
    public static final String VERSION = loadVersion();

    /**
     * 从 classpath 中的 {@code getty.properties} 加载版本号。
     * <p>
     * 该文件由 Maven resource filtering 在构建时将 {@code ${project.version}}
     * 替换为实际的版本号（如 "2.2.0"）。
     *
     * @return 版本号字符串；加载失败时返回 {@code "unknown"}
     */
    private static String loadVersion() {
        try (InputStream in = Version.class.getResourceAsStream(PROPERTIES_PATH)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty(VERSION_KEY, UNKNOWN);
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    private Version() {
    }
}
