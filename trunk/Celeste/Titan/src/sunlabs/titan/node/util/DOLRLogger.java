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
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.titan.node.util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import sunlabs.titan.api.TitanGuid;

/**
 * A DOLRLogger object is a wrapper for a {@link java.util.logging.Logger Logger}.
 * It replaces the convenience methods for simple cases ("severe", "warning",
 * etc.) to precisely obtain the calling class and method, rather than
 * making a best guess.
 * <p>
 * Loggers have a unique number appended to their names so there can
 * be multiple loggers for a single program element.  For example, if
 * multiple Nodes are created in the same VM, each would need to
 * have a unique name.
 */
public class DOLRLogger implements DOLRLoggerMBean {
    public Logger logger;
    private final String directory;
    private final String fileName;

    // Map to allow unique integers to be assigned to unique names.
    private static Map<String, Integer> nameIndexMap = new HashMap<String, Integer>();

//    // Utility routine which hands back a unique name be appending
//    // a number to a name.
//    // We don't expect this to be called too many times per VM,
//    // so we don't worry about the number wrapping.
//    private synchronized static String getUniqueName(String name) {
//        Integer id = nameIndexMap.get(name);
//        int value = 0;
//        if (id == null) {
//            // We've never seen this name before.
//            nameIndexMap.put(name, Integer.valueOf(0));
//        } else {
//            value = id.intValue();
//            value++;
//            nameIndexMap.put(name, Integer.valueOf(value));
//        }
//        return name + value;
//    }

    /**
     * Create a new DOLRLogger instance which wraps a
     * {@link java.util.logging.Logger} instance.
     * <p>
     * This Logger instance will include two {@link Handler}s, a
     * {@link FileHandler} which is created in the given directory
     * with the given name, and a {@link ConsoleHandler} which writes all
     * records to <code>System.err</code> with additional debugging information
     * prepended.
     * </p>
     * <p>
     * NOTE: Currently the file handler is disabled.
     * </p>
     * <p>
     * @param name  base name of the log file
     * @param nodeId nodeId for DOLRNode using this log file
     * @param logDirectory directory log file is to be stored in
     * @param defaultLogFileSize maximum number of bytes to write to a log file
     * @param defaultNumberOfLogFiles maximum number of log files to cycle through
     */
    public DOLRLogger(String name, TitanGuid nodeId, String logDirectory, int defaultLogFileSize, int defaultNumberOfLogFiles) {
        super();
        this.fileName = name + "log";
        this.directory = logDirectory;
        try {
//             Loggers must get unique names, otherwise we wouldn't be able
//             to create different loggers for different object instances
//             (say, unique loggers for multiple copies of the same
//             application, if multiple Nodes are created in one VM).
//            this.logger = Logger.getLogger(getUniqueName(name));

            this.logger = Logger.getLogger(name);// + nodeId.toString());

            // File for logging output.

            if (false) {
                FileHandler fh = new FileHandler(this.directory + File.separator + this.fileName + "%g.txt", defaultLogFileSize, defaultNumberOfLogFiles);
                //fh.setFormatter(new SimpleFormatter());
                fh.setFormatter(new DOLRLogFormatter());
                this.logger.addHandler(fh);
            }

            // Console, using our formatter, for global logging output.
            // We assume we were started with no ConsoleHandler attached
            // to the root logger, which allows us to do global operations
            // in the logging config file, like set levels for applications.
            boolean gotConsoleHandler = false;
            for (Handler h : this.logger.getHandlers()) {
                if (h instanceof ConsoleHandler)
                    gotConsoleHandler = true;
            }
            if (!gotConsoleHandler) {
                Handler ch = new ConsoleHandler();
                ch.setFormatter(new DOLRLogFormatter());
                this.logger.addHandler(ch);
            }
            
            this.logger.setUseParentHandlers(false);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private String getCallingClass() {
        StackTraceElement t = new Throwable().getStackTrace()[2];

        String[] tokens = t.getClassName().split("\\.");
        String className = tokens[tokens.length - 1];
        return className;
    }

    private String getCallingMethod() {
        StackTraceElement t = new Throwable().getStackTrace()[2];
        String methodName = t.getMethodName();
        return methodName;
    }

    public Logger getLogger() {
        return this.logger;
    }
    
    public String getName() {
        return this.logger.getName();
    }

    /**
     * Return a particular log file.
     * @param index the generation of file to return (0 is most recent)
     */
    public String getLogfile(int index) {
        return (this.directory + "/" + this.fileName + index + ".txt");
    }

    ///////////////////////////////////////////////////////////////////
    // The following methods are all pass-through calls to underlying
    // Logger methods.

    /**
     * Call {@link Logger#config(String)}.
     */
    public void config(String msg) {
        this.logger.logp(Level.CONFIG, this.getCallingClass(), this.getCallingMethod(), msg);
    }

    public void config(String format, Object... args) {
        this.logger.logp(Level.CONFIG, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
    }

    /**
     * Call {@link Logger#entering(String, String)} with the calling
     * class and method.
     */
    public void entering() {
        this.logger.entering(this.getCallingClass(), this.getCallingMethod());
    }

    /**
     * Call {@link Logger#entering(String, String, Object)} with the calling
     * class and method.
     */
    public void entering(Object param1) {
        this.logger.entering(this.getCallingClass(), this.getCallingMethod(), param1);
    }

    /**
     * Call {@link Logger#entering(String, String, Object[])} with the calling
     * class and method.
     */
    public void entering(Object[] params) {
        this.logger.entering(this.getCallingClass(), this.getCallingMethod(), params);
    }

    /**
     * Call {@link Logger#exiting(String, String)} with the calling
     * class and method.
     */
    public void exiting() {
        this.logger.exiting(this.getCallingClass(), this.getCallingMethod());
    }

    /**
     * Call {@link Logger#exiting(String, String, Object)} with the calling
     * class and method.
     */
    public void exiting(Object result) {
        this.logger.exiting(this.getCallingClass(), this.getCallingMethod(), result);
    }

    /**
     * Call {@link Logger#isLoggable(Level)} do determine if this logger
     * is enabled for the specified level.
     */
    public boolean isLoggable(Level level) {
        return this.logger.isLoggable(level);
    }

    /**
     * Call {@link Logger#logp(Level, String, String, String )} if this logger
     * is enabled for the FINE message level.
     */
    public void fine(String msg) {
        this.logger.logp(Level.FINE, this.getCallingClass(), this.getCallingMethod(), msg);
    }

    public void fine(String format, Object... args) {
        this.logger.logp(Level.FINE, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
    }

    /**
     * Call {@link Logger#logp(Level, String, String, String )} if this logger
     * is enabled for the FINER message level.
     */
    public void finer(String format, Object... args) {
        this.logger.logp(Level.FINER, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
    }

    /**
     * Call {@link Logger#logp(Level, String, String, String )} if this logger
     * is enabled for the FINEST message level.
     */
    public void finest(String msg) {
        this.logger.logp(Level.FINEST, this.getCallingClass(), this.getCallingMethod(), msg);
    }

    public void finest(String format, Object... args) {
        this.logger.logp(Level.FINEST, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
    }

    /**
     * Call {@link Logger#logp(Level, String, String, String )} if this logger
     * is enabled for the SEVERE message level.
     */
    public void severe(String msg) {
        this.logger.logp(Level.SEVERE, this.getCallingClass(), this.getCallingMethod(), msg);
    }

    public void severe(String format, Object... args) {
        this.logger.logp(Level.SEVERE, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
    }

    /**
     * Call {@link Logger#throwing(String, String, Throwable)}.
     */
    public void throwing(Throwable thrown) {
        this.logger.throwing(this.getCallingClass(), this.getCallingMethod(), thrown);
    }

    /**
     * Call {@link Logger#logp(Level, String, String, String )} if this logger
     * is enabled for the WARNING message level.
     */
    public void warning(String msg) {
    	this.logger.logp(Level.WARNING, this.getCallingClass(), this.getCallingMethod(), msg);
    }

    /**
     * Call {@link Logger#logp(Level, String, String, String )} if this logger
     * is enabled for the WARNING message level.
     */
    public void warning(String format, Object... args) {
    	this.logger.logp(Level.WARNING, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
    }

    /**
     * Call {@link Logger#logp(Level, String, String, String )} if this logger
     * is enabled for the INFO message level.
     */
    public void info(String msg) {
    	this.logger.logp(Level.INFO, this.getCallingClass(), this.getCallingMethod(), msg);
    }

    public void info(String format, Object... args) {
    	this.logger.logp(Level.INFO, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
    }

//    /**
//     * Call {@link Logger#logp(Level, String, String, String )} if this logger
//     * is enabled for the specified message level.
//     */
//    public void log(Level level, String msg) {
//        if (this.logger.isLoggable(level)) {
//            this.logger.logp(level, this.getCallingClass(), this.getCallingMethod(), msg);
//        }
//    }
//
//    public void log(Level level, String format, Object... args) {
//        if (this.logger.isLoggable(level)) {
//            this.logger.logp(level, this.getCallingClass(), this.getCallingMethod(), String.format(format, args));
//        }
//    }

    /**
     * Call {@link Logger#setLevel(Level)}.
     */
    public void setLevel(Level newLevel) {
        this.logger.setLevel(newLevel);
    }

    /**
     * Get the effective level of this logger.  If the logger's level is
     * null, find the nearest logger parent with a level set and return
     * that parent's level.
     *
     * @return effective level for this logger. This method is guaranteed
     *         to return a valid Level (not null)
     */
    public Level getEffectiveLevel() {
        Logger log = this.logger;
        Level logLevel = null;
        while (log != null && logLevel == null) {
            logLevel = log.getLevel();
            log = log.getParent();
        }
        // The root logger's level is, by default, Level.INFO.
        return logLevel;
    }

    public void load() {

    }

    public void store() {

    }

    /**
     * Call {@link Logger#getLevel}.  If the result is null,
     * return this logger's effective level.
     */
   public Level getLevel() {
        return this.logger.getLevel();
    }

   public String jmxGetLogLevel() {
       return this.logger.getLevel().toString();
   }

   public void jmxSetLogLevel(String name) {
       this.logger.setLevel(Level.parse(name));
   }
}
