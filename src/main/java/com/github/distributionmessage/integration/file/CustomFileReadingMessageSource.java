package com.github.distributionmessage.integration.file;

import com.github.distributionmessage.constant.CommonConstant;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.support.AbstractIntegrationMessageBuilder;

import java.io.File;
import java.security.SecureRandom;

/**
 * @author zhaopei
 */
@Getter
@Setter
@Slf4j
public class CustomFileReadingMessageSource extends FileReadingMessageSource {

    private String senderId;

    private String receiverId;

    private String serviceUrl;

    private String ieType;

    private String startNode;

    private String endNode;

    public CustomFileReadingMessageSource(String startNode, String endNode) {
        super(null);
        this.startNode = startNode;
        this.endNode = endNode;
    }

    public CustomFileReadingMessageSource(String senderId, String receiverId, String serviceUrl, String ieType) {
        super(null);
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.serviceUrl = serviceUrl;
        this.ieType = ieType;
    }

    @Override
    protected AbstractIntegrationMessageBuilder<File> doReceive() {
        AbstractIntegrationMessageBuilder<File> fileAbstractIntegrationMessageBuilder = super.doReceive();
        if (null != fileAbstractIntegrationMessageBuilder) {
            if (StringUtils.isNotBlank(this.senderId)) {
                fileAbstractIntegrationMessageBuilder.setHeader(CommonConstant.SENDER_ID, this.senderId);
            }
            if (StringUtils.isNotBlank(this.receiverId)) {
                fileAbstractIntegrationMessageBuilder.setHeader(CommonConstant.RECEIVE_ID, this.receiverId);
            }
            if (StringUtils.isNotBlank(this.serviceUrl)) {
                fileAbstractIntegrationMessageBuilder.setHeader(CommonConstant.SIGN_AND_WRAP_SERVICE_URL, this.serviceUrl);
            }
            if (StringUtils.isNotBlank(this.ieType)) {
                fileAbstractIntegrationMessageBuilder.setHeader(CommonConstant.SIGN_AND_WRAP_IE_TYPE, this.ieType);
            }
            if (StringUtils.isNotBlank(this.startNode)) {
                fileAbstractIntegrationMessageBuilder.setHeader(CommonConstant.START_NODE, this.startNode);
            }
            if (StringUtils.isNotBlank(this.endNode)) {
                fileAbstractIntegrationMessageBuilder.setHeader(CommonConstant.END_NODE, this.endNode);
            }
        }
        return fileAbstractIntegrationMessageBuilder;
    }

    public static CustomFileReadingMessageSource wrapMessageSource(String senderId, String receiverId) {
        return new CustomFileReadingMessageSource(senderId, receiverId, null, null);
    }

    public static CustomFileReadingMessageSource svWrapMessageSource(String startNode, String endNode) {
        return new CustomFileReadingMessageSource(startNode, endNode);
    }

    public static CustomFileReadingMessageSource signAndWrapMessageSource(String serviceUrl, String ieType) {
        return new CustomFileReadingMessageSource(null, null, serviceUrl, ieType);
    }
}
