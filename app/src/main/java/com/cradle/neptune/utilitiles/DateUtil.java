package com.cradle.neptune.utilitiles;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Calendar;

public class DateUtil {
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

    public static String getConciseDateString(Long unixDate) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime dateFromUnix = getZoneTimeFromLong(unixDate);

        if (dateFromUnix == null) {
            return "";
        }

        DateTimeFormatter formatter;
        if (now.toLocalDate().equals(dateFromUnix.toLocalDate())) {
            // Today: omit the date
            formatter = DateTimeFormatter.ofPattern("h:mm a");
        } else if (now.getYear() == dateFromUnix.getYear()) {
            // This year: omit the year
            formatter = DateTimeFormatter.ofPattern("MMM d '@' h a");
        } else {
            // Full date
            formatter = DateTimeFormatter.ofPattern("MMM d, yyyy");
        }

        return dateFromUnix.format(formatter);
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

    public static String getFullDateFromUnix(Long date) {
        if (date == null) {
            return "";
        }
        Instant i = Instant.ofEpochSecond(date);
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(i, ZoneId.of(Calendar.getInstance().getTimeZone().getID()));

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
