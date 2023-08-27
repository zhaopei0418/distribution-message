package com.github.distributionmessage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zhaopei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignWrapParam {

    private String serviceUrl;

    private String ieType;
}
