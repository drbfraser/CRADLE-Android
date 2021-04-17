package com.cradleVSA.neptune.utilitiles;

import android.util.Log;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class DateUtil {

    private static final String TAG = "DateUtil";

    public static String getDateString(ZonedDateTime date) {
        if (date == null) {
            return "";
        }
        ZonedDateTime now = ZonedDateTime.now();

        DateTimeFormatter formatter;
        if (now.toLocalDate().equals(date.toLocalDate())) {
            // Today: omit the date
            formatter = DateTimeFormatter.ofPattern("h:mm a");
        } else if (now.getYear() == date.getYear()) {
            // This year: omit the year
            formatter = DateTimeFormatter.ofPattern("MMM d '@' h:mm a");
        } else {
            // Full date
            formatter = DateTimeFormatter.ofPattern("MMM d, yyyy '@' h:mm a");
        }

        return date.format(formatter);
    }

    /**
     * @param unixDate           The Unix timestamp to use
     * @param useYMDOrderForFull If true, the full date will use YYYY-MM-DD format, else
     *                           it will use MMM d, yyyy format
     * @return A concise date string.
     */
    public static String getConciseDateString(BigInteger unixDate, boolean useYMDOrderForFull) {
        if ((unixDate.compareTo(BigInteger.valueOf(Long.MAX_VALUE))) > 0) {
          Log.e(TAG, "Input date is beyond the range of values supported by this utility " +
                  "- undefined behavior ahead");
        }
        final ZonedDateTime now = ZonedDateTime.now();
        // With our previous comparison and log, I think it's fine to take the longValue:
        final ZonedDateTime dateFromUnix = getZoneTimeFromLong(unixDate.longValue());

        if (dateFromUnix == null) {
            return "";
        }

        final DateTimeFormatter formatter;
        if (now.toLocalDate().equals(dateFromUnix.toLocalDate())) {
            // Today: omit the date
            formatter = DateTimeFormatter.ofPattern("h:mm a");
        } else if (now.getYear() == dateFromUnix.getYear()) {
            // This year: omit the year
            formatter = DateTimeFormatter.ofPattern("MMM d '@' h a");
        } else {
            // Full date
            if (useYMDOrderForFull) {
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            } else {
                formatter = DateTimeFormatter.ofPattern("MMM d, yyyy");
            }
        }

        return dateFromUnix.format(formatter);
    }

    /**
     * @param longDate           The Unix timestamp to use, represented by a Long
     * @param useYMDOrderForFull If true, the full date will use YYYY-MM-DD format, else
     *                           it will use MMM d, yyyy format
     * @return A concise date string.
     */
    public static String getConciseDateString(Long longDate, boolean useYMDOrderForFull) {
        return getConciseDateString(BigInteger.valueOf(longDate), useYMDOrderForFull);
    }

    /**
     * @param age Age in years
     * @return A date for the given age with the same month and day as today for the given age, in
     * yyyy-mm-dd format.
     */
    public static String getDateStringFromAge(long age) {
        ZonedDateTime dateFromAge = ZonedDateTime.now().minusYears(age);
        if (dateFromAge == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return dateFromAge.format(formatter);
    }

    /**
     * @param timestamp Unix timestamp
     * @return A date for the given timestamp in yyyy-mm-dd format.
     */
    public static String getDateStringFromTimestamp(long timestamp) {
        ZonedDateTime dateFromTimestamp = getZoneTimeFromLong(timestamp);
        return dateFromTimestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * @param UTCTimestamp Unix timestamp in the UTC time zone.
     * @return A date for the given timestamp in yyyy-mm-dd format.
     */
    public static String getDateStringFromUTCTimestamp(long UTCTimestamp) {
        ZonedDateTime dateFromTimestamp = ZonedDateTime.ofInstant(Instant.ofEpochSecond(UTCTimestamp), ZoneId.of("UTC"));
        return dateFromTimestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public static String getFullDateFromUnix(Long date) {
        if (date == null) {
            return "";
        }
        Instant i = Instant.ofEpochSecond(date);
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(i, ZoneId.systemDefault());

        DateTimeFormatter formatter;
        formatter = DateTimeFormatter.ofPattern("MMM d, yyyy '@' h:mm a");
        return zonedDateTime.format(formatter);
    }

    public static String getISODate(ZonedDateTime date) {
        // example:  '2011-12-03T10:15:30'.

        if (date == null) {
            return "";
        }

        return date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static String getISODateForFilename(ZonedDateTime date) {
        return getISODate(date).replace(":", ".");
    }

    public static ZonedDateTime getZoneTimeFromLong(Long date) {
        if (date == null || date.equals("")) {
            return null;
        }

        ZonedDateTime zonedDateTime = null;
        try {
            // if the date is already in zone format
            zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(date), ZoneId.systemDefault());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return zonedDateTime;
    }
}
