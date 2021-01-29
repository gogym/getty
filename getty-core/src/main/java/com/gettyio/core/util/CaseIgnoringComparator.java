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
package com.gettyio.core.util;

import java.io.Serializable;
import java.util.Comparator;


/**
 * 忽略大小写的比较器
 * @ClassName CaseIgnoringComparator.java
 * @author gogym.ggj
 * @version 1.0.0
 * @Description
 * @createTime 2020/12/15 14:34:21
 */
public final class CaseIgnoringComparator implements Comparator<String>, Serializable {

    private static final long serialVersionUID = 8426899657127533862L;

    public static final CaseIgnoringComparator INSTANCE = new CaseIgnoringComparator();

    private CaseIgnoringComparator() {
        super();
    }

    @Override
    public int compare(String o1, String o2) {
        return o1.compareToIgnoreCase(o2);
    }

    private Object readResolve() {
        return INSTANCE;
    }
}