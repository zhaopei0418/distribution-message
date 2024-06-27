package com.github.distributionmessage.integration.file;

import com.github.distributionmessage.constant.CommonConstant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.integration.file.filters.FileListFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhaopei
 */
@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class FileExtensionFilter implements FileListFilter<File> {

    private String extension;

    private int maxNum;

    public boolean accept(File file) {
        return null != file && file.exists() && !file.isHidden()
                && StringUtils.endsWithIgnoreCase(file.getName(), this.extension);
    }

    public File reading(File file) {
        try {
            File dest = new File(file.getAbsolutePath() + CommonConstant.READ_FILE_SUFFIX);
            FileUtils.moveFile(file, dest);
            return dest;
        } catch (Exception e) {
            log.error("reading file error", e);
            return null;
        }
    }

    @Override
    public List<File> filterFiles(File[] files) {
        List<File> accepted = new ArrayList<>();
        int count = 0;
        for (File f : files) {
            File file = null;
            if (this.accept(f)) {
                file = this.reading(f);
            }
            if (null != file) {
                accepted.add(file);
                count++;
            }
            if (this.maxNum <= count) {
                break;
            }
        }
        return accepted;
    }
}
