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


import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;


import java.text.ParseException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


/**
 * DateTimeUtil.java
 *
 * @description:时间工具类
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public class DateTimeUtil {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(DateTimeUtil.class);

    /**
     * 获取当前系统时间
     *
     * @return
     */
    public static String getCurrentTime() {
        // 设置日期格式
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // new Date()为获取当前系统时间
        String time = df.format(new Date());
        return time;
    }

    /**
     * Description: 获取当前系统日期
     *
     * @return
     * @see
     */
    public static String getCurrentDate() {
        // 设置日期格式
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        // new Date()为获取当前系统时间
        String time = df.format(new Date());
        return time;
    }

    /**
     * 获取当前系统时间
     *
     * @return
     */
    public static Long getCurrentLongTime() {
        Long time = System.currentTimeMillis();
        return time;
    }

    /**
     * date类型转String类型
     *
     * @param date
     * @return
     */
    public static String convertDateToString(Date date) {
        // 设置日期格式
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(date);

    }

    /**
     * date类型转String类型
     *
     * @param date
     * @return
     */
    public static String convertLongToString(Long date) {
        // 设置日期格式
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(new Date(date));

    }

    /**
     * String类型转date类型
     *
     * @param time
     * @return
     */
    public static Date convertStringToDate(String time) {
        // 设置日期格式
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return df.parse(time);
        } catch (ParseException e) {
            LOGGER.error(e);
        }
        return null;
    }


    /**
     * 获得指定时间的前一天
     *
     * @param specifiedDay
     * @return
     * @throws Exception
     */
    public static String getSpecifiedDayBefore(String specifiedDay) {
        Calendar c = Calendar.getInstance();
        Date date = null;
        try {
            date = new SimpleDateFormat("yy-MM-dd HH:mm:ss").parse(specifiedDay);
        } catch (ParseException e) {
            LOGGER.error(e);
        }
        c.setTime(date);
        int day = c.get(Calendar.DATE);
        c.set(Calendar.DATE, day - 1);

        String dayBefore = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(c.getTime());
        return dayBefore;
    }

    /**
     * 获得指定时间的后一天
     *
     * @param specifiedDay
     * @return
     */
    public static String getSpecifiedDayAfter(String specifiedDay) {
        Calendar c = Calendar.getInstance();
        Date date = null;

        try {
            date = new SimpleDateFormat("yy-MM-dd HH:mm:ss").parse(specifiedDay);
        } catch (ParseException e) {
            LOGGER.error(e);
        }

        c.setTime(date);
        int day = c.get(Calendar.DATE);
        c.set(Calendar.DATE, day + 1);

        String dayAfter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(c.getTime());
        return dayAfter;
    }

    /**
     * 获得指定时间的前一天
     *
     * @param specifiedDay
     * @return
     * @throws Exception
     */
    public static String getSpecifiedDayBefore(Date specifiedDay) {
        Calendar c = Calendar.getInstance();
        Date date = specifiedDay;
        c.setTime(date);
        int day = c.get(Calendar.DATE);
        c.set(Calendar.DATE, day - 1);

        String dayBefore = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(c.getTime());
        return dayBefore;
    }

    /**
     * 获得指定时间的后一天
     *
     * @param specifiedDay
     * @return
     */
    public static String getSpecifiedDayAfter(Date specifiedDay) {
        Calendar c = Calendar.getInstance();
        Date date = specifiedDay;

        c.setTime(date);
        int day = c.get(Calendar.DATE);
        c.set(Calendar.DATE, day + 1);

        String dayAfter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(c.getTime());
        return dayAfter;
    }

    /**
     * 获得当天0点时间
     *
     * @author：gj
     * @date: 2017/3/10
     * @time: 12:29
     **/
    public static Long getTimesMorning() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }


}
