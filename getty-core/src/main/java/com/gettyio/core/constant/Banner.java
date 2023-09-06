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
 * @author gogym.ggj
 * @version 1.0.0
 * @ClassName Banner.java
 * @Description TODO
 * @createTime 2021/01/28/ 18:44:00
 */
public class Banner {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(Banner.class);

    public static final String BANNER =
            "                       tt     yt             \n" +
                    "                       tt     ye             \n" +
                    "  ttttt      tttt     teet   ytety   tt   ty \n" +
                    " tetytgt    yey tt     et     tey    tey yet \n" +
                    "ytt  yet    et   ey    tt     ye     yet tey \n" +
                    "yet  yet    getttty    tt     ye      ttyet  \n" +
                    "ytt  ygt    et         tt     ye      yetey  \n" +
                    " tetytgt    yetytt     teyy   yeyy     tgt   \n" +
                    "     tet     tttty     ytty    tty     tey   \n" +
                    "ytt  yey                               te    \n" +
                    " ttttty                              yttt    \n" +
                    "   yy                                yyy     \n";


    public static void printBanner() {
        //打印框架信息
        LOGGER.info("\r\n" + Banner.BANNER + "\r\n  getty version:(" + Version.VERSION + ")");
    }


}
