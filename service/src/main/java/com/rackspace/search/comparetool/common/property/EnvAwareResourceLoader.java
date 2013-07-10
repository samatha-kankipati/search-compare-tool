package com.rackspace.search.comparetool.common.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;

public final class EnvAwareResourceLoader {

    private static Logger log = LoggerFactory.getLogger(EnvAwareResourceLoader.class);

    public static final String SYS_CONFIG_LOCATION = "config.location";

    private EnvAwareResourceLoader() {
    }

    public static Resource getResource(String overrideLocation, String fileName) {
        final String resourceFilename;
        final String overrideLocationValue = nullSafeGetSystemProperty(overrideLocation);

        // If an override location is set, then use that location
        if (overrideLocationValue != null && !overrideLocationValue.equals("")) {
            resourceFilename = overrideLocationValue;
        } else {
            resourceFilename = nullSafeGetSystemProperty(SYS_CONFIG_LOCATION) + "/" + fileName;
        }
        return getConfigFile(resourceFilename);
    }

    private static String nullSafeGetSystemProperty(String property) {
        if (property != null && !property.equals("")) {
            return System.getProperty(property, "");
        }
        return "";
    }

    private static Resource getConfigFile(String fileName) {
        final File file = new File(fileName);
        if (file.exists() && file.canRead()) {
            return new FileSystemResource(file);
        } else {
            return new ClassPathResource(fileName);
        }
    }
}
