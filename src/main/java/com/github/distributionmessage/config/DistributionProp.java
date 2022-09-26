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

    private Integer poolSize;

    private List<String> otherInputQueue;

    private List<String> otherRabbitInputQueue;

    private List<String> otherDirectorInput;

    private List<String> otherOutputQueue;

    private List<String> rabbitOtherOutputQueue;

    private Boolean conditionMutualExclusion;

    private Map<String, String> dxpidDistribution;

    private Map<String, String> msgtypeDistribution;

    private Map<String, String> senderIdDistribution;

    private Map<String, Integer> percentageDistribution;

    //比重总数
    private Integer percentageTotal;

    //队列比例范围，从percentageDistribution转化而来
    private Map<String, Integer[]> queuePercentage;

    private List<String> randomDistribution;

    private String defaultQueue;

    private String filePrefix;

    private String fileSuffix;

    private Boolean unWrap;
}
