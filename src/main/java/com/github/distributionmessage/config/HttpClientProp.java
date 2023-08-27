package com.github.distributionmessage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author zhaopei
 */
@Component
@ConfigurationProperties(prefix = "httpclient")
@Data
public class HttpClientProp {

    private String headerUserAgent;

    private Integer maxConn;

    private Integer monitorInterval;

    private Integer connectRequestTimeout;

    private Integer connectTimeout;

    private Integer socketTimeout;
}
