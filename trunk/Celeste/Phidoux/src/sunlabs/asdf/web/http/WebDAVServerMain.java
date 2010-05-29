/*
 * Copyright 2010 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.asdf.web.http;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Stack;
import java.util.logging.Logger;

/**
 * This is a basic WebDAV server that can use files in the local filesytem or in the jar file that contains this class.
 *
 * @author Glenn Scott - Sun Labs, Oracle 
 */
public class WebDAVServerMain {
    
    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }

    public static void main(String args[]) {       

        int httpPort = 8081;
        int clientTimeOutMillis = 10000;
        
        Stack<String> options = new Stack<String>();
        for (int i = args.length - 1; i >= 0; i--) {
            options.push(args[i]);
        }

        while (!options.empty()) {
            if (!options.peek().startsWith("--"))
                break;
            String option = options.pop();
            if (option.equals("--port")) {
                httpPort = Integer.parseInt(options.pop());
            } else if  (option.equals("--client-timeout")) {
                clientTimeOutMillis = Integer.parseInt(options.pop());
            } else if (option.equals("--help")) {
                System.out.printf("Arguments: [--port <port>] [--client-timeout <millis>]%n");
                System.exit(1);
            }
        }

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.socket().bind(new InetSocketAddress(httpPort));
            serverSocketChannel.socket().setReuseAddress(true);

            String localAddress = InetAddress.getLocalHost().getHostAddress();

            System.out.printf("WebDAV Server. http://%s:%d%n", localAddress, httpPort);
            
            WebDAV.Backend backend ;
            
            URL root = WebDAVServerMain.class.getClass().getResource("/");
            if (root == null) {
                backend = new ClassLoaderBackend(WebDAVServerMain.class.getClassLoader(), "docroot/");
            } else {
                backend = new FileSystemBackend("docroot/");                
            }
            
            while (true) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                Socket socket = socketChannel.socket();
                socket.setSoTimeout(clientTimeOutMillis);
                socket.setReceiveBufferSize(8192);
                socket.setSendBufferSize(8192);

                HTTPServer server = new HTTPServer(socketChannel);
                server.setTrace(true);
                HTTP.NameSpace nameSpace = new WebDAVNameSpace(server, backend);

                server.addNameSpace(new URI("/"), nameSpace);
                server.setLogger(Logger.getLogger(WebDAVServerMain.class.getName()));
                server.start();
            }
        } catch (Exception e) {
            System.out.flush();
            e.printStackTrace();
        }
    }
}
