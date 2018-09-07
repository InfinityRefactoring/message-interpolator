package com.infinityrefactoring.util.message;

import java.util.Locale;
import java.util.function.Function;

import javax.enterprise.inject.spi.CDI;

import com.infinityrefactoring.util.text.Expression;

public interface MessageTemplateProvider {

	public static MessageTemplateProvider getInstance() {
		return CDI.current().select(MessageTemplateProvider.class).get();
	}

	String get(String key, Locale locale, Function<Expression, ?> formatter);

}
