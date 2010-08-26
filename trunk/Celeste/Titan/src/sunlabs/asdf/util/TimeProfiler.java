/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;

/**
 * A mechanism to measure elapsed time.
 * <p>
 * Create an instance of this class to begin measuring from the time this instance is created.
 * Use repeated invocations of the method {@link #stamp(String, Object...)} to record a
 * time-stamp with an associated message (see {@link String#format(String, Object...)}.
 * </p>
 * <p>
 * To pretty-print the measurements use the {@link #toString()} method.
 * To print a comma-separated list of the recorded events, use the {@link #printCSV(PrintStream)} method.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class TimeProfiler {
    private static final boolean enabled =
        Boolean.getBoolean("enableProfiling");

    private final long startTime;
    private long endTime;
    private String message;
    protected LinkedList<TimeProfiler.Timestamp> timestamps;
    protected long lastTimestamp;

    public static class Timestamp {
        protected long startTime;
        protected long endTime;
        protected String message;
        
        public Timestamp(String message, long time) {
            this.startTime = time;
            this.endTime = System.currentTimeMillis();
            this.message = message;
        }
        
        public String toString() {
            return String.format("%s t0=%d t1=%d", this.message, this.startTime, this.endTime);
        }
    }

    public TimeProfiler() {
        this(TimeProfiler.enabled ?
             new Throwable().getStackTrace()[1].toString() : "disabled");
    }
    
    public TimeProfiler(String format, Object...args) {
        if (TimeProfiler.enabled) {
            this.timestamps = new LinkedList<TimeProfiler.Timestamp>();
            this.message = String.format(format, args);
            this.lastTimestamp = this.startTime = System.currentTimeMillis();
        } else {
            this.startTime = 0;
        }
    }
    
    /**
     * Insert a time-stamp and associated message into this TimingContext object.
     */
    public void stamp(String format, Object...args) {
        if (TimeProfiler.enabled) {
            String m = String.format(format, args);

            Timestamp stamp = new Timestamp(m, this.lastTimestamp);
            this.timestamps.add(stamp);
            this.lastTimestamp = stamp.endTime;
            this.endTime = stamp.endTime;
        }
    }

//    private static String getCallingPoint() {
//        StackTraceElement t = new Throwable().getStackTrace()[2];
//        return t.toString();
//    }
    
    public String toString() {
        if (TimeProfiler.enabled) {
            StringBuilder result = new StringBuilder(String.format("%1$-27s %2$tD %2$tT%n", this.message, this.startTime));
            long lastTime = this.startTime;
            for (Timestamp t : this.timestamps) {
                result.append(String.format("%1$-32s %2$10dms%n", t.message, t.endTime - t.startTime));
                lastTime = t.endTime;
            }
            result.append(String.format("%1$-32s %2$10dms%n", "total", lastTime - this.startTime));
            return result.toString();
        } else {
            return "disabled";
        }
    }

    public void print(PrintStream out) {
        if (TimeProfiler.enabled) {
            out.println(this.toString());
        }
    }
    
    /**
     * Produce a comma-separated list of the events recorded in this {@code
     * TimeProfiler} object.
     * <p>
     * The output consists of the string "TimingProfiler" followed by the
     * Thread id ({@link Thread#getId()} of the running thread,
     * the start time (see {@link System#currentTimeMillis()},
     * and the formatted string specified in the constructor of this class.
     * Then for each stamp invoked on this object, the output produces a
     * formatted string specified when invoking the method and the elapsed time
     * between each stamp.
     * </p>
     * <p>
     * All fields are separated by commas, and strings are enclosed in double
     * quote characters.
     * </p>
     *
     * @param out   a {@code PrintStream} on which to emit the output
     */
    public void printCSV(PrintStream out) {
        if (TimeProfiler.enabled) {
            StringBuilder result = new StringBuilder(String.format("TimingProfiler,%1$d,%2$d,%3$s,\"%4$s\",%5$d",
                Thread.currentThread().getId(), this.startTime, this.message.trim(), "total", this.endTime - this.startTime));
            for (Timestamp t : this.timestamps) {
                result.append(String.format(",\"%s\",%d", t.message.trim(), t.endTime - t.startTime));
            }
            out.println(result.toString());
        }
    }

    /**
     * Produce a comma-separated list of the events recorded in this {@code
     * TimeProfiler} object.
     * <p>
     * The output consists of the string "TimingProfiler" followed by the
     * Thread id ({@link Thread#getId()} of the running thread,
     * the start time (see {@link System#currentTimeMillis()},
     * and the formatted string specified in the constructor of this class.
     * Then for each stamp invoked on this object, the output produces a
     * formatted string specified when invoking the method and the elapsed time
     * between each stamp.
     * </p>
     * <p>
     * All fields are separated by commas, and strings are enclosed in double
     * quote characters.
     * </p>
     *
     * @param file  a {@code File} to which the output is to be appended
     */
    public void printCSV(File file) {
        FileOutputStream fos = null;
        try {
            //
            // Append to what's already there.
            //
            fos = new FileOutputStream(file, true);
            PrintStream ps = new PrintStream(new BufferedOutputStream(fos));
            this.printCSV(ps);
            ps.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace(System.err);
        } finally {
            if (fos != null)
                try {
                    fos.close();
                } catch (IOException e) {
                    // Shrug:  nothing useful to do.
                }
        }
    }

    /**
     * Produce a comma-separated list of the events recorded in this {@code
     * TimeProfiler} object.
     * <p>
     * The output consists of the string "TimingProfiler" followed by the
     * Thread id ({@link Thread#getId()} of the running thread,
     * the start time (see {@link System#currentTimeMillis()},
     * and the formatted string specified in the constructor of this class.
     * Then for each stamp invoked on this object, the output produces a
     * formatted string specified when invoking the method and the elapsed time
     * between each stamp.
     * </p>
     * <p>
     * All fields are separated by commas, and strings are enclosed in double
     * quote characters.
     * </p><p>
     * If the {@code fileName} argument is {@code null}, output goes to {@code
     * System.err}; otherwise it is appended to the file the argument names.
     * </p>
     *
     * @param fileName  the name of a file to which the output should be
     *                  appended
     */
    public void printCSV(String fileName) {
        if (fileName == null)
            this.printCSV(System.err);
        else
            this.printCSV(new File(fileName));
    }
    
    public static void main(String[] args) throws Exception {
        TimeProfiler time = new TimeProfiler("main   ");
        time.stamp("point a");
        Thread.sleep(1000);
        time.stamp("point b");
        
        time.printCSV(System.out);

        time.print(System.out);
    }
}
