package com.infinityrefactoring.util.message;

import static java.util.logging.Level.FINEST;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.cache.annotation.CacheDefaults;
import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * @author Thomás Sousa Silva (ThomasSousa96)
 */
@ApplicationScoped
@CacheDefaults(cacheName = "property-messages-cache")
public class PropertyMessageSource implements MessageSource {
    
    /**
     * Compare the message map using the {@linkplain #MESSAGE_ORDINAL} property.
     */
    public static final Comparator<Properties> MESSAGE_COMPARATOR = Comparator.comparing(p -> {
        String ordinal = p.getProperty(MessageSource.MESSAGE_ORDINAL);
        return ((ordinal == null) ? null : Integer.parseInt(ordinal));
    }, Comparator.nullsFirst(Integer::compareTo));
    
    @Inject
    private Logger logger;
    
    @Inject
    @ConfigProperty(name = "message.files.suffix", defaultValue = ".properties")
    private String messageFileSuffix;
    
    @Inject
    @ConfigProperty(name = "message.files", defaultValue = "META-INF/messages/messages")
    private Set<String> resources;
    
    @Override
    @CacheResult
    public Map<String, String> getMessages(@CacheKey Locale locale) {
        Properties properties = getLocalizedResources(locale)
                .map(this::loadProperties)
                .sorted(MESSAGE_COMPARATOR)
                .reduce((m1, m2) -> {
                    m1.putAll(m2);
                    return m1;
                }).orElse(null);
        
        Map<String, String> map = new HashMap<>();
        if (properties != null) {
            properties.forEach((key, value) -> map.put(key.toString(), value.toString()));
        }
        if (map.isEmpty() && logger.isLoggable(FINEST)) {
            logger.fine("Not found messages for locale: " + ((locale == null) ? null : locale.toLanguageTag()));
        }
        return map;
    }
    
    @Override
    public String getName() {
        return "PropertyMessageSource";
    }
    
    /**
     * Returns the properties file name.
     * <h1>Examples:</h1>
     * <ul>
     * <li>getPropertiesFileName("messages", null) returns "messages.properties"</li>
     * <li>getPropertiesFileName("messages", Locale.forLanguageTag("pt-BR")) returns "messages_pt_BR.properties"</li>
     * </ul>
     *
     * @param baseName the properties file name
     * @param locale the locale
     * @return If the locale is null,then returns baseName.properties else returns baseName_LanguageTag.properties
     */
    private String getLocalizedFilename(String baseName, Locale locale) {
        if (baseName == null) {
            throw new IllegalArgumentException("The baseName cannot be null.");
        } else if (locale == null) {
            return (baseName + messageFileSuffix);
        }
        return (baseName + '_' + locale.toLanguageTag().replace('-', '_') + messageFileSuffix);
    }
    
    private Stream<URL> getLocalizedResources(Locale locale) {
        ClassLoader classLoader = PropertyMessageSource.class.getClassLoader();
        return resources.stream()
                .map(baseName -> getLocalizedFilename(baseName, locale))
                .flatMap(name -> {
                    try {
                        return Collections.list(classLoader.getResources(name)).stream();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }
    
    private Properties loadProperties(URL url) {
        try (InputStream in = url.openStream()) {
            Properties properties = new Properties();
            if (logger.isLoggable(FINEST)) {
                logger.finest("Loading messages from file: " + url);
            }
            properties.load(in);
            return properties;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
