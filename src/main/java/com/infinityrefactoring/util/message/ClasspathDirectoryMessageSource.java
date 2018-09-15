package com.infinityrefactoring.util.message;

import static com.infinityrefactoring.util.io.Resources.getFilenameWithoutLocale;
import static com.infinityrefactoring.util.io.Resources.getLocaleOfFilename;
import static com.infinityrefactoring.util.io.Resources.getResources;
import static com.infinityrefactoring.util.io.Resources.readLines;
import static java.util.logging.Level.FINEST;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.maxBy;

import java.net.URL;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
@CacheDefaults(cacheName = "classpath-directory-messages-cache")
public class ClasspathDirectoryMessageSource implements MessageSource {

	public static final Pattern MESSAGE_ORDINAL_PATTERN = Pattern.compile("(message_ordinal)([\\s]*)=([\\s]*)([0-9]+)([\\s]*)");
	private static final Comparator<String[]> COMPARATOR = Comparator.comparing(array -> {
		if (array.length == 1) {
			return null;
		}
		Matcher matcher = MESSAGE_ORDINAL_PATTERN.matcher(array[0]);
		if (matcher.matches()) {
			String value = matcher.group(4);
			return Integer.valueOf(value);
		}
		return null;
	}, Comparator.nullsFirst(Integer::compareTo));

	@Inject
	private Logger logger;

	@Inject
	@ConfigProperty(name = "message.directories", defaultValue = "META-INF/messages/extended")
	private Set<String> directories;

	@Inject
	@ConfigProperty(name = "message.directories.files.suffix", defaultValue = "")
	private String messageFilesSuffix;

	@Override
	@CacheResult
	public Map<String, String> getMessages(@CacheKey Locale locale) {
		Map<String, String> map = directories.stream()
				.flatMap(directory -> getResources(directory).stream())
				.filter(hasEqualLocale(locale))
				.collect(groupingBy(url -> getFilenameWithoutLocale(url, messageFilesSuffix),
						collectingAndThen(mapping(url -> readLines(url).split("\n", 2),
								maxBy(COMPARATOR)), optional -> {
									String[] array = optional.get();
									if ((array.length == 1) || MESSAGE_ORDINAL_PATTERN.matcher(array[0]).matches()) {
										return array[array.length - 1];
									}
									return array[0].concat(array[1]);
								})));

		if (map.isEmpty() && logger.isLoggable(FINEST)) {
			logger.fine("Not found messages for locale: " + ((locale == null) ? null : locale.toLanguageTag()));
		}
		return map;
	}

	@Override
	public String getName() {
		return "ClasspathDirectoryMessageSource";
	}

	private Predicate<? super URL> hasEqualLocale(Locale locale) {
		return url -> {
			if (Objects.equals(getLocaleOfFilename(url.getPath(), messageFilesSuffix), locale)) {
				if (logger.isLoggable(FINEST)) {
					logger.finest("Loading messages from file: " + url);
				}
				return true;
			}
			return false;
		};
	}

}
