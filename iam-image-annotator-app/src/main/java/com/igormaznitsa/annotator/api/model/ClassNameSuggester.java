package com.igormaznitsa.annotator.api.model;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassNameSuggester {

  private static final String DEFAULT_PREFIX = "class";
  private static final Pattern NUMBERED_CLASS =
      Pattern.compile("^class-(\\d+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern SUFFIXED = Pattern.compile("^(.*)-(\\d+)$");

  private ClassNameSuggester() {
  }

  public static String suggest(final AnnotationDocument document,
                               final Optional<String> lastUsedId) {
    if (lastUsedId.isPresent()) {
      return uniquify(document, lastUsedId.get());
    }
    final List<AnnotationEntry> entries = document.entries();
    if (!entries.isEmpty()) {
      return uniquify(document, entries.get(entries.size() - 1).id());
    }
    return nextNumberedClass(document);
  }

  static String uniquify(final AnnotationDocument document, final String base) {
    final String normalized = ClassNames.normalize(base);
    if (document.findById(normalized).isEmpty()) {
      return normalized;
    }
    final Matcher suffixed = SUFFIXED.matcher(normalized);
    final String stem = suffixed.matches() ? suffixed.group(1) : normalized;
    for (int suffix = 2; suffix < 10_000; suffix++) {
      final String candidate = stem + "-" + suffix;
      if (document.findById(candidate).isEmpty()) {
        return candidate;
      }
    }
    return nextNumberedClass(document);
  }

  private static String nextNumberedClass(final AnnotationDocument document) {
    int max = 0;
    for (final AnnotationEntry entry : document.entries()) {
      final Matcher matcher = NUMBERED_CLASS.matcher(entry.id());
      if (matcher.matches()) {
        max = Math.max(max, Integer.parseInt(matcher.group(1)));
      }
    }
    final String candidate = "%s-%d".formatted(DEFAULT_PREFIX, max + 1);
    return document.findById(candidate).isPresent() ? uniquify(document, candidate) : candidate;
  }
}
