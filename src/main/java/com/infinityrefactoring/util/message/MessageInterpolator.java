package com.infinityrefactoring.util.message;

import java.util.Locale;

import javax.enterprise.inject.spi.CDI;

public interface MessageInterpolator {

	public static MessageInterpolator getInstance() {
		return CDI.current().select(MessageInterpolator.class).get();
	}

	public MessageInterpolator add(String key, Object value);

	public String get(String key);

	public String get(String key, Locale locale);

}
