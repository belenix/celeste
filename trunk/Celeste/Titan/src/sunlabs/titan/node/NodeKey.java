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
package sunlabs.titan.node;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import sunlabs.asdf.util.Time;
import sunlabs.titan.TitanGuidImpl;

/**
 * <p>
 * The key of a Titan Node
 * </p>
 */
public final class NodeKey {
    private static final String keyStorePassword = "celesteStore";
    private static final String keyPassword = "celesteKey";
    private static final String KEY_NAME = "PrivateKey";

    private KeyStore keyStore;
    private TitanGuidImpl objectId;
    private String keyStoreFileName;

    /**
     * Creates a key for the a beehive node operating on a given port on a
     * system with a given name. The key requires a keystore for SSL
     * communications in a file whose path is specified. If the file does
     * not exist, one will be created with a self-signed certificate.
     *
     * @param keyStoreFileName the path to a keystore file
     * @param localAddress the local host name (used in the name in the
     * keystore certificate if a keystore is created)
     * @param localPort port on which this node is operating (used
     * in the name in the keystore certificate if a keystore is created)
     *
     * @throws java.io.IOException
     */
    public NodeKey(String keyStoreFileName, String localAddress, int localPort) throws IOException {

        // If there is an existing file with the same name as the purported
        // keyStoreFileName, attempt to use it to initialise the NodeKey
        // instance.  Otherwise, create a new one and store it in the
        // file named keyStoreFileName.
        //
        this.keyStoreFileName = keyStoreFileName.trim(); // remove windows <CR>
        File keyStoreFile = new File(keyStoreFileName);
        if (!keyStoreFile.exists()) {
            createKeyStore(keyStoreFileName, localAddress, localPort);
        }
        try {
            this.keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            this.keyStore.load(new BufferedInputStream(new FileInputStream(keyStoreFile)), keyStorePassword.toCharArray());
            Certificate cert = this.keyStore.getCertificate(NodeKey.KEY_NAME);
            this.objectId = new TitanGuidImpl(cert.getPublicKey());
        } catch (GeneralSecurityException ex) {
            throw new IOException(ex);
        }
    }

    public String getKeyStoreFileName() {
        return this.keyStoreFileName;
    }
    
    public KeyStore getKeyStore() {
        return this.keyStore;
    }

    public char[] getKeyPassword() {
        return keyPassword.toCharArray();
    }

    public TitanGuidImpl getObjectId() {
        return this.objectId;
    }

    private void createKeyStore(String keyStoreFileName, String localAddress, int localBeehivePort)
        throws IOException {

        System.out.println("Creating keystore: " + keyStoreFileName);
        long startTime = System.currentTimeMillis();
        String distinguishedName =
            String.format("CN=%s, DC=%s, DNQ=%s, OU=%s, " + "O=%s, C=%s, IP=%s:%d",
                    localAddress, "beehive.sun.com", "v1", "Sun Microsystems Laboratories", "Sun Microsystems", "US", localAddress, localBeehivePort);
        String path = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool";
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
        System.out.printf("KeyStore created in %d seconds.%n", Time.millisecondsInSeconds(stopTime - startTime));
    }
    
    public SSLContext newSSLContext() throws UnrecoverableKeyException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        return NodeKey.TitanSSLContext(this.getKeyStore(), this.getKeyPassword());
    }
    
    public static SSLContext TitanSSLContext(KeyStore keyStore, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        KeyManager[] km = null;
        if (keyStore != null) {
            kmf.init(keyStore, password);
            km = kmf.getKeyManagers();
        }

        TrustManager[] tm = new TrustManager[1];
        tm[0] = new NodeX509TrustManager();

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(km, tm, null);
        sslContext.getServerSessionContext().setSessionCacheSize(1024);
        sslContext.getClientSessionContext().setSessionCacheSize(1024);
        sslContext.getClientSessionContext().setSessionTimeout(60*60);
        sslContext.getServerSessionContext().setSessionTimeout(60*60);

        return sslContext;
    }
}
