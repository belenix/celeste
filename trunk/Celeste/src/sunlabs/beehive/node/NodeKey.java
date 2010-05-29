/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.beehive.node;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import sunlabs.beehive.BeehiveObjectId;

/**
 * <p>
 * The key of a Beehive Node
 * </p>
 */
public final class NodeKey {

    private static final String keyStorePassword = "celesteStore";
    private static final String keyPassword = "celesteKey";
    private static final String KEY_NAME = "PrivateKey";

    private KeyStore keyStore;
    private BeehiveObjectId objectId;

    /**
     * Creates a key for the a beehive node operating on a given port on a
     * system with a given name. The key requires a keystore for SSL
     * communications in a file whose path is specified. If the file does
     * not exist, one will be created with a self-signed certificate.
     *
     * @param keyStoreFileName the path to a keystore file
     * @param localAddress the local host name (used in the name in the
     * keystore certificate if a keystore is created)
     * @param localBeehivePort port on which this node is operating (used
     * in the name in the keystore certificate if a keystore is created)
     *
     * @throws java.io.IOException
     */
    public NodeKey(String keyStoreFileName, String localAddress, int localBeehivePort) throws IOException {

        // If there is an existing file with the same name as the purported
        // keyStoreFileName, attempt to use it to initialise the NodeKey
        // instance.  Otherwise, create a new one and store it in the
        // file named keyStoreFileName.
        //
        keyStoreFileName = keyStoreFileName.trim(); // remove windows <CR>
        File keyStoreFile = new File(keyStoreFileName);
        if (!keyStoreFile.exists()) {
            createKeyStore(keyStoreFileName, localAddress, localBeehivePort);
        } 
//        System.out.println("Loading keystore: " + keyStoreFileName);
        try {
            this.keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            this.keyStore.load(new BufferedInputStream(new FileInputStream(keyStoreFile)), keyStorePassword.toCharArray());
            Certificate cert = this.keyStore.getCertificate(NodeKey.KEY_NAME);
            this.objectId = new BeehiveObjectId(cert.getPublicKey());
        } catch (GeneralSecurityException ex) {
            throw new IOException(ex);
        }
    }

    KeyStore getKeyStore() {
        return this.keyStore;
    }

    char[] getKeyPassword() {
        return keyPassword.toCharArray();
    }

    BeehiveObjectId getObjectId() {
        return this.objectId;
    }

    private void createKeyStore(String keyStoreFileName, String localAddress, int localBeehivePort)
        throws IOException {

        System.out.println("Creating keystore: " + keyStoreFileName);
        long startTime = System.currentTimeMillis();
        String distinguishedName =
            String.format("CN=%s, DC=%s, DNQ=%s, OU=%s, " +
                "O=%s, C=%s, IP=%s:%d", localAddress,
                "beehive.sun.com", "v1", "Sun Microsystems Laboratories",
                "Sun Microsystems", "US", localAddress, localBeehivePort);
        String path = System.getProperty("java.home") + File.separator +
            "bin" + File.separator + "keytool";
        String command[] = { path, "-genkeypair", "-dname", distinguishedName,
             "-keyalg", "RSA", "-alias", NodeKey.KEY_NAME,
             "-keypass", keyPassword, "-keystore", keyStoreFileName,
             "-storepass", keyStorePassword };
        System.out.println(java.util.Arrays.toString(command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] buf = new byte[4096];
        int n;
        while ((n = p.getInputStream().read(buf)) >= 0) {
            System.out.write(buf, 0, n);
        }
        while (true) {
            try {
                if (p.waitFor() != 0) {
                    throw new IOException("keytool failed to build keystore.");
                }
                break;
            } catch (InterruptedException ex) {
            }
        }
        long stopTime = System.currentTimeMillis();
        System.out.println("KeyStore created in " + (stopTime - startTime)/1000.
                           + " seconds.");
    }
}
