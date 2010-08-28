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
package sunlabs.titan.node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import sunlabs.asdf.util.Time;
import sunlabs.titan.Copyright;
import sunlabs.titan.node.BeehiveNode.ConfigurationException;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.util.OrderedProperties;

/**
 * <p>
 * Primary command line startup and supervisor of a Titan node.
 * </p>
 * <p>
 * This class has no instance and serves only to construct one or more
 * instances of CelesteNode and run each of them either in a separate
 * thread or in separate processes.
 * </p>
 * <p>
 * If the BeehiveNode instances are run in separate threads,
 * each instance of CelesteNode is programmatically independant
 * from the others and shares only static class variables.
 * </p>
 * <p>
 * Command line options are:
 * </p>
 * <style>
 * table.Beehive-options {
 *  border: 1px solid black;
 * }
 *
 * table.Beehive-options tr td:first-child {
 *  font-family: monospace;
 * }
 *
 * table.Beehive-options tr td:first-child + td {
 *
 * }
 *
 * table.Beehive-options tr td:first-child + td + td {
 *  font-style: italic;
 * }
 * </style>
 *
 * <table class='Beehive-options'>
 * <tr><td>--delay-time &lt;seconds&gt;</td><td>The number of seconds to delay when starting multiple nodes on this host.</td><td></td></tr>
 * <tr><td>--local-address &lt;ip-addr&gt;</td><td>The NodeAddress for this node (ip-addr:dolr-port:node-id).</td><td> (nodeAddress)</td></tr>
 * <tr><td>--dolr-server-port &lt;number&gt;</td><td>The TCP port number to use for the DOLR server of this Node.</td><td> (dolrServerPort)</td></tr>
 * <tr><td>--dolr-client-port &lt;number&gt;</td><td>The TCP port number to use for the DOLR client of this Node.</td><td> (dolrClientPort)</td></tr>
 * <tr><td>--n-nodes &lt;number&gt;</td><td>The number of nodes to start.</td><td>(n_nodes)</td></tr>
 * <tr><td>--dossier</td><td>Enable fast-startup using cached data on neighbours.</td><td>(false)</td></tr>
 * </table>
 * <p>
 * Options to create BeehiveNode instances in separate processes:
 * </p>
 * <table class='Beehive-options'>
 * <tr><td>--use-processes</td><td>Use separate processes for each Beehive Node</td><td>(false)</td></tr>
 * <tr><td>-jar &ltfile-name&gt</td><td>Use filename as the Java jar file to run each node./td><td>dist/celeste.jar</td></tr>
 * </table>
 */
public class Beehive {

    private static class SuperviseProcess implements Runnable {
        protected String command;

        public SuperviseProcess(String command) {
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
                System.out.println(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()) + ": " + this.command + " died.");
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

    private static String release = String.valueOf(Beehive.class.getPackage().getImplementationVersion());

    /**
     *
     */
    public static void main(String[] args) {
        System.out.println(Beehive.release);
        System.out.println(Copyright.miniNotice);
        
        //
        // Establish default values.  The argument processing code below can
        // override them.
    	//
    	OrderedProperties properties = new OrderedProperties();

        String gatewayArgument = null;

        // This way to construct the default spool directory is very UNIX-centric.
        properties.setProperty(BeehiveNode.LocalFileSystemRoot.getName(), File.listRoots()[0] + "tmp" + File.separator + "celeste" + File.separator);

        properties.setProperty(BeehiveNode.Port.getName(), 12000);
        properties.setProperty(BeehiveNode.ConnectionType.getName(), "ssl");
        properties.setProperty(WebDAVDaemon.Port.getName(), 12001);
        properties.setProperty(BeehiveNode.InterNetworkAddress.getName(), "127.0.0.1");
        properties.setProperty(BeehiveNode.GatewayRetryDelayMillis.getName(), Time.secondsInMilliseconds(30));
        properties.setProperty(BeehiveNode.ObjectStoreCapacity.getName(), "unlimited");

        String javaFile = System.getenv("JAVA");
        if (javaFile == null)
            javaFile = "/usr/bin/java";

        String jarFile = "lib/titan.jar";

        String keyStoreNames[] = null;

        //
        // JMX port setting:
        //
        //      When running in a single process, the
        //      com.sun.management.jmxremote.port system property should be
        //      set to whatever was given on the command line (or to its
        //      default) and ought to be usable with that value for all Node
        //      instances.
        //
        //      When running multiple processes, each process needs to have
        //      the property set to a distinct value, set by incrementing the
        //      base value for each successive process.
        //
        // Other JMX_related properties will already have been set as
        // part of jvmArguments, but the specific port for this JVM
        // still needs to be set.
        //
        Integer jmxPort = null; // null signifies that the jmxPort has not been set.
        int jmxPortIncrement = 1;
        
        int n_nodes = 1;
        int interprocessStartupDelayTimeSeconds = 5;

        int beehivePortIncrement = 2;
        int webdavPortIncrement = 2;
        
        boolean useThreads = false;

        RuntimeMXBean mxbean = ManagementFactory.getRuntimeMXBean();

        Set<String> jvmArgs = new HashSet<String>();
        jvmArgs.add("-server");
        
        StringBuilder jvmArguments = new StringBuilder(" -server");
        for (String a : mxbean.getInputArguments()) {
            jvmArguments.append(" ").append(a);
        }

        jarFile = mxbean.getClassPath();

        try {
            properties.setProperty(BeehiveNode.InterNetworkAddress.getName(), InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (args[i].equals("--url")) {
                    try {
                        OrderedProperties p = new OrderedProperties(new URL(args[++i]));
                        properties.putAll(p);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                } else if (args[i].equals("--threads")) {
                    useThreads = true;
                } else if (args[i].equals("--delay-time")) {
                    interprocessStartupDelayTimeSeconds = Integer.parseInt(args[++i]);
                    if (interprocessStartupDelayTimeSeconds < 1) {
                        System.err.printf("--delay-time %d: time must be greater than 1%n", interprocessStartupDelayTimeSeconds);
                        throw new IllegalArgumentException();
                    }
                } else if (args[i].equals("--localfs-root") || args[i].equals("--spool-root")) {
                    String value = args[++i];
                    properties.setProperty(BeehiveNode.LocalFileSystemRoot.getName(), value + File.separator);
                } else if (args[i].equals("--n-nodes")) {
                    n_nodes = Integer.parseInt(args[++i]);
                    if (n_nodes < 1) {
                        System.err.printf("--n-nodes %d: you must specify at least 1 node", n_nodes);
                        throw new IllegalArgumentException();
                    }
//                } else if (args[i].equals("--gateway")) {
//                    gatewayArgument = args[++i];
//                    properties.setProperty(BeehiveNode.GatewayURL.getName(), gatewayArgument);
//                } else if (args[i].equals("--gateway-retry")) {
//                    String value = args[++i];
//                    if (Integer.parseInt(value) < 10) {
//                        System.err.printf("--gateway-retry %s: retry time cannot be less than 10 seconds.", value);
//                        throw new IllegalArgumentException();
//                    }
//                    properties.setProperty(BeehiveNode.GatewayRetryDelayMillis.getName(), value);
                } else if (args[i].equals("--local-address") || args[i].equals("--node-address")) {
                    String value = args[++i];
//                    properties.setProperty(BeehiveNode.LocalNetworkAddress.getName(), value);
                    properties.setProperty(BeehiveNode.InterNetworkAddress.getName(), value);
                } else if (args[i].equals("--nat")) {
                    String[] value = args[++i].split(":");
//                    properties.setProperty(BeehiveNode.LocalNetworkAddress.getName(), value[0]);
                    properties.setProperty(BeehiveNode.InterNetworkAddress.getName(), value[1]);
                } else if (args[i].equals("--internetwork-address")) {
                    properties.setProperty(BeehiveNode.InterNetworkAddress.getName(), args[++i]);
                } else if (args[i].equals("--beehive-port") || args[i].equals("--titan-port")) {
                    String[] tokens = args[++i].split(",");
                    String port = tokens[0];
                    if (tokens.length > 1)
                        beehivePortIncrement = Integer.parseInt(tokens[1]);
                    properties.setProperty(BeehiveNode.Port.getName(), port);
                } else if (args[i].equals("--http-port")) {
                    String[] tokens = args[++i].split(",");
                    String port = tokens[0];
                    if (tokens.length > 1)
                        webdavPortIncrement = Integer.parseInt(tokens[1]);
                    properties.setProperty(WebDAVDaemon.Port.getName(), port);
                } else if (args[i].equals("--jmx-port")) {
                    String[] tokens = args[++i].split(",");
                    String port = tokens[0];
                    if (tokens.length > 1)
                        jmxPortIncrement = Integer.parseInt(tokens[1]);
                    jmxPort = Integer.parseInt(port);
                    if (jmxPort < 1024) {
                        System.err.printf("--jmx-port %d: port number must be greater than 1024.", jmxPort);
                        throw new IllegalArgumentException();
                    }
                } else if (args[i].equals("--jar") ) {
                    jarFile = args[++i];
                } else if (args[i].equals("--java") ) {
                    javaFile = args[++i];
                } else if (args[i].equals("--keystore") ) {
                    keyStoreNames = args[++i].split(",");
                } else if (args[i].equals("--version")) {
                    System.out.println(release);
                    System.out.println(Copyright.miniNotice);
                } else if (args[i].equals("--help") ) {
                    System.out.println("Usage: defaults are in parenthesis.");
                    System.out.printf(" [--delay-time <integer>] (%d)%n", interprocessStartupDelayTimeSeconds);
                    System.out.printf(" [--n-nodes <integer>] (%d)%n", n_nodes);
                    System.out.printf(" [--threads] (use threads instead of spawning a JVM for each node)%n");
//                    System.out.printf(" [--gateway <URL>] (%s)%n", String.valueOf(gatewayArgument));
//                    System.out.printf(" [--gateway-retry <seconds>] (%s)%n", properties.getPropertyAsInt(BeehiveNode.GatewayRetryDelayMillis.getName()));
                    System.out.printf(" [--http-port <integer>[,<integer>]] (%d,%d)%n", properties.getPropertyAsInt(WebDAVDaemon.Port.getName()), webdavPortIncrement);
                    System.out.printf(" [--jar <file name>] (%s)%n", String.valueOf(jarFile));
                    System.out.printf(" [--java <file name>] (%s)%n", javaFile);
                    System.out.printf(" [--jmx-port <integer>[,<integer>]] (%d,%d)%n] (%d)%n", jmxPort, jmxPortIncrement);
                    System.out.printf(" [--titan-port <integer>[,<integer>]] (%d,%d)%n", properties.getPropertyAsInt(BeehiveNode.Port.getName()), beehivePortIncrement);
                    System.out.printf(" [--internetwork-address <ip-addr>] (%s)%n", properties.getProperty(BeehiveNode.InterNetworkAddress.getName()));
                    System.out.printf(" [--localfs-root <directory name>] (%s)%n", properties.getProperty(BeehiveNode.LocalFileSystemRoot.getName()));
                    System.out.printf(" [--V<option>]%n");
                    System.out.printf(" [--D<option>]%n");
                    System.exit(0);
                } else {
                    System.out.println("Ignoring unknown option: " + args[i]);
                }
            } else if (args[i].startsWith("-D")) {
                String[] tokens = args[i].substring(2).split("=");
                if (tokens.length == 1) {
                    System.out.printf("property: %s%n", args[i]);
                } else {
                    properties.setProperty(tokens[0], tokens[1]);
                }
            } else if (args[i].startsWith("-V")) {
                String v = args[i].substring(2);
                jvmArgs.add(v);
            } else {
                // Read this command line argument as a URL to fetch configuration properties.
                // These properties are overridden by options subsequent on the command line.
                try {
                    OrderedProperties p = new OrderedProperties(new URL(args[i]));
                    properties.putAll(p);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }

        File rootDirectory = new File(properties.getProperty(BeehiveNode.LocalFileSystemRoot.getName()));
        if (!rootDirectory.exists()) {
            if (rootDirectory.mkdirs() == false) {
                System.err.printf("Failed to create %s%n", properties.getProperty(BeehiveNode.LocalFileSystemRoot.getName()));
                System.exit(1);
            }
        }

        if (keyStoreNames != null) {
            if (keyStoreNames.length < n_nodes) {
                System.err.printf("--n-nodes %d requires at least %d --keystore file names specified.%n", n_nodes, n_nodes);
                System.exit(-1);
            }
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); // ISO 8601    
        
        
        // Start all of the nodes as threads in one JVM.
        if (useThreads) {
            BeehiveNode[] node = new BeehiveNode[n_nodes];
            Thread[] thread = new Thread[n_nodes];

            try {
                for (int i = 0; i < n_nodes; i++) {
                    OrderedProperties configurationProperties = new OrderedProperties();
                    configurationProperties.putAll(properties);
                    if (keyStoreNames != null) {
                        configurationProperties.setProperty(BeehiveNode.KeyStoreFileName.getName(), keyStoreNames[i]);
                    }

                    node[i] = new BeehiveNode(configurationProperties);

                    thread[i] = node[i].start();

                    System.out.printf("%s [%d ms] %s%n",
                            dateFormat.format(new Date()),
                            System.currentTimeMillis() - Long.parseLong(node[i].getProperty(BeehiveNode.StartTime.getName())),
                            node[i].getNodeAddress().format());

                    try {
                        Thread.sleep(Time.secondsInMilliseconds(interprocessStartupDelayTimeSeconds));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    properties.setProperty(BeehiveNode.GatewayURL.getName(), node[0].getNodeAddress().getHTTPInterface());
                    properties.setProperty(BeehiveNode.Port.getName(), properties.getPropertyAsInt(BeehiveNode.Port.getName()) + beehivePortIncrement);
                    properties.setProperty(WebDAVDaemon.Port.getName(), properties.getPropertyAsInt(WebDAVDaemon.Port.getName()) + webdavPortIncrement);
                }
                if (n_nodes > 1) {
                    System.out.printf("%s All node threads running.%n", dateFormat.format(new Date()));
                }

                // Wait for all the threads to terminate.
                for (int j = 0; j < thread.length; j++) {
                    if (thread[j].isAlive()) {
                        try { thread[j].join(); } catch (InterruptedException e) { /**/ }
                        j = -1; // Make this loop start over again.
                    }
                }
            } catch (java.net.BindException e) {
                System.out.printf(
                        "%s beehive-port=%d http-port=%d celeste-port=%d jmx-port=%d%n",
                        e.toString(), properties.getPropertyAsInt(BeehiveNode.Port.getName()), properties.getPropertyAsInt(WebDAVDaemon.Port.getName()),
                        properties.getPropertyAsInt(WebDAVDaemon.Port.getName()), jmxPort);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ConfigurationException e) {
                e.printStackTrace();
            }
            System.err.println(dateFormat.format(new Date()) + " EXITED");
            System.exit(1);
        }
        
        System.out.printf("jvmArguments %s%n", jvmArguments);
        System.out.printf("jvmArgs %s%n", jvmArgs);
        jvmArguments = new StringBuilder();
        for (String a : jvmArgs) {
            jvmArguments.append(" ").append(a);
        }
        System.out.printf("new per node jvmArgs '%s'%n", jvmArguments);
        
        // Use separate processes for each Node instance.
        Executor executors = Executors.newCachedThreadPool();

        String applicationSpecification = " -cp " + jarFile + " sunlabs.titan.node.BeehiveNode";

        for (int i = 0; i < n_nodes; i++) {
            String jmxPortProperty = jmxPort == null ? "" : String.format(" -Dcom.sun.management.jmxremote.port=%d", jmxPort);

            OrderedProperties configurationProperties = new OrderedProperties();
            // Copy all of the properties into this node's properties.
            // Customise this node's properties.
            configurationProperties.putAll(properties);
            if (keyStoreNames != null) {
                configurationProperties.setProperty(BeehiveNode.KeyStoreFileName.getName(), keyStoreNames[i]);
            }

            String configurationFileName = String.format("%s/node-%s-%s-%d.cf",
                    configurationProperties.getProperty(BeehiveNode.LocalFileSystemRoot.getName()),
                    configurationProperties.getProperty(BeehiveNode.InterNetworkAddress.getName()),
                    configurationProperties.getProperty(BeehiveNode.Port.getName()),
                    i);
            
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(configurationFileName);
                configurationProperties.store(fout, "");
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            } finally {
                if (fout != null) try { fout.close(); } catch (IOException e) { /**/ }
            }
            String configurationURL = "file://" + configurationFileName;

            String command = javaFile + " -Dtitan-node" + jvmArguments.toString() + jmxPortProperty + applicationSpecification + " " + configurationURL;
            System.out.println(command);

            try {
                executors.execute(new SuperviseProcess(command));
            } catch(Exception e) {
                e.printStackTrace();
            }
            
            try {
                Thread.sleep(Time.secondsInMilliseconds(interprocessStartupDelayTimeSeconds));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //
            // If the first node created a new Beehive confederation, have
            // subsequent nodes use it as a gateway.
            //
            if (i == 0) {
                gatewayArgument = "http://" +  properties.getProperty(BeehiveNode.InterNetworkAddress.getName()) + ":" + properties.getProperty(WebDAVDaemon.Port.getName());
                properties.setProperty(BeehiveNode.GatewayURL.getName(), gatewayArgument);
            }

            properties.setProperty(BeehiveNode.Port.getName(), properties.getPropertyAsInt(BeehiveNode.Port.getName()) + beehivePortIncrement);
            properties.setProperty(WebDAVDaemon.Port.getName(), properties.getPropertyAsInt(WebDAVDaemon.Port.getName()) + webdavPortIncrement);
            if (jmxPort != null) {
                jmxPort += jmxPortIncrement;
            }
        }
        System.out.println(dateFormat.format(new Date()) + ": " + "done.");
    }

    //
    // Prevent instantiation.
    //
    private Beehive() {
    }
}
