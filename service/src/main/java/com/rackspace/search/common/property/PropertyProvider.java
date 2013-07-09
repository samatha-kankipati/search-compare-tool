package com.rackspace.search.common.property;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class PropertyProvider extends PropertyPlaceholderConfigurer {

    private static Map<String, String> propertiesMap;

    private static final String PROPERTY_LOCATION_OVERRIDE = "search-compare-tool.properties.location";
    private static final String SECRETS_LOCATION_OVERRIDE = "search-compare-tool.secrets.location";

    private static final String DEFAULT_PROPERTY_FILENAME = "search-compare-tool.properties";
    private static final String DEFAULT_SECRETS_FILENAME = "search-compare-tool.secrets.properties";

    public PropertyProvider() {
        super();

        this.setLocations(new Resource[]{
                EnvAwareResourceLoader.getResource(PROPERTY_LOCATION_OVERRIDE, DEFAULT_PROPERTY_FILENAME),
                EnvAwareResourceLoader.getResource(SECRETS_LOCATION_OVERRIDE, DEFAULT_SECRETS_FILENAME)
        });
    }

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props) {
        super.processProperties(beanFactory, props);

        propertiesMap = getPropertiesMap();
        for (Object key : props.keySet()) {
            String keyStr = key.toString();
            //Using default mode system_properties_mode_fallback
            String valueStr = resolvePlaceholder(keyStr, props, SYSTEM_PROPERTIES_MODE_FALLBACK);
            propertiesMap.put(keyStr, valueStr);
        }
    }

    public static Map<String,String> getPropertiesMap(){
        if (propertiesMap == null)  {
            propertiesMap =  new HashMap<String, String>();
        }
        return propertiesMap;

    }

    public static String getProperty(String name) {
        return propertiesMap.get(name);
    }

    public static String getProperty(String name, String defaultValue) {
        return (StringUtils.defaultIfEmpty(getProperty(name), defaultValue));
    }
}
