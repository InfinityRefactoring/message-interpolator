package com.infinityrefactoring.util.message;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.builder;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream.Builder;

import javax.annotation.PostConstruct;
import javax.cache.annotation.CacheDefaults;
import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

/**
 * @author Thomás Sousa Silva (ThomasSousa96)
 */
@ApplicationScoped
@CacheDefaults(cacheName = "messages-cache")
public class MessageProvider {

	/**
	 * Compare the message map using the {@linkplain MessageSource.MESSAGE_ORDINAL} property.
	 */
	private static final Comparator<Map<String, String>> MESSAGE_COMPARATOR = Comparator.comparing(m -> {
		String ordinal = m.get(MessageSource.MESSAGE_ORDINAL);
		return ((ordinal == null) ? null : Integer.parseInt(ordinal));
	}, Comparator.nullsFirst(Integer::compareTo));

	@Inject
	private Logger logger;

	@Inject
	private Instance<MessageSource> instances;

	@CacheResult
	public Map<String, String> getMessages(@CacheKey Locale locale) {
		if (logger.isLoggable(Level.CONFIG)) {
			logger.info("Getting messages map for locale: " + ((locale == null) ? null : locale.toLanguageTag()));
		}

		Builder<MessageSource> streamBuilder = builder();
		instances.forEach(streamBuilder::add);
		return streamBuilder.build()
				.map(s -> s.getMessages(locale))
				.sorted(MESSAGE_COMPARATOR)
				.reduce(new HashMap<>(), (m1, m2) -> {
					m1.putAll(m2);
					return m1;
				});
	}

	@PostConstruct
	private void postConstruct() {
		if (logger.isLoggable(Level.CONFIG)) {
			Builder<MessageSource> streamBuilder = builder();
			instances.forEach(streamBuilder::add);
			String sourceNames = streamBuilder.build()
					.map(s -> "    " + s.getName())
					.collect(joining(",\n"));
			logger.info("\nFound message sources: [\n" + sourceNames + "\n]");
		}
	}

}
