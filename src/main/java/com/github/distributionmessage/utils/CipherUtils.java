package com.github.distributionmessage.utils;

import com.github.distributionmessage.constant.CommonConstant;
import com.sansec.api.Custom;
import com.sansec.jce.provider.SwxaProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Base64Utils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * @author zhaopei
 */
@Slf4j
public class CipherUtils {

    public static void init(String userDir) {
        try {
            if (null != Security.getProvider(SwxaProvider.PROVIDER_NAME)) {
                Security.removeProvider(SwxaProvider.PROVIDER_NAME);
            } else {
                Security.addProvider(new SwxaProvider(userDir));
            }
        } catch (Exception e) {
            log.error("驱动器动态注册失败!", e);
        }
    }

    public static PublicKey getPublicKey(String signFile) {
        PublicKey publickey = null;
        try {
            X509Certificate x509Certificate =  createX509Certificate(signFile);
            if (null != x509Certificate) {
                publickey = x509Certificate.getPublicKey();
            }
        } catch (Exception e) {
            log.error("get public key error!", e);
        }
        return publickey;
    }

    private static X509Certificate createX509Certificate(String signFile) {
        X509Certificate x509Certificate = null;
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", "SwxaJCE");
            x509Certificate = (X509Certificate) factory.generateCertificate(new FileInputStream(new File(signFile)));
        } catch (Exception e) {
            log.error("create X509 certificate error!", e);
        }

        return x509Certificate;
    }

    public static String getX509CertificateString(String signFile) {
        X509Certificate x509Certificate = createX509Certificate(signFile);
        String result = null;
        if (null != x509Certificate) {
            try {
                result = Base64Utils.encodeToString(x509Certificate.getEncoded());
            } catch (Exception e) {
                log.error("get X509 certificate string error!", e);
            }
        }

        return result;
    }

    public static long getSerialNumber(String signFile) {
        X509Certificate x509Certificate = createX509Certificate(signFile);
        return null == x509Certificate ? 0 : x509Certificate.getSerialNumber().longValue();
    }

    public static String getSm3Digest(String data) {
        String digest = null;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SM3", "SwxaJCE");
            messageDigest.update(data.getBytes(StandardCharsets.UTF_8));
            digest = Base64Utils.encodeToString(messageDigest.digest());
        } catch (Exception e) {
            log.error("sm3 digest error!", e);
        }

        return digest;
    }

    public static String getSm3DigestPath(String dataPath) {
        String digest = null;
        try {
            digest = getSm3Digest(FileUtils.readFileToString(new File(dataPath), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("sm3 digest dataPath, error!", e);
        }
        return digest;
    }

    public static KeyPair getSm2KeyPair(int index) {
        KeyPair keyPair = null;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("SM2", "SwxaJCE");
            keyPairGenerator.initialize(index << 16);
            keyPair = keyPairGenerator.genKeyPair();
        } catch (Exception e) {
            log.error("get sm2 key pair error!", e);
        }

        return keyPair;
    }

    public static String getSm2SignValue(int index, String digest) {
        String sign = null;
        try {
            Signature signature = Signature.getInstance("SM2", "SwxaJCE");
            KeyPair keyPair = getSm2KeyPair(index);
            if (null != keyPair) {
                signature.initSign(keyPair.getPrivate());
                signature.update(digest.getBytes(StandardCharsets.UTF_8));
                sign = Base64Utils.encodeToString(signature.sign());
            }
        } catch (Exception e) {
            log.error("get sign value error!", e);
        }

        return sign;
    }

    public static String customSign(String userConfig, int signKeyIndex, String userCertDir, String encryption, String xml) {
        if (StringUtils.isEmpty(encryption)) {
            encryption = "sm";
        }

        try {
            if (null != Security.getProvider(SwxaProvider.PROVIDER_NAME)) {
                Security.removeProvider(SwxaProvider.PROVIDER_NAME);
            }
            Custom custom = new Custom("hsm", userConfig, null, signKeyIndex, userCertDir, encryption);
            return custom.doSign(xml);
        } catch (Exception e) {
            log.error("custom sign error!", e);
        }

        return null;
    }

    public static void main(String[] args) throws Exception {

//        log.info("now===" + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()));
//        log.info("now11111===" + CommonConstant.LOCAL_DATE_TIME.format(LocalDateTime.now()));
    }

    public static void test() {
        try {
            String userDir = "/home/xian/distribution-message/swsds.ini";
            String signFile = "/home/xian/distribution-message/sign.cer";
            String xmlFile = "/home/xian/distribution-message/ceb303.xml";
            String xmlStr = FileUtils.readFileToString(new File(xmlFile), StandardCharsets.UTF_8);
            if (xmlStr.indexOf("<ceb:") != -1) {
                xmlStr = xmlStr.replaceAll("<ceb:", "<");
                xmlStr = xmlStr.replaceAll("</ceb:", "</");
            }

            int keyIndex = 57;
            String digest = null;
            String digest222 = "Hzmny3gpJ9Di7R2s1GBa/GDk2+eBiVWxJzzTkHIkwu4=";
//        init(userDir);
            Custom custom = new Custom("hsm", userDir, null, 57, signFile, "sm");

//            digest = getSm3DigestPath(xmlFile);
//            log.info(String.format("digest = [%s]", digest));
//            log.info(String.format("sign = [%s]", getSm2SignValue(keyIndex, digest)));
//            log.info(String.format("sign 22222 = [%s]", getSm2SignValue(keyIndex, digest222)));
//            log.info(String.format("serial number = [%s]", getSerialNumber(signFile)));
//            log.info(String.format("x509certificate string = [%s]", getX509CertificateString(signFile)));

            log.info(String.format("custom sign = [%s]", custom.doSign(xmlStr)));
        } catch (Exception e) {
            log.error("test test =============================== error", e);
        }
    }
}
