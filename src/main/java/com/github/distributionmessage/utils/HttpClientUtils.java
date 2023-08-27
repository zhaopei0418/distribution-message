package com.github.distributionmessage.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author zhaopei
 */
@Slf4j
public final class HttpClientUtils {


    @Data
    public static final class ClientProp {

        private String headerUserAgent;

        private Integer maxConn;

        private Integer monitorInterval;

        private Integer connectRequestTimeout;

        private Integer connectTimeout;

        private Integer socketTimeout;
    }

    private static CloseableHttpClient httpClient;
    private static PoolingHttpClientConnectionManager manager;
    private static ScheduledExecutorService monitorExecutor;
    private static ClientProp clientProp;

    private final static Object sysncLock = new Object();

    private static CloseableHttpClient getHttpClient() {
        if (null == httpClient) {
            synchronized (sysncLock) {
                if (null == httpClient) {
                    httpClient = createHttpClient();
                    monitorExecutor = Executors.newScheduledThreadPool(1);
                    monitorExecutor.scheduleWithFixedDelay(new TimerTask() {
                        @Override
                        public void run() {
                            manager.closeExpiredConnections();
                            manager.closeIdleConnections(clientProp.getMonitorInterval(), TimeUnit.MILLISECONDS);
                        }
                    }, clientProp.getMonitorInterval(), clientProp.getMonitorInterval(), TimeUnit.MILLISECONDS);
                }
            }
        }
        return httpClient;
    }

    private static CloseableHttpClient createHttpClient() {
        ConnectionSocketFactory plainConnectionSocketFactory = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactory.getSocketFactory();
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create().register("http", plainConnectionSocketFactory)
                .register("https", sslSocketFactory).build();

        manager = new PoolingHttpClientConnectionManager(registry);

        manager.setMaxTotal(clientProp.getMaxConn());
        manager.setDefaultMaxPerRoute(clientProp.getMaxConn());
        return HttpClients.custom().setConnectionManager(manager).build();
    }

    private static void setRequestConfig(HttpRequestBase httpRequestBase) {
        RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(clientProp.getConnectRequestTimeout())
                .setConnectTimeout(clientProp.getConnectTimeout())
                .setSocketTimeout(clientProp.getSocketTimeout()).build();
        httpRequestBase.setConfig(requestConfig);
    }

    private static void setHeader(HttpRequestBase httpRequestBase, Map<String, String> header) {
        if (null != header) {
            for (Map.Entry<String, String> entry : header.entrySet()) {
                httpRequestBase.setHeader(entry.getKey(), entry.getValue());
            }
        }
        httpRequestBase.setHeader("User-Agent", clientProp.getHeaderUserAgent());
    }

    private static void setPostParams(HttpPost httpPost, Map<String, String> params) {
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        httpPost.setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));
    }

    public static String post(URI uri, Map<String, String> header, Map<String, String> params) {
        HttpPost httpPost = new HttpPost(uri);
        setRequestConfig(httpPost);
        setHeader(httpPost, header);
        setPostParams(httpPost, params);
        String result = null;

        try {
            result = getHttpClient().execute(httpPost, new BasicResponseHandler() {
                @Override
                public String handleEntity(HttpEntity entity) throws IOException {
                    try {
                        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new ClientProtocolException(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error(String.format("url=[%s] post request error", uri), e);
        }

        return result;
    }

    public static String postString(URI uri, Map<String, String> header, JSONObject param) throws Exception {
        HttpPost httpPost = new HttpPost(uri);
        setRequestConfig(httpPost);
        setHeader(httpPost, header);
        httpPost.setEntity(new StringEntity(param.toString(), StandardCharsets.UTF_8));
        String result = null;

        try {
            result = getHttpClient().execute(httpPost, new BasicResponseHandler() {
                @Override
                public String handleEntity(HttpEntity entity) throws IOException {
                    try {
                        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                    } catch (ParseException e) {
                        throw new ClientProtocolException(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error(String.format("url=[%s] postJson request error", uri), e);
            throw e;
        }

        return result;
    }

    public static JSONObject postJson(URI uri, Map<String, String> header, JSONObject param) throws Exception {
        if (null == header) {
            header = new HashMap<>();
        }
        header.put("Content-Type", "application/json");
        return JSON.parseObject(postString(uri, header, param));
    }

    public static String get(URI uri, Map<String, String> header) throws Exception {
        HttpGet httpGet = new HttpGet(uri);
        setRequestConfig(httpGet);
        setHeader(httpGet, header);
        String result = null;

        try {
            result = getHttpClient().execute(httpGet, new BasicResponseHandler() {
                @Override
                public String handleEntity(HttpEntity entity) throws IOException {
                    try {
                        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                    } catch (ParseException e) {
                        throw new ClientProtocolException(e);
                    }
                }
            });
        } catch (IOException e) {
            log.error(String.format("url=[%s] get request error", uri), e);
            throw e;
        }

        return result;
    }

    public static void setClientProp(ClientProp clientProp) {
        HttpClientUtils.clientProp = clientProp;
    }
}
