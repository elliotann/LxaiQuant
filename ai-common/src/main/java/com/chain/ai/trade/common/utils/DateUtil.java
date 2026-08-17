package com.chain.ai.trade.common.utils;

import cn.hutool.core.date.DatePattern;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 日期相关的操作
 *
 * @author andy
 */
public class DateUtil {

    public static final String STANDARD_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final String STANDARD_DATE_FORMAT = "yyyy-MM-dd";

    public static final String HOUR_FORMAT = "yyyy-MM-dd HH";

    public static final String MONTH_FORMAT = "yyyy-MM";

    public static final String STANDARD_DATE_NO_UNDERLINE_FORMAT = "yyyyMMdd";

    public static final String FULL_DATE = "yyyyMMddHHmmss";


    /**
     * 当天的开始时间
     *
     * @return 今天开始时间
     */
    public static Long getDayOfStart() {
        return DateUtil.getDateline()/(60*24*60);
    }
    /**
     * 指定日的开始时间
     *
     * @return 指定日时间
     */
    public static Long getDayOfStart(Date date) {
        return date.getTime()/(60*24*60);
    }

    /**
     * 当天的开始时间
     *
     * @return 今天开始时间
     */
    public static Date startOfTodDayTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * UNIX timestamp ISO 8601 rule eg: 2018-02-03T05:34:14.110Z
     * OKX requires the format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (fixed 3 decimal places)
     */
    public static String getUnixTime() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneId.of("UTC"))
                .format(Instant.now());
    }
    /**
     * 当天的开始时间
     *
     * @param date 时间
     * @return 根据传入的时间获取开始时间
     */
    public static Date startOfTodDayTime(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 当天的开始时间
     *
     * @return 今天开始时间
     */
    public static long startOfTodDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date = calendar.getTime();
        return date.getTime() / 1000;
    }

    /**
     * 当天的结束时间
     *
     * @return 今天结束时间
     */
    public static Date endOfDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    /**
     * 当天的结束时间
     *
     * @param date 传入日期
     * @return 获得传入日期当天结束时间
     */
    public static Date endOfDate(Date date) {
        if (date == null) {
            date = new Date();
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    /**
     * 某天的年月日
     *
     * @param dayUntilNow 距今多少天以前
     * @return 年月日map key为 year month day
     */
    public static Map<String, Object> getYearMonthAndDay(int dayUntilNow) {

        Map<String, Object> map = new HashMap<String, Object>(3);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DATE, -dayUntilNow);
        map.put("year", calendar.get(Calendar.YEAR));
        map.put("month", calendar.get(Calendar.MONTH) + 1);
        map.put("day", calendar.get(Calendar.DAY_OF_MONTH));
        return map;
    }

    /**
     * 将一个字符串转换成日期格式
     *
     * @param date    字符串日期
     * @param pattern 日期格式
     * @return date
     */
    public static Date toDate(String date, String pattern) {
        if ("".equals("" + date)) {
            return null;
        }
        if (pattern == null) {
            pattern = STANDARD_DATE_FORMAT;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
        Date newDate = new Date();
        try {
            newDate = sdf.parse(date);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return newDate;
    }

    /**
     * 获取上个月的开始结束时间
     *
     * @return 上个月的开始结束时间
     */
    public static Long[] getLastMonth() {
        //取得系统当前时间
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;

        //取得系统当前时间所在月第一天时间对象
        cal.set(Calendar.DAY_OF_MONTH, 1);

        //日期减一,取得上月最后一天时间对象
        cal.add(Calendar.DAY_OF_MONTH, -1);

        //输出上月最后一天日期
        int day = cal.get(Calendar.DAY_OF_MONTH);

        String months = "";
        String days = "";

        if (month > 1) {
            month--;
        } else {
            year--;
            month = 12;
        }
        if (String.valueOf(month).length() <= 1) {
            months = "0" + month;
        } else {
            months = String.valueOf(month);
        }
        if (String.valueOf(day).length() <= 1) {
            days = "0" + day;
        } else {
            days = String.valueOf(day);
        }
        String firstDay = "" + year + "-" + months + "-01";
        String lastDay = "" + year + "-" + months + "-" + days + " 23:59:59";

        Long[] lastMonth = new Long[2];
        lastMonth[0] = DateUtil.getDateline(firstDay);
        lastMonth[1] = DateUtil.getDateline(lastDay, STANDARD_FORMAT);

        return lastMonth;
    }

    /**
     * 把日期转换成字符串型
     *
     * @param date 日期
     * @return 字符串时间
     */
    public static String toString(Date date) {
        return toString(date, STANDARD_FORMAT);
    }

    /**
     * 把日期转换成字符串型
     *
     * @param date 日期
     * @return 字符串时间
     */
    public static String toString(Long date) {
        return toString(date, STANDARD_FORMAT);
    }

    /**
     * 把日期转换成字符串型
     *
     * @param date    日期
     * @param pattern 类型
     * @return 字符串时间
     */
    public static String toString(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        if (pattern == null) {
            pattern = STANDARD_DATE_FORMAT;
        }
        String dateString = "";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        try {
            dateString = sdf.format(date);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return dateString;
    }

    /**
     * 时间戳转换成时间类型
     *
     * @param time    时间戳
     * @param pattern 格式
     * @return 字符串时间
     */
    public static String toString(Long time, String pattern) {
        if (time > 0) {
            if (time.toString().length() == 10) {
                time = time * 1000;
            }
            Date date = new Date(time);
            return DateUtil.toString(date, pattern);
        }
        return "";
    }

    /**
     * 判断当前时间是否在某个时间范围
     *
     * @param start 开始时间，以秒为单位的时间戳
     * @param end   结束时间，以秒为单位的时间戳
     * @return 是否在范围内
     */
    public static boolean inRangeOf(long start, long end) {
        long now = getDateline();
        return start <= now && end >= now;
    }

    /**
     * 获取指定日期的时间戳
     *
     * @param date 指定日期
     * @return 时间戳
     */
    public static long getDateline(String date) {
        return Objects.requireNonNull(toDate(date, STANDARD_DATE_FORMAT)).getTime() / 1000;
    }


    /**
     * 获取当前时间的时间戳
     *
     * @return 时间戳
     */
    public static long getDateline() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 获取当前时间格式化字符串
     *
     * @return 时间戳
     */
    public static String getCurrentDateStr(String format) {
        return toString(new Date(), format);
    }

    /**
     * 获取当前时间格式化字符串
     *
     * @return 格式化的时间
     */
    public static String getCurrentDateStr() {
        return toString(new Date(), FULL_DATE);
    }

    /**
     * 根据日期格式及日期获取时间戳
     *
     * @param date    日期
     * @param pattern 日期格式
     * @return 时间戳
     */
    public static long getDateline(String date, String pattern) {
        return Objects.requireNonNull(toDate(date, pattern)).getTime() / 1000;
    }

    /**
     * 获取几个月之前的日期时间戳
     *
     * @param beforeMonth 几个月之前
     * @return 时间戳
     */
    public static long getBeforeMonthDateline(int beforeMonth) {
        SimpleDateFormat format = new SimpleDateFormat(STANDARD_FORMAT);
        Calendar c = Calendar.getInstance();

        //过去一月
        c.setTime(new Date());
        c.add(Calendar.MONTH, (0 - beforeMonth));
        Date m = c.getTime();
        String mon = format.format(m);
        return getDateline(mon, STANDARD_FORMAT);
    }

    /**
     * 获取当前天的结束时间
     *
     * @return 当前天的结束时间
     */
    public static Date getCurrentDayEndTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.DATE, cal.get(Calendar.DATE) + 1);
        cal.set(Calendar.SECOND, cal.get(Calendar.SECOND) - 1);
        return cal.getTime();
    }

    /**
     * 获取延时时间（秒）
     *
     * @param startTime 开始时间
     * @return 延时时间（秒）
     */
    public static Integer getDelayTime(Long startTime) {
        int time = Math.toIntExact((startTime - System.currentTimeMillis()) / 1000);
        //如果时间为负数则改为一秒后执行
        if (time <= 0) {
            time = 1;
        }
        return time;
    }

    /**
     * 获取某年某月开始时间
     *
     * @param year  年
     * @param month 月
     * @return 开始时间
     */
    public static Date getBeginTime(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate localDate = yearMonth.atDay(1);
        LocalDateTime startOfDay = localDate.atStartOfDay();
        ZonedDateTime zonedDateTime = startOfDay.atZone(ZoneId.of("Asia/Shanghai"));

        return Date.from(zonedDateTime.toInstant());

    }

    // 获取指定时间段的开始时间，并返回指定格式的字符串
    public static String getStartTimeString(LocalDateTime dateTime, int intervalMinutes) {
        LocalDateTime startTime = getStartTime(dateTime, intervalMinutes);
        return formatDateTime(startTime);
    }

    // 获取指定时间段的结束时间，并返回指定格式的字符串
    public static String getEndTimeString(LocalDateTime dateTime, int intervalMinutes) {
        LocalDateTime endTime = getEndTime(dateTime, intervalMinutes);
        return formatDateTime(endTime);
    }

    // 获取指定时间段的开始时间
    public static LocalDateTime getStartTime(LocalDateTime dateTime, int intervalMinutes) {
        int currentMinute = dateTime.getMinute();
        int remainingMinutes = currentMinute % intervalMinutes;
        return dateTime.minusMinutes(remainingMinutes).minusSeconds(dateTime.getSecond()).withNano(0);
    }

    // 获取指定时间段的结束时间
    public static LocalDateTime getEndTime(LocalDateTime dateTime, int intervalMinutes) {
        LocalDateTime startTime = getStartTime(dateTime, intervalMinutes);
        return startTime.plusMinutes(intervalMinutes - 1).plusSeconds(60).plusNanos(999999999);
    }

    // 格式化日期时间为指定格式的字符串
    public static String formatDateTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }

    /**
     * 获取某年某月结束时间
     *
     * @param year  年
     * @param month 月
     * @return 结束时间
     */
    public static Date getEndTime(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        LocalDateTime localDateTime = endOfMonth.atTime(23, 59, 59, 999);
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.of("Asia/Shanghai"));
        return Date.from(zonedDateTime.toInstant());
    }

    /**
     * 获取两个时间相差小时数
     * @param startDate
     * @param endDate
     * @return
     */
    public static int getDifferHour(Date startDate, Date endDate) {
        long dayM = 1000 * 24 * 60 * 60;
        long hourM = 1000 * 60 * 60;
        long differ = endDate.getTime() - startDate.getTime();
        long hour = differ % dayM / hourM + 24 * (differ / dayM);
        return Integer.parseInt(String.valueOf(hour));
    }

    /**
     * 获取两个时间相差小时数
     * @param startDate
     * @param endDate
     * @return
     */
    public static int getDifferTime(Date startDate, Date endDate,String unit) {
        if("MIN".equals(unit)){
            long differ = endDate.getTime() - startDate.getTime();
            long hour = differ/(1000*60l);
            return Integer.parseInt(String.valueOf(hour));
        }
        if("DAY".equals(unit)){
            long differ = endDate.getTime() - startDate.getTime();
            long hour = differ/(1000*60*60*24l);
            return Integer.parseInt(String.valueOf(hour));
        }
        return 0;
    }

    /**
     * 增加指定日期的年或月或天
     * @param date
     * @param type
     * @param addTime
     * @return
     */
    public static Date addTime(Date date,int type,int addTime){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(type,addTime);
        return cal.getTime();
    }

    public static String longConvertDateTime(Long timestamp){
        if((timestamp.toString()).length()==16){
            timestamp = timestamp/1000;
        }
        if((timestamp.toString()).length()==10){
            timestamp = timestamp*1000;
        }
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat(STANDARD_FORMAT);
        return simpleDateFormat.format(timestamp);
    }

    public static Date longConvertDate(Long timestamp){
        if((timestamp.toString()).length()==16){
            timestamp = timestamp/1000;
        }
        if((timestamp.toString()).length()==10){
            timestamp = timestamp*1000;
        }
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat(STANDARD_FORMAT);
        return DateUtil.toDate(simpleDateFormat.format(timestamp),STANDARD_FORMAT);
    }

    /**
     * 将K线开始时间Instant转换为订单时间Date（减去8小时时区偏移）。
     *
     * @param instant K线开始时间
     * @return 转换后的订单时间Date
     */
    public static Date toOrderDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return longConvertDate(instant.toEpochMilli() - 8 * 60 * 60 * 1000L);
    }

    /**
     * 比较
     * @param startDate
     * @param endDate
     * @return
     */
    public static int compare(Date startDate,Date endDate){
        if(startDate.getTime()>endDate.getTime()){
            return 1;
        }
        if(startDate.getTime()<endDate.getTime()){
            return -1;
        }
        return 0;
    }

    /**
     * 获取以5分钟，30分钟为间隔开始时间
     * @return
     */
    public static long getSpaceTime(LocalDateTime date,int space){
        //获取当前时间

        int minute = date.getMinute();
        //格式化输出
        String pattern = "yyyy-MM-dd HH:mm:00";
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
        String format = date.format(dateTimeFormatter);

        DateTimeFormatter df = DateTimeFormatter.ofPattern(pattern);
        LocalDateTime changeDate =  LocalDateTime.parse(format,df);
        long timestamp = Timestamp.valueOf(changeDate).getTime()-minute%space*60*1000;

        return timestamp;
    }

    // 判断字符串日期是否在距指定日期指定天数前内
    public static boolean isBeforeDays(String dateStr, LocalDate lastDate, int days) {
        // 定义日期格式，这里假设你的日期字符串格式是 "yyyy-MM-dd"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 将字符串转换为 LocalDate
        LocalDate date = LocalDate.parse(dateStr, formatter);

        // 计算两个日期之间的天数
        long daysBetween = ChronoUnit.DAYS.between(date, lastDate);

        // 如果天数在范围内，返回 true
        return daysBetween <= days && daysBetween >= 0;
    }

    /**
     * 将时间字符串对齐到前一个指定间隔的分钟倍数(简单来说就是向前取整，比如2024-01-01 01:41:00，返回2024-01-01 01:30:00)
     * @param timeStr 输入时间字符串（格式需与pattern匹配）
     * @param intervalMinutes 对齐间隔（如15、30）
     * @return 对齐后的时间字符串
     */
    public static String alignToInterval(String timeStr, int intervalMinutes) {
        // 1. 创建时间格式化器(yyyy-MM-dd HH:mm:ss)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN);

        // 2. 解析字符串为LocalDateTime[1,10](@ref)
        LocalDateTime dateTime = LocalDateTime.parse(timeStr, formatter);

        // 3. 计算余数并对齐分钟数[1,7](@ref)
        int minutes = dateTime.getMinute();
        int remainder = minutes % intervalMinutes;
        LocalDateTime alignedTime = dateTime
                .minusMinutes(remainder)    // 对齐到前一个间隔倍数
                .withSecond(0)              // 秒归零[7](@ref)
                .withNano(0);               // 纳秒归零

        // 4. 格式化为字符串返回
        return alignedTime.format(formatter);
    }
    /**
     * 在指定时间上获取指定分钟后的时间
     * @param date
     * @param amount
     * @return
     */
    public static long strTimeToLong(String time){
        // 创建格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(STANDARD_FORMAT);

        // 解析字符串为LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.parse(time, formatter);

        // 转换为系统默认时区的ZonedDateTime，再转为Instant获取时间戳
        long timestamp = localDateTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return timestamp;
    }
    /**
     * 得到日期时间字符串，转换格式（yyyy-MM-dd HH:mm:ss）
     */
    public static String formatDateTime(Date date) {
        return formatDate(date, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 得到日期字符串 默认格式（yyyy-MM-dd） pattern可以为："yyyy-MM-dd" "HH:mm:ss" "E"
     */
    public static String formatDate(Date date, String pattern) {
        String formatDate = null;
        if (pattern != null) {
            formatDate = DateFormatUtils.format(date, pattern);
        } else {
            formatDate = DateFormatUtils.format(date, "yyyy-MM-dd");
        }
        return formatDate;
    }
    /**
     * 解析时间字符串为时间戳（毫秒）
     *
     * @param timeStr 时间字符串，支持 "yyyy-MM-dd HH:mm:ss" 或 "yyyy-MM-dd" 格式
     * @return 时间戳（毫秒），如果输入为null或空字符串则返回null
     * @throws IllegalArgumentException 如果时间格式无法解析
     */
    public static Long parseTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }

        try {
            // 尝试解析完整格式：yyyy-MM-dd HH:mm:ss
            // 将输入的时间字符串视为UTC时间（与数据库存储格式一致）
            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(timeStr.trim(), formatter1);
            // 使用UTC时区转换为时间戳
            return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeParseException e1) {
            try {
                // 尝试解析日期格式：yyyy-MM-dd（默认为当天的00:00:00 UTC）
                DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDateTime dateTime = LocalDate.parse(timeStr.trim(), formatter2).atStartOfDay();
                // 使用UTC时区转换为时间戳
                return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("无法解析时间格式: " + timeStr, e2);
            }
        }
    }

    /**
     * 解析时间字符串为时间戳（毫秒），如果解析失败返回默认值
     *
     * @param timeStr 时间字符串
     * @param defaultValue 解析失败时的默认值
     * @return 时间戳（毫秒）
     */
    public static Long parseTimeString(String timeStr, Long defaultValue) {
        try {
            return parseTimeString(timeStr);
        } catch (Exception e) {
            return defaultValue;
        }
    }

}
