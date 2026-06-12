package com.igormaznitsa.annotator.api.model;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassNameSuggester {

  private static final String DEFAULT_PREFIX = "class";
  private static final Pattern NUMBERED_CLASS =
      Pattern.compile("^class_(\\d+)$", Pattern.CASE_INSENSITIVE);

  private ClassNameSuggester() {
  }

  public static String suggest(final AnnotationDocument document,
                               final Optional<String> lastUsedId) {
    if (lastUsedId.isPresent()) {
      final String normalized = ClassNames.normalize(lastUsedId.get());
      if (!isAutoNumberedLabel(normalized) || !labelExists(document, normalized)) {
        return normalized;
      }
    }
    return nextNumberedClass(document);
  }

  private static boolean isAutoNumberedLabel(final String label) {
    return NUMBERED_CLASS.matcher(label).matches();
  }

  private static boolean labelExists(final AnnotationDocument document, final String label) {
    return document.entries().stream().anyMatch(entry -> entry.id().equalsIgnoreCase(label));
  }

  private static String nextNumberedClass(final AnnotationDocument document) {
    int max = 0;
    for (final AnnotationEntry entry : document.entries()) {
      final Matcher matcher = NUMBERED_CLASS.matcher(entry.id());
      if (matcher.matches()) {
        max = Math.max(max, Integer.parseInt(matcher.group(1)));
      }
    }
    int candidate = max + 1;
    while (labelExists(document, DEFAULT_PREFIX + '_' + candidate)) {
      candidate++;
    }
    return String.format(Locale.ROOT, "%s_%d", DEFAULT_PREFIX, candidate);
  }
}
