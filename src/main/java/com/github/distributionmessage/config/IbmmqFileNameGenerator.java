package com.github.distributionmessage.config;

import org.springframework.messaging.Message;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by zhaopei on 17/12/20.
 */
public class IbmmqFileNameGenerator implements org.springframework.integration.file.FileNameGenerator {

    private String prefix = "";

    private String suffix = "";

    private Boolean headerId = false;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS");

    public IbmmqFileNameGenerator(String prefix, String suffix, Boolean headerId) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.headerId = headerId;
    }

    public IbmmqFileNameGenerator() {

    }

    @Override
    public String generateFileName(Message<?> message) {
        StringBuffer buffer = new StringBuffer(this.prefix + "_");
        buffer.append(TIME_FORMAT.format(Calendar.getInstance().getTime()));
        if (headerId) {
            buffer.append("_" + message.getHeaders().getId());
        }
        buffer.append(this.suffix);
        return buffer.toString();
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public Boolean getHeaderId() {
        return headerId;
    }

    public void setHeaderId(Boolean headerId) {
        this.headerId = headerId;
    }
}
