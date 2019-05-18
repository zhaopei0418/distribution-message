package com.github.distributionmessage.utils;

import org.apache.commons.logging.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CommonUtils {

    public static void logError(Log log, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        log.error(sw.toString());
    }
}
