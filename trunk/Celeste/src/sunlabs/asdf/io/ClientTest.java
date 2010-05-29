package sunlabs.asdf.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.LinkedList;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;



public class ClientTest {

    private static class NodeX509TrustManager implements X509TrustManager {
        NodeX509TrustManager() { 
            //System.out.println("NodeX509TrustManager constructor");
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            //System.out.println("NodeX509TrustManager checkServerTrusted");
        }
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            //System.out.println("NodeX509TrustManager checkclientTrusted");
        }
        public X509Certificate[] getAcceptedIssuers() {
            //System.out.println("NodeX509TrustManager getAcceptedIssuers");
            return new X509Certificate[0];
        }
    }
    
    public static class SSLConnect extends Thread {
        private String keyStoreName;
        private String keyStorePassword;
        private String keyPassword;
        
        public SSLConnect(String keyStoreName, String keyStorePassword, String keyPassword) {
            super();
            this.keyStoreName = keyStoreName;
            this.keyStorePassword = keyStorePassword;  
            this.keyPassword = keyPassword;            
        }
        
        public void run() {
            LinkedList<SSLSocket> pseudoCache = new LinkedList<SSLSocket>();
            
            try {
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(new BufferedInputStream(new FileInputStream(keyStoreName)), keyStorePassword.toCharArray());
                
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(keyStore, keyPassword.toCharArray());
                KeyManager[] km = kmf.getKeyManagers();

                TrustManager[] tm = new TrustManager[1];
                tm[0] = new NodeX509TrustManager();

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(km, tm, null);
                sslContext.getServerSessionContext().setSessionCacheSize(128);
                sslContext.getClientSessionContext().setSessionCacheSize(128);
                sslContext.getClientSessionContext().setSessionTimeout(60*60);
                sslContext.getServerSessionContext().setSessionTimeout(60*60);

                for (int i = 0; i < 10000; i++) {
                    try {                  
                        SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
                        SocketAddress server = new InetSocketAddress("127.0.0.1", 8084);
                        socket.connect(server);
                        socket.startHandshake();

                        long limit = 2;
                        Writer writer = new Writer(socket, limit);
                        Reader reader = new Reader(socket, limit);

                        writer.start();
                        reader.start();

                        reader.join();
                        writer.join();
                        if (writer.cummulative != reader.cummulative) {
                            System.out.printf("writer %d bytes, read %d%n", writer.cummulative, reader.cummulative);
                        } else {
                            if ((i % 1000) == 0)
                                System.out.printf("%d%s", i, (i % 10000) == 0 ? "\n " + new Date() + ": " : " "); System.out.flush();
                        }
                        pseudoCache.add(socket);
//                        socket.close();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }            
                }
            } catch (KeyStoreException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (CertificateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (UnrecoverableKeyException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (KeyManagementException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                
            }
            try {
                Thread.currentThread().sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    public static class Writer extends Thread {
        private Socket socket;
        private long limit;
        public long cummulative;
        
        public Writer(Socket socket, long limit) {
            super();
            this.socket = socket;
            this.limit = limit;
            this.cummulative = 0;
        }
        
        public void run() {
            try {
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(this.socket.getOutputStream()));
                for (long i = 0; i < this.limit; i++) {
                    for (int j = 0; j < 2000; j++) {
                        out.writeLong(i);
                        this.cummulative += 8;
                    }
                }
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static class Reader extends Thread {
        private Socket socket;
        private long limit;
        public long cummulative;
        
        public Reader(Socket socket, long limit) {
            super();
            this.socket = socket;
            this.limit = limit;
            this.cummulative = 0;
        }
        
        public void run() {
            long counter = 0;
            DataInputStream in;
            try {
                in = new DataInputStream(this.socket.getInputStream());
                while (true) {
                    long i = 0;
                    for (int j = 0; j < 2000; j++) {
                        i = in.readLong();
                        this.cummulative += 8;
                        if (i != counter) {
                            System.out.printf("%d %d%n", i, counter);
                        }
                    }
                    if (i == this.limit -1)
                        break;
                    counter++;
                }
            } catch (IOException e) {

            }
        }
    }
    
    public static void simpleSSLOpenWaitForClose(String keyStoreName, String keyStorePassword, String keyPassword) {
        try {

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(new BufferedInputStream(new FileInputStream(keyStoreName)), keyStorePassword.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, keyPassword.toCharArray());
            KeyManager[] km = kmf.getKeyManagers();

            TrustManager[] tm = new TrustManager[1];
            tm[0] = new NodeX509TrustManager();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(km, tm, null);
            sslContext.getServerSessionContext().setSessionCacheSize(128);
            sslContext.getClientSessionContext().setSessionCacheSize(128);
            sslContext.getClientSessionContext().setSessionTimeout(60*60);
            sslContext.getServerSessionContext().setSessionTimeout(60*60);

            SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
            SocketAddress server = new InetSocketAddress("127.0.0.1", 8084);
            socket.connect(server);
            socket.startHandshake();
            System.out.printf("wait for close%n");
            socket.getInputStream().read();
            System.out.printf("done%n");
        } catch (KeyStoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CertificateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (KeyManagementException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {

        }

    }

    public static void main(String[] args) throws IOException, InterruptedException {

        SSLContext sslContext = null;
        String keyStoreName = "celeste.jks";
        String keyStorePassword = "celesteStore";
        String keyPassword = "celesteKey";

//        byte[] line = new byte[256];
//        System.out.printf("keyStoreName: '%s'> ", keyStoreName); System.out.flush();
//        int nread = System.in.read(line);
//        if (nread > 1) {
//            keyStoreName = new String(line, 0, nread).trim();
//        }
//
//        System.out.printf("keyStorePassword: '%s'> ", keyStorePassword); System.out.flush();
//        nread = System.in.read(line);
//        if (nread > 1) {
//            keyStorePassword = new String(line, 0, nread).trim();
//        }
//
//        System.out.printf("keyPassword: '%s'> ", keyPassword); System.out.flush();
//        nread = System.in.read(line);
//        if (nread > 1) {
//            keyPassword = new String(line, 0, nread).trim();
//        }

        System.out.printf("keyStoreName '%s'%n", keyStoreName);
        System.out.printf("keyStorePassword '%s'%n", keyStorePassword);
        System.out.printf("keyPassword '%s'%n", keyPassword);
        
        // Connect and wait for the other side to close.
//        ClientTest.simpleSSLOpenWaitForClose(keyStoreName, keyStorePassword, keyPassword);
//        System.exit(0);
        
        // Make repeated connections.
        SSLConnect connect = new SSLConnect(keyStoreName, keyStorePassword, keyPassword);
        connect.start();
        connect.join();
        
//        try {
//
//            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
//            keyStore.load(new BufferedInputStream(new FileInputStream(keyStoreName)), keyStorePassword.toCharArray());
//
//            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
//            kmf.init(keyStore, keyPassword.toCharArray());
//            KeyManager[] km = kmf.getKeyManagers();
//
//            TrustManager[] tm = new TrustManager[1];
//            tm[0] = new NodeX509TrustManager();
//
//            sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(km, tm, null);
//            sslContext.getServerSessionContext().setSessionCacheSize(128);
//            sslContext.getClientSessionContext().setSessionCacheSize(128);
//            sslContext.getClientSessionContext().setSessionTimeout(60*60);
//            sslContext.getServerSessionContext().setSessionTimeout(60*60);
//
//
//            SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
//            SocketAddress server = new InetSocketAddress("127.0.0.1", 8084);
//            socket.connect(server);
//            socket.startHandshake();
//
//            long limit = 1000;
//            Writer writer = new Writer(socket, limit);
//            Reader reader = new Reader(socket, limit);
//
//            writer.start();
//            reader.start();
//
//            reader.join();
//        if (writer.cummulative != reader.cummulative) {
//            System.out.printf("writer %d bytes, read %d%n", writer.cummulative, reader.cummulative);
//        }
//
//
//        } catch (java.security.NoSuchAlgorithmException exception) {
//            throw new IOException(exception.toString());
//        } catch (java.security.KeyStoreException exception) {
//            throw new IOException(exception.toString());
//        } catch (java.security.KeyManagementException exception){
//            throw new IOException(exception.toString());
//        } catch (java.security.UnrecoverableKeyException exception){
//            throw new IOException(exception.toString());
//        } catch (CertificateException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
    }

}
