package com.seudev.util.message;

import java.util.Locale;
import java.util.Map;

/**
 * @author Thomás Sousa Silva (ThomasSousa96)
 */
public interface MessageSource {

	public static final String MESSAGE_ORDINAL = "message_ordinal";

	public Map<String, String> getMessages(Locale locale);

	public String getName();

}
