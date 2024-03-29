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
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;

import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.ReplicationParameters;
//import sunlabs.celeste.client.filesystem.fast.FSMetaData;
//import sunlabs.celeste.client.filesystem.fast.FastFileSystem;
import sunlabs.celeste.client.filesystem.CelesteFileSystem;
import sunlabs.celeste.client.filesystem.FileAttributes;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.celeste.client.filesystem.HierarchicalFileSystem;
import sunlabs.celeste.client.filesystem.PathName;
import sunlabs.celeste.client.operation.ExtensibleOperation;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.ReadProfileOperation;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.Copyright;
import sunlabs.titan.Release;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.Credential;
import sunlabs.titan.util.OrderedProperties;

/**
 *
 */
public class CelesteFs {
    private final static String release = Release.ThisRevision();
    public Properties properties;
    private InetSocketAddress celeste;
    private long timeOutSeconds;
    private final CelesteProxy.Cache proxyCache;
    private HierarchicalFileSystem.Factory factory;

    //
    // The default size used for buffers in methods that copy data from one
    // place to another.  That is, the default value used when the
    // --block-size option is not specified.
    //
    private final static int defaultBufferSize = 32 * 1024 * 1024;

    protected CelesteFs(InetSocketAddress celeste, DataOutputStream logFile, long timeOutSeconds, String identity, String password) throws FileNotFoundException {
        this.properties = new Properties();
        this.properties.setProperty("verbose", "false");
        this.celeste = celeste;
        this.timeOutSeconds = timeOutSeconds;
        this.proxyCache = new CelesteProxy.Cache(4, Time.secondsInMilliseconds(this.timeOutSeconds));
        this.factory = new CelesteFileSystem.Factory(celeste, this.proxyCache, identity, password);
        //this.factory = new FastFileSystem.Factory(celeste, this.proxyCache, identity, password, new FSMetaData(new PathName("/tmp/ffs")));
    }

    private void close() {
        this.proxyCache.dispose();
    }
    
    /**
     * Return the proxy cache that this {@code CelesteFs} instance uses.
     *
     * @return this instance's proxy cache
     */
    public CelesteProxy.Cache getProxyCache() {
        return this.proxyCache;
    }

    /**
     * Create a new {@link Credential}.
     *
     * @param name the name of the Credential
     * @param password the password used to unlock the Credential
     * @param replicationParams the {@link ReplicationParameters} instance governing the replication of the Credential
     * @throws Credential.Exception
     * @throws IOException
     * @throws CelesteException.RuntimeException
     * @throws CelesteException.AlreadyExistsException
     * @throws CelesteException.NoSpaceException
     * @throws CelesteException.VerificationException
     * @throws CelesteException.CredentialException
     * @throws ClassNotFoundException
     */
    public void createCredential(String name, String password, String replicationParams) throws Credential.Exception, IOException,
      CelesteException.RuntimeException, CelesteException.AlreadyExistsException, CelesteException.NoSpaceException, CelesteException.VerificationException,
      CelesteException.CredentialException, ClassNotFoundException, FileException.CelesteInaccessible  {
        
        Credential credential = new Profile_(name, password.toCharArray());

        NewCredentialOperation operation = new NewCredentialOperation(credential.getObjectId(), TitanGuidImpl.ZERO, replicationParams);
        Credential.Signature signature = credential.sign(password.toCharArray(), operation.getId());

        CelesteAPI proxy = null;
        try {
            proxy = this.proxyCache.getAndRemove(this.celeste);
            proxy.newCredential(operation, signature, credential);
        } catch (CelesteException.CredentialException e) {
            throw e;
        } catch (CelesteException.AlreadyExistsException e) {
            throw e;
        } catch (CelesteException.RuntimeException e) {
            throw e;
        } catch (CelesteException.NoSpaceException e) {
            throw e;
        } catch (CelesteException.VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new FileException.CelesteInaccessible(e);
        } finally {
            this.proxyCache.addAndEvictOld(this.celeste, proxy);
        }
    }

    public int mkid(String idName, String idPassword, String command, Stack<String> options, Stats stats) throws IOException {

        String replicationParams = "Credential.Replication.Store=2";

        OrderedProperties attrs = new OrderedProperties();
        while (!options.empty() && options.peek().equals("--attr")) {
            options.pop();
            String operand = options.pop();
            String[] keyValue = operand.split("=", 2);
            if (keyValue.length != 2) {
                System.err.printf("malformed --attr operand: %s%n", operand);
                return 1;
            }
            attrs.setProperty(keyValue[0], keyValue[1]);

            if (keyValue[0].compareTo(FileAttributes.Names.REPLICATION_PARAMETERS_NAME) == 0) {
                replicationParams = keyValue[1];
            }
        }

        try {
            createCredential(idName, idPassword, replicationParams);
        } catch (Profile_.Exception e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        } catch (CelesteException e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        } catch (ClassNotFoundException e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        } catch (FileException.CelesteInaccessible e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }

        stats.addMessage("%s", idName);
        return 0;
    }

    // XXX: Note that the replicationParams parameter is relevant only to
    //      creating the name space in which the file system will live.
    //      Replication parameters for the file system itself (which are used
    //      as defaults for files and directories created within it) should be
    //      specified using "--attr ReplicationParameters=<whatever>".
    //
    public int mkfs(String command, Stack<String> options, Stats stats) throws IOException {
        String replicationParams = "Credential.Replication.Store=3; AObject.Replication.Store=3; VObject.Replication.Store=3; BObject.Replication.Store=3;";
        //
        // Gather up attributes to be applied to the file system.
        // They're specified as a sequence of "--attr <name>=<value>" pairs.
        //
        OrderedProperties fileSystemAttributes = new OrderedProperties();
        fileSystemAttributes.setProperty(FileAttributes.Names.REPLICATION_PARAMETERS_NAME, replicationParams);
        
        while (!options.empty() && options.peek().equals("--attr")) {
            options.pop();
            String operand = options.pop();
            String[] keyValue = operand.split("=", 2);
            if (keyValue.length != 2) {
                System.err.printf("malformed --attr operand: %s%n", operand);
                return 1;
            }
            fileSystemAttributes.setProperty(keyValue[0], keyValue[1]);
        }

        if (options.size() != 2) {
            System.err.printf("%s [--attr <key>=<value>]... <fileSystemName> <password>%n", command);
            return -1;
        }

        boolean verbose = this.properties.getProperty("verbose", "false").equals("true");

        try {
            String fileSystemName = options.pop();
            String fileSystemPassword = options.pop();

            HierarchicalFileSystem fileSystem = this.factory.create(fileSystemName, fileSystemPassword, fileSystemAttributes);

            stats.addMessage("%s", fileSystemName);
            return 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (verbose) {
                e.printStackTrace();
            }
            return -1;
        } catch (Exception e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    //
    // XXX: Needs better error checking for argument processing.
    //
    public int mkdir(String command, Stack<String> options, Stats stats) throws Exception, IOException, Credential.Exception {
        boolean pFlag = false;

        OrderedProperties attrs = new OrderedProperties();
        OrderedProperties props = new OrderedProperties();
        while (!options.empty()) {
            if (options.peek().startsWith("-")) {
                //
                // Allowable options are "-p" (to request that intermediate
                // directories be created) and an arbitrary number of
                // attribute or property specifications.
                //
                // Attributes are specified as a sequence of "--attr
                // <name>=<value>" pairs, and properties are specified as
                // "--prop <name>=<value>" pairs.
                //
                String option = options.pop();
                boolean isAttr = option.equals("--attr");;
                if (option.equals("-p")) {
                    pFlag = true;
                } else if (isAttr || option.equals("--prop")) {
                    String operand = options.pop();
                    String[] keyValue = operand.split("=", 2);
                    if (keyValue.length != 2) {
                        System.err.printf("malformed %s operand: %s%n", option, operand);
                        return 1;
                    }
                    if (isAttr)
                        attrs.setProperty(keyValue[0], keyValue[1]);
                    else
                        props.setProperty(keyValue[0], keyValue[1]);
                } else {
                    System.err.printf("unknown option %s%n", option);
                    System.err.printf("mkdir [-p] name%n");
                    return -1;
                }
            } else {
                String fullPath = options.pop();
                String[] tokens = fullPath.split("/", 3);
                String fileSystemName = tokens[1];
                fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

                HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

                if (pFlag) {
                    String accumulator = "";
                    for (String s : new PathName(fullPath).getComponents()) {
                        accumulator += "/" + s;
                        accumulator = accumulator.replaceAll("/+", "/");
                        if (accumulator.compareTo("/") != 0) {
                            try {
                                fileSystem.createDirectory(new PathName(accumulator), attrs, props);
                            } catch (FileException.Exists ignore) {
                                //
                                // Having intermediate directories already
                                // exist is a normal occurrence.
                                //
                                // XXX: But this exception doesn't
                                //      discriminate a directory existing from
                                //      a plain file existing...
                                //
                            } catch (FileException trouble) {
                                throw new IOException(trouble);
                            }
                        }
                    }
                } else {
                    try {
                        fileSystem.createDirectory(new PathName(fullPath), attrs, props);
                    } catch (FileException trouble) {
                        throw new IOException(trouble);
                    }
                }
                stats.addMessage("%s", fullPath);
            }
        }

        return 0;
    }

    public void deleteDirectory(HierarchicalFileSystem fileSystem, PathName name) throws FileException, IOException, ClassNotFoundException {
        HierarchicalFileSystem.Node file = fileSystem.getNode(name);
        if (file instanceof HierarchicalFileSystem.Directory) {
            HierarchicalFileSystem.Directory dir = (HierarchicalFileSystem.Directory) file;
            for (String n : dir.list()) {
                if (n.compareTo(".") != 0 && n.compareTo("..") != 0 ) {
                    deleteDirectory(fileSystem, name.append(n));
                }
            }
            fileSystem.deleteDirectory(name);
        } else {
            fileSystem.deleteFile(name);
        }
    }

    public int deleteFile(String command, Stack<String> options, Stats stats) throws IOException {
        //
        // At this point, it is perfectly legal to delete a directory with
        // content.  What happens is that all files in that directory are
        // unreferenced, and may cease to be refreshed, unless they are
        // referenced through other directories.  This applies to
        // sub-directories as well.
        //
        try {
            while (!options.empty()) {
                String fullPath = options.pop();
                String[] tokens = fullPath.split("/", 3);
                String fileSystemName = tokens[1];
                fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

                HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

                PathName path = new PathName(fullPath);

                HierarchicalFileSystem.Node file = fileSystem.getNode(path);
                if (file instanceof HierarchicalFileSystem.Directory) {
                    this.deleteDirectory(fileSystem, path);
                } else {
                    fileSystem.deleteFile(path);
                }
            }
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }

        return 0;
    }

    public int preadFile(String command, Stack<String> options, Stats stats)
    throws IOException, FileException {
        try {
            String fullPath = options.pop();

            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

            long offset = options.empty() ? 0 : Long.parseLong(options.pop());

            int length = options.empty() ? -1 : Integer.parseInt(options.pop());

            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            PathName path = new PathName(fullPath);
            HierarchicalFileSystem.Node fileOrDirectory = fileSystem.getNode(path);
            if (fileOrDirectory instanceof HierarchicalFileSystem.Directory) {
                System.out.printf("read a directory%n");
            } else if (fileOrDirectory instanceof HierarchicalFileSystem.File) {
                HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) fileOrDirectory;

                file.position(offset);

                InputStream input = file.getInputStream();

                byte[] buffer = new byte[file.getBlockSize()];

                long remaining = length == -1 ? file.length() : length;

                do {
                    int nread = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (nread < 1)
                        break;
                    System.out.write(buffer, 0, nread);
                    remaining -= nread;
                } while (remaining > 0);
                input.close();
                System.out.flush();

                stats.addMessage("%s/%s %s bytes", fileSystemName, fullPath, length == -1 ? file.length() : length);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }

        return 0;
    }

    public int readdir(String command, Stack<String> options, Stats stats) throws IOException, FileException {
        try {
            boolean lFlag = false;

            if (options.peek().startsWith("-")) {
                String option = options.pop();
                for (int i = 1; i < option.length(); i++) {
                    if (option.charAt(i) == 'l') {
                        lFlag = true;
                    }
                }
            }

            String fullPath = options.pop();

            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            PathName path = new PathName(fullPath);
            HierarchicalFileSystem.Node fileOrDirectory = fileSystem.getNode(path);

            if (fileOrDirectory instanceof HierarchicalFileSystem.Directory) {
                HierarchicalFileSystem.Directory directory = (HierarchicalFileSystem.Directory) fileOrDirectory;
                for (String entryName : directory.list()) {
                    if (lFlag) {
                        System.out.printf("%s%n", new String(path + "/" + entryName).replaceAll("/+", "/"));
                    } else {
                        System.out.printf("%s%n", entryName);
                    }
                }
                return 0;
            }

            return 1;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    public int testFile(String command, Stack<String> options, Stats stats) throws IOException, FileException {
        boolean eFlag = false;
        boolean dFlag = false;
        String typeFlag = null;
        boolean credentialFlag = false;
        boolean filesystemFlag = false;
        boolean successful = false;
        boolean verbose = this.properties.getProperty("verbose", "false").equals("true");

        if (options.peek().startsWith("-")) {
            String option = options.pop();
            for (int i = 1; i < option.length(); i++) {
                if (option.charAt(i) == 'e') { // exists
                    eFlag = true;
                } else if (option.charAt(i) == 'd') { // exists and is directory
                    dFlag = true;
                } else if (option.charAt(i) == 't') { // a particular content-type
                    typeFlag = options.pop();
                } else if (option.charAt(i) == 'c') { // exists and is a credential
                    credentialFlag = true;
                } else if (option.charAt(i) == 'f') { // exists and is a filesystem.
                    filesystemFlag = true;
                }
            }
        }

        if (credentialFlag) {
            // Test for a credential
            String credentialName = options.pop();
            ReadProfileOperation operation = new ReadProfileOperation(new TitanGuidImpl(credentialName.getBytes()));

            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCache.getAndRemove(celeste);
                proxy.readCredential(operation);
                successful = true;
            } catch (CelesteException.CredentialException e) {
                successful = false;
            } catch (CelesteException.AccessControlException e) {
                successful = false;
            } catch (CelesteException.NotFoundException e) {
                successful = false;
            } catch (CelesteException.RuntimeException e) {
                successful = false;
            } catch (ClassNotFoundException e) {
                successful = false;
            } catch (Exception e) {
                successful = false;
            } finally {
                this.proxyCache.addAndEvictOld(celeste, proxy);
            }
            if (verbose)
                System.out.println(successful ? "true" : "false");
            return successful ? 0 : 1;
        }

        if (filesystemFlag) {
            String fileSystemName = options.pop();

            successful = factory.exists(fileSystemName);
            if (verbose)
                System.out.println(successful ? "true" : "false");
            return successful ? 0 : 1;
        }

        String fullPath = options.pop();
        String[] tokens = fullPath.split("/", 3);
        String fileSystemName = tokens[1];
        fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");
        PathName path = new PathName(fullPath);

        try {
            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            if (fileSystem.fileExists(path)) {
                HierarchicalFileSystem.Node fileOrDirectory = fileSystem.getNode(path);
                if (typeFlag != null) {
                    if (fileOrDirectory instanceof HierarchicalFileSystem.File) {
                        HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) fileOrDirectory;
                        successful = file.getContentType().equals(typeFlag);                        
                    } else {
                        successful = false;
                    }
                } else if (dFlag) {
                    successful = fileOrDirectory instanceof HierarchicalFileSystem.Directory;
                } else if (eFlag) {
                    successful = true;
                } else {
                    successful = false;
                }
            } else {
                successful = false;
            }
        } catch (Exception e) {
            if (verbose) {
                e.printStackTrace(System.err);
                System.err.printf("%s %s %s%n", path, e.toString(), e.toString());
            }
            successful = false;
        }

        if (verbose)
            System.out.println(successful ? "true" : "false");
        return successful ? 0 : 1;
    }
    
    public int createFile(String command, Stack<String> options, Stats stats) throws IOException {
        //
        // Gather up attributes and client-supplied metadata ("properties") to
        // be applied to the file's initial version.
        //
        // Attributes are specified as a sequence of "--attr <name>=<value>"
        // pairs, and properties are specified as "--prop <name>=<value>"
        // pairs.
        //
        // The purposes of attributes and properties and the differences
        // between then are not adequately described anywhere.
        //
        // Allow attributes and properties to be intermixed on the command
        // line.
        //
        OrderedProperties fileProperties = new OrderedProperties();
        OrderedProperties clientProperties = new OrderedProperties();
        while (!options.empty() && (options.peek().equals("--attr") || options.peek().equals("--prop"))) {
            String option = options.pop();
            boolean isAttr = option.equals("--attr");;
            String operand = options.pop();
            String[] keyValue = operand.split("=", 2);
            if (keyValue.length != 2) {
                System.err.printf("malformed %s operand: %s%n", option, operand);
                return 1;
            }
            if (isAttr)
                fileProperties.setProperty(keyValue[0], keyValue[1]);
            else
                clientProperties.setProperty(keyValue[0], keyValue[1]);
        }

        //
        // Check for and validate an explicitly given content type.  If it's
        // given but doesn't validate, pretend we didn't see it.
        //
        // XXX: Phase this option out, but accept it for now.  (The --attr
        //      option handled above subsumes it.)
        //
        String contentType = null;
        if (options.peek().equals("--content-type")) {
            options.pop();
            contentType = options.pop();
            InternetMediaType mediaType = InternetMediaType.getInstance(contentType);
            if (mediaType == null)
                contentType = null;
            if (contentType != null)
                fileProperties.setProperty("ContentType", contentType);
        }

        try {
            String fullPath = options.pop();
            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");
            
            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            HierarchicalFileSystem.FileName path = new PathName(fullPath);

            fileSystem.createFile(path, fileProperties, clientProperties);
            stats.addMessage("%s/%s", fileSystemName, fullPath);
        } catch (Exception e) {
            stats.addMessage("%s", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }
        return 0;
    }

    /**
     * <p>
     * Open a file and return its contents as a byte array.  If {@code name}
     * is {@code "-"}, use {@code System.in} as the file.
     * </p><p>
     * Note that the entire file will be read into a byte array in memory.  A
     * large file can consume all available memory.
     * </p>
     *
     * @param name  the name of the file to be opened or {@code "-"} for {@code System.in}
     *
     * @return  a byte array containing the contents of the file
     */
    private static byte[] readNamedFile(String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (name.equals("-")) {
            byte [] b = new byte[1024*1024];

            int nread;
            while (System.in.available() > 0) {
                nread = System.in.read(b);
                out.write(b, 0 , nread);
            }
            return out.toByteArray();
        }

        FileInputStream fis = null;
        try {
            File file = new File(name);
            if (file.length() > Integer.MAX_VALUE) {
                throw new IOException("File too big");
            }
            byte[] b = new byte[(int) file.length()];
            fis = new FileInputStream(file);

            while (fis.available() > 0) {
                int nread = fis.read(b);
                if (nread < 0) {
                    //
                    // Unexpected EOF.  (Should not occur.)
                    //
                    break;
                }
                out.write(b, 0 , nread);
            }
            return out.toByteArray();
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ignore) { }
        }
    }

    public int writeFile(String command, Stack<String> options, Stats stats) throws IOException {
        try {
            String fullPath = options.pop();
            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");
            PathName path = new PathName(fullPath);

            long offset = options.empty() ? 0 : Long.parseLong(options.pop());

            String inputPath = options.empty() ? "-" : options.pop();

            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            HierarchicalFileSystem.Node outputfile = fileSystem.getNode(path);
            if (outputfile instanceof HierarchicalFileSystem.File) {
                HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) outputfile; 
                ByteBuffer data = ByteBuffer.wrap(readNamedFile(inputPath));
                file.write(data, offset);
            } else {
                throw new IOException("Cannot write to a directory.");
            }
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }
        return 0;
    }

    /**
     * Writes a Celeste file by copying data from a file in the local file
     * system or from {@code System.in}.  The specific command to execute is
     * given by the contents of the {@code options} argument as follows:
     *
     * <pre>
     * [--buffer-size &lt;n&gt;] &lt;pathname&gt;|- &lt;offset&gt; &lt;fileToWrite&gt;</pre>
     *
     * <p>
     *
     * In the synopsis above, the {@code "--buffer-size"} flag is optional and,
     * if present, must be accompanied by an positive integer giving the
     * amount of data to transfer in each write during the overall copy
     * sequence.
     *
     * </p><p>
     *
     * Input data comes from either {@code pathname} or {@code System.in}.
     *
     * </p><p>
     *
     * {@code fileToWrite} designates the Celeste file to be written, and
     * {@code offset} the starting position within that file of the sequence
     * of writes.
     *
     * </p>
     *
     * @return  {@code 0} on success and {@code -1} on failure
     */
    public int writeFileChunked(String command, Stack<String> options, Stats stats) {
        try {
            //
            // Check for options.
            //
            int bufferSize = CelesteFs.defaultBufferSize;
            if (options.peek().equals("--buffer-size")) {
                options.pop();
                bufferSize = Integer.parseInt(options.pop());
                if (bufferSize <= 0)
                    throw new IllegalArgumentException("buffer size must be positive");
            }

            String fullPath = options.pop();
            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");
            PathName path = new PathName(fullPath);

            long offset = options.empty() ? 0 : Long.parseLong(options.pop());

            String inputPath = options.empty() ? "-" : options.pop();

            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            HierarchicalFileSystem.Node outputfile = fileSystem.getNode(path);

            if (outputfile instanceof HierarchicalFileSystem.File) {
                HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) outputfile;
                //
                // Force the buffer size to be the largest multiple of the block
                // size that's less than or equal to the buffer size's specified
                // value.
                //
                int blockSize = file.getBlockSize();
                if (bufferSize % blockSize != 0) {
                    int ratio = bufferSize / blockSize;
                    bufferSize = blockSize * ratio;
                }

                byte[] buffer = new byte[bufferSize];

                InputStream fin = inputPath.equals("-") ? System.in : new FileInputStream(inputPath);

                file.position(offset);
                while (true) {
                    int nread = fin.read(buffer);
                    if (nread == -1)
                        break;

                    ByteBuffer data = ByteBuffer.wrap(buffer, 0, nread);
                    file.write(data);
                }
            } else {
                throw new IOException("Cannot write to a directory.");
            }
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }
        return 0;
    }

    public int pwriteFile(String command, Stack<String> options, Stats stats) {
        try {
            //
            // Check for options.
            //
            int bufferSize = CelesteFs.defaultBufferSize;
            if (options.peek().equals("--buffer-size")) {
                options.pop();
                bufferSize = Integer.parseInt(options.pop());
                if (bufferSize <= 0)
                    throw new IllegalArgumentException("buffer size must be positive");
            }

            //
            // Gather arguments.
            //
            String fullPath = options.pop();
            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

            long offset = options.empty() ? 0 : Long.parseLong(options.pop());

            InputStream input = options.empty() ?
                    System.in : new FileInputStream(options.pop());

            //
            // Look up the Celeste file to be written.
            //

            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            PathName path = new PathName(fullPath);
            HierarchicalFileSystem.Node node = fileSystem.getNode(path);
            if (node instanceof HierarchicalFileSystem.File) {
                HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) node;

                //
                // Force the buffer size to be the largest multiple of the block
                // size that's less than or equal to the buffer size's specified
                // value.
                //
                int blockSize = file.getBlockSize();
                if (bufferSize % blockSize != 0) {
                    int ratio = bufferSize / blockSize;
                    bufferSize = blockSize * ratio;
                }

                //
                // Prepare to write to the file by wrapping it in an output
                // stream, being careful to avoid truncating it as part of the
                // wrapping.  Ensure that buffer sizes are set properly
                // throughout.
                //
                OutputStream fos = file.getOutputStream(false, bufferSize);
                OutputStream o = new BufferedOutputStream(fos, bufferSize);

                file.position(offset);

                //
                // Use CelesteSh's readBuffer() method to avoid partial reads
                // whenever possible.  (Partial reads are bad on two counts:  they
                // induce extra writes, which create extra file versions; and they
                // destroy block alignment for those writes, which causes
                // otherwise-unnecessary block object copying within Celeste.)
                //
                byte[] buffer = new byte[bufferSize];
                int nread;
                long length = 0;
                while ((nread = CelesteSh.readBuffer(input, buffer)) > 0) {
                    o.write(buffer, 0, nread);
                    length += nread;
                }

                o.close();
                stats.addMessage("%s/%s %s bytes", fileSystemName, fullPath, length);
            } else {
                throw new IOException("Cannot write to a directory.");
            }
        } catch (Exception e) {
            stats.addMessage("%s", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }
        return 0;
    }

    public int runExtension(String command, Stack<String> options, Stats stats) throws IOException {
        try {
            String fullPath = options.pop();
            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

            String[] urls = options.pop().split(",");

            List<String> argList = new LinkedList<String>();
            while (!options.empty()) {
                argList.add(options.pop());
            }

            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

            HierarchicalFileSystem.FileName path = new PathName(fullPath);
            HierarchicalFileSystem.Node node = fileSystem.getNode(path);

            URL[] jarFileURLs = new URL[urls.length];
            for (int i = 0; i < urls.length; i++) {
                jarFileURLs[i] = new URL(urls[i]);
            }
            if (node instanceof HierarchicalFileSystem.File) {
                HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) node;

                FileIdentifier fid = file.getFileIdentifier();

                TitanGuid credentialId = TitanGuidImpl.IsValid(this.factory.getCredentialName()) ?
                        new TitanGuidImpl(this.factory.getCredentialName()) :
                            new TitanGuidImpl(this.factory.getCredentialName().getBytes());

                        CelesteAPI proxy = proxyCache.getAndRemove(celeste);
                        try {
                            Credential readerCredential = proxy.readCredential(new ReadProfileOperation(credentialId));

                            ExtensibleOperation operation = new ExtensibleOperation(fid, credentialId, jarFileURLs, argList.toArray(new String[0]));

                            Credential.Signature signature = readerCredential.sign(this.factory.getCredentialPassword().toCharArray(), operation.getId());

                            ResponseMessage reply = proxy.runExtension(operation, signature, null);
                            Serializable result = reply.get(Serializable.class);
                            System.out.printf("%s%n", result.toString());
                        } finally {
                            proxyCache.addAndEvictOld(celeste, proxy);
                        }
            }
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }
        return 0;
    }

    public int setFileLength(String command, Stack<String> options, Stats stats) throws IOException {
        try {
            String fullPath = options.pop();
            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

            long length = options.empty() ? 0 : Long.parseLong(options.pop());

            HierarchicalFileSystem fileSystem = factory.mount(fileSystemName);

            HierarchicalFileSystem.FileName path = new PathName(fullPath);
            HierarchicalFileSystem.Node node = fileSystem.getNode(path);
            if (node instanceof HierarchicalFileSystem.File) {
                HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) node;
                file.truncate(length);
            } else {
                throw new IOException("Cannot truncate a directory.");
            }
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }
        return 0;
    }

    public int statFile(String command, Stack<String> options, Stats stats) throws IOException {
        boolean lOption = false;
        boolean sOption = false;
        boolean pOption = false;
        boolean oneOption = false;
        boolean verbose = this.properties.getProperty("verbose", "false").equals("true");

        if (options.peek().startsWith("-")) {
            String option = options.pop();
            for (int i = 1; i < option.length(); i++) {
                if (option.charAt(i) == 'l') {
                    lOption = true;
                } else if (option.charAt(i) == 's') {
                    sOption = true;
                } else if (option.charAt(i) == '1') {
                    oneOption = true;
                } else if (option.charAt(i) == 'p') {
                    pOption = true;
                }
            }
        }

        while (!options.empty()) {
            String fullPath = options.pop();

            String[] tokens = fullPath.split("/", 3);
            String fileSystemName = tokens[1];
            fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

            PathName path = new PathName(fullPath);
            try {
                HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);

                HierarchicalFileSystem.Node node = fileSystem.getNode(path);

                if (node instanceof HierarchicalFileSystem.Directory) {
                    HierarchicalFileSystem.Directory directory = (HierarchicalFileSystem.Directory) node;
                    for (String entryName : directory.list()) {
                        HierarchicalFileSystem.Node child = fileSystem.getNode(path.append(entryName));
                        if (oneOption) {
                            System.out.printf("%s%n", entryName);
                        } else if (lOption) {
                            String d = String.format("%1$tF %1$tT", new java.util.Date(child.lastModified()));
                            if (child instanceof HierarchicalFileSystem.Directory) {
                                System.out.printf("%-25.25s %21d %s %s%n", child.getContentType(), child.length(), d, entryName);
                            } else {
                                System.out.printf("%-25.25s %21d %s %s%n", child.getContentType(), child.length(), d, entryName);
                            }
                        } else if (sOption) {
//                            System.out.printf("%10d %21d %s%n", child.getBlockSize(), child.length(), entryName);
                            System.out.printf("%21d %s%n", child.length(), entryName);
                        } else if (pOption) {

                            System.out.printf("dir props %s%n", child.getClientProperties());
                            OrderedProperties props = child.getClientProperties();
                            for (Object key : props.keySet()) {
                                System.out.printf("%s %s%n", key, props.get(key));
                            }
                        } else {
                            System.out.printf("%s%n", entryName);
                        }
                    }
                } else if (node instanceof HierarchicalFileSystem.File) {
                    HierarchicalFileSystem.File file = (HierarchicalFileSystem.File) node;
                    if (lOption) {
                        String d = String.format("%1$tF %1$tT", new java.util.Date(file.lastModified()));
                        System.out.printf("%-25.25s %21d %s %s%n", file.getContentType(), file.length(), d, path);
                    } else if (sOption) {
                        System.out.printf("%10d %21d %s%n", file.getBlockSize(), file.length(), path);
                    }  else if (pOption) {

                        System.out.printf("props %d%n", file.getClientProperties().size());
                        OrderedProperties props = file.getClientProperties();
                        for (Object key : props.keySet()) {
                            System.out.printf("%s %s%n", key, props.get(key));
                        }
                    } else {
                        System.out.printf("%s%n", path);
                    }
                } else {
                    throw new IOException(String.format("Unknown file kind: %s", node.getClass()));
                }
                stats.addMessage("%s/%s", fileSystemName, path);
            } catch (Exception e) {
                if (verbose) {
                    System.err.printf("%s%n", e.toString());
                    e.printStackTrace(System.err);
                }
                return 1;
            }
        }

        return 0;
    }

    /**
     * <p>
     *
     * Fetches attributes associated with the file given as the last command
     * line argument, returning them in their {@link
     * sunlabs.titan.util.OrderedProperties#store(OutputStream, String)
     * stored} {@code OrderedProperties} form.
     *
     * </p><p>
     *
     * Each attribute to be fetched should be specified as an {@code --attr
     * <name>} pair of command line arguments.
     *
     * </p><p>
     *
     * If no {@code --attr} option is supplied, this method will fetch all of
     * the file's attributes.
     *
     * </p>
     */
    public int getFileAttributes(String command, Stack<String> options, Stats stats) throws IOException {

        Set<String> attrNames = new HashSet<String>();
        gatherNames("--attr", options, attrNames);

        //
        // Handle requests for all attributes.
        //
        if (attrNames.size() == 0)
            attrNames = null;

        //
        // The options stack should now contain one entry:  the full path name
        // of the file whose attributes are desired.
        //
        if (options.size() != 1)
            throw new IllegalArgumentException(
                "getFileAttributes: exactly one file name required");

        String fullPath = options.pop();
        String[] tokens = fullPath.split("/", 3);
        String fileSystemName = tokens[1];
        fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");
        PathName path = new PathName(fullPath);

        try {
            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);
            HierarchicalFileSystem.Node node = fileSystem.getNode(path);

            OrderedProperties attrs = node.getAttributes(attrNames);
            attrs.store(System.out, "");
            return 0;
        } catch (FileException e) {
            throw new IOException(e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * <p>
     *
     * Fetches properties associated with the file given as the last command
     * line argument, returning them in their {@link
     * sunlabs.titan.util.OrderedProperties#store(OutputStream, String)
     * stored} {@code OrderedProperties} form.
     *
     * </p><p>
     *
     * Each property to be fetched should be specified as an {@code --prop
     * <name>} pair of command line arguments.
     *
     * </p><p>
     *
     * If no {@code --prop} option is supplied, this method will fetch all of
     * the file's properties.
     *
     * </p>
     */
    public int getFileProperties(String command, Stack<String> options, Stats stats) throws IOException {

        Set<String> propNames = new HashSet<String>();
        gatherNames("--prop", options, propNames);

        //
        // Handle requests for all properties.
        //
        if (propNames.size() == 0)
            propNames = null;

        //
        // The options stack should now contain one entry:  the full path name
        // of the file whose attributes are desired.
        //
        if (options.size() != 1)
            throw new IllegalArgumentException("getFileAttributes: exactly one file name required");

        String fullPath = options.pop();
        String[] tokens = fullPath.split("/", 3);
        String fileSystemName = tokens[1];
        fullPath = "/" + (tokens.length >= 3 ? tokens[2] : "");
        PathName path = new PathName(fullPath);

        try {
            HierarchicalFileSystem fileSystem = this.factory.mount(fileSystemName);
            HierarchicalFileSystem.Node node = fileSystem.getNode(path);

            OrderedProperties props = node.getClientProperties();
            if (propNames != null) {
                //
                // Strip out properties not named in propNames.
                //
                // XXX: Quadratic algorithm.  But the number of properties is
                //      expected to be small enough in practice that this
                //      shouldn't be a problem.
                //
                Iterator<Object> it = props.keySet().iterator();
                while (it.hasNext()) {
                    Object key = it.next();
                    if (!propNames.contains(key))
                        it.remove();
                }
            }
            props.store(System.out, "");
            return 0;
        } catch (FileException e) {
            throw new IOException(e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }

    //
    // Search the command line contained in the options stack for options
    // named by optionFlag, stripping out each occurrence and its corresponding
    // operand and adding the operands to the operands set.
    //
    private void gatherNames(String optionFlag, Stack<String>options, Set<String> operands) {
        //
        // March through options looking for occurrences of optionFlag,
        // stripping them out and adding their corresponding operands to
        // operands.
        //
        // Note that options contains the command line arguments in reverse
        // order, so that the leftmost argument appears at the highest indexed
        // position.
        //
        int index = options.size() - 1;
        while (index >= 0) {
            String item = options.get(index);
            if (item.equals(optionFlag)) {
                if (index == 0)
                    throw new IllegalArgumentException(String.format("missing operand for %s flag", optionFlag));
                operands.add(options.get(index - 1));
                //
                // Remove the option and its operand, and adjust index to
                // refer to the next argument to be considered.
                //
                options.remove(index);
                options.remove(index - 1);
                index -= 2;
                continue;
            }
            index -= 1;
        }
    }

    public int renameFile(String command, Stack<String> options, Stats stats) throws IOException {
        //
        // At this point, it is perfectly legal to delete a directory with
        // content.  What happens is that all files in that directory are
        // unreferenced, and may cease to be refreshed, unless they are
        // referenced through other directories.  This applies to
        // sub-directories as well.
        //
        try {
            while (!options.empty()) {
                String fromPath = options.pop();
                String[] tokens = fromPath.split("/", 3);
                String fromFileSystemName = tokens[1];
                fromPath = "/" + (tokens.length >= 3 ? tokens[2] : "");

                String toPath = options.pop();
                tokens = toPath.split("/", 3);
                String toFileSystemName = tokens[1];
                toPath = "/" + (tokens.length >= 3 ? tokens[2] : "");
                if (!fromFileSystemName.equals(toFileSystemName)) {
                    System.err.println("Cannot move across filesystems");
                }
                HierarchicalFileSystem fileSystem = this.factory.mount(fromFileSystemName);

                HierarchicalFileSystem.FileName fromName = new PathName(fromPath);
                HierarchicalFileSystem.FileName toName = new PathName(toPath);

                try {
                    fileSystem.renameFile(fromName, toName);
                } catch (FileException e) {
                    System.err.printf("Cannot rename file %s to %s: %s%nn", fromName, toName, e);
                }
            }
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return 1;
        }

        return 0;
    }

    private static String readLine(String prompt) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(System.in);
        BufferedReader stdin = new BufferedReader(inputStreamReader);
        System.out.flush();
        System.err.flush();
        System.out.print(prompt + "> ");
        System.out.flush();
        return stdin.readLine();
    }

    public static int interactive(InetSocketAddress celeste, String identity, String password) throws FileNotFoundException {
        System.out.printf("%s%n%s%n", release, Copyright.miniNotice);
        if (identity == null) {
            System.err.printf("Identity not set.  Use --id <name> or --identity <name> <password>%n");
            return -1;
        }
        if (password == null) {
            System.err.printf("Password not set.  Use --password <name>%n");
            return -1;
        }
        CelesteFs celestefs = new CelesteFs(celeste, null, 300, identity, password);

        Stats stats = new Stats("");
        while (true) {
            try {
                String line = CelesteFs.readLine("celestefs");
                if (line == null)
                    break;
                String[] args = line.trim().split("[ \t\n\r]+");
                Stack<String> options = new Stack<String>();
                for (int i = args.length -1; i >= 0; i--) {
                    options.push(args[i]);
                }
                int status = 0;
                while (!options.empty()) {
                    String command = options.pop();
                    long startTime = System.currentTimeMillis();
                    if (command.equals("help") || command.equals("?")) {
                        System.out.println("celeste [<address>]");
                        System.out.println("create [--attr|--prop <name>=<value>]... <pathname>");
                        System.out.println("getAttributes [-attr name]... <pathname>");
                        System.out.println("getProperties [-prop name]... <pathname>");
                        System.out.println("ls [-ls1] <pathname>");
                        System.out.println("identity name");
                        System.out.println("mkdir[-p] [--attr|--prop <name>=<value>]... <pathname>");
                        System.out.println("mkfs [--attr <name>=<value>]... <root-name>");
                        System.out.println("mkid");
                        System.out.println("password password");
                        System.out.println("pread <pathname> [<offset> [<length>]]");
                        System.out.println("pwrite [--buffer-size <n>] <pathname> [<offset> <fileToWrite>]");
                        System.out.println("write-file <pathname> <offset> <fileToWrite>");
                        System.out.println("write [--buffer-size <n>] <pathname>|- <offset> <fileToWrite>");
                        System.out.println("readdir [-l] <pathname>");
                        System.out.println("rename <from> <to>");
                        System.out.println("rm <pathname>");
                        System.out.println("set-length <pathname> <length>");
                        System.out.println("test [-cdeft] <pathname>");
                        System.out.println("version");
                        System.out.println("verbose [on|off|true|false]");
                    } else if (command.equals("whoami")) {
                        System.out.printf("%s%n", identity);
                        status = 0;
                    } else if (command.equals("celeste")) {
                        try {
                            String address = options.pop();
                            celeste = makeAddress(address);
                        } catch (EmptyStackException e) {
                            System.out.printf("%s%n", celeste);
                        }
                        status = 0;
                    } else if (command.equals("mkfs")) {
                        status = celestefs.mkfs(command, options, stats);
                    } else if (command.equals("mkid")) {
                        status = celestefs.mkid(identity, password, command, options, stats);
                    } else if (command.equals("mkdir")) {
                        status = celestefs.mkdir(command, options, stats);
                    } else if (command.equals("pread")) {
                        status = celestefs.preadFile(command, options, stats);
                    } else if (command.equals("write-file")) {
                        status = celestefs.writeFile(command, options, stats);
                    } else if (command.equals("write")) {
                        status = celestefs.writeFileChunked(command, options, stats);
                    } else if (command.equals("pwrite")) {
                        status = celestefs.pwriteFile(command, options, stats);
                    } else if (command.equals("create")) {
                        status = celestefs.createFile(command, options, stats);
                    } else if (command.equals("getAttributes")) {
                        status = celestefs.getFileAttributes(command, options, stats);
                    } else if (command.equals("getProperties")) {
                        status = celestefs.getFileProperties(command, options, stats);
                    } else if (command.equals("ls") ) {
                        status = celestefs.statFile(command, options, stats);
                    } else if (command.equals("rm") ) {
                        status = celestefs.deleteFile(command, options, stats);
                    } else if (command.equals("readdir") ) {
                        status = celestefs.readdir(command, options, stats);
                    } else if (command.equals("rename") ) {
                        status = celestefs.renameFile(command, options, stats);
                    } else if (command.equals("test") ) {
                        status = celestefs.testFile(command, options, stats);
                    } else if (command.equals("set-length") ) {
                        status = celestefs.setFileLength(command, options, stats);
                    } else if (command.equals("identity") || command.equals("id") ) {
                        try {
                            identity = options.pop();
                            password = options.pop();
                            celestefs.close();
                            celestefs = new CelesteFs(celeste, null, 300, identity, password);
                        } catch (EmptyStackException e) {
                            System.out.printf("%s%n", identity);
                        }
                    } else if (command.equals("verbose")) {
                        try {
                            String onOff = options.pop();
                            celestefs.properties.setProperty("verbose", (onOff.equals("on") || onOff.equals("true"))? "true" : "false");
                        } catch (EmptyStackException e) {
                            System.out.printf("verbose %s%n", celestefs.properties.getProperty("verbose"));
                        }
                    } else {
                        System.err.printf("unknown command %s: Use help for help%n", command);
                    }

                    System.out.printf("%1$tF %1$tT %1$tZ [%2$.2fs] %3$-15.15s %4$s %5$s%n",
                            System.currentTimeMillis(),
                            (System.currentTimeMillis() - startTime) / 1000.0,
                            command,
                            stats.getMessage(),
                            (status == 0) ? "OK" : String.format("ERROR %d %s", status, stats.getMessage()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println(e);
            }
        }
        return 0;
    }

    public static void printf(String format, Object... args) {
        System.err.printf("%s: ",
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()));
        System.err.printf(format, args);
    }

    public static void println(String s) {
        System.err.println(DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(new Date()) + ": " + s);
    }

    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }


    public static class Stats {
        private String message;

        public Stats(String s, Object...args) {
            this.message = String.format(s, args);
        }

        public String setMessage(String s, Object...args) {
            this.message = String.format(s, args);
            return this.message;
        }

        public String addMessage(String s, Object...args) {
            this.message += String.format(s, args);
            return this.message;
        }

        public String getMessage() {
            return this.message;
        }
    }

    public static void main(String[] args) {
        String celesteAddress = "127.0.0.1:14000";
        boolean verboseFlag = false;
        long timeOut = 300;
        boolean noError = false;  // If true, don't exit with an error status.

        try {
            String identity = null;
            String password = null;

            Stack<String> options = new Stack<String>();
            for (int i = args.length -1; i >= 0; i--) {
                    options.push(args[i]);
            }

            while (!options.empty()) {
                String option = options.pop();
                if (option.equals("--celeste-address")) {
                    celesteAddress = options.pop();
                } else if (option.equals("--timeout")) {
                    timeOut = Integer.parseInt(options.pop());
                } else if (option.equals("--replication")) {
                    System.err.printf("WARNING: --replication is deprecated.  Use --attr %s=params%n", FileAttributes.Names.REPLICATION_PARAMETERS_NAME);
                    System.exit(-1);
                } else if (option.equals("--identity")) {
                    identity = options.pop();
                    password = options.pop();
                } else if (option.equals("--id")) {
                    identity = options.pop();
                } else if (option.equals("--password")) {
                    password = options.pop();
                } else if (option.equals("--verbose")) {
                    verboseFlag = true;
                } else if (option.equals("--no-error")) {
                    noError = true;
                } else if (option.equals("--help") ) {
                    System.out.printf("%s\n%s\n", release, Copyright.miniNotice);
                    System.out.println("Usage:");
                    System.out.printf(" [--celeste-address <address:port> (%s)]  [--timeout <seconds> (%d)] %n", celesteAddress, timeOut);
                    System.out.printf(" --identity <identity> <password>%n");
                    System.out.printf(" --id <identity>%n");
                    System.out.printf(" --password <password>%n");
                    System.out.printf(" --no-error%n");

                    System.out.println(" [create [--attr|--prop <name>=<value>]... <pathname>]");
                    System.out.println(" [getAttributes [-attr name]... <pathname>]");
                    System.out.println(" [getProperties [-prop name]... <pathname>]");
                    System.out.println(" [ls [-ls] <pathname>]");
                    System.out.println(" [mkdir[-p] [--attr|--prop <name>=<value>]... <pathname>]");
                    System.out.println(" [mkfs [--attr <name>=<value>]... <root-name>]");
                    System.out.println(" [mkid]");
                    System.out.println(" [pread <pathname> [<offset> [<length>]]]");
                    System.out.println(" [pwrite [--buffer-size <n>] <pathname> <offset> <fileToWrite>]");
                    System.out.println(" [write-file <pathname> <offset> <fileToWrite>]");
                    System.out.println(" [write [--buffer-size <n>] <pathname>|- <offset> <fileToWrite>]");
                    System.out.println(" [readdir [-l] <pathname>]");
                    System.out.println(" [rename <from> <to>]");
                    System.out.println(" [run-extension <pathname> <jar-files> <args>]");
                    System.out.println(" [rm <pathname>]");
                    System.out.println(" [set-length <pathname> <length>]");
                    System.out.println(" [test [[-edt] <pathname>]]| -c");
                    System.out.println(" [version]");
                    System.exit(0);
                } else {
                    options.push(option);
                    break;
                }
            }

            InetSocketAddress celeste = makeAddress(celesteAddress);

            Stats stats = new Stats("");

            if (options.empty()) {
                System.exit(interactive(celeste, identity, password));
            }

            CelesteFs celestefs = new CelesteFs(celeste, null, timeOut, identity, password);
            celestefs.properties.setProperty("verbose", verboseFlag ? "true" : "false");

            int status = 0;

            while (!options.empty()) {
                String command = options.pop();
                long startTime = System.currentTimeMillis();
                if (command.equals("mkfs")) {
                    status = celestefs.mkfs(command, options, stats);
                } else if (command.equals("mkid")) {
                    status = celestefs.mkid(identity, password, command, options, stats);
                } else if (command.equals("mkdir")) {
                    status = celestefs.mkdir(command, options, stats);
                } else if (command.equals("pread")) {
                    status = celestefs.preadFile(command, options, stats);
                } else if (command.equals("write-file")) {
                    status = celestefs.writeFile(command, options, stats);
                } else if (command.equals("write")) {
                    status = celestefs.writeFileChunked(command, options, stats);
                } else if (command.equals("pwrite")) {
                    status = celestefs.pwriteFile(command, options, stats);
                } else if (command.equals("create")) {
                    status = celestefs.createFile(command, options, stats);
                } else if (command.equals("getAttributes")) {
                    status = celestefs.getFileAttributes(command, options, stats);
                } else if (command.equals("getProperties")) {
                    status = celestefs.getFileProperties(command, options, stats);
                } else if (command.equals("ls")) {
                    status = celestefs.statFile(command, options, stats);
                } else if (command.equals("rm")) {
                    status = celestefs.deleteFile(command, options, stats);
                } else if (command.equals("readdir")) {
                    status = celestefs.readdir(command, options, stats);
                } else if (command.equals("rename") ) {
                    status = celestefs.renameFile(command, options, stats);
                } else if (command.equals("run-extension") ) {
                    status = celestefs.runExtension(command, options, stats);
                } else if (command.equals("test") ) {
                    status = celestefs.testFile(command, options, stats);
                } else if (command.equals("set-length") ) {
                    status = celestefs.setFileLength(command, options, stats);
                } else {
                    System.err.printf("unknown command %s: Use --help for help.%n", command);
                    System.exit(1);
                }
                System.err.printf("%1$tF %1$tT %1$tZ [%2$.2fs] %3$-15.15s %4$s %5$s%n",
                        System.currentTimeMillis(),
                                (System.currentTimeMillis() - startTime) / 1000.0,
                                command,
                                stats.getMessage(),
                                (noError && status != 0) ? "(ignoring error)" : "");
            }

            System.exit(noError ? 0 : status);
        } catch (java.net.ConnectException e) {
            System.err.printf("%s %s%n", celesteAddress, e.toString());
            System.exit(-1);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(-1);
        }

        System.exit(0);
    }
}
