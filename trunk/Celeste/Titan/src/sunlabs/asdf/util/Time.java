/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * only, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.sun.com if you need additional
 * information or have any questions.
 */
package sunlabs.asdf.util;



public class Time {
    /** The number of seconds in 1 minute */
    public final static long MINUTES_IN_SECONDS = 60L;

    public static long minutesInSeconds(long minutes) {
        return minutes * Time.MINUTES_IN_SECONDS;
    }

    /** The number of seconds in 1 hour */
    public final static long HOURS_IN_SECONDS = 60 * Time.MINUTES_IN_SECONDS;

    /** The number of seconds in 1 day */
    public final static long DAYS_IN_SECONDS = 24 * HOURS_IN_SECONDS;

    public final static long SECONDS_IN_MILLISECONDS = 1000L;

    /**
     * Return the number of milliseconds equivalent to the given number of seconds.
     */
    public static long secondsInMilliseconds(long seconds) {
        return seconds * SECONDS_IN_MILLISECONDS;
    }

    /** The number of milliseconds in 1 minute */
    public final static long MINUTES_IN_MILLISECONDS = Time.MINUTES_IN_SECONDS * 1000L;

    /** Return the number of milliseconds in the given number of minutes. */
    public static long minutesInMilliseconds(long minutes) {
        return minutes * MINUTES_IN_MILLISECONDS;
    }

    /** The number of milliseconds in 1 hour */
    public final static long HOURS_IN_MILLISECONDS = Time.HOURS_IN_SECONDS * 1000L;

    /** Return the number of milliseconds in the given number of hours. */
    public static long hoursToMilliseconds(long hours) {
        return hours * HOURS_IN_MILLISECONDS;
    }

    /** return the number of seconds in the given number of hours. */
    public static long hoursInSeconds(int hours) {
        return hours * HOURS_IN_SECONDS;
    }

    public static long millisecondsToHours(long milliseconds) {
        return milliseconds / Time.HOURS_IN_MILLISECONDS;
    }

    public static long millisecondsToMinutes(long milliseconds) {
        return milliseconds / Time.MINUTES_IN_MILLISECONDS;
    }

    public static long millisecondsToSeconds(long milliseconds) {
        return milliseconds / Time.SECONDS_IN_MILLISECONDS;
    }

    /** The number of milliseconds in 1 day */
    public final static long DAYS_IN_MILLISECONDS = Time.DAYS_IN_SECONDS * 1000L;

    /** Return the number of milliseconds in the given number of days. */
    public static long daysToMilliseconds(long days) {
        return days * Time.DAYS_IN_MILLISECONDS;
    }

    /** Return the number of seconds in the given number of days. */
    public static long daysToSeconds(int days) {
        return days * Time.DAYS_IN_SECONDS;
    }

    public static long millisecondsToDays(long milliseconds) {
        return milliseconds / Time.DAYS_IN_MILLISECONDS;
    }

    /**
     * Convert the value given in milliseconds, to seconds.
     */
    public static long millisecondsInSeconds(long milliseconds) {
        return milliseconds / Time.SECONDS_IN_MILLISECONDS;
    }

    /**
     * Convert the value given in milliseconds, to minutes.
     */
    public static long millisecondsInMinutes(long milliseconds) {
        return milliseconds / Time.MINUTES_IN_MILLISECONDS;
    }

    /**
     * Returns the current time in seconds. Note that while the unit of time of the return value is a second,
     * the granularity of the value depends on the underlying operating system.
     * @see System#currentTimeMillis()
     */
    public static long currentTimeInSeconds() {
        return Time.millisecondsInSeconds(System.currentTimeMillis());
    }
    
    /**
     * Return a formatted String containing the given number of {@code milliseconds} measured as elasped time
     * including days, hours, minutes, and seconds.
     */
    public static String formattedElapsedTime(long milliseconds) {
        long days = Time.millisecondsToDays(milliseconds);
        long hours = Time.millisecondsToHours(milliseconds % Time.DAYS_IN_MILLISECONDS);
        long minutes = Time.millisecondsToMinutes(milliseconds % Time.HOURS_IN_MILLISECONDS);
        long seconds = Time.millisecondsToSeconds(milliseconds % Time.MINUTES_IN_MILLISECONDS);
        milliseconds = milliseconds % 1000;
        
        if (days != 0) {
        	return String.format("%dd %dh %dm %d.%03ds", days, hours, minutes, seconds, milliseconds);
        }
        if (hours != 0) {
        	return String.format("%dh %dm %d.%03ds", hours, minutes, seconds, milliseconds);
        }
        if (minutes != 0) {
        	return String.format("%dm %d.%03ds", minutes, seconds, milliseconds);
        }
        
        return String.format("%d.%03ds", seconds, milliseconds);
    }

    /**
     * Format the given time in ISO 8601 format consisting of the string containing the components:
     * YYYY-MM-DDThh:m:ss.sss[+-]OOOO
     * @param timeInMillis
     * @return the given time in ISO 8601
     */
    public static String ISO8601(long timeInMillis) {        
        return String.format("%1$tFT%1$tT.%1$tL%1$tz", timeInMillis);
    }
    
    public static void main(String[] args) {
        System.out.printf("%s%n", Time.ISO8601(System.currentTimeMillis()));
    }
}
