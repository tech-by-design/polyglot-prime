package org.techbd.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
public class DateUtil {

    /**
     * Converts a date string to an Optional Instant in ISO format. If parsing
     * fails, returns an empty Optional.
     *
     * @param dateStr The date string to be converted
     * @return Optional Instant representing the date
     */
    public static Optional<Instant> convertToIsoInstant(String dateStr) {
        try {
            ZonedDateTime parsedDate = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            return Optional.of(parsedDate.toInstant());
        } catch (DateTimeParseException e) {
            // Log a warning if needed
            return Optional.empty();
        }
    }

    /**
     * Converts a date string to Instant. If parsing fails, returns the current
     * Instant.
     *
     * @param dateStr Date string to convert.
     * @return Instant object representing the date.
     */
    public static Instant toIsoDateOrNow(String dateStr) {
        try {
            return ZonedDateTime.parse(dateStr).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now(); // Default to current Instant if parsing fails
        }
    }
  /**
     * Converts a String (in ISO 8601 format) to a java.util.Date.
     * If the string is null or invalid, returns null.
     *
     * @param dateStr The date string in ISO 8601 format (e.g., "2024-11-15T12:34:56Z").
     * @return A java.util.Date representing the parsed date, or null if the string is invalid or null.
     */
    public static Date convertStringToDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;  // Return null if the string is null or empty
        }

        try {
            // Parse the string into an Instant (ISO 8601 format)
            Instant instant = Instant.parse(dateStr);
            // Convert the Instant to java.util.Date
            return Date.from(instant);
        } catch (Exception e) {
            // Handle invalid format or parsing issues
            return null;
        }
    }

    public static Date parseDate(String dateString) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace(); // Handle parsing error (log it, or return null, etc.)
            return null;
        }
    }
}
