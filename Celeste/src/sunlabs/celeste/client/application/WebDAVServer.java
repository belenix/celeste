/*
 * Copyright 2009-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle, 16 Network Circle, Menlo Park, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.celeste.client.application;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTPServer;
import sunlabs.asdf.web.http.WebDAV;
import sunlabs.asdf.web.http.WebDAVNameSpace;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.application.webdav.CelesteBackend;
import sunlabs.titan.Release;

/**
 * This is a work in progress.
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class WebDAVServer extends HTTPServer {

    public WebDAVServer(SocketChannel channel)
    throws IOException, MalformedObjectNameException, MBeanRegistrationException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        super(channel);
    }
    
    public WebDAVServer(SocketChannel channel, OutputStream inputTap, OutputStream outputTap)
    throws IOException, MalformedObjectNameException, MBeanRegistrationException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        super(channel, inputTap, outputTap);
    }
    

    /**
     * This method is here as a stub to call out to an external authentication system.
     *
     * @param userName
     * @param password
     */
    public static boolean checkExternalAuthentication(String userName, String password) {
        return true;
    }

    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }

    public static boolean createMissingUserAccounts = true;

    public static void main(String args[]) {       

        String celesteAddress = "127.0.0.1:14000";
        int webDAVPort = 8080;
        int clientTimeOutMillis = 0;
        int celesteTimeOutMillis = 0;
        long watchdogMillis = 0;
        boolean trace = false;
        
        Stack<String> options = new Stack<String>();
        for (int i = args.length - 1; i >= 0; i--) {
            options.push(args[i]);
        }

        while (!options.empty()) {
            if (!options.peek().startsWith("--"))
                break;
            String option = options.pop();

            if (option.equals("--celeste-address")) {
                celesteAddress = options.pop();
            } else if (option.equals("--trace")) {
                trace = true;
            } else if (option.equals("--port")) {
                webDAVPort = Integer.parseInt(options.pop());
            } else if (option.equals("--timeout")) {
                clientTimeOutMillis = Integer.parseInt(options.pop());
                System.err.printf("--timeout is deprecated.  Use --client-timeout instead.%n");
            } else if  (option.equals("--client-timeout")) {
                clientTimeOutMillis = Integer.parseInt(options.pop());
            } else if (option.equals("--celeste-timeout")) {
                celesteTimeOutMillis = Integer.parseInt(options.pop());
            } else if (option.equals("--watchdog")) {
                watchdogMillis = Integer.parseInt(options.pop());
            } else if (option.equals("--help")) {
                System.out.printf("Usage: celeste-webdav [--celeste-address address:port] [--port port] [--create-accounts] [--client-timeout millis] [--celeste-timeout millis] [--watchdog millis]%n");
                System.exit(1);
            } else if (option.equals("--create-accounts")) {
                WebDAVServer.createMissingUserAccounts = true;
                System.out.printf("Creating missing user accounts on demand.%n");
            }
        }

        if (watchdogMillis != 0) {
            ScheduledExecutorService threads = Executors.newScheduledThreadPool(1);
            threads.scheduleWithFixedDelay(new Runnable() { public void run() { System.err.printf("Watchdog%n"); System.exit(1); }}, watchdogMillis, watchdogMillis, TimeUnit.MILLISECONDS);
        }

        if (!options.empty()) {
            // Run the WebDAV server on a prerecorded input file.
            String inputFile = options.pop();
            try {
                // Run from a pre-recorded input file.
                String fileName = options.pop();

                try {
                    HTTPServer httpServer = new HTTPServer(new FileInputStream(inputFile), new FileOutputStream(fileName));
                    httpServer.setTrace(trace);

                    WebDAV.Backend celesteBackend = new CelesteBackend(makeAddress(celesteAddress), new CelesteProxy.Cache(4, celesteTimeOutMillis));
                    HTTP.NameSpace webDAV = new WebDAVNameSpace(httpServer, celesteBackend);
                    
                    httpServer.addNameSpace(new URI("/"), webDAV);
                    httpServer.setLogger(Logger.getLogger(WebDAVServer.class.getName()));
                    httpServer.start();
                    try { httpServer.join(); } catch (InterruptedException e) { }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.exit(1);
        }

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.socket().bind(new InetSocketAddress(webDAVPort));
            serverSocketChannel.socket().setReuseAddress(true);

            String localAddress = InetAddress.getLocalHost().getHostAddress();

            System.out.printf("WebDAVServer  http://%s:%d%nUsing Celeste at %s.%n", localAddress, webDAVPort, celesteAddress);
            System.out.println(Release.ThisRevision());

            while (true) {
                SocketChannel channel = serverSocketChannel.accept();
                Socket socket = channel.socket();
                socket.setSoTimeout(clientTimeOutMillis);
                socket.setReceiveBufferSize(8192);
                socket.setSendBufferSize(8192);

                WebDAVServer server = new WebDAVServer(channel);
                server.setTrace(trace);
//                ,
//                        new FileOutputStream(String.format("webdav-r-%s:%s", socket.getInetAddress().getHostAddress(), socket.getPort())),
//                        new FileOutputStream(String.format("webdav-s-%s:%s", socket.getInetAddress().getHostAddress(), socket.getPort())));

                WebDAV.Backend backend = new CelesteBackend(makeAddress(celesteAddress), new CelesteProxy.Cache(4, celesteTimeOutMillis));
                HTTP.NameSpace webDAV = new WebDAVNameSpace(server, backend);

                server.addNameSpace(new URI("/"), webDAV);
                server.setLogger(Logger.getLogger(WebDAVServer.class.getName()));
                server.start();
            }
        } catch (Exception e) {
            System.out.flush();
            e.printStackTrace();
        }
    }
}
