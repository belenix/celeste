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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import sunlabs.asdf.util.AbstractStoredMap;
import sunlabs.asdf.util.Time;
import sunlabs.titan.Copyright;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.node.TitanNodeImpl.ConfigurationException;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.util.OrderedProperties;

/**
 * <p>
 * Primary command line startup and supervisor of a Titan node.
 * </p>
 * <p>
 * The static {@link #main(String[])} method constructs one or more
 * instances of {@link TitanNode} and runs each of them either in their own {@link Thread} or in separate processes.
 * </p>
 * <p>
 * If the {@code TitanNode} instances are run in there own {@code Thread},
 * each instance of {@code TitanNode} is programmatically independent
 * from the others and shares only static class variables.
 * </p>
 * <p>
 * Command line options are:
 * </p>
 * <style>
 * table.Titan-options {
 *  border: 1px solid black;
 * }
 *
 * table.Titan-options tr td:first-child {
 *  font-family: monospace;
 * }
 *
 * table.Titan-options tr td:first-child + td {
 *
 * }
 *
 * table.Titan-options tr td:first-child + td + td {
 *  font-style: italic;
 * }
 * </style>
 *
 * <table class='Titan-options'>
 * <tr><td>--delay-time &lt;seconds&gt;</td><td>The number of seconds to delay when starting multiple nodes on this host.</td><td></td></tr>
 * <tr><td>--local-address &lt;ip-addr&gt;</td><td>The NodeAddress for this node (ip-addr:dolr-port:node-id).</td><td> (nodeAddress)</td></tr>
 * <tr><td>--dolr-server-port &lt;number&gt;</td><td>The TCP port number to use for the DOLR server of this Node.</td><td> (dolrServerPort)</td></tr>
 * <tr><td>--dolr-client-port &lt;number&gt;</td><td>The TCP port number to use for the DOLR client of this Node.</td><td> (dolrClientPort)</td></tr>
 * <tr><td>--n-nodes &lt;number&gt;</td><td>The number of nodes to start.</td><td>(n_nodes)</td></tr>
 * <tr><td>--dossier</td><td>Enable fast-startup using cached data on neighbours.</td><td>(false)</td></tr>
 * </table>
 * <p>
 * Options to create TitanNode instances in separate processes:
 * </p>
 * <table class='Titan-options'>
 * <tr><td>--use-processes</td><td>Use separate processes for each Titan Node</td><td>(false)</td></tr>
 * <tr><td>-jar &ltfile-name&gt</td><td>Use filename as the Java jar file to run each node./td><td>dist/titan.jar</td></tr>
 * </table>
 */
public class Titan {

    public static class SuperviseProcess implements Runnable {
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
                System.out.println(Time.ISO8601(System.currentTimeMillis()) + ": " + this.command + " died.");
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
    

    private static String release = String.valueOf(Titan.class.getPackage().getImplementationVersion());

    /**
     *
     */
    public static void main(String[] args) {
        System.out.println(Titan.release);
        System.out.println(Copyright.miniNotice);
        
        //
        // Establish default values.  The argument processing code below can
        // override them.
    	//
    	OrderedProperties properties = new OrderedProperties();

        String gatewayArgument = null;

        // This way to construct the default spool directory is very UNIX-centric.
        properties.setProperty(TitanNodeImpl.LocalFileSystemRoot.getName(), File.listRoots()[0] + "tmp" + File.separator + "titan" + File.separator);

        properties.setProperty(WebDAVDaemon.Port.getName(), 12001);
        properties.setProperty(TitanNodeImpl.Port.getName(), 12000);
        properties.setProperty(TitanNodeImpl.ConnectionType.getName(), "plain");
        properties.setProperty(TitanNodeImpl.InterNetworkAddress.getName(), "127.0.0.1");
        properties.setProperty(TitanNodeImpl.GatewayRetryDelaySeconds.getName(), 30);

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

        int titanPortIncrement = 2;
        int webdavPortIncrement = 2;
        
        boolean useThreads = false;

        RuntimeMXBean mxbean = ManagementFactory.getRuntimeMXBean();

        StringBuilder nodeVMArguments = new StringBuilder(" -server ");

        jarFile = mxbean.getClassPath();

        try {
            properties.setProperty(TitanNodeImpl.InterNetworkAddress.getName(), InetAddress.getLocalHost().getHostAddress());
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
                } else if (args[i].equals("--delay")) {
                    interprocessStartupDelayTimeSeconds = Integer.parseInt(args[++i]);
                    if (interprocessStartupDelayTimeSeconds < 1) {
                        System.err.printf("--delay %d: time must be greater than 1%n", interprocessStartupDelayTimeSeconds);
                        throw new IllegalArgumentException();
                    }
                } else if (args[i].equals("--n-nodes")) {
                    n_nodes = Integer.parseInt(args[++i]);
                    if (n_nodes < 1) {
                        System.err.printf("--n-nodes %d: you must specify at least 1 node", n_nodes);
                        throw new IllegalArgumentException();
                    }
                } else if (args[i].equals("--titan-port")) {
                    String[] tokens = args[++i].split(",");
                    String port = tokens[0];
                    if (tokens.length > 1)
                        titanPortIncrement = Integer.parseInt(tokens[1]);
                    properties.setProperty(TitanNodeImpl.Port.getName(), port);
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
                    System.out.printf(" [--version]%n");
                    System.out.printf(" [-D<name>=<value>]%n");
                    System.out.printf(" [--delay <integer>] (%d)%n", interprocessStartupDelayTimeSeconds);
                    System.out.printf(" [--n-nodes <integer>] (%d)%n", n_nodes);
                    System.out.printf(" [--threads] (use threads instead of spawning a JVM for each node)%n");
                    System.out.printf(" [--http-port <integer>[,<integer>]] (%d,%d)%n", properties.getPropertyAsInt(WebDAVDaemon.Port.getName()), webdavPortIncrement);
                    System.out.printf(" [--jar <file name>] (%s)%n", String.valueOf(jarFile));
                    System.out.printf(" [--java <file name>] (%s)%n", javaFile);
                    System.out.printf(" [--jmx-port <integer>[,<integer>]] (%d,%d)%n] (%d)%n", jmxPort, jmxPortIncrement);
                    System.out.printf(" [--titan-port <integer>[,<integer>]] (%d,%d)%n", properties.getPropertyAsInt(TitanNodeImpl.Port.getName()), titanPortIncrement);
                    System.out.printf(" [--V<option>]%n");
                    System.out.printf(" [--D<option>]%n");
                    System.exit(0);
                } else {
                    System.out.println("Ignoring unknown option: " + args[i]);
                }
            } else if (args[i].startsWith("-D")) {
                String[] tokens = args[i].substring(2).split("=");
                if (tokens.length == 1) {
                } else {
                    properties.setProperty(tokens[0], tokens[1]);
                }
            } else if (args[i].startsWith("-V")) {
                String v = args[i].substring(3);
                if (v.startsWith("'")) {
                    v.substring(1, v.length()-1);
                }
                nodeVMArguments.append(v);
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

        File rootDirectory = new File(properties.getProperty(TitanNodeImpl.LocalFileSystemRoot.getName()));
        if (!rootDirectory.exists()) {
            if (rootDirectory.mkdirs() == false) {
                System.err.printf("Failed to create %s%n", properties.getProperty(TitanNodeImpl.LocalFileSystemRoot.getName()));
                System.exit(1);
            }
        }

        if (keyStoreNames != null) {
            if (keyStoreNames.length < n_nodes) {
                System.err.printf("--n-nodes %d requires at least %d --keystore file names specified.%n", n_nodes, n_nodes);
                System.exit(-1);
            }
        }

        // Start all of the nodes as threads in one JVM.
        if (useThreads) {
            TitanNodeImpl[] node = new TitanNodeImpl[n_nodes];
            Thread[] thread = new Thread[n_nodes];

            try {
                for (int i = 0; i < n_nodes; i++) {
                    OrderedProperties configurationProperties = new OrderedProperties();
                    configurationProperties.putAll(properties);
                    if (keyStoreNames != null) {
                        configurationProperties.setProperty(TitanNodeImpl.KeyStoreFileName.getName(), keyStoreNames[i]);
                    }

                    node[i] = new TitanNodeImpl(configurationProperties);

                    thread[i] = node[i].start();

                    System.out.printf("%s [%d ms] %s%n", Time.ISO8601(System.currentTimeMillis()),
                            System.currentTimeMillis() - Long.parseLong(node[i].getProperty(TitanNodeImpl.StartTime.getName())),
                            node[i].getNodeAddress().format());

                    try {
                        Thread.sleep(Time.secondsInMilliseconds(interprocessStartupDelayTimeSeconds));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    properties.setProperty(TitanNodeImpl.GatewayURL.getName(), node[0].getNodeAddress().getHTTPInterface());
                    properties.setProperty(TitanNodeImpl.Port.getName(), properties.getPropertyAsInt(TitanNodeImpl.Port.getName()) + titanPortIncrement);
                    properties.setProperty(WebDAVDaemon.Port.getName(), properties.getPropertyAsInt(WebDAVDaemon.Port.getName()) + webdavPortIncrement);
                }
                if (n_nodes > 1) {
                    System.out.printf("%s All node threads running.%n", Time.ISO8601(System.currentTimeMillis()));
                }

                // Wait for all the threads to terminate.
                for (int j = 0; j < thread.length; j++) {
                    if (thread[j].isAlive()) {
                        try { thread[j].join(); } catch (InterruptedException e) { /**/ }
                        j = -1; // Make this loop start over again.
                    }
                }
            } catch (java.net.BindException e) {
                System.out.printf("%s titan-port=%d http-port=%d jmx-port=%d%n",
                        e.toString(),  properties.getPropertyAsInt(TitanNodeImpl.Port.getName()), properties.getPropertyAsInt(WebDAVDaemon.Port.getName()),
                        jmxPort);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ConfigurationException e) {
                e.printStackTrace();
            } catch (AbstractStoredMap.OutOfSpace e) {
                e.printStackTrace();
            }
            System.err.println(Time.ISO8601(System.currentTimeMillis()) + " EXITED");
            System.exit(1);
        }
        
        // Use separate processes for each Node instance.
        Executor executors = Executors.newCachedThreadPool();

        String applicationSpecification = " -cp " + jarFile + " sunlabs.titan.node.TitanNodeImpl";

        for (int i = 0; i < n_nodes; i++) {
            String jmxPortProperty = jmxPort == null ? "" : String.format(" -Dcom.sun.management.jmxremote.port=%d", jmxPort);

            OrderedProperties configurationProperties = new OrderedProperties();
            // Copy all of the properties into this node's properties.
            // Customise this node's properties.
            configurationProperties.putAll(properties);
            if (keyStoreNames != null) {
                configurationProperties.setProperty(TitanNodeImpl.KeyStoreFileName.getName(), keyStoreNames[i]);
            }

            String configurationFileName = String.format("%s/node-%s-%s-%d.cf",
                    configurationProperties.getProperty(TitanNodeImpl.LocalFileSystemRoot.getName()),
                    configurationProperties.getProperty(TitanNodeImpl.InterNetworkAddress.getName()),
                    configurationProperties.getProperty(TitanNodeImpl.Port.getName()),
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
            String configurationURL = " file://" + configurationFileName;

            String command = javaFile + " -Dtitan-node" +  nodeVMArguments.toString() + jmxPortProperty + applicationSpecification + configurationURL;
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
            // If the first node created a new Titan confederation, have
            // subsequent nodes use it as a gateway.
            //
            if (i == 0) {
                gatewayArgument = "http://" +  properties.getProperty(TitanNodeImpl.InterNetworkAddress.getName()) + ":" + properties.getProperty(WebDAVDaemon.Port.getName());
                properties.setProperty(TitanNodeImpl.GatewayURL.getName(), gatewayArgument);
            }

            properties.setProperty(TitanNodeImpl.Port.getName(), properties.getPropertyAsInt(TitanNodeImpl.Port.getName()) + titanPortIncrement);
            properties.setProperty(WebDAVDaemon.Port.getName(), properties.getPropertyAsInt(WebDAVDaemon.Port.getName()) + webdavPortIncrement);
            if (jmxPort != null) {
                jmxPort += jmxPortIncrement;
            }
        }
        System.out.println(Time.ISO8601(System.currentTimeMillis()) + ": " + "done.");
    }

    public Titan() {
        
    }
}
