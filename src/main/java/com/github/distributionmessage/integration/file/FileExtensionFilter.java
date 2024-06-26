package com.github.distributionmessage.integration.file;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.integration.file.filters.AbstractFileListFilter;

import java.io.File;

/**
 * @author zhaopei
 */
@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class FileExtensionFilter extends AbstractFileListFilter<File> {

    private String extension;

    @Override
    public boolean accept(File file) {
        return null != file && file.exists() && StringUtils.endsWithIgnoreCase(file.getName(), this.extension);
    }

}
