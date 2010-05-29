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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import sunlabs.asdf.util.Time;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.Copyright;
import sunlabs.beehive.Release;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.node.services.api.BeehiveExtension;
import sunlabs.beehive.util.ExtentBufferStreamer;
import sunlabs.beehive.util.OrderedProperties;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.operation.CreateFileOperation;
import sunlabs.celeste.client.operation.DeleteFileOperation;
import sunlabs.celeste.client.operation.ExtensibleOperation;
import sunlabs.celeste.client.operation.InspectFileOperation;
import sunlabs.celeste.client.operation.InspectLockOperation;
import sunlabs.celeste.client.operation.LockFileOperation;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.NewNameSpaceOperation;
import sunlabs.celeste.client.operation.ProbeOperation;
import sunlabs.celeste.client.operation.ReadFileOperation;
import sunlabs.celeste.client.operation.ReadProfileOperation;
import sunlabs.celeste.client.operation.SetACLOperation;
import sunlabs.celeste.client.operation.SetFileLengthOperation;
import sunlabs.celeste.client.operation.SetOwnerAndGroupOperation;
import sunlabs.celeste.client.operation.UnlockFileOperation;
import sunlabs.celeste.client.operation.WriteFileOperation;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;

/**
 * This is a command line Celeste client interface.
 */
public class CelesteSh {
    private static BeehiveObjectId parseObjectId(String value) {
        if (value.equals("null"))
            return null;

        if (BeehiveObjectId.IsValid(value)) {
            return new BeehiveObjectId(value);
        }
        return new BeehiveObjectId(value.getBytes());
    }
    
    private static void writeNamedFile(String fileName, byte[] data)
    throws IOException {
        if (data != null) {
            OutputStream fout = null;

            if (fileName.equals("-")) {
                fout = System.out;
                fout.write(data);
                return;
            }

            try {
                fout = new FileOutputStream(fileName);
                fout.write(data);
            } catch (IOException e) {
                throw e;
            } finally {
                if (fout != null) try { fout.close(); }
                catch (IOException ignore) { }
            }
        }
    }

    private static void writeNamedFile(String fileName, ExtentBufferStreamer data)
    throws IOException {
        if (data != null) {

            if (fileName.equals("-")) {
                data.streamExtentBufferMap(System.out);
                return;
            }

            OutputStream fout = null;
            try {
                fout = new FileOutputStream(fileName);
                data.streamExtentBufferMap(fout);
                fout.flush();
            } catch (IOException e) {
                throw e;
            } finally {
                if (fout != null) try { fout.close(); }
                catch (IOException ignore) { }
            }
        }
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

    private CelesteAPI celeste;
    protected Properties properties;

    public CelesteSh(CelesteAPI celeste) {
        this.celeste = celeste;
        this.properties = new Properties();
        this.properties.setProperty("verbose", "false");
    }

    private static Credential getCredential(CelesteAPI celeste, BeehiveObjectId credentialId) throws IOException, Credential.Exception, Exception {
        return celeste.readCredential(new ReadProfileOperation(credentialId)).get(Profile_.class);
    }

    /**
     * Open a file and return its contents as a String.
     * Note that the entire file will be read into a byte array in memory.
     * A large file can consume all available memory.
     *
     * @param name  the name of the file to be opened
     *
     * @return  a String instance containing the contents of the file
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
            byte [] b = new byte[(int) file.length()];
            fis = new FileInputStream(file);

            int nread;

            while (fis.available() > 0) {
                nread = fis.read(b);
                out.write(b, 0 , nread);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw e;
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ignore) { }
        }
    }

    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }



    // This is actually just creating a credential for the name space.
    public int configure(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            System.out.println("configure");
            return -1;
        }
        try {
//            String credentialName = options.pop();
//            String passphrase = options.pop();
//
//            Profile_ credential = new Profile_(credentialName, passphrase.toCharArray());
            ProbeOperation operation = new ProbeOperation();
//            Credential.Signature signature = credential.sign(passphrase.toCharArray(), operation.getId());

            this.celeste.probe(operation);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    /**
     * Create a file.
     *
     */
    public int createFile(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("create-file", "[--unsigned-writes | --signed-writes] <requestor> <requestorPassword> <namespace> <password> <file> <owner> <group> <deleteToken> <replication-parameters> <bObjectSize> <timeToLive> [<data-FileName> <metaData-FileName>]");
            return -1;
        }

        boolean writesMustBeSigned = true;

        try {
            while (options.peek().startsWith("-")) {
                String option = options.pop();
                if (option.equals("--unsigned-writes")) {
                    writesMustBeSigned = false;
                } else if (option.equals("--signed-writes")) {
                    writesMustBeSigned = true;
                } else {
                    System.err.printf("Unknown option: %s%n", option);
                    return -1;
                }
            }
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            String nameSpacePassword = options.pop();
            BeehiveObjectId fileId = parseObjectId(options.pop());
            BeehiveObjectId ownerId = parseObjectId(options.pop());
            BeehiveObjectId groupId = parseObjectId(options.pop());
            String userSuppliedData = options.pop();
            String replicationParams = options.pop();
            int bObjectSize = Integer.parseInt(options.pop());
            long timeToLive = Long.parseLong(options.pop());

            Credential clientCredential = CelesteSh.getCredential(celeste, requestorId);
            Credential nameSpaceCredential = CelesteSh.getCredential(celeste, nameSpaceId);

            BeehiveObjectId deleteToken = new BeehiveObjectId((nameSpaceId.toString() + fileId.toString() + userSuppliedData).getBytes());
            BeehiveObjectId deleteTokenId = deleteToken.getObjectId();

            ClientMetaData clientMetaData = new ClientMetaData();

            CreateFileOperation operation = new CreateFileOperation(
                    requestorId,
                    new FileIdentifier(nameSpaceCredential.getObjectId(), fileId),
                    deleteTokenId,
                    timeToLive,
                    bObjectSize,
                    replicationParams,
                    clientMetaData,
                    ownerId, groupId,
                    (CelesteACL) null,
                    writesMustBeSigned);

            Credential.Signature signature = clientCredential.sign(requestorPassword.toCharArray(), operation.getId(), clientMetaData.getId());

            OrderedProperties reply = this.celeste.createFile(operation, signature);

            if (!options.empty()) {
                try {
                    writeNamedFile(options.pop(), reply.toByteArray());
                } catch (Exception e) {
                    System.err.printf("%s%n", e.toString());
                    if (this.properties.getProperty("verbose", "false").equals("true")) {
                        e.printStackTrace();
                    }
                    return -1;
                }
            }

            return 0;
        } catch (CelesteException e) {
            e.printStackTrace();
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        } catch (Credential.Exception e) {
            e.printStackTrace();
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        } catch (Exception e) {
            e.printStackTrace();
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    // This is actually just creating a credential for the name space.
    public int newNameSpace(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("new-namespace", "<name> <password> [<replication-parameters>]");
            return -1;
        }
        try {
            String nameSpaceName = options.pop();
            String passphrase = options.pop();
            String replicationParams = "Credential.Replication.Store=2";
            if (!options.empty()) {
                replicationParams = options.pop();
            }

            Profile_ credential = new Profile_(nameSpaceName, passphrase.toCharArray());
            NewNameSpaceOperation operation = new NewNameSpaceOperation(credential.getObjectId(), BeehiveObjectId.ZERO, replicationParams);
            Credential.Signature signature = credential.sign(passphrase.toCharArray(), operation.getId());

            this.celeste.newNameSpace(operation, signature, credential);
            stats.addMessage("%s", nameSpaceName);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    public int newCredential(String command, Stack<String> options, Stats stats)
        throws IOException, Credential.Exception {

        if (command == null) {
            Usage("new-credential", "<name> <password> [<repliation-parameters>]");
            return -1;
        }
        try {
            String credentialName = options.pop();
            String passphrase = options.pop();
            String replicationParams = "Credential.Replication.Store=2";
            if (!options.empty()) {
                replicationParams = options.pop();
            }

            Profile_ credential = new Profile_(credentialName, passphrase.toCharArray());
            NewCredentialOperation operation = new NewCredentialOperation(credential.getObjectId(), BeehiveObjectId.ZERO, replicationParams);
            Credential.Signature signature = credential.sign(passphrase.toCharArray(), operation.getId());

            this.celeste.newCredential(operation, signature, credential);
            stats.addMessage("%s", credentialName);
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

//    /**
//     * Create a credential from a Java KeyStore file.
//     *
//     * @throws IOException
//     * @throws Profile_.Exception
//     */
//    public int keyStoreCredential(String command, Stack<String> options) throws IOException, Profile_.Exception {
//        if (command == null) {
//            System.out.println("keystore-credential <keystore-FileName> <keystore-Password> [<repliation-parameters>]");
//            return -1;
//        }
//        try {
//            File file = new File(options.pop());
//            String passphrase = options.pop();
//            String replicationParams = "AObject.Replication.Store=2;VObject.Replication.Store=2;BObject.Replication.Store=2;AObjectVersionMap.Params=1,1";
//            if (!options.empty()) {
//                replicationParams = options.pop();
//            }
//
//            Profile_ profile = new Profile_(file, passphrase.toCharArray());
//            NewCredentialOperation operation = new NewCredentialOperation(profile.getObjectId(), BeehiveObjectId.ZERO, replicationParams);
//            CredentialOperations.Signature signature = profile.sign(passphrase.toCharArray(), operation.getId());
//
//            StorageMessage reply = this.celeste.newCredential(operation, signature, profile);
//
//            if (reply.getStatus().isSuccessful()) {
//                return 0;
//            }
//            System.out.printf("%s%n", reply.getStatus());
//            return reply.getStatus().toEncoding();
//        } catch (Exception e) {
//            System.err.printf("%s%n", e.toString());
//            if (this.properties.getProperty("verbose", "false").equals("true")) {
//                e.printStackTrace();
//            }
//            return -1;
//        }
//    }

    public  int readCurrentFile(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("read-file", "<requestor> <requestorPassword> <namespace> <fileId> [<offset> [<length> [<result-data result-metadata]]] (deprecated)");
            return -1;
        }
        String metaDataOutput = null;
        String output = "-";
        long offset = 0;
        int length = -1;
        try {
            BeehiveObjectId readerId = parseObjectId(options.pop());
            String readerPassword =options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            if (!options.empty()) {
                offset = Long.parseLong(options.pop());
            }

            if (!options.empty()) {
                length = Integer.parseInt(options.pop());
            }

            if (!options.empty()) {
                output = options.pop();
            }
            if (!options.empty()) {
                metaDataOutput = options.pop();
            }

            Credential readerCredential = CelesteSh.getCredential(this.celeste, readerId);
            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);

            ReadFileOperation operation = new ReadFileOperation(fileIdentifier, readerId, offset, length);

            Credential.Signature signature = readerCredential.sign(readerPassword.toCharArray(), operation.getId());

            ResponseMessage reply = this.celeste.readFile(operation, signature);

            ExtentBufferStreamer eb = reply.get(ExtentBufferStreamer.class);

            try {
                writeNamedFile(output, eb);
            } catch (IOException e) {
                System.err.println(output + " " + e.toString());
            }

            try {
                if (metaDataOutput != null) {
                    if (reply.getMetadata() != null) {
                        reply.getMetadata().store(new FileOutputStream(metaDataOutput), "");
                    }
                }
            } catch (IOException e) {
                System.err.println(metaDataOutput + " " + e.toString());
                return -1;
            }
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    public  int readCurrentFileChunked(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            System.out.println("read <requestor> <requestorPassword> <namespace> <fileId> [<factor=1> [<offset=0> [<length=eof> [<data-file]]]]");
            return -1;
        }
        long offset = 0;
        long length = -1;
        int blockFactor = 1;
        boolean verbose = false;
        String dataOutputFileName = "-";

        if (this.properties.getProperty("verbose", "false").equals("true")) {
            verbose = true;
        }

        try {
            BeehiveObjectId readerId = parseObjectId(options.pop());
            String readerPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());

            if (!options.empty()) {
                blockFactor = Integer.parseInt(options.pop());
                if (!options.empty()) {
                    offset = Long.parseLong(options.pop());

                    if (!options.empty()) {
                        length = Integer.parseInt(options.pop());
                        if (!options.empty()) {
                            dataOutputFileName = options.pop();
                        }
                    }
                }
            }

            Credential readerCredential = CelesteSh.getCredential(this.celeste, readerId);
            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);
            
            InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, readerId);
            ResponseMessage a = this.celeste.inspectFile(inspect);
            OrderedProperties metadata = a.getMetadata();

            long fileSize = metadata.getPropertyAsLong(CelesteAPI.FILE_SIZE_NAME, -1);
            long blockSize = metadata.getPropertyAsLong(CelesteAPI.DEFAULTBOBJECTSIZE_NAME, 1024*1024); // XXX Use the default size set in createFile()

            int bufferSize = (int) Math.min(blockSize * blockFactor, Integer.MAX_VALUE);

            if (length < 0)
            	length = fileSize;
            BeehiveObjectId versionObjectId = null;

            OutputStream fout = dataOutputFileName.equals("-") ? System.out : new FileOutputStream(dataOutputFileName);

            for (long index = offset; index < length; /**/) {
            	// An optimisation here would be to read partial blocks until the index is aligned with a block boundary.
            	int toRead = (int) Math.min((length - index), bufferSize);

            	ReadFileOperation operation = new ReadFileOperation(fileIdentifier, readerId, versionObjectId, index, toRead);

            	Credential.Signature signature = readerCredential.sign(readerPassword.toCharArray(), operation.getId());

            	ResponseMessage reply = this.celeste.readFile(operation, signature);
            	versionObjectId = reply.getMetadata().getPropertyAsObjectId(CelesteAPI.VOBJECTID_NAME, null);

            	ExtentBufferStreamer eb = reply.get(ExtentBufferStreamer.class);

            	eb.streamExtentBufferMap(fout);

            	index += eb.getLength();
            }
            stats.addMessage("%d bytes", length);
            return 0;

        } catch (Exception e) {
            stats.addMessage("%s", e.toString());
        	System.err.printf("%s%n", e.toString());
        	if (verbose) {
        		e.printStackTrace();
        	}
        	return -1;
        }
    }

    /**
     * Run a {@link BeehiveExtension} in the Celeste node.
     * <p>
     * This is still experimental and subject to significant changes.
     * </p>
     */
    public int runExtension(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("run-extension", "<requestor> <requestorPassword> <jarfile[,jarFile]*> <namespace> <fileId> [<args>]");
            return -1;
        }

        try {
            BeehiveObjectId readerId = parseObjectId(options.pop());
            String readerPassword = options.pop();
            String[] urls = options.pop().split(",");
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            
        	List<String> argList = new LinkedList<String>();
            while (!options.empty()) {
            	argList.add(options.pop());
            }

            Credential readerCredential = CelesteSh.getCredential(this.celeste, readerId);

            // Get the initial "driver" class out of the jar file and instantiate an object.

            URL[] jarFileURLs = new URL[urls.length];
            for (int i = 0; i < urls.length; i++) {
                jarFileURLs[i] = new URL(urls[i]);
            }

            ExtensibleOperation operation = new ExtensibleOperation(new FileIdentifier(nameSpaceId, fileId), readerId, jarFileURLs, argList.toArray(new String[0]));

            Credential.Signature signature = readerCredential.sign(readerPassword.toCharArray(), operation.getId());

            ResponseMessage reply = this.celeste.runExtension(operation, signature, null);

            Serializable result = reply.get(Serializable.class);
            System.out.printf("%s%n", result.toString());
            return 0;
        } catch (CelesteException e) {
            System.err.printf("Exception %s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        } catch (Credential.Exception e) {
            System.err.printf("Exception %s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        } catch (Exception e) {
            System.err.printf("Exception %s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    /**
     *
     * @throws IOException
     */
    public  int readFileVersion(String command, Stack<String> options, Stats stats) throws IOException {
        if (command == null) {
            Usage("read-file-version", "<requestor> <requestorPassword> <namespace> <file> <versionId> <offset> <length> <data-FileName> <metadata-FileName>");
            return -1;
        }
        try {
            BeehiveObjectId readerId = parseObjectId(options.pop());
            String readerPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            BeehiveObjectId vObjectId = parseObjectId(options.pop());
            long offset = Long.parseLong(options.pop());
            int length = Integer.parseInt(options.pop());
            String output = options.pop();
            String metaDataOutput = options.pop();

            Credential readerCredential = CelesteSh.getCredential(this.celeste, readerId);
            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);

            ReadFileOperation operation = new ReadFileOperation(fileIdentifier, readerId, vObjectId, offset, length);
            Credential.Signature signature = readerCredential.sign(readerPassword.toCharArray(), operation.getId());

            ResponseMessage reply = this.celeste.readFile(operation, signature);

            ExtentBufferStreamer eb = reply.get(ExtentBufferStreamer.class);
            byte[] data = eb.render();

            try {
                writeNamedFile(output, data);
            } catch (IOException e) {
                System.err.println(output + " " + e.toString());
            }

            try {
                if (reply.getMetadata() != null) {
                    FileOutputStream fout = null;
                    try {
                        fout = new FileOutputStream(metaDataOutput);
                        reply.getMetadata().store(fout, "");
                    } finally {
                        if (fout != null)
                            fout.close();
                    }
                }
            } catch (IOException e) {
                System.err.println(metaDataOutput + " " + e.toString());
            }
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    /**
     *
     */
    public int writeFile(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("write-file", "<requestor> <requestorPassword> <namespace> <file> <start> [<result>|- [<metaData-FileName>]] (deprecated)");
            return -1;
        }
        String fileName = "-";
        String metaDataFileName = null;
        boolean signatureMustIncludeData = true;

        try {
            BeehiveObjectId clientId = parseObjectId(options.pop());
            String passphrase = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            long startByte = Long.parseLong(options.pop());

            if (!options.empty()) {
                fileName = options.pop();
                if (!options.empty()) {
                    metaDataFileName = options.pop();
                }
            }
            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);

            // retrieve the latest known VObjectId -- needed to create a correct signature
            InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, clientId);

            ResponseMessage a = this.celeste.inspectFile(inspect);
            OrderedProperties metadata = a.getMetadata();
            BeehiveObjectId latestVObjectId = new BeehiveObjectId(metadata.getProperty(CelesteAPI.VOBJECTID_NAME));
            signatureMustIncludeData = Boolean.parseBoolean(metadata.getProperty(CelesteAPI.WRITE_SIGNATURE_INCLUDES_DATA, "true"));

            Credential clientCredential = CelesteSh.getCredential(this.celeste, clientId);

            ClientMetaData woc = new ClientMetaData();

            ByteBuffer data = ByteBuffer.wrap(readNamedFile(fileName));

            WriteFileOperation operation = new WriteFileOperation(fileIdentifier, clientId, latestVObjectId, woc, startByte, data.remaining());

            Credential.Signature signature;
            if (signatureMustIncludeData) {
                signature = clientCredential.sign(passphrase.toCharArray(), operation.getId(), new BeehiveObjectId(data));
            } else {
                signature = clientCredential.sign(passphrase.toCharArray(), operation.getId());
            }

            OrderedProperties reply = this.celeste.writeFile(operation, signature, data);

            if (metaDataFileName != null) {
                try {
                    reply.store(new FileOutputStream(metaDataFileName), "");
                } catch (IOException e) {
                    System.err.println(metaDataFileName + " " + e.toString());
                }
            }

            return 0;
        } catch (Exception e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }
    /**
    *
    */
  public int writeFileChunked(String command, Stack<String> options, Stats stats) {
      if (command == null) {
          Usage("write", "<requestor> <requestorPassword> <namespace> <fileName> <startOffset> [<result>|- [<metaData-FileName>]]");
          return -1;
      }
      String fileName = "-";

      try {
          BeehiveObjectId requestorId = parseObjectId(options.pop());
          String passphrase = options.pop();
          BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
          BeehiveObjectId fileId = parseObjectId(options.pop());
          long startByte = Long.parseLong(options.pop());
          int chunkFactor = 2;

          if (!options.empty()) {
              fileName = options.pop();
          }
          FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);
          // retrieve the latest known VObjectId -- needed to create a correct signature
          InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, requestorId);
          ResponseMessage inspection = this.celeste.inspectFile(inspect);
          OrderedProperties metadata = inspection.getMetadata();
          BeehiveObjectId latestVObjectId = new BeehiveObjectId(metadata.getProperty(CelesteAPI.VOBJECTID_NAME));

          boolean signatureMustIncludeData = Boolean.parseBoolean(metadata.getProperty(CelesteAPI.WRITE_SIGNATURE_INCLUDES_DATA, "true"));

          Credential clientCredential = CelesteSh.getCredential(this.celeste, requestorId);
          int bObjectSize = (int) metadata.getPropertyAsLong(CelesteAPI.DEFAULTBOBJECTSIZE_NAME, 8*1024*1024);

          ClientMetaData woc = new ClientMetaData();

          byte[] buffer = new byte[bObjectSize*chunkFactor];
          InputStream fin = fileName.equals("-") ? System.in : new FileInputStream(fileName);

          long byteCount = 0;

          while (true) {
       	   int nread;
       	   nread = readBuffer(fin, buffer);
       	   if (nread < 1)
       		   break;
   		   byteCount += nread;

              ByteBuffer data = ByteBuffer.wrap(buffer, 0, nread);

              WriteFileOperation operation = new WriteFileOperation(fileIdentifier, requestorId, latestVObjectId, woc, startByte, data.remaining());

              Credential.Signature signature;
              if (signatureMustIncludeData) {
                  signature = clientCredential.sign(passphrase.toCharArray(), operation.getId(), new BeehiveObjectId(data));
              } else {
                  signature = clientCredential.sign(passphrase.toCharArray(), operation.getId());
              }
              OrderedProperties reply = this.celeste.writeFile(operation, signature, data);
              latestVObjectId = new BeehiveObjectId(reply.getProperty(CelesteAPI.VOBJECTID_NAME));
              startByte += nread;
          }

          stats.addMessage(String.format("%d bytes", byteCount));
          return 0;
      } catch (Exception e) {
          stats.addMessage(String.format("%s", e.toString()));
          System.err.printf("%s%n", e.toString());
          if (this.properties.getProperty("verbose", "false").equals("true")) {
              e.printStackTrace();
          }
          return -1;
      }
  }
  
    /**
     *
     */
   public int writeFileChunked2(String command, Stack<String> options, Stats stats) {
       if (command == null) {
           Usage("write", "<requestor> <requestorPassword> <namespace> <fileName> <startOffset> <bufferFactor> [<result>|-]");
           return -1;
       }
       String fileName = "-";

       try {
//    	   String metaDataFileName = null;
           BeehiveObjectId requestorId = parseObjectId(options.pop());
           String passphrase = options.pop();
           BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
           BeehiveObjectId fileId = parseObjectId(options.pop());
           long startByte = Long.parseLong(options.pop());
           int bufferFactor = Integer.parseInt(options.pop());

           if (!options.empty()) {
               fileName = options.pop();
//               if (!options.empty()) {
//            	   metaDataFileName = options.pop();
//               }
           }
           FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);
           
           // retrieve the latest known VObjectId -- needed to create a correct signature
           InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, requestorId);
           ResponseMessage inspection = this.celeste.inspectFile(inspect);
           OrderedProperties metadata = inspection.getMetadata();
           BeehiveObjectId latestVObjectId = new BeehiveObjectId(metadata.getProperty(CelesteAPI.VOBJECTID_NAME));

           boolean signatureMustIncludeData = Boolean.parseBoolean(metadata.getProperty(CelesteAPI.WRITE_SIGNATURE_INCLUDES_DATA, "true"));

           Credential clientCredential = CelesteSh.getCredential(this.celeste, requestorId);
           int bObjectSize = (int) metadata.getPropertyAsLong(CelesteAPI.DEFAULTBOBJECTSIZE_NAME, 8*1024*1024);

           ClientMetaData woc = new ClientMetaData();

           byte[] buffer = new byte[bObjectSize*bufferFactor];
           InputStream fin = fileName.equals("-") ? System.in : new FileInputStream(fileName);

           long byteCount = 0;

           while (true) {
        	   int nread = readBuffer(fin, buffer);
        	   if (nread < 1)
        		   break;
    		   byteCount += nread;

               ByteBuffer data = ByteBuffer.wrap(buffer, 0, nread);

               WriteFileOperation operation = new WriteFileOperation(fileIdentifier, requestorId, latestVObjectId, woc, startByte, data.remaining());

               Credential.Signature signature;
               if (signatureMustIncludeData) {
                   signature = clientCredential.sign(passphrase.toCharArray(), operation.getId(), new BeehiveObjectId(data));
               } else {
                   signature = clientCredential.sign(passphrase.toCharArray(), operation.getId());
               }
               OrderedProperties reply = this.celeste.writeFile(operation, signature, data);
               latestVObjectId = new BeehiveObjectId(reply.getProperty(CelesteAPI.VOBJECTID_NAME));
               startByte += nread;
           }

           stats.addMessage(String.format("%d bytes", byteCount));
           return 0;
       } catch (Exception e) {
           System.err.printf("%s%n", e.toString());
           if (this.properties.getProperty("verbose", "false").equals("true")) {
               e.printStackTrace();
           }
           return -1;
       }
   }

   /**
    * Read from the given InputStream placing bytes in the given byte buffer.
    * <p>
    * Fill the byte buffer reading from the InputStream until EOF or until the buffer is full.
    * This avoids partial reads of buffers typically encountered when the InputStream is reading
    * from an IPC channel (UNIX pipe, for example) where data in the channel is readable in chunks.
    * </p>
    * @param in the {@code InputStream} to read
    * @param buffer the byte buffer containing the read data
    * @return the number of bytes read
    * @throws IOException if the underlying {@code InputStream} throws {@code IOException}.
    */
   public static int readBuffer(InputStream in, byte[] buffer) throws IOException {
	   int bytesRead = 0;

	   for (;;) {
		   int bytesToRead = buffer.length - bytesRead;
		   if (bytesToRead == 0) {
			   return bytesRead;
		   }
		   int nread = in.read(buffer, bytesRead, bytesToRead);
		   if (nread < 1) {
			   return bytesRead;
		   }
		   bytesRead += nread;		   
	   }
   }

    public int deleteFile(String command, Stack<String> options, Stats stats) throws IOException {
        if (command == null) {
            Usage("delete-file", "<requestor> <requestorPassword>  <namespace> <file> <deleteToken> <timeToLive>");
            return -1;
        }
        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            String userSuppliedData = options.pop();
            long timeToLive = Long.parseLong(options.pop());

            BeehiveObjectId deleteToken = new BeehiveObjectId((nameSpaceId.toString() + fileId.toString() + userSuppliedData).getBytes());

            DeleteFileOperation operation = new DeleteFileOperation(new FileIdentifier(nameSpaceId, fileId), requestorId, deleteToken, timeToLive);

            Credential requestorCredential = CelesteSh.getCredential(celeste, requestorId);

            Credential.Signature sig = requestorCredential.sign(requestorPassword.toCharArray(), operation.getId());

            Boolean reply = this.celeste.deleteFile(operation, sig);

            return reply.booleanValue() == true ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    /**
     *
     */
    public int inspectFile(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("inspect-file", "<requestor> <requestorPassword> <namespace> <file> <versionId> <client-metadata> <celeste-metadata>");
            return -1;
        }

        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            BeehiveObjectId vObjectId = parseObjectId(options.pop());
            String clientMetaDataFile = options.pop();
            String celesteMetaDataFile = options.pop();

            InspectFileOperation operation = new InspectFileOperation(new FileIdentifier(nameSpaceId, fileId), requestorId, vObjectId);

            ResponseMessage reply = this.celeste.inspectFile(operation);

            ClientMetaData cmd = reply.get(ClientMetaData.class);
            if (cmd != null) {
                writeNamedFile(clientMetaDataFile, cmd.getContext());
            }
            if (celesteMetaDataFile.equals("-")) {
                reply.getMetadata().store(System.out, "");
            } else {
                reply.getMetadata().store(new FileOutputStream(celesteMetaDataFile), "");
            }
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }
    
    /**
     *
     */
    public int inspectLock(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("inspect-lock", "<requestor> <requestorPassword> <namespace> <file> <output-file>");
            return -1;
        }

        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());

            InspectLockOperation operation = new InspectLockOperation(new FileIdentifier(nameSpaceId, fileId), requestorId);

            ResponseMessage reply = this.celeste.inspectLock(operation);

            AObjectVersionMapAPI.Lock lock = reply.get(AObjectVersionMapAPI.Lock.class);
            if (lock != null) {
                System.out.printf("%s%n", lock.toString());
            } else {
                System.out.printf("No lock%n");
            }
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    /**
     *
     */
    public int lockFile(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("lock-file", "<requestor> <requestorPassword> <namespace> <file> <lock-token> [<result-metadata>]");
            return -1;
        }
        String celesteMetaDataFile = null;

        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            
            String token = options.pop();
            if (token.compareTo("null") == 0)
                token = null;
            if (!options.isEmpty()) {
                celesteMetaDataFile = options.pop();
            }

            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);

            InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, requestorId);
            ResponseMessage inspection = this.celeste.inspectFile(inspect);
            OrderedProperties metadata = inspection.getMetadata();
            BeehiveObjectId latestVObjectId = new BeehiveObjectId(metadata.getProperty(CelesteAPI.VOBJECTID_NAME));

            Credential readerCredential = CelesteSh.getCredential(this.celeste, requestorId);
            LockFileOperation operation = new LockFileOperation(fileIdentifier, requestorId, token);
            Credential.Signature signature = readerCredential.sign(requestorPassword.toCharArray(), operation.getId());
            ResponseMessage reply = this.celeste.lockFile(operation, signature);
            
            AObjectVersionMapAPI.Lock lock = reply.get(AObjectVersionMapAPI.Lock.class);
            if (lock != null) {
                System.out.printf("%s%n", lock.toString());
            } else {
                System.out.printf("No lock%n");
            }

            if (celesteMetaDataFile != null) {
                if (celesteMetaDataFile.equals("-")) {
                    reply.getMetadata().store(System.out, "");
                } else {
                    reply.getMetadata().store(new FileOutputStream(celesteMetaDataFile), "");
                }
            }
            return 0;
        } catch (Exception e) {
            stats.addMessage(String.format("%s", e.toString()));
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    public int getFileAttributes(String command, Stack<String>options, Stats stats) {
        if (command == null) {
            Usage("get-file-attributes", "<requestor> <requestorPassword> <namespace> <file> <versionId> [<attr-name>[, <attr-name>]*]");
            return -1;
        }

        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            BeehiveObjectId vObjectId = parseObjectId(options.pop());

            InspectFileOperation operation =
                new InspectFileOperation(new FileIdentifier(nameSpaceId, fileId), requestorId, vObjectId);

            ResponseMessage reply = this.celeste.inspectFile(operation);
            OrderedProperties metadata = reply.getMetadata();
            //
            // Form the key set for the subset of overall properties to be
            // returned.
            //
            Set<Object> metadataKeys = metadata.keySet();
            Set<Object> keys = null;
            if (options.empty()) {
                keys = metadataKeys;
            } else {
                //
                // Form a set from the comma-separated string given as
                // argument and intersect that with the complete set of
                // attribute keys, yielding the desired key set.  Allow white
                // space to follow the commas.
                //
                Set<Object> argKeys = new HashSet<Object>(Arrays.asList(options.pop().split(",[ \t]*")));
                keys = new HashSet<Object>(metadataKeys);
                keys.retainAll(argKeys);
            }

            //
            // Extract the desired properties.
            //
            OrderedProperties resultAttrs = new OrderedProperties();
            for (Object key : keys)
                resultAttrs.put(key, metadata.get(key));
            resultAttrs.store(System.out, "");

            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    public int setFileLength(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("set-file-length", "<requstor> <requestorPassword> <namespace> <file> <length> [<metaData-FileName>]");
            return -1;
        }

        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId nameId = parseObjectId(options.pop());
            long stopByte = Long.parseLong(options.pop());

            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, nameId);
            InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, requestorId);

            ResponseMessage a = celeste.inspectFile(inspect);

            OrderedProperties metadata = a.getMetadata();
            BeehiveObjectId latestVObjectId = new BeehiveObjectId(metadata.getProperty(CelesteAPI.VOBJECTID_NAME));

            Credential requestorCredential = CelesteSh.getCredential(this.celeste, requestorId);

            ClientMetaData woc = new ClientMetaData();
            SetFileLengthOperation operation = new SetFileLengthOperation(fileIdentifier, requestorCredential.getObjectId(), latestVObjectId, woc, stopByte);

            Credential.Signature sig = requestorCredential.sign(requestorPassword.toCharArray(), operation.getId());

            OrderedProperties reply = this.celeste.setFileLength(operation, sig);

            if (!options.empty()) {
                String option = options.pop();
                try {
                    if (reply != null) {
                        reply.store(new FileOutputStream(option), "");
                    }
                } catch (IOException e) {
                    System.err.println(option + " " + e.toString());
                    return -1;
                }
            }

            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    public int setFileOwnerAndGroup(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("set-file-owner-and-group", "<requstor> <requestorPassword> <namespace> <file> <owner> <group> [<metaData-FileName>]");
            return -1;
        }
        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String requestorPassword = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            BeehiveObjectId ownerId = parseObjectId(options.pop());
            BeehiveObjectId groupId = parseObjectId(options.pop());
            ClientMetaData clientMetaData = new ClientMetaData();

            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);
            InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, requestorId);

            ResponseMessage a = this.celeste.inspectFile(inspect);
            OrderedProperties metadata = a.getMetadata();
            BeehiveObjectId predicatedVObjectId = new BeehiveObjectId(metadata.getProperty(CelesteAPI.VOBJECTID_NAME));

            SetOwnerAndGroupOperation operation = new SetOwnerAndGroupOperation(fileIdentifier, requestorId, predicatedVObjectId, clientMetaData, ownerId, groupId);

            Credential clientCredential = CelesteSh.getCredential(this.celeste, requestorId);
            Credential.Signature sig = clientCredential.sign(requestorPassword.toCharArray(), operation.getId());

            OrderedProperties reply = this.celeste.setOwnerAndGroup(operation, sig);
            if (!options.empty()) {
                String option = options.pop();
                try {
                    if (reply != null) {
                        reply.store(new FileOutputStream(option), "");
                    }
                } catch (IOException e) {
                    System.err.println(option + " " + e.toString());
                    return -1;
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    public int testFile(String command, Stack<String> options, Stats stats) {
        if (command == null) {
            Usage("test", "[-c credential]|[-e <requestor> <requestorPassword> <namespace> <file> [versionId]]");
            return -1;
        }
        try {
            boolean eFlag = false;
            boolean credentialFlag = false;
            boolean successful = false;
            boolean verbose = this.properties.getProperty("verbose", "false").equals("true");

            if (options.peek().startsWith("-")) {
                String option = options.pop();
                for (int i = 1; i < option.length(); i++) {
                    if (option.charAt(i) == 'e') {
                        eFlag = true;
                    } else if (option.charAt(i) == 'c') {
                        credentialFlag = true;
                    }
                }
            }

            if (credentialFlag) {
                // Test for a credential
                String credentialName = options.pop();
                ReadProfileOperation operation = new ReadProfileOperation(new BeehiveObjectId(credentialName.getBytes()));

                try {
                    this.celeste.readCredential(operation);
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
                }
                if (verbose)
                    System.out.println(successful ? "true" : "false");
                return successful ? 0 : 1;
            }

            if (eFlag) {
                BeehiveObjectId requestorId = parseObjectId(options.pop());
                String requestorPassword = options.pop();
                BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
                BeehiveObjectId fileId = parseObjectId(options.pop());
                BeehiveObjectId vObjectId = null;
                if (!options.empty()) {
                    vObjectId = parseObjectId(options.pop());
                }

                InspectFileOperation operation = new InspectFileOperation(new FileIdentifier(nameSpaceId, fileId), requestorId, vObjectId);

                ResponseMessage reply = this.celeste.inspectFile(operation);

                reply.get(ClientMetaData.class);

                if (this.properties.getProperty("verbose", "false").equals("true")) {
                    if (verbose)
                        System.out.println("true");
                }

                return 0;
            }
        } catch (Exception e) {
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
        }

        if (this.properties.getProperty("verbose", "false").equals("true")) {
            System.out.println("false");
        }
        return -1;
    }

    /**
    *
    */
   public int unlockFile(String command, Stack<String> options, Stats stats) {
       if (command == null) {
           Usage("unlock-file", "<requestor> <requestorPassword> <namespace> <file> <lock-token> [<result-metadata>]");
           return -1;
       }
       
       String celesteMetaDataFile = null;
       String token = null;

       try {
           BeehiveObjectId requestorId = parseObjectId(options.pop());
           String requestorPassword = options.pop();
           BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
           BeehiveObjectId fileId = parseObjectId(options.pop());
           if (!options.isEmpty()) {
               token = options.pop();
               if (token.compareTo("null") == 0)
                   token = null;
           }
           if (!options.isEmpty())
               celesteMetaDataFile = options.pop();

           Credential readerCredential = CelesteSh.getCredential(this.celeste, requestorId);
           UnlockFileOperation operation = new UnlockFileOperation(new FileIdentifier(nameSpaceId, fileId), requestorId, token);
           Credential.Signature signature = readerCredential.sign(requestorPassword.toCharArray(), operation.getId());
           ResponseMessage reply = this.celeste.unlockFile(operation, signature);

           AObjectVersionMapAPI.Lock lock = reply.get(AObjectVersionMapAPI.Lock.class);
           if (lock != null) {
               System.out.printf("%s%n", lock.toString());
           } else {
               System.out.printf("No lock%n");
           }
           
           if (celesteMetaDataFile != null) {
               if (celesteMetaDataFile.equals("-")) {
                   reply.getMetadata().store(System.out, "");
               } else {
                   reply.getMetadata().store(new FileOutputStream(celesteMetaDataFile), "");
               }
           }
           return 0;
       } catch (Exception e) {
           stats.addMessage(String.format("%s", e.toString()));
           if (this.properties.getProperty("verbose", "false").equals("true")) {
               e.printStackTrace();
           }
           return -1;
       }
   }


    public int setFileACL(String command, Stack<String> options, Stats stats)
        throws IOException, Credential.Exception {

        if (command == null) {
            Usage("set-file-acl", "<requestor> <requestorPassword> <namespace> <file> <acl> [<metaData-FileName>]");
            return -1;
        }
        try {
            BeehiveObjectId requestorId = parseObjectId(options.pop());
            String passphrase = options.pop();
            BeehiveObjectId nameSpaceId = parseObjectId(options.pop());
            BeehiveObjectId fileId = parseObjectId(options.pop());
            String aclString = options.pop();

            ClientMetaData clientMetaData = new ClientMetaData();

            FileIdentifier fileIdentifier = new FileIdentifier(nameSpaceId, fileId);
            InspectFileOperation inspect = new InspectFileOperation(fileIdentifier, requestorId);

            ResponseMessage a = this.celeste.inspectFile(inspect);
            OrderedProperties metadata = a.getMetadata();
            BeehiveObjectId predicatedVObjectId = new BeehiveObjectId(metadata.getProperty(CelesteAPI.VOBJECTID_NAME));
            CelesteACL acl = CelesteACL.getEncodedInstance(aclString.getBytes());

            SetACLOperation operation = new SetACLOperation(fileIdentifier, requestorId, predicatedVObjectId, clientMetaData, acl);

            Credential clientCredential = CelesteSh.getCredential(this.celeste, requestorId);
            Credential.Signature sig = clientCredential.sign(passphrase.toCharArray(), operation.getId());

            OrderedProperties reply = this.celeste.setACL(operation, sig);
            if (!options.empty()) {
                String option = options.pop();
                try {
                    if (reply != null) {
                        reply.store(new FileOutputStream(option), "");
                    }
                } catch (IOException e) {
                    System.err.println(option + " " + e.toString());
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    /**
     *
     * @throws IOException
     */
    public int readCredential(String command, Stack<String> options, Stats stats) throws IOException {
        if (command == null) {
            System.out.println("read-credential <credential-name> <data-file>");
            return -1;
        }
        String output = "-";
        String metaDataOutput = null;

        try {
            BeehiveObjectId credentialId = parseObjectId(options.pop());
            if (!options.empty())
                output = options.pop();
            if (!options.empty())
                metaDataOutput = options.pop();

            ResponseMessage reply = this.celeste.readCredential(new ReadProfileOperation(credentialId));
            Profile_ eb = reply.get(Profile_.class);

            if (metaDataOutput != null) {
                if (reply.getMetadata() != null) {
                    reply.getMetadata().store(new FileOutputStream(metaDataOutput), "");
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (this.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    private static void Usage(String name, String arguments) {
        System.out.printf("%-25s %s%n", name, arguments);
    }
    
    private static void Usage() {
        System.out.println(Release.ThisRevision());
        System.out.println(Copyright.miniNotice);
        System.out.println("Usage: CelesteClient --help | [--celeste-address <address>:<port>] [--verbose] [--timeout <seconds>] [operation]");
        System.out.println("operation is one of:");
        CelesteSh celestesh = new CelesteSh(null);
        Stats stats = new Stats("");
        try {
            celestesh.createFile(null, null, stats);
            celestesh.deleteFile(null, null, stats);
            celestesh.inspectFile(null, null, stats);
            celestesh.getFileAttributes(null, null, stats);
//            celestesh.keyStoreCredential(null, null, stats);
            celestesh.lockFile(null, null, stats);
            celestesh.newCredential(null, null, stats);
            celestesh.newNameSpace(null, null, stats);
            celestesh.readCurrentFile(null, null, stats);
            celestesh.readFileVersion(null, null, stats);
            celestesh.readCredential(null, null, stats);
            celestesh.setFileLength(null, null, stats);
            celestesh.setFileOwnerAndGroup(null, null, stats);
            celestesh.testFile(null, null, stats);
            celestesh.writeFile(null, null, stats);
            celestesh.unlockFile(null, null, stats);
            System.out.println("In all cases, the use of the string 'null' indicates the Java null value.");
        } catch (Exception e) {
            /**/
        }
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        String celesteAddress = "127.0.0.1:14000";
        boolean verboseFlag = false;
        long timeOutSeconds = 300;

        Stack<String> options = new Stack<String>();
        for (int i = args.length - 1; i >= 0; i--) {
            options.push(args[i]);
        }

        if (options.empty()) {
            Usage();
            System.exit(-1);
        }
        
        while (!options.empty()) {
            String option = options.pop();
            if (option.startsWith("--")) {
                if (option.equals("--celeste-address")) {
                    celesteAddress = options.pop();
                } else if (option.equals("--timeout")) {
                    timeOutSeconds = Integer.parseInt(options.pop());
                } else if (option.equals("--help")) {
                    Usage();
                    System.exit(0);
                } else if (option.equals("--verbose")) {
                    verboseFlag = true;
                }
            } else {
                options.push(option);
                break;
            }
        }

        CelesteAPI celeste = null;
        try {
            celeste = new CelesteProxy(makeAddress(celesteAddress), Time.secondsInMilliseconds(timeOutSeconds), TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            System.err.printf("Cannot connect to the Celeste node at %s: %s%n", celesteAddress, e.toString());
            System.err.printf("Verify the address and port number %s is correct, or use --celeste-address address:port to specify another.%n", celesteAddress);
            System.exit(1);
        }

        CelesteSh celestesh = new CelesteSh(celeste);
        celestesh.properties.setProperty("verbose", verboseFlag ? "true" : "false");
        int status = -1;

        try {
            while (!options.empty()) {
                String option = options.pop();
                Stats stats = new Stats("");

                long startTime = System.currentTimeMillis();
                if (option.equals("create-file")) {
                    status = celestesh.createFile(option, options, stats);
//                } else if (option.equals("keystore-credential")) {
//                    int status = celestesh.keyStoreCredential(option, options);
//                    System.exit(status);
                } else if (option.equals("new-namespace")) {
                    status = celestesh.newNameSpace(option, options, stats);
                } else if (option.equals("new-credential")) {
                    status = celestesh.newCredential(option, options, stats);
                } else if (option.equals("read-credential")) {
                    status = celestesh.readCredential(option, options, stats);
                } else if (option.equals("read-file")) {
                    status = celestesh.readCurrentFile(option, options, stats);
                } else if (option.equals("configure")) {
                    status = celestesh.configure(option, options, stats);
                } else if (option.equals("read")) {
                    status = celestesh.readCurrentFileChunked(option, options, stats);
                } else if (option.equals("read-file-version")) {
                    status = celestesh.readFileVersion(option, options, stats);
                } else if (option.equals("write-file")) {
                    status = celestesh.writeFile(option, options, stats);
                } else if (option.equals("write")) {
                    status = celestesh.writeFileChunked(option, options, stats);
                } else if (option.equals("write2")) {
                    status = celestesh.writeFileChunked2(option, options, stats);
                } else if (option.equals("delete-file")) {
                    status = celestesh.deleteFile(option, options, stats);
                } else if (option.equals("set-file-length")) {
                    status = celestesh.setFileLength(option, options, stats);
                } else if (option.equals("inspect-file")) {
                    status = celestesh.inspectFile(option, options, stats);
                } else if (option.equals("inspect-lock")) {
                    status = celestesh.inspectLock(option, options, stats);
                } else if (option.equals("get-file-attributes")) {
                    status = celestesh.getFileAttributes(option, options, stats);
                } else if (option.equals("set-file-owner-and-group")) {
                    status = celestesh.setFileOwnerAndGroup(option, options, stats);
                } else if (option.equals("test")) {
                    status = celestesh.testFile(option, options, stats);
                } else if (option.equals("run-extension")) {
                    status = celestesh.runExtension(option, options, stats);
                } else if (option.equals("lock-file")) {
                    status = celestesh.lockFile(option, options, stats);
                } else if (option.equals("unlock-file")) {
                    status = celestesh.unlockFile(option, options, stats);
                } else {
                    System.err.println("Unknown function: " + option);
                    Usage();
                    System.exit(-1);
                }
                System.err.printf("%1$tF %1$tT %1$tZ [%2$.4f s] %3$s %4$s%n",
                        System.currentTimeMillis(),
                                (System.currentTimeMillis() - startTime) / 1000.0,
                                option,
                                stats.getMessage());
                System.exit(status);
            }
        } catch (Exception e) {
            System.err.printf("%s%n", e.toString());
            if (celestesh.properties.getProperty("verbose", "false").equals("true")) {
                e.printStackTrace();
            }
            System.exit(1);
        }

        System.err.printf("Abnormal exit%n");
        System.exit(-1);
    }
}
