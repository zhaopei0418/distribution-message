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
public class SvWrapParam {

    private String startNode;

    private String endNode;
}
