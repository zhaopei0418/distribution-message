package com.github.distributionmessage.transformer;

import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.utils.DistributionUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.aspectj.util.FileUtil;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.integration.transformer.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @author zhaopei
 */
@Slf4j
public class WrapTransformer implements Transformer {

    @Override
    public Message<?> transform(Message<?> message) {
        try {
            Assert.notNull(message, "Message must not be null");
            Object payload = message.getPayload();
            Assert.notNull(payload, "Message payload must not be null");
            String strPayload = null;
            MessageHeaders messageHeaders = message.getHeaders();
            String senderId = messageHeaders.get(CommonConstant.SENDER_ID, String.class);
            String receiveId = messageHeaders.get(CommonConstant.RECEIVE_ID, String.class);
            String result = null;
            if (payload instanceof byte[]) {
                result = DistributionUtils.wrap((byte[]) payload, senderId, receiveId);
            } else if (payload instanceof File) {
                File pl = (File) payload;
                String fileName = messageHeaders.get(CommonConstant.HEADER_FILE_NAME, String.class);
                if (pl.exists()) {
                    result = DistributionUtils.wrap(FileUtils.readFileToByteArray(pl), senderId, receiveId);
                    if (pl.delete()) {
                        log.error("file [{}] delete fail.", fileName);
                    }
                } else {
                    log.info("file [{}] message not exists. not handler.", fileName);
                    return null;
                }
            } else {
                strPayload = (String) payload;
                result = DistributionUtils.wrap(strPayload, senderId, receiveId);
            }

            if (StringUtils.isBlank(result)) {
                return null;
            }
            Message<?> transformedMessage = new DefaultMessageBuilderFactory().withPayload(result.getBytes(StandardCharsets.UTF_8))
                    .copyHeaders(message.getHeaders())
                    .build();
//            log.info("transformed message [{}]", transformedMessage);
            return transformedMessage;
        }
        catch (Exception e) {
            throw new MessagingException(message, "failed to wrap Message", e);
        }
    }
}
