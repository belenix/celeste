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

package sunlabs.celeste.client.application;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.Xxhtml;
import sunlabs.asdf.web.XML.XHTML.Table;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTPServer;
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.asdf.web.http.HttpResponse;
import sunlabs.asdf.web.http.HttpSession;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.asdf.web.http.NameSpace;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.Copyright;
import sunlabs.beehive.Release;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.util.WeakMBeanRegistrar;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.filesystem.CelesteFileSystem;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.celeste.client.filesystem.HierarchicalFileSystem;
import sunlabs.celeste.client.filesystem.simple.FileImpl;
import sunlabs.celeste.client.filesystem.tabula.PathName;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.ReadProfileOperation;

/**
 * <style>
 * h2 {
 *  font-size: large;
 * }
 * table.form {
 *  border: 1px solid black;
 * }
 * table.form tr td:first-child {
 *  font-family: monospace;
 * }
 * table.form tr th {
 *  border-bottom: 3px double black;
 * }
 * pre.example {
 *  background: none #D0D0D0;
 * }
 * table.status-codes {
 *  border: 1px solid black;
 *  background: none pink;
 * }
 * table.status-codes tr th {
 *  border-bottom: 3px double black;
 * }
 * table.status-codes tr td:first-child {
 *  font-family: monospace;
 * }
 * </style>
 * <h1>This Documentation Is Out Of Date</h1>
 * <p>
 * Celeste Browser - An HTTP interface to Celeste file system.
 * </p>
 * <p>
 * The HTTP interface receives HTTP-GET, HTTP-HEAD, HTTP-POST, and HTTP-PUT
 * requests.  In all cases where an HTTP-GET request is sent, an HTTP-HEAD
 * request may be sent in its stead in which case the response is only the
 * HTTP header of the response and none of the message-body is in the reply.
 * </p>
 * <dl><b>Definitions</b>
 * <dt><i>celeste</i></dt>
 * <dd>The fully specified {@literal <host>:<port>} of the Celeste node server.</dd>
 * </dl>
 *
 * <h1>New File System (newFileSystem)</h1>
 * <p>
 * To create a new Celeste File System, transmit an HTTP-GET or HTTP-HEAD
 * request with a url-encoded form consisting of the form variables.
 * </p>
 * <table class="form">
 * <tr><th>Name</th>
 *     <th>Description</th></tr>
 * <tr><td>nameSpaceId</td>
 *     <td>The name space to host the file system.</td></tr>
 * <tr><td>nameSpacePassword</td>
 *     <td>The password for the specified name space.</td></tr>
 * <tr><td>accessorId</td>
 *     <td>The name of the Profile to be used to access the file system.</td></tr>
 * <tr><td>accessorPassword</td>
 *     <td>The password for the specified accessor Profile.</td></tr>
 * <tr><td>replicationParams</td>
 *     <td>The list of replication parameters separated by a single ';' character.</td></tr>
 * </table>
 * <p>
 * The accessorId and accessorPassword form variables may be omitted; if they
 * are, the nameSpaceId and nameSpacePassword variables serve that purpose as
 * well as their own.
 * </p>
 * <h2>Request</h2>
 * <pre class="example">
 * http://<i>celeste</i>/newFileSystem/?
 * nameSpaceId=<i>name</i>&amp;
 * nameSpacePassword=<i>pw</i>&amp;
 * accessorId=<i>another-name</i>&amp;
 * accessorPassword=<i>another-pw</i>&amp;
 * replicationParams=<i>replicationParameters</i></pre>
 * <h2>Response</h2>
 * <p>The name space identifier.</p>
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/newFileSystem/user/?
 * nameSpaceId=user&amp;
 * nameSpacePassword=password&amp;
 * replicationParams=AObject.Replication.Store=2;VObject.Replication.Store=2;BObject.Replication.Store=2'
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes return via the HTTP response</p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th>
 *     <th>Interpretation</th></tr>
 * <tr><td>OK</td>
 *     <td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td>
 *     <td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td>
 *     <td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 * <h1>Connecting to File System (mount)</h1>
 * <p>To connect to a Celeste hierarchical file system root and obtain a
 * sessionId for subsequent use, transmit an HTTP-GET or HTTP-HEAD request
 * with a url-encoded form consisting of the form variables.</p>
 * <table class="form">
 * <tr><th>Name</th>
 *     <th>Description</th></tr>
 * <tr><td>nameSpaceId</td>
 *     <td>The name space to host the file system.</td></tr>
 * <tr><td>profilePassword</td>
 *     <td>The password for the specified name space.</td></tr>
 * <tr><td>accessorId</td>
 *     <td>The name of the Profile to be used to access the file system.</td></tr>
 * <tr><td>accessorPassword</td>
 *     <td>The password for the specified accessor Profile.</td></tr>
 * <tr><td>mountPoint</td>
 *     <td>The name of the existing file-system directory to use as the root
 *         of the file-system.</td></tr>
 * <tr><td>replicationParams</td>
 *     <td>The list of replication parameters separated by a single ';' character.</td></tr>
 * </table>
 * <p>
 * The accessorId and accessorPassword form variables may be omitted; if they
 * are, the nameSpaceId and nameSpacePassword variables serve that purpose as
 * well as their own.
 * </p>
 *
 * <h2>Request</h2>
 * <pre class="example">
 * http://<i>celeste</i>/mount/<i>mountPoint</i>?
 * nameSpaceId=<i>name</i>&amp;
 * nameSpacePassword=<i>pw</i>&amp;
 * accessorId=<i>another-name</i>&amp;
 * accessorPassword=<i>another-pw</i>&amp;
 * mountPoint=<i>path</i>&amp;
 * replicationParams=<i>replicationParameters</i></pre>
 * <h2>Response</h2>
 * <p>The session-Id to use in subsequent invocations.</p>
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/mount/user/?
 * nameSpaceId=user&amp;
 * nameSpacePassword=password&amp;
 * replicationParams=AObject.Replication.Store=2;VObject.Replication.Store=2;BObject.Replication.Store=2'
 * mountPoint=user'
 * 37ED634F5D85BBBD43C3DF8465C73214C2886C1E381EC5F2A319347DB885A3E7
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 * <h1>Create a New Directory (mkdir)</h1>
 * <p>To create a new directory, transmit an HTTP-GET or HTTP-HEAD request with a url-encoded form consisting of the form variables.</p>
 * <table class="form">
 * <tr><th>Name</th><th>Description</th></tr>
 * <tr><td>sessionId</td><td>The sessionId previously obtained via an invocation of mount.</td></tr>
 * </table>
 *
 * <h2>Request</h2>
 * <pre class="example">
 * http://<i>celeste</i>/mkdir/<i>path</i>?sessionId=<i>id</i></pre>
 * <h2>Response</h2>
 * <p>The canonical path of the new directory.</p>
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/mkdir/user/Foo?
 * sessionId=37ED634F5D85BBBD43C3DF8465C73214C2886C1E381EC5F2A319347DB8853E7'
 * /Foo
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 *
 * <h1>Read a File or Directory (read)</h1>
 * To read the contents of a file or directory, transmit an HTTP-GET or HTTP-HEAD request with a url-encoded form consisting of the form variables.
 * <table class="form">
 * <tr><th>Name</th><th>Description</th></tr>
 * <tr><td>sessionId</td><td>The sessionId previously obtained via an invocation of mount.</td></tr>
 * </table>
 *
 * <pre class="example">
 * http://<i>celeste</i>/read/<i>path</i>?sessionId=<i>id</i></pre>
 * <h2>Response</h2>
 * <p>If the file is a regular file, then the reply consists of contents of the file unencoded and inline.</p>
 * <p>If the file is a directory, then the reply consists of lines each containing the mime-type, size, last modified time and canonical path-names of each file in the directory</p>
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/read/user/photo1.jpg?sessionId=37ED634F5D85BBBD43C3DF8465C73214C2886C1E381EC5F2A319347DB885A3E7' > photo1.jpg
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 * <h1>List Files in a Directory (read)</h1>
 * <p>To obtain the list of files in a directory, transmit an HTTP-GET or HTTP-HEAD request with a url-encoded form consisting of the form variables.</p>
 * <table class="form">
 * <tr><th>Name</th><th>Description</th></tr>
 * <tr><td>sessionId</td><td>The sessionId previously obtained via an invocation of mount.</td></tr>
 * </table>
 *
 * <pre class="example">
 * http://<i>celeste</i>/read/<i>path</i>?sessionId=<i>id</i>
 * </pre>
 * <h2>Response</h2>
 * <p>A newline separated list of mime-type, size, last modified time and canonical path-names of each file in the directory.</p>
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/read/user?sessionId=37ED634F5D85BBBD43C3DF8465C73214C2886C1E381EC5F2A319347DB885A3E7'
 * application/directory 4 1139338899000 /.
 * application/directory 4 1139338899000 /..
 * application/directory 3 1139338927000 /Foo
 * image/jpeg 198035 1139334076000 /photo1.jpg
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 *
 * <h1>File Meta-data (stat)</h1>
 * <p>To obtain information about a specific file, transmit an HTTP-GET or HTTP-HEAD request with a url-encoded form consisting of the form variables.</p>
 * <table class="form">
 * <tr><th>Name</th><th>Description</th></tr>
 * <tr><td>sessionId</td><td>The sessionId previously obtained via an invocation of mount.</td></tr>
 * </table>
 *
 * <pre class="example">
 * http://<i>celeste</i>/stat/<i>path</i>?sessionId=<i>id</i></pre>
 * <h2>Response</h2>
 * <p>The mime-type, size, last modified time and canonical path-names of the specified file.</p>
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/stat/user/photo1.jpg?sessionId=37ED634F5D85BBBD43C3DF8465C73214C2886C1E381EC5F2A319347DB885A3E7'
 * image/jpeg 198035 1139334076000 /photo1.jpg
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 * <h1>Write a File (write)</h1>
 * To write the contents of a file, transmit an HTTP-PUT request with a url-encoded form consisting of the form variables.
 * <table class="form">
 * <tr><th>Name</th><th>Description</th></tr>
 * <tr><td>sessionId</td><td>The sessionId previously obtained via an invocation of mount.</td></tr>
 * </table>
 *
 * <pre class="example">
 * http://<i>celeste</i>/write/<i>path</i>?sessionId=<i>id</i></pre>
 * <h2>Response</h2>
 * <p>Nothing is returned from an HTTP-PUT request.</p>
 *
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl -T /etc/motd 'http://127.0.0.1:15000/write/user/motd.txt?sessionId=7ECFB3B371BAE914611946F87141C784421796F99BD1818E69F0394C620F15A9'
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 * <h1>Testing File Writeability (isWriteable)</h1>
 * To test if the current session can write to a file, transmit an HTTP-GET
 * or HTTP-HEAD request with a url-encoded form consisting of the form variables.
 * <table class="form">
 * <tr><th>Name</th><th>Description</th></tr>
 * <tr><td>sessionId</td><td>The sessionId previously obtained via an invocation of mount.</td></tr>
 * </table>
 *
 * <pre class="example">
 * http://<i>celeste</i>/isWriteable/<i>path</i>?sessionId=<i>id</i></pre>
 *
 * <h2>Response</h2>
 * <p>An ASCII string encoding the HTTP status code representing the result of the test.</p>
 *
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/isWriteable/user/motd.txt?sessionId=7ECFB3B371BAE914611946F87141C784421796F99BD1818E69F0394C620F15A9'
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The named file was writable at the time of the test.</td></tr>
 * <tr><td>Forbidden</td><td>The named file was not writable at the time of the test.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 *
 * <h1>Delete a File (delete)</h1>
 * <p>To delete file, transmit an HTTP-GET or HTTP-HEAD request with a url-encoded form consisting of the form variables.</p>
 * <table class="form">
 * <tr><th>Name</th><th>Description</th></tr>
 * <tr><td>sessionId</td><td>The sessionId previously obtained via an invocation of mount.</td></tr>
 * </table>
 *
 * <pre class="example">
 * http://<i>celeste</i>/delete/<i>path</i>?sessionId=<i>id</i>
 * </pre>
 * <h2>Response</h2>
 * <p>Nothing is returned.</p>
 *
 * <h2>Interactive command-line line example:</h2>
 * <pre class="example">
 * % curl 'http://127.0.0.1:15000/delete/user/motd.txt?sessionId=7ECFB3B371BAE914611946F87141C784421796F99BD1818E69F0394C620F15A9'
 * %</pre>
 *
 * <h3>Status</h3>
 * <p>Status codes returned via the HTTP response conform to RFC-2616 section 6.1.1 <i>Status Code and Reason Phrase</i></p>
 * <table class="status-codes">
 * <tr><th>Reason Phrase</th><th>Interpretation</th></tr>
 * <tr><td>OK</td><td>The request was performed successfully.</td></tr>
 * <tr><td>Bad Request</td><td>The request is malformed.  Possibly missing required parameters.</td></tr>
 * <tr><td>Internal Server Error</td><td>Failure due to unexpected or unrecoverable internal problems.</td></tr>
 * </table>
 */

//
// XXX: Much of the work in this class consists of constructing XHTML web
//      pages.  That code could be made considerably more efficient by
//      factoring out elements that are constant over successive constructions
//      of a given page rather than reconstructing each element from scratch
//      each time it's needed.

public final class CelesteHTTPd implements CelesteHTTPdMBean {
//    private final static String Version = "20081222";

    private final static String CSSFILE = "/css/celeste-browser.css";
    private final static String SCRIPTFILE = "/js/celeste-browser.js";

    private final static String release = Release.ThisRevision();
    
    private long celesteTimeOutMillis = Time.secondsInMilliseconds(300);
    private final CelesteProxy.Cache proxyCache =
        new CelesteProxy.Cache(4, this.celesteTimeOutMillis);

    //
    // Arrange to use weak references for registrations with the MBean server.
    //
    private final static WeakMBeanRegistrar registrar =
        new WeakMBeanRegistrar(ManagementFactory.getPlatformMBeanServer());

    private static class SessionState extends HttpSession {
        public static final String SESSIONID = "sessionId";
        public static final String NAMESPACEID = "nameSpaceId";
        public static final String NAMESPACEPASSWORD = "nameSpacePassword";
        public static final String NAMESPACEPASSWORD2 = "nameSpacePassword2";
        public static final String ACCESSORID = "accessorId";
        public static final String ACCESSORPASSWORD = "accessorPassword";
        public static final String ACCESSORPASSWORD2 = "accessorPassword2";
        public static final String MOUNTPOINT = "mount";
        public static final String CELESTE = "celeste";
        public static final String REPLICATIONPARAMS = "replicationParams";

        public static final String[] replicationParamList = {
            "AObject.Replication.Store=2;VObject.Replication.Store=2;BObject.Replication.Store=2",
            "AObject.Replication.Store=6;VObject.Replication.Store=3;BObject.Replication.Store=3",
            "AObject.Replication.Store=3;VObject.Replication.Store=3;BObject.Replication.Store=3",
        };

        private CelesteFileSystem fileSystem;

        //
        // A session has two credentials, one denoting the name space in which
        // its associated file system lives, and the other capturing the
        // identity to be used to access that file system.
        //
        private String      nameSpaceId = null;
        private String      nameSpacePassword = null;
        private Profile_    nameSpaceProfile = null;
        private String      accessorId = null;
        private String      accessorPassword = null;
        private Profile_    accessorProfile = null;

        //
        // Used as the nameSpace argument to the CelesteFileSystem
        // constructor.  Typically set to the nameSpaceId field, although
        // there seem to be code paths that let it be set differently via form
        // data input.
        //
        private String mountPoint;

        private String replicationParams;
        private int autoRefresh = 0;
        private boolean showStatistics = false;
        

        public SessionState() {
            super(new BeehiveObjectId().toString());
            this.replicationParams = SessionState.replicationParamList[0];
//            this.currentWorkingDirectory = "/";
        }

        public void fini() {
            //
            // Encourage resource reclamation both explicitly and by dropping
            // references to resource-holding objects.
            //
            this.fileSystem.dispose();
            this.fileSystem = null;
        }

        public String getNameSpace() {
            return this.nameSpaceId;
        }

        public String getAccessorId() {
            return this.accessorId;
        }

        public boolean isValid() {
            return true;
        }

        public XHTML.Select selectReplicationParamsXHTML() {
            XHTML.Select select =
                new XHTML.Select(new XML.Attr("name", SessionState.REPLICATIONPARAMS));
            for (String params : replicationParamList) {
                XHTML.Option option = new XHTML.Option(
                        new XML.Attr("label", params),
                        new XML.Attr("value", params)).
                        add(params);
                if (this.replicationParams.equals(params))
                    option.addAttribute(new XML.Attr("selected", "selected"));
                select.add(option);
            }
            return select;
        }
    }

    private int serverPort;
    private InetSocketAddress celesteAddress;
    private CelesteAPI celesteProxy;
    private Hashtable<String, SessionState> sessions;
    private DataOutputStream logFile;
    private XHTML.Div XHTMLPageFooter;
    private XHTML.HeadSubElement XHTMLStyleLink;
    private XHTML.HeadSubElement XHTMLScriptLink;
    private ObjectName jmxObjectName;

    private String serverRoot = "./web";

    protected CelesteHTTPd(int port, String celesteAddress, DataOutputStream logFile)
        throws
            MalformedObjectNameException,
            NotCompliantMBeanException,
            InstanceAlreadyExistsException,
            MBeanRegistrationException,
            IOException {
        super();
        this.serverPort = port;
        this.sessions = new Hashtable<String, SessionState>();
        this.celesteAddress = makeAddress(celesteAddress);
        this.celesteProxy = new CelesteProxy(this.celesteAddress, this.celesteTimeOutMillis, TimeUnit.MILLISECONDS);
        this.logFile = logFile;

        this.XHTMLPageFooter = new XHTML.Div(
            new XHTML.Div(new XHTML.Span("Powered By Sun Microsystems, Inc.")).setClass("float-right"),
            new Table(new Table.Body(new Table.Row(
                new Table.Data(new XHTML.Anchor("Contact").setHref(XHTML.SafeURL("http://www.opensolaris.org/jive/forum.jspa?forumID=230"))),
                new Table.Data(new XHTML.Anchor("About").setHref(XHTML.SafeURL("http://www.opensolaris.org/os/project/celeste/"))),
                new Table.Data(new XHTML.Anchor("Trademarks").setHref(XHTML.SafeURL("http://www.sun.com/suntrademarks/")))
            ))),
            new XHTML.Div(Copyright.miniNotice),
            new XHTML.Div(this.getClass().getName(), " v", CelesteHTTPd.release))
        .setId("footer");

        this.XHTMLStyleLink = new XHTML.Link().setHref(XHTML.SafeURL(CelesteHTTPd.CSSFILE)).setMedia("all").setRel("stylesheet").setType("text/css");
        this.XHTMLScriptLink = new XHTML.Script("text/javascript").setSource(CelesteHTTPd.SCRIPTFILE);

        this.jmxObjectName = JMX.objectName(
            "com.sun.sunlabs.CelesteBrowser",
            Integer.toString(this.serverPort));
        CelesteHTTPd.registrar.registerMBean(this.jmxObjectName, this,
            CelesteHTTPdMBean.class);
    }

    /**
     * Instances of this class are invoked to process an
     * HTTP protocol PUT request from a client.
     */
    private class HttpPost implements HTTP.Request.Method.Handler {

        public HttpPost() {
            super();
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
            try {
                HTTP.Message.Body.MultiPart.FormData props = request.getMessage().decodeMultiPartFormData();
                return CelesteHTTPd.this.dispatch(request, request.getURI(), props);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } catch (HTTP.BadRequestException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * Instances of this class are invoked to process an
     * HTTP protocol GET request from a client.
     */
    private class HttpGet implements HTTP.Request.Method.Handler {
        public HttpGet() {
            super();
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
            try {
                HttpContent.Multipart.FormData props = new HttpContent.Multipart.FormData(request.getURI());
                return CelesteHTTPd.this.dispatch(request, request.getURI(), props);
            } catch (UnsupportedEncodingException unsupportedEncoding) {
                return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(unsupportedEncoding.toString()));
            } catch (IOException ioException) {
                return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(ioException.toString()));
            }
        }
    }

    /**
     * Instances of this class are invoked to process an
     * HTTP protocol HEAD request from a client.
     */
    private class HttpHead implements HTTP.Request.Method.Handler {
        public HttpHead() {
            super();
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
            try {
                HttpContent.Multipart.FormData props = new HttpContent.Multipart.FormData(request.getURI());
                //
                // XXX: This code treats the request identically to a GET request.
                //      Is that right?  HEAD requests aren't supposed to include a
                //      message body.  Does dispatch() ever do so?
                //
                // This is correct.  The body MUST be computed in order to
                // properly report the content length.  The HTTP server which
                // executes this message takes care to only emit the header.
                //
                return CelesteHTTPd.this.dispatch(request, request.getURI(), props);
            } catch (UnsupportedEncodingException unsupportedEncoding) {
                return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(unsupportedEncoding.getLocalizedMessage()));
            } catch (IOException ioException) {
                return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(ioException.getLocalizedMessage()));
            }
        }
    }

    /**
     * Instances of this class are invoked to process an
     * HTTP protocol PUT request from a client.
     */
    private class HttpPut implements HTTP.Request.Method.Handler {
        public HttpPut() {
            super();
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
            try {
                HttpContent.Multipart.FormData props = new HttpContent.Multipart.FormData(request.getURI());
                return this.putFile(request, request.getURI(), props);
            }  catch (UnsupportedEncodingException unsupportedEncoding) {
                return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(unsupportedEncoding.getLocalizedMessage()));
            } catch (IOException ioException) {
                return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(ioException.getLocalizedMessage()));
            }
        }

        private HTTP.Response putFile(HTTP.Request request, URI uri, HttpContent.Multipart.FormData props) throws IOException {
            String sessionId  = HttpMessage.asString(props.get(SessionState.SESSIONID), null);
            SessionState session = (sessionId == null) ? null : CelesteHTTPd.this.sessions.get(sessionId);
            PathName path = CelesteHTTPd.this.extractPath(uri);
            CelesteHTTPd.printf("putFile: %s %s%n",  path, request.getMessage().getHeader(HTTP.Message.Header.CONTENTLENGTH));

            try {
                long start = System.currentTimeMillis();
                //
                // There is now a second operation to create a file, where you
                // pass in the ContentType (as string).
                //
                HierarchicalFileSystem.File file = session.fileSystem.createFile(path);
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(file.getOutputStream(false, file.getBlockSize()), file.getBlockSize()));
                long length = request.getMessage().getBody().writeTo(o);
                o.close();
                long stop = System.currentTimeMillis();

                double elapsedTime = ((double) stop - (double) start) / 1000.0;
                double bytesPerSecond =  (length / elapsedTime);
                double megaBitsPerSecond = (bytesPerSecond * 8.0) / (1048576.0);
                String report = String.format(
                        "Created %d byte file in %f s.  %f bytes/s %f megabits/s%n",
                        length, elapsedTime, bytesPerSecond,
                        megaBitsPerSecond);
                CelesteHTTPd.println(report);
            } catch (IOException e) {
                e.printStackTrace();
                return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                        new HttpContent.Text.Plain("/write failed: %s %s\n", path, e.getLocalizedMessage()));
            } catch (FileException e) {
                e.printStackTrace();
                return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                        new HttpContent.Text.Plain("/write failed: %s %s\n", path, e.getLocalizedMessage()));
            }
            CelesteHTTPd.println("putFile: OK");
            return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("OK\n"));
        }
    }

    //
    // Given an URI encoding a file path, produce an absolute PathName
    // representing that path.  Throw IllegalArgumentException if the URI is
    // improperly formatted.
    //
    // The path part of the URI is expected to hold the encoding, and is
    // formatted as:
    //      /<op>/<profile-name>/<path-relative-to-name-space>
    // The part of interest is the final one.  Neither <op> nor <profile-name>
    // can contain '/' characters.
    //
    // XXX: Add an argument stating what op is expected, along with
    //      verification that it's present?
    //
    private PathName extractPath(URI uri) {
        //
        // XXX: Consider making this pattern a private static member, so that
        //      it doesn't have to be reconstructed at every invocation.
        //
        Pattern re = Pattern.compile("/[^/]*/[^/]*(/.*)$");
        Matcher matcher = re.matcher(uri.getPath());
        if (!matcher.lookingAt())
            throw new IllegalArgumentException(
            "URI does not embed well formed path: " + uri.getPath());
        return new PathName(matcher.group(1));
    }

    private XHTML.Document makeDocument(XHTML.HeadSubElement[] headParts, XHTML.BodySubElement[] bodyParts) {
        XHTML.Head head = new XHTML.Head();
        head.add(this.XHTMLStyleLink);
        head.add(this.XHTMLScriptLink);
        head.add(headParts);

        XHTML.Body body = new XHTML.Body(
                new XML.Attr("onload", "tableStripe('browse', '#ecf3fe', '#ffffff');")).
                add(bodyParts).
                add(this.XHTMLPageFooter);

        XHTML.Html html = new XHTML.Html(head, body);
        return new XHTML.Document(html);
    }

    //
    // XXX: Factor out common code from this method and mountFileSystem()
    //      below.
    //
    private HTTP.Response newFileSystem(URI uri, HTTP.Message.Body.MultiPart.FormData props) throws IOException {
        SessionState session = new SessionState();

        //
        // Grab and validate session information.  The mount point defaults to
        // the name space id.
        //
        session.nameSpaceId = HttpMessage.asString(
            props.get(SessionState.NAMESPACEID),
            null);
        session.nameSpacePassword = HttpMessage.asString(
            props.get(SessionState.NAMESPACEPASSWORD),
            null);
        session.accessorId = HttpMessage.asString(
            props.get(SessionState.ACCESSORID),
            null);
        session.accessorPassword = HttpMessage.asString(
            props.get(SessionState.ACCESSORPASSWORD),
            null);
        session.mountPoint = HttpMessage.asString(
            props.get(SessionState.MOUNTPOINT),
            session.getNameSpace());
        session.replicationParams = HttpMessage.asString(
            props.get(SessionState.REPLICATIONPARAMS),
            SessionState.replicationParamList[0]);
        if (session.nameSpaceId == null) {
            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST,
                new HttpContent.Text.Plain("Missing %s\n", SessionState.NAMESPACEID));
        }
        if (session.nameSpacePassword == null) {
            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST,
                new HttpContent.Text.Plain("Missing %s\n", SessionState.NAMESPACEPASSWORD));
        }
        if (session.mountPoint == null) {
            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST,
                new HttpContent.Text.Plain("Missing %s\n", SessionState.MOUNTPOINT));
        }

        try {
            session.nameSpaceProfile = new Profile_(
                session.nameSpaceId, session.nameSpacePassword.toCharArray());
            NewCredentialOperation operation = new NewCredentialOperation(
                session.nameSpaceProfile.getObjectId(),
                BeehiveObjectId.ZERO, session.replicationParams);
            Credential.Signature signature = session.nameSpaceProfile.sign(
                session.nameSpacePassword.toCharArray(), operation.getId());

            this.celesteProxy.newCredential(operation, signature, session.nameSpaceProfile);

            //
            // Treat a missing or empty accessorId as an indication from the
            // form submitter that the accessor profile is to be the same as
            // the name space profile.
            //
            if (session.accessorId == null || session.accessorId.equals("")) {
                session.accessorId = session.nameSpaceId;
                session.accessorPassword = session.nameSpacePassword;
                session.accessorProfile = session.nameSpaceProfile;
            } else {
                session.accessorProfile = new Profile_(
                    session.accessorId, session.accessorPassword.toCharArray());
                operation = new NewCredentialOperation(
                    session.accessorProfile.getObjectId(),
                    BeehiveObjectId.ZERO, session.replicationParams);
                signature = session.accessorProfile.sign(
                    session.accessorPassword.toCharArray(), operation.getId());

                celesteProxy.newCredential(operation, signature, session.accessorProfile);
            }

            session.fileSystem = new CelesteFileSystem(this.celesteAddress, this.proxyCache, session.mountPoint, session.accessorId, session.accessorPassword);
            session.fileSystem.newFileSystem();
        } catch (Profile_.Exception e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                new HttpContent.Text.Plain("%s\n", e.getLocalizedMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                new HttpContent.Text.Plain("%s\n", e.getLocalizedMessage()));
        }

        return new HttpResponse(HTTP.Response.Status.OK,
            new HttpContent.Text.Plain("%s\n", session.mountPoint));
    }


    private HTTP.Response mountFileSystem(URI uri, HTTP.Message.Body.MultiPart.FormData props) throws IOException {
        SessionState session = new SessionState();
        this.sessions.put(session.getIdentity(), session);

        //
        // Grab and validate session information.  The mount point defaults to
        // the name space id.
        //
        session.nameSpaceId = HttpMessage.asString(props.get(SessionState.NAMESPACEID), null);
        session.nameSpacePassword = HttpMessage.asString(props.get(SessionState.NAMESPACEPASSWORD), null);
        session.accessorId = HttpMessage.asString(props.get(SessionState.ACCESSORID), null);
        session.accessorPassword = HttpMessage.asString(props.get(SessionState.ACCESSORPASSWORD), null);
        session.mountPoint = HttpMessage.asString(props.get(SessionState.MOUNTPOINT), session.getNameSpace());
        session.replicationParams = HttpMessage.asString(props.get(SessionState.REPLICATIONPARAMS), SessionState.replicationParamList[0]);
        if (session.nameSpaceId == null) {
            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Missing %s\n", SessionState.NAMESPACEID));
        }
        if (session.nameSpacePassword == null) {
            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Missing %s\n", SessionState.NAMESPACEPASSWORD));
        }
        if (session.mountPoint == null) {
            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Missing %s\n", SessionState.MOUNTPOINT));
        }

        //
        // Treat a missing or empty accessorId as an indication from the form
        // submitter that the accessor profile is to be the same as the name
        // space profile.
        //
        if (session.accessorId == null || session.accessorId.equals("")) {
            session.accessorId = session.nameSpaceId;
            session.accessorPassword = session.nameSpacePassword;
            session.accessorProfile = session.nameSpaceProfile;
        }

        try {
            session.fileSystem = new CelesteFileSystem(this.celesteAddress, this.proxyCache, session.mountPoint, session.accessorId, session.accessorPassword);
        } catch (FileException e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                new HttpContent.Text.Plain("Session creation failed: %s\n", e.getLocalizedMessage()));
        }

        return new HttpResponse(HTTP.Response.Status.OK,
            new HttpContent.Text.Plain("%s\n", session));
    }


    private HTTP.Response readFile(HTTP.Request request, SessionState session, URI uri, HTTP.Message.Body.MultiPart.FormData props) {
        CelesteHTTPd.println("readFile: " + session + " " + uri);
        PathName path = this.extractPath(uri);

        try {
            CelesteFileSystem.File fileOrDirectory =
                session.fileSystem.getNode(path);

            long start = 0;
            long stop = fileOrDirectory.length();
            HttpHeader.Range rangeHeader = (HttpHeader.Range) request.getMessage().getHeader(HTTP.Message.Header.RANGE);

            if (rangeHeader != null) {
                long ranges[][] = rangeHeader.getRange();
                if (ranges[0][0] != -1 && ranges[0][1] != -1) { // M-N
                    start = ranges[0][0];
                    stop = ranges[0][1];
                } else if (ranges[0][0] != -1 && ranges[0][1] == -1) { // M-
                    start = ranges[0][0];
                    stop = fileOrDirectory.length();
                } else if (ranges[0][0] == -1 && ranges[0][1] != -1) { // -N
                    start = fileOrDirectory.length() - ranges[0][1];
                    stop = fileOrDirectory.length();
                }
            }

            CelesteHTTPd.println("Range: " + start + "-" + stop + " Content-type: " + fileOrDirectory.getContentType());

            if (fileOrDirectory instanceof CelesteFileSystem.Directory) {
                CelesteFileSystem.Directory directory = (CelesteFileSystem.Directory) fileOrDirectory;
                HierarchicalFileSystem.FileName dirPath = directory.getPathName();
                StringBuilder result = new StringBuilder();
                for (String entryName : directory.list()) {
                    HierarchicalFileSystem.FileName entryPath = dirPath.append(entryName);
                    CelesteFileSystem.File file = directory.getFile(entryName);
                    String info = String.format("%s %d %d %s",
                            file.getContentType(), file.length(),
                            file.lastModified(), entryPath.toString());
                    result.append(info).append("\n");
                }
                return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("%s", result));
            }

            fileOrDirectory.position(start);
            CelesteFileSystem.CelesteInputStream input = fileOrDirectory.getInputStream();
            //input.seek(start);

            if (start == 0 && stop == fileOrDirectory.length()) {
                HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK,
                    new HttpContent.RawInputStream(new HttpHeader.ContentType(fileOrDirectory.getContentType()), input));

                response.getMessage().addHeader(new HttpHeader.ContentDisposition("inline", new HttpHeader.Parameter("filename", path.getLeafComponent())));
                response.getMessage().addHeader(new HttpHeader.ContentLength(fileOrDirectory.length()));
                CelesteHTTPd.println("Read " + stop + " byte file streamed.");
                return response;
            }

            byte[] buffer = new byte[(int) (stop - start)];

            long startTime = System.currentTimeMillis();

            int bytesRead = 0;
            do {
                bytesRead += input.read(buffer, bytesRead, buffer.length - bytesRead);
            } while (bytesRead < (buffer.length - 1));

            long stopTime = System.currentTimeMillis();
            long elapsedTime = stopTime - startTime;

            long bytesPerSecond = buffer.length * 1000L /
            (elapsedTime == 0 ? 1 : elapsedTime);
            double bitsPerSecond = (bytesPerSecond * 8.0) / (1024.0 * 1024.0);
            String report = String.format(
                "Read %d bytes in %dms. %d Bps %f Mbps%n",
                buffer.length, elapsedTime, bytesPerSecond, bitsPerSecond);
            CelesteHTTPd.println(report);
            input.close();
            HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, new HttpContent.RawByteArray(new HttpHeader.ContentType(fileOrDirectory.getContentType()), buffer));
            response.getMessage().addHeader(new HttpHeader.ContentDisposition("inline", new HttpHeader.Parameter("filename", path.getLeafComponent())));
            response.getMessage().addHeader(new HttpHeader.ContentLength(buffer.length));
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain("%s: %s\n", uri, e.getLocalizedMessage()));
        } catch (FileException e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain("%s: %s\n", uri, e.getLocalizedMessage()));
        }
    }

    private HTTP.Response deleteFile(HTTP.Request request, SessionState session, URI uri, HTTP.Message.Body.MultiPart.FormData props) {
        CelesteHTTPd.println("deleteFile: " + session + " " + uri);

        //
        // Obtain the file or directory to delete by stripping off the action
        // prefix.
        //
        PathName path = this.extractPath(uri);

        //
        // At this point, it is perfectly legal to delete a directory with
        // content.  What happens is that all files in that directory are
        // unreferenced, and may cease to be refreshed, unless they are
        // referenced through other directories.  This applies to
        // sub-directories as well.
        //
        try {
            session.fileSystem.deleteFile(path, true);
        } catch (FileException e) {
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                new HttpContent.Text.Plain("Cannot delete file: %s\n", path));
        }

        String pathName =
            (props == null) ? "/" : HttpMessage.asString(props.get("path"), "/");
        String url = String.format("/browse?path=%s&sessionId=%s",
            pathName, session.getIdentity());
        return redirectTo(url, "");
    }

    private HTTP.Response mkdir(SessionState session, URI uri, HTTP.Message.Body.MultiPart.FormData props) {
        PathName path = this.extractPath(uri);

        try {
            session.fileSystem.createDirectory(path);
        } catch (FileException e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain("%s: %s\n", path, e.getLocalizedMessage()));
        }

        return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("%s\n", path));
    }

    //
    // XXX: Dummy implementation.  Considerable work is needed to fix it.
    //
    private HTTP.Response accessFile(SessionState session, URI uri, HTTP.Message.Body.MultiPart.FormData props) {
        //CelesteFileName fileName = new CelesteFileName(uri.getPath().substring("/isWriteable/".length()));

        return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("%s\n", HTTP.Response.Status.OK));
    }

    private HTTP.Response statusFile(SessionState session, URI uri, HTTP.Message.Body.MultiPart.FormData props) {
        PathName path = this.extractPath(uri);

        try {
            CelesteFileSystem.File file = session.fileSystem.getNode(path);
            String info = file.getContentType() + " " + file.length() + " " +
            file.lastModified() + " " + path.toString();
            return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("%s\n", info));
        } catch (FileException e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain("%s: %s\n", path, e.getLocalizedMessage()));
        }
    }

    private HTTP.Response browse(SessionState session, HTTP.Message.Body.MultiPart.FormData props) throws IOException {
        String pathName =
            (props == null) ? "/" : HttpMessage.asString(props.get("path"), "/");

        String action = (props == null) ? null : HttpMessage.asString(props.get("action"), null);

        if (action == null) {
            PathName directoryPath = new PathName(pathName);

            //
            // XXX: The alignment style shouldn't be embedded in the document;
            //      instead the relevant cells should be given a suitable
            //      class attribute.  And along the same lines, the interior
            //      cells ought to be Table.Heading instances rather than
            //      Table.Data instances, except that the latter align left by
            //      default and the former have centered alignment.  If the
            //      cells were all to be decorated with class attributes, the
            //      style sheet could specify the desired left alignment
            //      explicitly.
            //
            final XML.Attribute alignRight =
                new XML.Attr("style", "text-align: right;");
            final Table.Head tableHead = new XHTML.Table.Head(
                new Table.Row(
                    new Table.Data("File Name"),
                    new Table.Data(alignRight).add("Size"),
                    new Table.Data("Last Modified"),
                    new Table.Data("")));

            Table.Body tableBody = new Table.Body();

            try {
                CelesteFileSystem.File file =
                    session.fileSystem.getNode(directoryPath);

                if (file instanceof CelesteFileSystem.Directory) {
                    //
                    // Generate XHTML to render a listing of the directory.
                    //
                    CelesteFileSystem.Directory directory =
                        (CelesteFileSystem.Directory) file;
                    String[] fileList = directory.list();

                    for (String fileName : fileList) {
                        CelesteFileSystem.File f = null;
                        try {
                            f = directory.getFile(fileName);
                        } catch (FileException.NotFound e) {
                            // the file was deleted underneath, but the directory was not updated
                        } catch (FileException e) {
                            throw e;
                        }

                        //
                        // XXX: Factor out deleteButton construction to here.
                        //      (Don't worry about overriding it for "/".)
                        //
                        XHTML.Anchor deleteButton = new XHTML.Anchor();
                        Table.Row row = null;
                        if (f == null) {
                            //
                            // The file's not there enough to list attributes,
                            // but it potentially can have its deletion
                            // completed, so supply the delete button.
                            //
                            // XXX: To delete the item, we need its full
                            //      pathname.  But since f is null, we can't
                            //      obtain it directly from f, but instead
                            //      have to reconstruct it.  Do the local
                            //      pathName and fileName variables taken
                            //      together provide enough information to do
                            //      so?  Not sure at the moment, so omit the
                            //      delete button for now and come back to
                            //      this.
                            //
                            //deleteButton = makeDeleteButton(session, f);
                            row = new Table.Row(
                                    new Table.Data("%s (not available)", fileName),
                                    new Table.Data(""),
                                    new Table.Data(""),
                                    new Table.Data(new XHTML.Span().
                                            add(new XHTML.Anchor()).
                                            add(" ").
                                            add(deleteButton))
                            );
                        } else {
                            XHTML.Span fileLink = null;
                            //
                            // Second thoughts about allowing inspection of
                            // the underlying AObject...
                            //
                            //XHTML.Anchor inspector = new XHTML.Anchor(
                            //    new XHTML.Attr("class", "toggle-button on"),
                            //    new XHTML.Attr("href", String.format(
                            //            "/inspect?action=AObject&file=%s&sessionId=%s",
                            //            f.pathName.getCanonicalPath(),
                            //            session)).add(
                            //    "?");
                            XHTML.Anchor inspector = new XHTML.Anchor();
                            if (f instanceof CelesteFileSystem.Directory) {
                                String path = f.getPathName().toString();
                                fileLink = new XHTML.Span(
                                        new XHTML.Anchor(
                                    new XML.Attr("class", "filename"),
                                    new XML.Attr("href",
                                            String.format("/browse?path=%s&sessionId=%s", path, session.getIdentity()))).add(fileName)).add("/");
                                if (path.equals("/")) {
                                    // deleteButton = new XHTML.Anchor();
                                } else {
                                    deleteButton = makeDeleteButton(session, f);
                                }
                            } else {
                                fileLink = new XHTML.Span(new XHTML.Anchor(
                                    new XML.Attr("class", "filename"),
                                    new XML.Attr("href", String.format(
                                        "/read/%s%s?%s=%s",
                                        session.getNameSpace(),
                                        f.getPathName().toString(),
                                        SessionState.SESSIONID,
                                        session))
                                ).add(fileName));
                                deleteButton = makeDeleteButton(session, f);
                            }

                            row = new Table.Row(
                                new Table.Data(fileLink),
                                new Table.Data(new XML.Attr("style", "text-align: right;"))
                                    .add(String.valueOf(f.length())),
                                new Table.Data(String.format("%1$tF %1$tT",
                                    new java.util.Date(f.lastModified()))),
                                    new Table.Data(new XHTML.Span().
                                        add(inspector).
                                        add(" ").
                                        add(deleteButton))
                            );
                        }

                        tableBody.add(row);
                    }
                }
            } catch (FileException.FileSystemNotFound e) {
                //e.printStackTrace();
                String msg = String.format(
                    "Error: no file system associated with '%s'",
                    session.mountPoint);
                return new HttpResponse(HTTP.Response.Status.NOT_FOUND,
                    new HttpContent.Text.Plain(msg));
            } catch (FileException e) {
                e.printStackTrace();
                String msg = String.format("Error: '%s': %s", directoryPath.toString(), e.toString());
                return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                    new HttpContent.Text.Plain(msg));
            }

            //
            // Build the row at the bottom of the directory listing for adding
            // new files.
            //
            // XXX: Add a button to it to allow content type to be explicitly
            //      set.
            //
            tableBody.add(new Table.Row(
                new Table.Data(
                    Xxhtml.InputText(
                        new XML.Attr("name", "name"),
                        new XML.Attr("size", "30"),
                        new XML.Attr("title", "New file or directory's name")),
                    Xxhtml.InputHidden(new XML.Attr("name", SessionState.SESSIONID),
                        new XML.Attr("value", session.toString()))
                ),
                new Table.Data(new XML.Attr("colspan", "4")).add(
                    Xxhtml.InputSubmit(new XML.Attr("name", "action"),
                        new XML.Attr("title", "Make Directory"),
                        new XML.Attr("value", "mkdir")),
                    Xxhtml.InputHidden( new XML.Attr("name", "path"),
                        new XML.Attr("value", pathName)),
                    new XHTML.Input(XHTML.Input.Type.FILE).setName("content").setTitle("Provide name in local file system"),
                    contentTypeSelector(),
                    Xxhtml.InputSubmit(new XML.Attr("name", "action"),
                        new XML.Attr("title", "New File"),
                        new XML.Attr("value", "create")),
                    Xxhtml.InputHidden(new XML.Attr("name", SessionState.SESSIONID),
                        new XML.Attr("value", session.toString())),
                    Xxhtml.InputSubmit(new XML.Attr("name", "action"),
                        new XML.Attr("title", "Logout"),
                        new XML.Attr("value", "logout"))
                )
            ));

            //
            // Wrap the main table in a form, so that the table's input fields
            // and buttons can be retrieved.
            //
            XHTML.Form table = new XHTML.Form("/browse").setMethod("post").setEncodingType("multipart/form-data")
            .add(new XHTML.Div(new XML.Attr("class", "section")).
                add(new Table(
                        new XML.Attr("class", "browse"),
                        new XML.Attr("id", "browse")).
                    add(new Table.Caption("")).
                    add(tableHead).
                    add(tableBody)));

            //
            // Set up the session properties section that will precede the
            // main directory listing table.
            //
            XHTML.Div sessionProperties = new XHTML.Div(
                new XHTML.Form("/browse").setMethod("post").setEncodingType("multipart/form-data")
                .add(new XHTML.Div(new XML.Attr("class", "section"))
                .add(new Table(new XML.Attr("class", "SessionStateProperties"))
                    .add(
                    new Table.Caption("Celeste Login")).
                    add(new Table.Body(
                        new Table.Row(
                            new Table.Heading(""),
                            new Table.Heading("Name&nbsp;Space"),
                            new Table.Heading("Replication Parameters")),
                        new Table.Row(
                            new Table.Data(
                                Xxhtml.InputHidden(new XML.Attr("name", SessionState.SESSIONID),
                                    new XML.Attr("value", session.toString())),
                                Xxhtml.InputSubmit(new XML.Attr("name", "action"),
                                    new XML.Attr("title", "Logout"),
                                    new XML.Attr("value", "logout"))),
                            new Table.Data(session.getNameSpace()),
                            new Table.Data(session.replicationParams)
                        )
                    ))
                ))
            );

            sessionProperties = new XHTML.Div(
                new XHTML.Form("/browse").setMethod("post").setEncodingType("multipart/form-data")
                .add(new XHTML.Div(new XHTML.Span(new XHTML.Bold(session.getNameSpace() + "/" + pathName))).setClass("section")));

            sessionProperties = null;

            XHTML.Div pathAsXHTML = new XHTML.Div(new XML.Attr("class", "section")).add(new XHTML.Bold(session.getNameSpace() + "/" + pathName));

            XHTML.Document msg = null;
            XHTML.HeadSubElement httpEquivRefresh = session.autoRefresh == 0 ? null : Xxhtml.HttpEquivRefresh(session.autoRefresh, "");
            XHTML.HeadSubElement title = new XHTML.Title("Celeste File Browser");
            if (session.showStatistics) {
                msg = makeDocument(new XHTML.HeadSubElement[] { httpEquivRefresh, title },
                    new XHTML.BodySubElement[] { sessionProperties, pathAsXHTML, table, FileImpl.getAggregateStatisticsAsXHTML()});
            } else {
                msg = makeDocument(new XHTML.HeadSubElement[] { httpEquivRefresh, title },
                    new XHTML.BodySubElement[] { sessionProperties, pathAsXHTML, table});
            }

            HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(msg));
            response.getMessage().addHeader(new HttpHeader.ContentLength(msg.streamLength()));
            return response;
        } else if (action.equals("mkdir")) {
            try {
                PathName newDirPath = new PathName(pathName).appendComponent(
                    HttpMessage.asString(props.get("name"), null));
                session.fileSystem.createDirectory(newDirPath);
            } catch (FileException e) {
                e.printStackTrace();
                return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("/browse mkdir failed " + e.getLocalizedMessage()));
            }

            String url = String.format("/browse?path=%s&sessionId=%s",
                pathName, session.getIdentity());
            return redirectTo(url, "");
        }  else if (action.equals("create")) {
            //
            // Creates a new file with some initial content.  This could go
            // for a Celeste create operation which includes payload in the
            // inital version...
            //
            String name = HttpMessage.asString(props.get("name"), null);
            if (name == null || name.equals("")) {
                XHTML.Document document = makeDocument(new XHTML.HeadSubElement[] { new XHTML.Title("Error") },
                    new XHTML.BodySubElement[] { new XHTML.Div("Missing file name.  Did you forget to fill in all of the form input fields?") });
                return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.HTML(document));
            }
            PathName filePath = new PathName(pathName).appendComponent(name);
            String contentType =
                HttpMessage.asString(props.get("contentType"), "?");

            HTTP.Message fdat = props.get("content");

            try {
//                byte[] content_cr = HttpMessage.asByteArray(fdat, null);
                long start = System.currentTimeMillis();
                //
                // If content type is "?", then the file system must attempt
                // to infer the proper value.
                //
                HierarchicalFileSystem.File file = null;
                if (contentType.equals("?")) {
                    file = session.fileSystem.createFile(filePath);
                } else {
                    file = session.fileSystem.createFile(filePath, contentType);
                }
                OutputStream o = new BufferedOutputStream(file.getOutputStream(false, 8*1024*1024), 8*1024*1024);
                
                InputStream in = fdat.getBody().toInputStream();
                byte buffer[] = new byte[8192];
                long length = 0;
                while (true) {
                    int nread = in.read(buffer);
                    if (nread < 1)
                        break;
                    length += nread;
                    o.write(buffer, 0, nread);
                }
                //o.write(content_cr);
                o.close();
                long stop = System.currentTimeMillis();

                double elapsedTime = ((double) stop - (double) start) / 1000.0;
                double bytesPerSecond =  (length / elapsedTime);
                double megaBitsPerSecond = (bytesPerSecond * 8.0) / (1048576.0);
                String report = String.format(
                        "Created %d byte file in %f s.  %f bytes/s %f megabits/s%n",
                        length, elapsedTime, bytesPerSecond,
                        megaBitsPerSecond);
                CelesteHTTPd.println(report);
                String url = String.format("/browse?path=%s&sessionId=%s",
                        pathName, session.getIdentity());
                return redirectTo(url, "");
            } catch (IOException e) {
                e.printStackTrace();
                return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                    new HttpContent.Text.Plain("/browse create failed : " + filePath.toString() + " " + e.getLocalizedMessage()));
            } catch (FileException e) {
                e.printStackTrace();
                return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                    new HttpContent.Text.Plain("/browse create failed : " + filePath.toString() + " " + e.getLocalizedMessage()));
            }
        } else if (action.equals("delete")) {
            //
            // XXX: How is this code reached?  It's beginning to look like the
            //      only way to reach it is via a hand-crafted url (as opposed
            //      to one that this application generates).
            //
            String url = String.format("/browse?path=%s&sessionId=%s",
                    pathName, session.getIdentity());
            return redirectTo(url, "");
        } else if (action.equals("logout")) {
            if (session != null) {
                session.fini();
                this.sessions.remove(session.getIdentity());
            }
            return loginSession(null);
        }

        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST,
                new HttpContent.Text.Plain("/browse action=" + action + " is not understood."));
    }

    //
    // Instantiate a button (in the form of a decorated anchor) to delete the
    // given file.
    //
    private static XHTML.Anchor makeDeleteButton(SessionState session,
            CelesteFileSystem.File f) {
        return new XHTML.Anchor(
            new XML.Attr("class", "toggle-button on"),
            Xxhtml.Attr.HRef("/delete/%s%s?%s=%s",
                session.getNameSpace(),
                f.getPathName().toString(),
                SessionState.SESSIONID, session),
                new XML.Attr("title", "Irrevocably delete this file.")).
            add("X");
    }

    //
    // Issue a redirection response to the given URL, transitorily displaying
    // a link to that url prepended with text.
    //
    private static HTTP.Response redirectTo(String url, String text) {
        XHTML.Head head = new XHTML.Head(Xxhtml.HttpEquivRefresh(0, url));
        XHTML.Body body = new XHTML.Body(new XHTML.Div(text).add(new XHTML.Anchor("refreshing").setHref(XHTML.SafeURL(url))));
        return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(new XHTML.Document(new XHTML.Html(head, body))));
    }

    private HTTP.Response loginSession(Map<String,HTTP.Message> props) throws IOException {
        String action = (props != null) ?
            HttpMessage.asString(props.get("action"), null) : null;
        String sessionId = (props == null) ?
            null : HttpMessage.asString(props.get(SessionState.SESSIONID), null);
        String autoRefreshInput =  (props != null) ?
            HttpMessage.asString(props.get("autoRefresh"), "off") : "off";
        String showStatisticsInput =  (props != null) ?
            HttpMessage.asString(props.get("showStatistics"), "off") : "off";

        //
        // Note that sessionId will be null except when arriving here to
        // confirm creation of a new profile.  (See the
        // action.equals("register") case below.)  In the latter case, we
        // don't want to disturb settings established from the profile
        // creation form.
        //
        SessionState session = null;
        if (sessionId == null) {
            session = new SessionState();
            session.autoRefresh = autoRefreshInput.equals("on") ? 15 : 0;
            session.showStatistics = showStatisticsInput.equals("on");
        } else {
            session = this.sessions.get(sessionId);
        }

        if (action == null) {
            //
            // The session is either newly created or was inactive.  Put up
            // the login page.
            //
            return this.postLoginForm(session);
        }

        if (action.equals("register")) {
            //
            // The login attempt mentioned at least one unknown profile and we
            // requested confirmation before creating them.  Process the
            // response to our confirmation request.
            //
            String nameSpacePassword2 = HttpMessage.asString(
                props.get(SessionState.NAMESPACEPASSWORD2),
                null);
            String accessorPassword2 = HttpMessage.asString(
                props.get(SessionState.ACCESSORPASSWORD2),
                null);
            //
            // XXX: Factor the duplicated code below into a method?
            //      Problematic, because each copy sets a distinct
            //      SessionState field.
            //
            if (nameSpacePassword2 != null) {
                if (!nameSpacePassword2.equals(session.nameSpacePassword)) {
                    String msg = String.format(
                        "Password for \"%s\" does not match",
                        session.nameSpaceId);
                    XHTML.Document document = makeDocument(
                        new XHTML.HeadSubElement[] { new XHTML.Title("Registration Failed") },
                        new XHTML.BodySubElement[] { new XHTML.Div(msg) });
                    return new HttpResponse(HTTP.Response.Status.NOT_ACCEPTABLE, new HttpContent.Text.HTML(document));
                }
                try {
                    session.nameSpaceProfile = new Profile_(session.nameSpaceId,
                        session.nameSpacePassword.toCharArray());
                    NewCredentialOperation operation = new NewCredentialOperation(
                        session.nameSpaceProfile.getObjectId(),
                        BeehiveObjectId.ZERO, session.replicationParams);
                    Credential.Signature signature = session.nameSpaceProfile.sign(
                        session.nameSpacePassword.toCharArray(), operation.getId());
                    this.celesteProxy.newCredential(operation, signature, session.nameSpaceProfile);
                } catch (Profile_.Exception e) {
                    e.printStackTrace();
                    XHTML.Document document = makeDocument(
                        new XHTML.HeadSubElement[] { new XHTML.Title("Registration Failed") },
                        new XHTML.BodySubElement[] { new XHTML.Div(e.getLocalizedMessage()) });
                    return new HttpResponse(HTTP.Response.Status.UNAUTHORIZED,
                        new HttpContent.Text.HTML(document));
                } catch (Exception e) {
                    e.printStackTrace();
                    XHTML.Document document = makeDocument(
                        new XHTML.HeadSubElement[] { new XHTML.Title("Registration Failed") },
                        new XHTML.BodySubElement[] { new XHTML.Div(e.getLocalizedMessage()) });
                    return new HttpResponse(HTTP.Response.Status.UNAUTHORIZED,
                        new HttpContent.Text.HTML(document));
                }
            }
            //
            // accessorPassword2 isn't relevant if the previous form omitted
            // the accessor Id (so that accessor defaults to name space).
            //
            if (session.nameSpaceId.equals(session.accessorId)) {
                session.accessorProfile = session.nameSpaceProfile;
            } else if (accessorPassword2 != null) {
                if (!accessorPassword2.equals(session.accessorPassword)) {
                    String msg = String.format(
                        "Password for \"%s\" does not match",
                        session.accessorId);
                    XHTML.Document document = makeDocument(
                        new XHTML.HeadSubElement[] { new XHTML.Title("Registration Failed") },
                        new XHTML.BodySubElement[] { new XHTML.Div(msg) });
                    return new HttpResponse(HTTP.Response.Status.NOT_ACCEPTABLE,
                        new HttpContent.Text.HTML(document));
                }
                try {
                    session.accessorProfile = new Profile_(session.accessorId,
                        session.accessorPassword.toCharArray());
                    NewCredentialOperation operation = new NewCredentialOperation(
                        session.accessorProfile.getObjectId(),
                        BeehiveObjectId.ZERO, session.replicationParams);
                    Credential.Signature signature = session.accessorProfile.sign(
                        session.accessorPassword.toCharArray(), operation.getId());
                    this.celesteProxy.newCredential(operation, signature, session.accessorProfile);
                } catch (Profile_.Exception e) {
                    e.printStackTrace();
                    XHTML.Document document = makeDocument(
                        new XHTML.HeadSubElement[] { new XHTML.Title("Registration Failed") },
                        new XHTML.BodySubElement[] { new XHTML.Div(e.getLocalizedMessage()) });
                    return new HttpResponse(HTTP.Response.Status.UNAUTHORIZED,
                        new HttpContent.Text.HTML(document));
                } catch (Exception e) {
                    e.printStackTrace();
                    XHTML.Document document = makeDocument(
                        new XHTML.HeadSubElement[] { new XHTML.Title("Registration Failed") },
                        new XHTML.BodySubElement[] { new XHTML.Div(e.getLocalizedMessage()) });
                    return new HttpResponse(HTTP.Response.Status.UNAUTHORIZED,
                        new HttpContent.Text.HTML(document));
                }
            }

            //
            // The profiles are in place: create the file system.
            //
            try {
                session.fileSystem = new CelesteFileSystem(this.celesteAddress, this.proxyCache, session.mountPoint, session.accessorId, session.accessorPassword);
                session.fileSystem.newFileSystem();
                String url = String.format("/browse?sessionId=%s", session);
                return redirectTo(url, "");
            } catch (Exception e) {
                e.printStackTrace(System.err);
                XHTML.Document document = makeDocument(
                    new XHTML.HeadSubElement[] { new XHTML.Title("File System Creation Failed") },
                    new XHTML.BodySubElement[] { new XHTML.Div(e.getLocalizedMessage()) }
                );
                return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                    new HttpContent.Text.HTML(document));
            }
        }

        //
        // Use the data submitted in the "login" form to dig up the file system
        // associated with the profile.  This will fail if either or both of
        // the name space and accessor profiles haven't yet been created in
        // Celeste.
        //

        this.sessions.put(session.getIdentity(), session);
        session.nameSpaceId = HttpMessage.asString(
            props.get(SessionState.NAMESPACEID),
            null);
        session.nameSpacePassword = HttpMessage.asString(
            props.get(SessionState.NAMESPACEPASSWORD),
            null);
        session.accessorId = HttpMessage.asString(
            props.get(SessionState.ACCESSORID),
            session.nameSpaceId);
        session.accessorPassword = HttpMessage.asString(
            props.get(SessionState.ACCESSORPASSWORD),
            session.nameSpacePassword);
        //
        // At least in some cases, an omitted field is reported as an empty
        // string.  Check for that situation with the accessor Id field.
        //
        if (session.accessorId == null || session.accessorId.equals("")) {
            session.accessorId = session.nameSpaceId;
            session.accessorPassword = session.nameSpacePassword;
        }
        session.mountPoint = session.getNameSpace();
        session.replicationParams = HttpMessage.asString(props.get(SessionState.REPLICATIONPARAMS), null);

        try {
            //
            // Optimistically assume that all profiles required to construct
            // the session's file system handle have been created within
            // Celeste.
            //
            session.fileSystem = new CelesteFileSystem(this.celesteAddress, this.proxyCache, session.mountPoint, session.accessorId, session.accessorPassword);
        } catch (FileException.CredentialProblem e) {
            //
            // Control reaches this point when the login request mentioned an
            // unknown profile.  Put up the confirmation page.
            //
            Table.Body body = new Table.Body(new Table.Row(
                new Table.Heading(""),
                new Table.Heading("Name"),
                new Table.Heading("Password"),
                new Table.Heading(new XML.Attr("style", "width: 100%;"))
            ));
            //
            // Add rows for the missing profiles, making sure the "register"
            // button appears only once.
            //
            boolean haveRegisterButton = false;
            if (!this.profileIsRegistered(session, session.nameSpaceId)) {
                body.add(
                    new Table.Row(
                        new Table.Data(
                            Xxhtml.InputSubmit(new XML.Attr("name", "action"),
                                new XML.Attr("title", "Submit registration info"),
                                new XML.Attr("value", "register"))),
                        new Table.Data(session.nameSpaceId),
                        new Table.Data(
                            new XHTML.Input(XHTML.Input.Type.PASSWORD).
                                setName(SessionState.NAMESPACEPASSWORD2).setSize(16)
                        )
                    )
                );
                haveRegisterButton = true;
            }
            //
            // Avoid adding this row if the accessorId has been omitted (so
            // that it coincides with the name space Id).
            //
            if (!session.nameSpaceId.equals(session.accessorId) &&
                    !this.profileIsRegistered(session, session.accessorId)) {
                Table.Data registerItem = null;
                if (haveRegisterButton) {
                    registerItem = new Table.Data("");
                } else {
                    registerItem = new Table.Data(
                        Xxhtml.InputSubmit(new XML.Attr("name", "action"),
                            new XML.Attr("title", "Submit registration info"),
                            new XML.Attr("value", "register")));
                }
                body.add(
                    new Table.Row(
                        registerItem,
                        new Table.Data(session.accessorId),
                        new Table.Data(
                            new XHTML.Input(XHTML.Input.Type.PASSWORD).
                                setName(SessionState.ACCESSORPASSWORD2).setSize(16)
                        )
                    )
                );
            }
            body.add(new Table.Row(
                new Table.Data("Enter password(s) again to access this name space.").
                setColumnSpan(4)
            ));

            XHTML.Form form =
                new XHTML.Form("login").setMethod("post").setEncodingType("multipart/form-data")
            .add(new XHTML.Div(new XML.Attr("class", "section")).
                add(Xxhtml.InputHidden(new XML.Attr("name", SessionState.SESSIONID),
                    new XML.Attr("value", session.toString()))).
                add(new Table(new XML.Attr("class", "SessionStateProperties")).
                add(new Table.Caption("Session Properties")).
                add(body)
            ));

            XHTML.Document document = makeDocument(
                    new XHTML.HeadSubElement[] { new XHTML.Title("Register") },
                    new XHTML.BodySubElement[] { form });
            return new HttpResponse(HTTP.Response.Status.OK,
                new HttpContent.Text.HTML(document));
        } catch (FileException e) {
            //
            // We failed to create the file system for some reason other than
            // missing credentials.
            //
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                new HttpContent.Text.Plain(e.getLocalizedMessage()));
        }

        //
        // Using Refresh here instead of just calling filesys sets the
        // browser's notion of the URL.
        //
        return redirectTo(String.format("/browse?sessionId=%s", session), "");
    }

    private HTTP.Response postLoginForm(SessionState session) {
        XHTML.Form sessionProperties = new XHTML.Form("login").setMethod("post").setEncodingType("multipart/form-data")
            .add(new XHTML.Div(new XML.Attr("class", "section")).
                add(new Table(new XML.Attr("class", "SessionStateProperties")).
                add(new Table.Caption("Session Properties")).
                add(new Table.Body(
                    new Table.Row(
                        new Table.Heading(""),
                        new Table.Heading("Name&nbsp;Space"),
                        new Table.Heading("Name&nbsp;Space&nbsp;Password"),
                        new Table.Heading("Replication Parameters")
                    ),
                    new Table.Row(
                        new Table.Data(new XHTML.Input(
                            XHTML.Input.Type.SUBMIT).setName("action").setValue("login").setTitle("Submit login information")
                        ),
                        new Table.Data(new XHTML.Input(
                            XHTML.Input.Type.TEXT).setName(SessionState.NAMESPACEID).setSize(16)
                        ),
                        new Table.Data(new XHTML.Input(
                            XHTML.Input.Type.PASSWORD).setName(SessionState.NAMESPACEPASSWORD).setSize("16")
                        ),
                        new Table.Data(session.selectReplicationParamsXHTML())
                    ),
                    new Table.Row(
                        new Table.Heading(""),
                        new Table.Heading("User&nbsp;Name"),
                        new Table.Heading("User&nbsp;Password")
                    ),
                    new Table.Row(
                        new Table.Data(""),
                        new Table.Data(new XHTML.Input(
                            XHTML.Input.Type.TEXT).setName(SessionState.ACCESSORID).setSize(16)
                        ),
                        new Table.Data(new XHTML.Input(
                            XHTML.Input.Type.PASSWORD).setName(SessionState.ACCESSORPASSWORD).setSize("16")
                        ),
                        new Table.Heading("(if&nbsp;distinct&nbsp;from&nbsp;name&nbsp;space)")
                    ),
                    new Table.Row(
                        new Table.Data(""),
                        new Table.Data(new XHTML.Input(XHTML.Input.Type.CHECKBOX).setName("autoRefresh").setValue("on"))
                        .add("Periodically refresh display")
                        .setColumnSpan(4)
                    ),
                    new Table.Row(
                        new Table.Data(""),
                        new Table.Data()
                            .add(new XHTML.Input(XHTML.Input.Type.CHECKBOX).setName("showStatistics").setValue("on"))
                            .add("Show performance statistics")
                            .setColumnSpan(4)
                    )
                ))
            ));

        XHTML.Document document = makeDocument(
            new XHTML.HeadSubElement[] { new XHTML.Title("Celeste Browser File System Login") },
            new XHTML.BodySubElement[] { sessionProperties });

        HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(document));
        return response;
    }

    private HTTP.Response postRegisterForm(SessionState session) {
        //
        // Copy method guts here from loginSession!
        //
        return null;
    }

    private boolean profileIsRegistered(SessionState session, String name) {
        try {
            ReadProfileOperation op = new ReadProfileOperation(
                new BeehiveObjectId(name.getBytes()));
            this.celesteProxy.readCredential(op);
            return true;
        } catch (CelesteException.CredentialException e) {
            // fall through
        } catch (CelesteException.NotFoundException e) {
            // fall through
        } catch (CelesteException.RuntimeException e) {
            // fall through
        } catch (IOException e) {
            // fall through
        } catch (ClassNotFoundException e) {
            // fall through
        }
        return false;
    }

    //
    // This gets called both from the POST and the GET handler.  (And, as it
    // turns out, from the HEAD handler as well.)
    //
    // That is, clicks on hyperlinks and form submission buttons in the page
    // (or its subpages) result in control flowing here.
    //
    // The URIs that these links and form submissions generate are structured
    // as:
    //      /action/file-path?attr=value&attr=value...
    //
    protected HTTP.Response dispatch(HTTP.Request request, URI uri, HTTP.Message.Body.MultiPart.FormData props) throws IOException {
        String sessionId = HttpMessage.asString(props.get(SessionState.SESSIONID), null);
        SessionState session =
            (sessionId == null) ? null : this.sessions.get(sessionId);
        HTTP.Response unauthorised = new HttpResponse(HTTP.Response.Status.UNAUTHORIZED, new HttpContent.Text.Plain("Unauthorized Access.\r\n"));

        HTTP.Response response = null;

        //
        // Emit both the entire incoming URI and its decomposed name-value
        // attribute pairs as debugging/logging info.
        //
        if (true) {
            CelesteHTTPd.println("Dispatch: " + uri.toString());
            for (Map.Entry<String,HTTP.Message> entry : props.entrySet()) {
                String key = entry.getKey();
                HTTP.Message m = entry.getValue();
                String value = HttpMessage.asString(m, "(null)");
                if (value.length() > 65)
                    value = value.substring(0, 65) + "...";
                CelesteHTTPd.println(String.format("%s=%s", key, value));
            }
        }

        //
        // Dispatch according to the requested action.
        //

        if (uri.getPath().equals("/")) {
            return loginSession(null);
        } else if (uri.getPath().equals("/login")) {
            return loginSession(props);
        }

        if (uri.getPath().startsWith("/newFileSystem")) {
            //
            // N.B.  The normal flow of control through the sequence of login
            // and registration forms does not create URIs contining
            // "/newFileSystem".  So the URI leading here must have been
            // constructed by hand.
            //
            response = newFileSystem(uri, props);
        } else if (uri.getPath().startsWith("/browse")) {
            if (props != null) {
                String action =  HttpMessage.asString(props.get("action"), null);
                if (action != null && action.equals("logout")) {
                    return loginSession(null);
                }
            }
            response = (session == null) ? unauthorised : browse(session, props);
        } else if (uri.getPath().startsWith("/isWriteable")) {
            response = (session == null) ? unauthorised : accessFile(session, uri, props);
        } else if (uri.getPath().startsWith("/read")) {
            response = (session == null) ? unauthorised : readFile(request, session, uri, props);
        } else if (uri.getPath().startsWith("/delete")) {
            response = (session == null) ? unauthorised : deleteFile(request, session, uri, props);
        } else if (uri.getPath().startsWith("/stat")) {
            response = (session == null) ? unauthorised : statusFile(session, uri, props);
        } else if (uri.getPath().startsWith("/mkdir")) {
            response = (session == null) ? unauthorised : mkdir(session, uri, props);
        } else if (uri.getPath().startsWith("/mount")) {
            response = mountFileSystem(uri, props);
        } else if (uri.getPath().equals("/logout")) {
            if (session != null) {
                session.fini();
                this.sessions.remove(session.getIdentity());
            }
            response = loginSession(null);
        } else {
            // Fetch a file.
            try {
                //
                // XXX: This is a security problem in that when running
                //      outside of a .jar file, any file in the working
                //      directory can be fetched.
                //
                return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.RawInputStream(this.serverRoot, uri.getPath()));
            } catch (IOException e) {
                e.printStackTrace();
                response = new HttpResponse(HTTP.Response.Status.NOT_FOUND, new HttpContent.Text.Plain(uri + " " + e.getLocalizedMessage()));
            }
        }
        return response;
    }

    //
    // Return XHTML code that displays an XHTML selector element whose
    // choices are the various possibilities for content type.
    //
    private static XHTML.Select contentTypeSelector() {
        XHTML.Select selector = new XHTML.Select(
                new XML.Attr("name", "contentType"),
                new XML.Attr("title", "Choose content type"));
        //
        // Add in a "don't make me choose" default alternative, followed by a
        // choice for each supported content type.
        //
        selector.add(new XHTML.Option(
                new XML.Attr("label", "(infer content type from name suffix)"),
                new XML.Attr("selected", "selected"),
                new XML.Attr("value", "?")).
                add("(infer content type from name)"));
        for (InternetMediaType contentType : contentTypes) {
            String type = contentType.toString();
            selector.add(new XHTML.Option(
                    new XML.Attr("label", type),
                    new XML.Attr("value", type)).
                    add(type));
        }
        return selector;
    }

    //
    // XXX: ContentType ought to be an enum.  Then we'd get this array for
    //      free.
    //
    private final static InternetMediaType[] contentTypes = {
        InternetMediaType.Text.Plain,
        InternetMediaType.Text.HTML,
        InternetMediaType.Text.XML,
        InternetMediaType.Text.CSS,
        InternetMediaType.Image.Gif,
        InternetMediaType.Image.Jpeg,
        InternetMediaType.Image.Icon,
        InternetMediaType.Application.XWWWFormURLEncoded,
        InternetMediaType.Application.OctetStream,
        InternetMediaType.Application.Directory,
        InternetMediaType.Application.Ogg,
        InternetMediaType.Audio.Mpeg,
        InternetMediaType.Audio.XMpegUrl,
        InternetMediaType.Audio.XMsWma,
        InternetMediaType.Audio.XMsWax,
        InternetMediaType.Video.XMsAsf,
        InternetMediaType.Video.XMsAsx,
        InternetMediaType.Video.XMsWmv,
        InternetMediaType.Video.XMsWvx,
        InternetMediaType.Video.XMsWmx,
        InternetMediaType.Video.Mp4,
        InternetMediaType.Video.Mpeg,
        InternetMediaType.Video.AVI
    };

    /**
     * Run this server thread.
     */
    private void run() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.socket().bind(new InetSocketAddress(this.serverPort));
            serverSocketChannel.socket().setReuseAddress(true);

            HTTPServer.debugPrintRequests = false;
            while (true) {
                System.out.printf("CelesteHTTPd run%n");

                SocketChannel socketChannel = serverSocketChannel.accept();
                System.out.printf("CelesteHTTPd accepted%n");

                socketChannel.socket().setTcpNoDelay(true);

                socketChannel.socket().setReceiveBufferSize(1024*1024);
                socketChannel.socket().setSendBufferSize(1024*1024);
                HTTPServer server = new HTTPServer(socketChannel);

                server.addNameSpace(new URI("/"), new HTTPNameSpace(server));
                
                Logger logger = Logger.getLogger("CelesteHTTPd");
                server.setLogger(logger);
                server.start();
            }
        } catch (Exception e) {
            e.printStackTrace ();
        }
    }

    public class HTTPNameSpace extends NameSpace {
        public HTTPNameSpace(HTTP.Server server) {
            super(server, null);

            this.add(HTTP.Request.Method.GET, new HttpGet());
            this.add(HTTP.Request.Method.POST, new HttpPost());
            this.add(HTTP.Request.Method.PUT, new HttpPut());
            this.add(HTTP.Request.Method.HEAD, new HttpHead());
        }
    }
    
    public static void printf(String format, Object... args) {
        System.out.printf("%s: ",
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()));
        System.out.printf(format, args);
    }

    public static void println(String s) {
        System.out.println(DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(new Date()) + ": " + s);
    }

    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }

    /**
     * <style>
     * table.argv td {
     *  padding: 0 1em 0 1em;
     * }
     * </style>
     * <table class="argv">
     * <tr><td>--port</td><td><i>port-number</i></td></tr>
     * <tr><td>--celeste-address</td><td><i>hostname:port</i></td></tr>
     * <tr><td>--log</td><td><i>file-name</i></td></tr>
     * </table>
     *
     */
    public static void main(String[] args) {
        System.out.printf("Celeste File Browser %s%n%s%n", release, Copyright.miniNotice);

        try {
            String celesteAddress = "127.0.0.1:14000";
            int port = 15000;
            DataOutputStream logFile = null;

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--port")) {
                    port = Integer.parseInt(args[++i]);
                } else if (args[i].equals("--celeste-address")) {
                    celesteAddress = args[++i];
                } else if (args[i].equals("--log") ) {
                    logFile = new DataOutputStream(new FileOutputStream(args[++i]));
                } else {
                    System.out.printf("Unknown argument: %s%n", args[i]);
                    System.out.println("Usage: (defaults are in parentheses)");
                    System.out.printf(" [--port <number>] (%d)%n", port);
                    System.out.printf(" [--celeste-address <address:port>] (%s)%n", celesteAddress);
                    System.out.printf(" [--log] (%s)%n", logFile);
                    System.exit(0);
                }
            }

            CelesteHTTPd server = new CelesteHTTPd(port, celesteAddress, logFile);
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
