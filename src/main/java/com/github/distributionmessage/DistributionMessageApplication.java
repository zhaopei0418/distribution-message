package com.github.distributionmessage;

import com.github.distributionmessage.utils.CommonUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;

@SpringBootApplication
@EnableIntegration
@Configuration
public class DistributionMessageApplication {
    public static void main(String[] args) {
        ApplicationContext applicationContext = SpringApplication.run(DistributionMessageApplication.class, args);
        CommonUtils.setApplicationContext(applicationContext);
        CommonUtils.initParams();
    }
}
