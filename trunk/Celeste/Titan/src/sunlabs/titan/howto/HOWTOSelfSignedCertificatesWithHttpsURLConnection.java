package sunlabs.titan.howto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLSession;

public class HOWTOSelfSignedCertificatesWithHttpsURLConnection {
    public static class NodeX509TrustManager implements X509TrustManager {
        public NodeX509TrustManager() { 
            System.out.println("NodeX509TrustManager constructor");
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            System.out.println("NodeX509TrustManager checkServerTrusted");
        }
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            System.out.println("NodeX509TrustManager checkclientTrusted");
        }
        public X509Certificate[] getAcceptedIssuers() {
            System.out.println("NodeX509TrustManager getAcceptedIssuers");
            return new X509Certificate[0];
        }
    }

    public static void main(String[] argv) {
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String urlHostName, SSLSession session) {
                System.out.println("Warning: URL Host: " + urlHostName + " vs. " + session.getPeerHost());
                return true;
            }
        };

        try {
            SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
            SSL_CONTEXT.init(null, new TrustManager[] { new NodeX509TrustManager() }, null);

            String s = "https://127.0.0.1:12001/gateway";
            URL url = new URL(s);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.setSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
            connection.setHostnameVerifier(hv);

            connection.setDoOutput(true);
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
            in.close();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}