package com.gettyio.core.util;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;


/**
 * 数据工具类
 *
 * @author getty
 * @version 2017年8月30日
 * @see DateTimeUtil
 * @since
 */
public class DateTimeUtil {

    /**
     * 获取当前系统时间
     *
     * @return
     * @author getty
     * @date 2016-4-28 上午10:07:54
     * @comment
     */
    public static String getCurrentTime() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// 设置日期格式
        String time = df.format(new Date());// new Date()为获取当前系统时间
        return time;
    }

    /**
     * Description: 获取当前系统日期
     *
     * @return
     * @see
     */
    public static String getCurrentDate() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");// 设置日期格式
        String time = df.format(new Date());// new Date()为获取当前系统时间
        return time;
    }

    /**
     * 获取当前系统时间
     *
     * @return
     * @author getty
     * @date 2016-4-28 上午10:07:54
     * @comment
     */
    public static Long getCurrentLongTime() {
        Long time = new Date().getTime();// new Date()为获取当前系统时间
        return time;
    }

    /**
     * date类型转String类型
     *
     * @param date
     * @return
     * @author getty
     * @date 2016-4-28 上午10:10:25
     * @comment
     */
    public static String convertDateToString(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// 设置日期格式
        return df.format(date);

    }

    /**
     * date类型转String类型
     *
     * @param date
     * @return
     * @author getty
     * @date 2016-4-28 上午10:10:25
     * @comment
     */
    public static String convertLongToString(Long date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// 设置日期格式
        return df.format(new Date(date));

    }

    /**
     * String类型转date类型
     *
     * @param date
     * @return
     * @author getty
     * @date 2016-4-28 上午10:10:25
     * @comment
     */
    public static Date convertStringToDate(String time) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// 设置日期格式
        try {
            return df.parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 指定日期加上天数后的日期
     *
     * @param num     为增加的天数
     * @param newDate 指定时间
     * @return
     * @throws ParseException
     */
    public static String plusDay(int num, String newDate) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date currdate = format.parse(newDate);
            Calendar ca = Calendar.getInstance();
            ca.add(Calendar.DATE, num);// num为增加的天数，可以改变的
            currdate = ca.getTime();
            String enddate = format.format(currdate);
            return enddate;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 返回结束日期的23:59:59
     *
     * @param num
     * @param currdate
     * @return
     */
    public static Date plusDayToDate(int num) {
        Calendar ca = Calendar.getInstance();
        ca.add(Calendar.DATE, num);// num为增加的天数，可以改变的
        long t = ca.getTimeInMillis() / (1000 * 3600 * 24) * (1000 * 3600 * 24)
                - TimeZone.getDefault().getRawOffset() + (1000 * 3600 * 24) - 1000;
        Date currdate = new Date(t);
        return currdate;
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
            e.printStackTrace();
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
            e.printStackTrace();
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
    public static Long getTimesmorning() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 获取星期几
     *
     * @author：gj
     * @date: 2017/3/10
     * @time: 14:19
     **/
    public static String getWeekOfDate(Date dt) {
        String[] weekDays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        Calendar cal = Calendar.getInstance();
        cal.setTime(dt);
        int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (w < 0) w = 0;
        return weekDays[w];
    }

    /**
     * 获取时间点，类似微信
     *
     * @author：gj
     * @date: 2017/3/10
     * @time: 14:49
     **/
    public static String getTimePoint(String time) {
        Long Tmp = Long.valueOf(time);
        return getTimePoint(Tmp);
    }

    public static String getTimePoint(Long time) {
        // 现在时间
        Long now = new Date().getTime();
        DateFormat df;
        int day = (60 * 60 * 24) * 1000;
        String pointText = "1970-01-01";

        // 时间点比当天零点早
        if (time <= now && time != null) {
            Date date = new Date(time);
            if (time < getTimesmorning()) {
                if (time >= getTimesmorning() - day) {// 比昨天零点晚
                    pointText = "昨天";
                    return pointText;
                } else {// 比昨天零点早
                    if (time >= getTimesmorning() - 6 * day) {// 比七天前的零点晚，显示星期几
                        return getWeekOfDate(date);
                    } else {// 显示具体日期
                        df = new SimpleDateFormat("yyyy-MM-dd");
                        pointText = df.format(date);
                        return pointText;
                    }
                }
            } else {// 无日期时间,当天内具体时间
                df = new SimpleDateFormat("HH:mm");
                pointText = df.format(date);
                return pointText;
            }
        }
        return pointText;
    }

    /**
     * 获取时间间隔提示，类似微博
     *
     * @author：gj
     * @date: 2017/6/12
     * @time: 13:08
     **/
    public static String getInterval(Long t) {
        String interval = null;
        // 用现在距离1970年的时间间隔new Date().getTime()减去以前的时间距离1970年的时间间隔d1.getTime()得出的就是以前的时间与现在时间的时间间隔
        long time = new Date().getTime() - t;// 得出的时间间隔是毫秒

        if (time / 1000 < 10 && time / 1000 >= 0) {
            // 如果时间间隔小于10秒则显示“刚刚”time/10得出的时间间隔的单位是秒
            interval = "刚刚";

        } else if (time / 1000 < 60 && time / 1000 > 0) {
            // 如果时间间隔小于60秒则显示多少秒前
            int se = (int) ((time % 60000) / 1000);
            interval = se + "秒前";

        } else if (time / 60000 < 60 && time / 60000 > 0) {
            // 如果时间间隔小于60分钟则显示多少分钟前
            int m = (int) ((time % 3600000) / 60000);// 得出的时间间隔的单位是分钟
            interval = m + "分钟前";

        } else if (time / 3600000 < 24 && time / 3600000 >= 0) {
            // 如果时间间隔小于24小时则显示多少小时前
            int h = (int) (time / 3600000);// 得出的时间间隔的单位是小时
            interval = h + "小时前";

        } else if (time / 3600000 / 24 < 30 && time / 3600000 / 24 >= 0) {
            // 如果时间小于30天
            int h = (int) (time / 3600000 / 24);
            interval = h + "天前";
        } else {
            // 大于30天，则显示正常的时间，但是不显示秒
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            ParsePosition pos2 = new ParsePosition(0);
            // Date d2 = (Date) sdf.parse(creattetime, pos2);
            Date d2 = new Date(t);
            interval = sdf.format(d2);
        }
        return interval;

    }

    public static String getInterval(String createtime) { // 传入的时间格式必须类似于2012-8-21 17:53:20这样的格式
        String interval = null;
        SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ParsePosition pos = new ParsePosition(0);
        Date d1 = (Date) sd.parse(createtime, pos);
        return getInterval(d1.getTime());
    }

}
