/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle, 16 Network Circle, MenloPark, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.titan.node.util;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A simple formatter to help log output from multiple nodes to a single
 * location.  It prepends the following information to each logged record:
 * <p>
 * nodeId.threadName: callingClass.callingMethod: 
 * <p>
 * The nodeId can be truncated depending on the logging property 
 * <code>DOLRFormat.prefixSize</code>.  If that property is negative,
 * the entire id is printed. If it is zero, the id is not printed.
 * Otherwise, for positive values, the id's first prefixSize digits
 * are printed.
 */
public class DOLRLogFormatter extends Formatter {
    private static final String LINESEP = System.getProperty("line.separator");

    private DateFormat dateFormat;
    
    /**
     * Create a new DOLRLogFormatter.
     */
    public DOLRLogFormatter() {
        super();         
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    }
    
//    /**
//     * Return the prefix of a given {@link BeehiveObjectId}, based on the 
//     * logging property <code>DOLRFormat.prefixSize</code>.
//     * A '.' will be appended to the name if the prefixSize is not zero.
//     *
//     * @param objectId  id to shorten based on logging property
//     * @return shortened objId
//     */
//    @Deprecated
//    public static String prefixObjId(BeehiveObjectId objectId) {
//        LogManager lm = LogManager.getLogManager();
//        
//        // Set the objId to a substring of the actual id, depending
//        // on the prefix size property.
//        String prop = lm.getProperty("DOLRLogFormat.prefixSize");      
//        
//        // prefix defaults to -1 if property is not found (use entire id)
//        final int prefix;
//        if (prop != null) {
//            prefix = Integer.parseInt(lm.getProperty("DOLRLogFormat.prefixSize"));
//        } else {
//            prefix = -1;
//        }
//        
//        final String id;
//        if (prefix > 0) {
//            id = objectId.toString().substring(0, prefix) + ".";
//        } else if (prefix < 0) {
//            id = objectId.toString() + ".";
//        } else {
//            id = "";
//        }
//        return id;
//    }
    
    /**
     * Prepend console output with additional information:
     * <ul>
     * <li> node Id
     * <li> thread name
     * <li> calling class
     * <li> calling method
     * </ul>
     * 
     * {@inheritDoc}
     */
    @Override public String format(LogRecord record) {        
        String now = this.dateFormat.format(new Date());
        StringBuilder sb = new StringBuilder(now).append(" ").append(record.getLevel()).append(" ");
        sb.append(Thread.currentThread().getName());
        sb.append(": ");
        sb.append(record.getSourceClassName());
        sb.append(".");
        sb.append(record.getSourceMethodName());
        sb.append(": ");
        sb.append(formatMessage(record));
        sb.append(LINESEP);
        return sb.toString();
    }

    public static String prettyPrint(String prefix, String format, byte[] array, int width) {
        boolean newline = true;
        StringBuilder string = new StringBuilder();
        for (int cnt = 0; cnt < array.length; cnt++) {
            if (newline) {
                string.append(prefix);
                newline = false;
            }
            string.append(String.format(format, array[cnt]));
            if((cnt+1)%width == 0) {
                string.append("\n");
                newline = true;
            }
        }
        if ((array.length % width) != 0) {
            string.append("\n");
        }
        return string.toString();
    }
    
    public static void prettyPrint(String prefix, String format, byte[] array, int width, PrintStream out) throws IOException {
        boolean newline = true;
        for (int cnt = 0; cnt < array.length; cnt++) {
            if (newline) {
                out.write(prefix.getBytes());
                newline = false;
            }
            out.write(String.format(format, array[cnt]).getBytes());
            if((cnt+1)%width == 0) {
                out.write("\n".getBytes());
                newline = true;
            }
        }
        if ((array.length % width) != 0) {
            out.write("\n".getBytes());
        }
    }
}
