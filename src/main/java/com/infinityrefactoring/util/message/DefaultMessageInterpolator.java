package com.infinityrefactoring.util.message;

import java.util.Locale;

import javax.el.ELProcessor;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

@RequestScoped
public class DefaultMessageInterpolator implements MessageInterpolator {

	@Inject
	private MessageTemplateProvider properties;

	@Inject
	private ELProcessor elProcessor;

	@Inject
	private Locale locale;

	@Override
	public MessageInterpolator add(String key, Object value) {
		elProcessor.defineBean(key, value);
		return this;
	}

	@Override
	public String get(String key) {
		return get(key, locale);
	}

	@Override
	public String get(String key, Locale locale) {
		return properties.get(key, locale, expression -> elProcessor.eval(expression.getSubExpression()));
	}

}
