package io.sesam.examples;


import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * A builder class for configuring HTTP client objects.
 */
public class ClientBuilder {

    // ----- Static utility methods

    /**
     * Makes a client with the default configuration, set up to use
     * basic authentication using the given user name and password
     * against all domains. If no username and password are given, it
     * doesn't authenticate.
     */
    public static CloseableHttpClient makeClient(String username, String password) {
        return makeClient(username, password, null, "basic");
    }

    /**
     * Makes a client with the default configuration, set up to use
     * the configured type of authentication using the given user name
     * and password against the given domain. If no username and
     * password are given, it doesn't authenticate. Setting domain to
     * null means authenticating against all domains.
     * @param authtype the authentication mechanism to use. Supported
     *        values are "basic", "digest" and "ntlm".
     */
    public static CloseableHttpClient makeClient(String username,
                                        String password,
                                        String domain,
                                        String authtype) {
        ClientBuilder builder = new ClientBuilder();
        builder.setUsername(username);
        builder.setPassword(password);
        builder.setDomain(domain);
        builder.setAuthType(authtype);
        return builder.makeClient();
    }

    // ----- The full builder

    private String username;
    private String password;
    private String workstation;
    private String domain;
    private String authtype = "basic"; // the default
    private int socketTimeout = 1000 * 60 * 2; // two minutes
    private int connectionTimeout = 10000;
    private int connectionRequestTimeout = 5000;
    private boolean compression = true; // on by default

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
    public CloseableHttpClient makeClient() {
        // Making a connection manager so that HttpClient will use
        // persistent HTTP connections.  Must use the pooling manager,
        // because multiple threads can use the same client at the same
        // time.
        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setDefaultMaxPerRoute(30);

        RequestConfig rc = RequestConfig.custom()
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout).build();

        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setConnectionManager(mgr);
        builder.setDefaultRequestConfig(rc);

        // we disable compression because Virtuoso cuts the connection if we
        // use gzip compression
        if (!compression)
            builder.disableContentCompression();

        switch (authtype) {
            case "ntlm":
                CredentialsProvider ntlmCreds = new BasicCredentialsProvider();
                NTCredentials creds = new NTCredentials(username, password, workstation, domain);
                ntlmCreds.setCredentials(AuthScope.ANY, creds);
                builder.setDefaultCredentialsProvider(ntlmCreds);
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

        return builder.build();
    }
}
