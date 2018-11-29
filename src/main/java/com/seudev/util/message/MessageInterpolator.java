package com.seudev.util.message;

import java.util.List;
import java.util.Locale;

import javax.enterprise.inject.spi.CDI;

public interface MessageInterpolator {
    
    public static MessageInterpolator getInstance() {
        return CDI.current().select(MessageInterpolator.class).get();
    }
    
    public MessageInterpolator add(String key, Object value);
    
    public default String get(String key) {
        return get(key, true);
    }
    
    public String get(String key, boolean required);
    
    public default String get(String key, List<Locale> locales) {
        return get(key, locales, true);
    }
    
    public String get(String key, List<Locale> locales, boolean required);
    
}
