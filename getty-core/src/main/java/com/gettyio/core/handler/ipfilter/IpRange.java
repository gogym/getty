/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.handler.ipfilter;

/**
 * IpRange.java
 *
 * @description:包装起止ip段
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class IpRange {

    /**
     * 开始ip
     */
    private String ipStart;

    /**
     * 结束ip
     */
    private String ipEnd;

    public IpRange(String ipStart, String ipEnd) {

        this.ipStart = ipStart;
        this.ipEnd = ipEnd;
    }

    public String getIpStart() {

        return ipStart;
    }

    public String getIpEnd() {

        return ipEnd;
    }

}
