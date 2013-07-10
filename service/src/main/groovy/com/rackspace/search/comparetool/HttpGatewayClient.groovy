package com.rackspace.search.comparetool

import groovyx.net.http.RESTClient
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams

import java.security.SecureRandom
import java.security.cert.CertificateException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpGatewayClient {

    public static RESTClient getRESTClient(String url, int connectionTimeout) throws Exception {
        RESTClient client = new RESTClient(url)
        setConnectionTimeout(client, connectionTimeout)
        if (url.startsWith("https")) {
            registerTrustAllCerts(client)
        }
        return client
    }

    private static void setConnectionTimeout(RESTClient client, int connectionTimeout) {
        HttpParams httpParams = client.getClient().getParams()
        HttpConnectionParams.setConnectionTimeout(httpParams, connectionTimeout)
        HttpConnectionParams.setSoTimeout(httpParams, connectionTimeout)
    }

    private static void registerTrustAllCerts(RESTClient client) {

        TrustManager[] trustAllCerts = [
                new X509TrustManager() {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                    throws CertificateException {
                    }

                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
                    throws CertificateException {
                    }
                }];

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        SSLSocketFactory sf = new SSLSocketFactory(sslContext);
        sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)

        SchemeRegistry sr = client.client.getConnectionManager().getSchemeRegistry();
        sr.register(new Scheme("https", sf, 443));

    }
}
