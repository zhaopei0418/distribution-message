package com.github.distributionmessage.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "distribution")
@Data
public class DistributionProp {

    private boolean update = true;

    private Integer cacheSize;

    private String hostName;

    private Integer port;

    private String queueManager;

    private String channel;

    private Integer ccsid;

    @Value("${distribution.second.ccsid}")
    private Integer secondCcsid;

    @Value("${distribution.third.ccsid}")
    private Integer thirdCcsid;

    private String queueName;

    private Integer minConcurrency;

    private Integer maxConcurrency;

    private Integer keepAliveSeconds;

    private Integer queueCapacity;

    private String  threadNamePrefix;

    private Boolean conditionMutualExclusion;

    private Map<String, String> dxpidDistribution;

    private Map<String, String> msgtypeDistribution;

    private Map<String, Integer> percentageDistribution;

    //比重总数
    private Integer percentageTotal;

    //队列比例范围，从percentageDistribution转化而来
    private Map<String, Integer[]> queuePercentage;

    private List<String> randomDistribution;

    private String defaultQueue;
}
