package com.github.distributionmessage.utils;

import com.github.distributionmessage.config.HttpClientProp;
import com.github.distributionmessage.config.IntegrationConfiguration;
import com.github.distributionmessage.constant.CommonConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.springframework.integration.file.transformer.FileToByteArrayTransformer;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class MessageUtils {

    private static HttpClientProp httpClientProp;

    private static MessageChannel messageChannel;

    public static void setHttpClientProp(HttpClientProp httpClientProp) {
        MessageUtils.httpClientProp = httpClientProp;
    }

    public static void setMessageChannel(MessageChannel messageChannel) {
        MessageUtils.messageChannel = messageChannel;
    }

    public static void removeResendSignKey(byte[] bytesPayload) {
        try {
            if (null != bytesPayload) {
                String key = Base64.encodeBase64String(bytesPayload);
                IntegrationConfiguration.RESET_MAP.remove(key);
            }
        } catch (Exception e) {
            log.error("remove resend sign key error", e);
        }
    }

    public static void resendSignMessage(Message<?> message) {
        try {
            if (null != message) {
                Object payload = message.getPayload();
                byte[] bytesPayload = null;
                if (null != payload) {
                    if (payload instanceof byte[]) {
                        bytesPayload = (byte[]) payload;
                    } else if (payload instanceof File) {
                        FileToByteArrayTransformer fileToByteArrayTransformer = new FileToByteArrayTransformer();
                        fileToByteArrayTransformer.setDeleteFiles(true);
                        message = fileToByteArrayTransformer.transform(message);
                        bytesPayload = (byte[]) message.getPayload();
                    } else {
                        bytesPayload = ((String) payload).getBytes(StandardCharsets.UTF_8);
                    }
                    String key = Base64.encodeBase64String(bytesPayload);

                    synchronized (MessageUtils.class) {
                        if (IntegrationConfiguration.RESET_MAP.containsKey(key)) {
                            if (httpClientProp.getRetryTimes() > IntegrationConfiguration.RESET_MAP.get(key).intValue()) {
                                log.info(String.format("resend sign [%b].", messageChannel.send(message, httpClientProp.getConnectTimeout())));
                                IntegrationConfiguration.RESET_MAP.get(key).incrementAndGet();
                            } else {
                                IntegrationConfiguration.RESET_MAP.remove(key);
                            }
                        } else {
                            log.info(String.format("resend sign [%b].", messageChannel.send(message, httpClientProp.getConnectTimeout())));
                            IntegrationConfiguration.RESET_MAP.put(key, new AtomicInteger(0));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("resend sign error", e);
        }
    }
}
