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
package sunlabs.celeste.node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.titan.Copyright;
import sunlabs.titan.Release;
import sunlabs.titan.node.TitanNodeImpl;
import sunlabs.titan.node.TitanNodeImpl.ConfigurationException;
import sunlabs.titan.util.OrderedProperties;

public class Supervisor {
    public final static Attributes.Prototype JavaArguments = new Attributes.Prototype(Supervisor.class, "JavaArguments", "-server",
            "The full list of command line options to the JVM interpreter.");

    /**
     * A simple process spawn and run.
     */
    private static class RunProcess implements Runnable {
        protected String command;

        /**
         * Spawn an external process consisting of the command {@code command}.
         */
        public RunProcess(String command) {
            this.command = command;
        }

        public void run() {
            ProcessBuilder p = new ProcessBuilder(this.command.split(" "));
            p.redirectErrorStream(true);
            BufferedReader input = null;
            try {
                Process proc = p.start();

                input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line;
                while ((line = input.readLine()) != null) {
                    System.out.println(line);
                }
                System.out.printf("%tT: %s died.%n", System.currentTimeMillis(), this.command);
                input.close();
                proc.waitFor();
            } catch (RuntimeException weDidAllWeCouldDo) {
                throw weDidAllWeCouldDo;
            } catch (IOException e) {

            } catch (InterruptedException e) {

            } finally {
                if (input != null) try { input.close(); } catch (IOException ignore) { }
            }
        }
    }

    private static String release = Release.ThisRevision();

    /**
     *
     */
    public static void main(String[] args) {
        
        List<String[]> configuration = new LinkedList<String[]>();
        
        Integer httpPort = 8090;
        
        //
        // Establish default values.  The argument processing code below can
        // override them.
    	//

        String binJava = System.getenv("JAVA");
        if (binJava == null)
            binJava = "/usr/bin/java";
        
//        Integer jmxPortBase = null;
        
        boolean useThreads = false;
        int interprocessStartupDelayTimeSeconds = 5;

        RuntimeMXBean mxbean = ManagementFactory.getRuntimeMXBean();
//        System.out.println("BootClassPath: " + mxbean.getBootClassPath());
//        System.out.println("ClassPath: " + mxbean.getClassPath());
//        System.out.println("LibraryPath: " + mxbean.getLibraryPath());
//        System.out.println("Name: " + mxbean.getName());

//        System.out.println("InputArguments " + mxbean.getInputArguments().size());

        //
        // The getInputArguments() method doesn't pass through the "-server"
        // flag.  As a workaround, the bin/celeste launcher sets the "server"
        // Java property to accompany the flag.  So check for that property
        // and use it to set the "-server" flag or not.
        //
        String serverFlag = System.getProperty("server", "").equals("true") ? " -server" : "";
        StringBuilder jvmArguments = new StringBuilder(serverFlag);
        for (String a : mxbean.getInputArguments()) {
            jvmArguments.append(" ").append(a);
        }

        String jarFile = mxbean.getClassPath();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (args[i].equals("--delay-time")) {
                    interprocessStartupDelayTimeSeconds = Integer.parseInt(args[++i]);
                    if (interprocessStartupDelayTimeSeconds < 1) {
                        System.err.printf("--delay-time %d: time must be greater than 1%n", interprocessStartupDelayTimeSeconds);
                        throw new IllegalArgumentException();
                    }
                } else if (args[i].equals("--use-threads") ) {
                    useThreads = true;
                } else if (args[i].equals("--jar") ) {
                    jarFile = args[++i];
                } else if (args[i].equals("--java") ) {
                    binJava = args[++i];
                } else if (args[i].equals("--java-args") ) {
                    jvmArguments = new StringBuilder(args[i++]);
                } else if (args[i].equals("--http-port") ) {
                    httpPort = new Integer(args[i++]);
                } else if (args[i].equals("--version")) {
                    System.out.println(release);
                    System.out.println(Copyright.miniNotice);
                } else if (args[i].equals("--help") ) {
                    System.out.println(release);
                    System.out.println(Copyright.miniNotice);
                    System.out.println("Usage: defaults are in parenthesis.");
                    System.out.printf(" [--delay-time <integer>] (%d)\n", interprocessStartupDelayTimeSeconds);
                    System.out.printf(" [--jar <file name>] (%s)\n", String.valueOf(jarFile));
                    System.out.printf(" [--java <file name>] (%s)\n", binJava);
                    System.out.printf(" [--jvm-args <args>] (%s)\n", jvmArguments.toString());
                    System.out.printf(" [--use-threads] (use processes)\n");
                    System.exit(0);
                } else {
                    System.out.println("Ignoring unknown option: " + args[i]);
                }
            } else {
                configuration.add(args[i].split("[,]"));
            }
        }

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);

        Executor executors = Executors.newCachedThreadPool();

        // Start all of the nodes as threads in one JVM.
        // This is also the code that runs a single node.
        if (useThreads) {
            CelesteNode[] node = new CelesteNode[configuration.size()];
            Thread[] thread = new Thread[configuration.size()];

            int i = 0;
            for (String[] config : configuration) {
                try {
                    OrderedProperties configurationProperties = new OrderedProperties(new URL(config[0]));

                    node[i] = new CelesteNode(configurationProperties);
                    thread[i] = node[i].start();

                    System.out.printf("%s [%d ms] %s%n",
                            dateFormat.format(new Date()),
                            System.currentTimeMillis() - Long.parseLong(node[i].getProperty(TitanNodeImpl.StartTime.getName())),
                            node[i].getNodeAddress().format());

                    try {
                        Thread.sleep(Time.secondsInMilliseconds(interprocessStartupDelayTimeSeconds));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                } catch (ConfigurationException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                i++;
            }

            if (configuration.size() > 1) {
                System.out.printf("%s All node threads running.%n", dateFormat.format(new Date()));
            }

            // Wait for all the threads to terminate.
            for (int j = 0; j < thread.length; j++) {
                if (thread[j].isAlive()) {
                    try { thread[j].join(); } catch (InterruptedException e) { /**/ }
                    j = -1; // Make this loop start over again.
                }
            }
            System.err.println(dateFormat.format(new Date()) + " EXITED");
            System.exit(1);
        }

        // Use separate processes for each Celeste Node.

        System.out.println(release);
        System.out.println(Copyright.miniNotice);
        String celeste = " -jar " + jarFile;

        for (int i = 0; i < configuration.size(); i++) {
            try {
                String[] config = configuration.get(i);
                String celesteConfigurationURL = config[0];
                
                OrderedProperties configurationProperties = new OrderedProperties(new URL(celesteConfigurationURL));

                String jvmArgument = configurationProperties.getProperty(Supervisor.JavaArguments.getName(), "");
                
//                if (config.length > 1) {
//                    DataInputStream in = new DataInputStream((InputStream) new URL(config[1]).getContent());
//                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
//                    byte[] buffer = new byte[512];
//                    for (;;) {
//                        int nread = in.read(buffer);
//                        if (nread < 1)
//                            break;
//                        bout.write(buffer, 0, nread);
//                    }
//                    
//                    jvmArgument = bout.toString().replaceAll("[\n]*", "").replaceAll("[ \t]+", " ");
//                }

                String command = binJava + " -Dceleste-node " + jvmArgument + " " + celeste + " " + celesteConfigurationURL;
                System.out.println(command);

//                try {
//                    executors.execute(new RunProcess(command));
//                } catch(RejectedExecutionException e) {
//                    e.printStackTrace();
//                }

                try {
                    Thread.sleep(Time.secondsInMilliseconds(interprocessStartupDelayTimeSeconds));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                i++;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        System.out.println(dateFormat.format(new Date()) + ": " + "done.");
    }

    private Supervisor(String[] configurationURLs) {
        
    }
}
