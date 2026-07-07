package com.gj.llm.common.util;

import com.gj.llm.common.exception.ConversionFailedException;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.lang.management.ManagementFactory;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 时间工具类
 *
 * @author xiangxun
 */
@SuppressWarnings("deprecation")
public class DateUtils extends org.apache.commons.lang3.time.DateUtils {
    public static String YYYY = "yyyy";

    public static String YYYY_MM = "yyyy-MM";

    public static String YYYY_MM_DD = "yyyy-MM-dd";

    public static String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";

    public static String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private static String[] parsePatterns = {
            "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM",
            "yyyy/MM/dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM",
            "yyyy.MM.dd", "yyyy.MM.dd HH:mm:ss", "yyyy.MM.dd HH:mm", "yyyy.MM",
            "HH:mm:ss", "HH:mm", "hh:mm", "hh:mm:ss"};

    private static final SimpleDateFormat SDF_HHMMSS = new SimpleDateFormat("HH:mm:ss");
    // formatter to format java.util.Date to string
    public static final ThreadLocal<DateFormat> TL_DATETIME_FORMATTER = getThreadLocalDateFormat(YYYY_MM_DD_HH_MM_SS);
    public static final ThreadLocal<DateFormat> TL_DATE_FORMATTER = getThreadLocalDateFormat(YYYY_MM_DD);

    // formatter to format string to java.time.LocalDateTime or java.time.LocalDate or java.time.LocalTime, vice versa
    public static final ThreadLocal<DateTimeFormatter> TL_LOCAL_DATETIME_FORMATTER = getThreadLocalDateTimeFormatter(YYYY_MM_DD_HH_MM_SS);
    public static final ThreadLocal<DateTimeFormatter> TL_LOCAL_DATE_FORMATTER = getThreadLocalDateTimeFormatter(YYYY_MM_DD);

    /**
     * 获取当前Date型日期
     *
     * @return Date() 当前日期
     */
    public static Date getNowDate() {
        return new Date();
    }

    /**
     * 获取当前日期, 默认格式为yyyy-MM-dd
     *
     * @return String
     */
    public static String getDate() {
        return dateTimeNow(YYYY_MM_DD);
    }

    public static final String getTime() {
        return dateTimeNow(YYYY_MM_DD_HH_MM_SS);
    }

    public static final String dateTimeNow() {
        return dateTimeNow(YYYYMMDDHHMMSS);
    }

    public static final String dateTimeNow(final String format) {
        return parseDateToStr(format, new Date());
    }

    public static final String dateTime(final Date date) {
        return parseDateToStr(YYYY_MM_DD, date);
    }

    public static final String parseDateToStr(final String format, final Date date) {
        return new SimpleDateFormat(format).format(date);
    }

    public static String formatLocalDate(final LocalDate date, final DateTimeFormatter formatter) {
        if (date == null) {
            return null;
        }

        return date.format(formatter);
    }

    public static String formatLocalDate(final LocalDate date) {
        if (date == null) {
            return null;
        }

        return date.format(TL_LOCAL_DATE_FORMATTER.get());
    }

    public static String formatDate(final Date date) {
        if (date == null)
            return null;

        return TL_DATE_FORMATTER.get().format(date);
    }

    public static String formatDateTime(final Date dateTime) {
        if (dateTime == null) {
            return null;
        }

        return TL_DATETIME_FORMATTER.get().format(dateTime);
    }

    public static String formatLocalDateTime(final LocalDateTime dateTime, final DateTimeFormatter formatter) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.format(formatter);
    }

    public static final String formatLocalDateTime(final LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.format(TL_LOCAL_DATETIME_FORMATTER.get());
    }

    public static final Date dateTime(final String format, final String ts) {
        try {
            return new SimpleDateFormat(format).parse(ts);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static LocalDate parseLocalDate(final String source) {
        if (StringUtils.isEmpty(source)) {
            return null;
        }

        String tempSource = getStandardDateText(source);

        try {
            return LocalDate.parse(tempSource, TL_LOCAL_DATE_FORMATTER.get());
        } catch (DateTimeParseException dtpe) {
            /* ignore */
        }

        try {
            return LocalDateTime.parse(tempSource, TL_LOCAL_DATETIME_FORMATTER.get()).toLocalDate();
        } catch (DateTimeParseException dtpe) {
            /* ignore */
        }

        // 存在毫秒或纳秒
        if (StringUtils.contains(tempSource, ".")) {
            try {
                int qty = tempSource.length() - (tempSource.lastIndexOf(".") + 1);

                if (qty > 0) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS + "." + StringUtils.repeat("S", qty));

                    return LocalDateTime.parse(tempSource, formatter).toLocalDate();
                }
            } catch (Exception pe) {
                /* ignore */
            }
        }

        throw new ConversionFailedException(String.class, LocalDate.class, source);
    }

    public static LocalDateTime parseLocalDateTime(final String source) {
        if (StringUtils.isEmpty(source)) {
            return null;
        }

        String tempSource = getStandardDateText(source);

        try {
            return LocalDateTime.parse(tempSource, TL_LOCAL_DATETIME_FORMATTER.get());
        } catch (DateTimeParseException dtpe) {
            /* ignore */
        }

        try {
            return LocalDate.parse(tempSource, TL_LOCAL_DATE_FORMATTER.get()).atStartOfDay();
        } catch (DateTimeParseException dtpe) {
            /* ignore */
        }

        // 存在毫秒或纳秒
        if (StringUtils.contains(tempSource, ".")) {
            try {
                int qty = tempSource.length() - (tempSource.lastIndexOf(".") + 1);

                if (qty > 0) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS + "." + StringUtils.repeat("S", qty));

                    return LocalDateTime.parse(tempSource, formatter);
                }
            } catch (Exception pe) {
                /* ignore */
            }
        }

        throw new ConversionFailedException(String.class, LocalDateTime.class, source);
    }

    /**
     * 日期路径 即年/月/日 如2018/08/08
     */
    public static final String datePath() {
        Date now = new Date();
        return DateFormatUtils.format(now, "yyyy/MM/dd");
    }

    /**
     * 日期路径 即年/月/日 如20180808
     */
    public static final String dateTime() {
        Date now = new Date();
        return DateFormatUtils.format(now, "yyyyMMdd");
    }

    /**
     * 日期型字符串转化为日期 格式
     */
    public static Date parseDate(Object str) {
        if (str == null) {
            return null;
        }

        String tempSource = getStandardDateText(str.toString());
        try {
            return parseDate(tempSource, parsePatterns);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * 获取服务器启动时间
     */
    public static Date getServerStartDate() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        return new Date(time);
    }

    /**
     * 计算相差天数
     */
    public static int differentDaysByMillisecond(Date date1, Date date2) {
        return Math.abs((int) ((date2.getTime() - date1.getTime()) / (1000 * 3600 * 24)));
    }

    /**
     * 计算时间差
     *
     * @param endDate   最后时间
     * @param startTime 开始时间
     * @return 时间差（天/小时/分钟）
     */
    public static String timeDistance(Date endDate, Date startTime) {
        long nd = 1000 * 24 * 60 * 60;
        long nh = 1000 * 60 * 60;
        long nm = 1000 * 60;
        // long ns = 1000;
        // 获得两个时间的毫秒时间差异
        long diff = endDate.getTime() - startTime.getTime();
        // 计算差多少天
        long day = diff / nd;
        // 计算差多少小时
        long hour = diff % nd / nh;
        // 计算差多少分钟
        long min = diff % nd % nh / nm;
        // 计算差多少秒//输出结果
        // long sec = diff % nd % nh % nm / ns;
        return day + "天" + hour + "小时" + min + "分钟";
    }

    /**
     * 增加 LocalDateTime ==> Date
     */
    public static Date toDate(LocalDateTime temporalAccessor) {
        ZonedDateTime zdt = temporalAccessor.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    /**
     * 增加 LocalDate ==> Date
     */
    public static Date toDate(LocalDate temporalAccessor) {
        LocalDateTime localDateTime = LocalDateTime.of(temporalAccessor, LocalTime.of(0, 0, 0));
        ZonedDateTime zdt = localDateTime.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    /**
     * 两个时间合成
     */
    public static Date merge(Date date1, Date date2) {
        LocalDate localDate1 = LocalDate.of(date1.getYear(), date1.getMonth(), date1.getDate());

        LocalTime localTime = LocalTime.of(date2.getHours(), date2.getMinutes(), date2.getSeconds());

        LocalDateTime localDateTime1 = LocalDateTime.of(localDate1, localTime);

        return Date.from(localDateTime1.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static String getStandardDateText(String dateText) {
        StringBuilder sb = new StringBuilder();

        int zoneIndex = Math.max(dateText.lastIndexOf("-"), dateText.lastIndexOf("+"));
        if (zoneIndex >= "yyyy-M-d H:m:s".length()) { // 有时区
            dateText = dateText.substring(0, zoneIndex).trim();
        }

        // 处理yyyy-MM-ddTHH:mm:ss.SSSSSSSSSZ这种类型的日期格式
        if (StringUtils.contains(dateText, "T")) {
            dateText = StringUtils.replace(dateText, "T", " ");
        }
        if (StringUtils.endsWith(dateText, "Z")) {
            dateText = StringUtils.removeEnd(dateText, "Z");
        }

        String[] values = dateText.split("(-|\\/|年|月|日|:|T|t|\\.|\\s)");

        for (int i = 0, j = values.length; i < j; i++) {
            String value = values[i];

            if (i == 0) { // 年
                sb.append(value);
            } else if (i <= 2) { // 月、日
                sb.append("-").append(StringUtils.leftPad(value, 2));
            } else if (i == 3) { // 时
                sb.append(" ").append(StringUtils.leftPad(value, 2));
            } else if (i <= 5) { // 分、秒
                sb.append(":").append(StringUtils.leftPad(value, 2));
            } else if (i == 6) { // 毫秒
                sb.append(".").append(value);
            }
        }

        // 没有秒时，自动补上秒
        if (sb.length() == "yyyy-MM-dd HH:mm".length()) {
            sb.append(":00");
        }

        return sb.toString();
    }

    private static ThreadLocal<DateFormat> getThreadLocalDateFormat(String pattern) {
        return new ThreadLocal<DateFormat>() {
            @Override
            protected DateFormat initialValue() {
                return new SimpleDateFormat(pattern);
            }
        };
    }

    private static ThreadLocal<DateTimeFormatter> getThreadLocalDateTimeFormatter(String pattern) {
        return new ThreadLocal<DateTimeFormatter>() {
            @Override
            protected DateTimeFormatter initialValue() {
                return DateTimeFormatter.ofPattern(pattern);
            }
        };
    }


    /**
     * 先格式化保留HH:mm:ss，再获取毫秒差值（绝对值）
     */
    public static long diffMillis(Date d1, Date d2) {
        Date time1 = formatToHhMmSs(d1);
        Date time2 = formatToHhMmSs(d2);
        if (time1 == null || time2 == null) {
            return 0;
        }
        return Math.abs(time1.getTime() - time2.getTime());
    }

    /**
     * 格式化Date为只保留当天时分秒（剔除年月日影响）
     *
     * @param date 原Date
     * @return 同一天 00:00:00 + HH:mm:ss 的新Date
     */
    public static Date formatToHhMmSs(Date date) {
        if (date == null) {
            return null;
        }
        try {
            String timeStr = SDF_HHMMSS.format(date);
            return SDF_HHMMSS.parse(timeStr);
        } catch (ParseException e) {
            return date;
        }
    }

    /**
     * date time 转为 date  yyyy-MM-dd
     */
    public static Date formatToDate(Date date) {
        if (date == null) {
            return null;
        }
        String dateToStr = parseDateToStr("yyyy-MM-dd", date);

        return parseDate(dateToStr);
    }
}
