/*******************************************************************************
 * Copyright 2018 InfinityRefactoring
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.infinityrefactoring.util.message;

import static com.infinityrefactoring.util.text.ExpressionDefinition.DOLLAR_CURLY_BRACKET;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.infinityrefactoring.util.text.Expression;
import com.infinityrefactoring.util.text.ExpressionDefinition;
import com.infinityrefactoring.util.text.ExpressionException;

/**
 * Default implementation of the {@linkplain MessageTemplateProvider} interface.
 *
 * @author Thomás Sousa Silva (ThomasSousa96)
 */
@ApplicationScoped
public class DefaultMessageTemplateProvider implements MessageTemplateProvider {

	static final ExpressionDefinition EXPRESSION_DEFINITION = DOLLAR_CURLY_BRACKET;

	/**
	 * The default properties file name suffix: {@value}
	 */
	private static final String PROPERTIES_FILE_NAME_SUFFIX = ".properties";

	/**
	 * Compare the properties map using the {@code "message_ordinal"} property.
	 */
	private static final Comparator<Properties> PROPERTIES_COMPARATOR = Comparator.comparing(p -> {
		String priority = p.getProperty("message_ordinal");
		return ((priority == null) ? null : Integer.parseInt(priority));
	}, Comparator.nullsLast(Integer::compareTo).reversed());

	@Inject
	@ConfigProperty(name = "message.files", defaultValue = "messages")
	private Set<String> resources;

	@Inject
	@ConfigProperty(name = "message.reload.interval", defaultValue = "0")
	private long reloadInterval;

	@Inject
	@ConfigProperty(name = "message.reload.interval.unit", defaultValue = "SECONDS")
	private ChronoUnit reloadIntervalUnit;

	private final Map<Locale, MessageTemplateMap> MESSAGE_TEMPLATES = new HashMap<>();

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
	private static String getLocalizedFilename(String baseName, Locale locale) {
		if (baseName == null) {
			throw new IllegalArgumentException("The baseName cannot be null.");
		} else if (locale == null) {
			return (baseName + PROPERTIES_FILE_NAME_SUFFIX);
		}
		return (baseName + '_' + locale.toLanguageTag().replace('-', '_') + PROPERTIES_FILE_NAME_SUFFIX);
	}

	private static Properties loadProperties(URL url) throws IOException {
		try (InputStream in = url.openStream()) {
			Properties properties = new Properties();
			properties.load(in);
			return properties;
		}
	}

	@Override
	public String get(String key, Locale locale, Function<Expression, ?> formatter) {
		if (key == null) {
			throw new IllegalArgumentException("The key must be not null");
		}
		MessageTemplateMap messageTemplateMap = MESSAGE_TEMPLATES.computeIfAbsent(locale, l -> {
			Set<URL> resources = getLocalizedResources(l);
			return new MessageTemplateMap(resources);
		});

		load(messageTemplateMap);

		String template = messageTemplateMap.get(key);
		if (template == null) {
			if (locale == null) {
				throw new IllegalArgumentException("Not message for key: " + key);
			}
			return get(key, null, formatter);
		}

		try {
			return EXPRESSION_DEFINITION.interpolate(template, formatter);
		} catch (RuntimeException ex) {
			throw new ExpressionException("The message cannot be interpolate: " + key, ex);
		}
	}

	private Set<URL> getLocalizedResources(Locale locale) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		return resources.stream()
				.map(baseName -> getLocalizedFilename(baseName, locale))
				.flatMap(name -> {
					try {
						return Collections.list(classLoader.getResources(name)).stream();
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				})
				.collect(toSet());
	}

	private void load(MessageTemplateMap messageTemplateMap) {
		if (needLoad(messageTemplateMap)) {
			try {
				LinkedList<Properties> list = new LinkedList<>();
				for (URL url : messageTemplateMap.getUrls()) {
					list.add(loadProperties(url));
				}
				Properties properties = mergeProperties(list);
				messageTemplateMap.setMessages(properties);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	/**
	 * Merge the properties with base in the priority.
	 *
	 * @param list the properties
	 * @return the merged properties
	 */
	private Properties mergeProperties(List<Properties> list) {
		return list.stream()
				.sorted(PROPERTIES_COMPARATOR)
				.reduce((p1, p2) -> {
					p1.putAll(p2);
					return p1;
				}).orElseGet(Properties::new);
	}

	/**
	 * Returns true if is necessary load the properties.
	 *
	 * @return true if is necessary load the properties
	 */
	private boolean needLoad(MessageTemplateMap messageTemplateMap) {
		return (messageTemplateMap.getLastLoad() == null) || (((reloadInterval > 0) && (reloadIntervalUnit.between(messageTemplateMap.getLastLoad(), Instant.now()) >= reloadInterval)));
	}

	private static class MessageTemplateMap {

		private final Set<URL> RESOURCES;
		private final Map<String, String> TEMPLATES;
		private Instant lastLoad;

		public MessageTemplateMap(Set<URL> resources) {
			RESOURCES = resources;
			TEMPLATES = new HashMap<>();
		}

		public String get(String key) {
			return TEMPLATES.get(key);
		}

		public Instant getLastLoad() {
			return lastLoad;
		}

		public Set<URL> getUrls() {
			return RESOURCES;
		}

		public void setMessages(Properties properties) {
			TEMPLATES.keySet().stream()
					.filter(k -> !properties.containsKey(k))
					.forEach(template -> {
						EXPRESSION_DEFINITION.removeCache(template);
						TEMPLATES.remove(template);
					});

			properties.forEach((key, value) -> {
				String template = value.toString();
				String oldTemplate = TEMPLATES.put(key.toString(), template);
				if ((oldTemplate != null) && !template.equals(oldTemplate)) {
					EXPRESSION_DEFINITION.removeCache(oldTemplate);
				}
			});
			lastLoad = Instant.now();
		}

	}

}
