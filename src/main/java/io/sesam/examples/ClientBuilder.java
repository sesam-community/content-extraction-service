package io.sesam.examples;


import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A builder class for configuring HTTP client objects.
 */
public class ClientBuilder {

    static Logger log = LoggerFactory.getLogger(ClientBuilder.class);

    private String username;
    private String password;
    private String workstation;
    private String domain;
    private String authtype = "basic"; // the default
    private int socketTimeout = 1000 * 60 * 2; // two minutes
    private int connectionTimeout = 10000;
    private int connectionRequestTimeout = 5000;
    private boolean compression = true;
    private boolean trustEverything = false;

    /**
     * Set to true you want to trust SSL and do no host verification.
     */
    public void setTrustEverything(boolean trustEverything) {
        this.trustEverything = trustEverything;
    }

    /**
     * Sets the user name to use when authenticating.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Sets the password to use when authenticating.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Sets the "workstation". Described as "The workstation the
     * authentication request is originating from. Essentially, the
     * computer name for this machine."
     */
    public void setWorkstation(String workstation) {
        this.workstation = workstation;
    }

    /**
     * The domain to authenticate against. Default (null) is all
     * domains.
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * Sets the authentication type to use. Supports "basic", "digest" and
     * "ntlm".  The default is no authentication.
     */
    public void setAuthType(String authtype) {
        if (authtype == null)
            throw new RuntimeException("Cannot set authtype to null");
        this.authtype = authtype.toLowerCase(); // normalization
    }

    /**
     * Sets the socket timeout in milliseconds. That is, the maximum
     * allowed time with no traffic on the socket, before the
     * connection is killed. The default is six hours.
     */
    public void setSocketTimeout(int timeout) {
        this.socketTimeout = timeout;
    }

    /**
     * Sets the connection timeout in milliseconds. That is, the maximum
     * allowed time is the timeout until a connection with the server is established. . The default is six hours.
     */
    public void setConnectionTimeout(int timeout) {
        this.connectionTimeout  = timeout;
    }

    /**
     * Controls whether compression is supported or not. The default
     * is for compression to be used if available.
     */
    public void setUseCompression(boolean compression) {
        this.compression = compression;
    }

    /**
     * Returns an HTTP client configured as requested.
     */
    public CloseableHttpClient create() {
        Builder rcBuilder = RequestConfig.custom()
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout);

        HttpClientBuilder builder = HttpClientBuilder.create();

        Registry<ConnectionSocketFactory> registry = null;
        if (trustEverything) {
            try {
                // Trust all certificates and do no hostname verification
                SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                    public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                        return true;
                    }
                }).build();
                builder.setSSLContext(sslContext);

                HostnameVerifier hostnameVerifier = new NoopHostnameVerifier();

                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
                registry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", sslSocketFactory)
                        .build();

            } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
                log.error("Not able to set up SSLContext", e);
            }
        }
        if (registry == null) {
            registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", SSLConnectionSocketFactory.getSocketFactory())
                    .build();
        }

        if (!compression)
            builder.disableContentCompression();

        switch (authtype) {
        case "ntlm":
            CredentialsProvider ntlmCreds = new BasicCredentialsProvider();
            NTCredentials creds = new NTCredentials(username, password, workstation, domain);
            ntlmCreds.setCredentials(AuthScope.ANY, creds);
            builder.setDefaultCredentialsProvider(ntlmCreds);
            rcBuilder.setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM));
            break;
        case "basic":
            if (username != null && password != null) {
                BasicCredentialsProvider basicCreds = new BasicCredentialsProvider();
                basicCreds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                builder.setDefaultCredentialsProvider(basicCreds);
            }
            break;
        case "digest":
            if (username != null && password != null) {
                BasicCredentialsProvider basicCreds = new BasicCredentialsProvider();
                basicCreds.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT,
                        AuthScope.ANY_REALM, AuthSchemes.DIGEST), new UsernamePasswordCredentials(username, password));
                builder.setDefaultCredentialsProvider(basicCreds);
            }
            break;
        default:
            break;
        }
        builder.setDefaultRequestConfig(rcBuilder.build());

        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager(registry);
        mgr.setDefaultMaxPerRoute(30);
        builder.setConnectionManager(mgr);

        return builder.build();
    }
}
