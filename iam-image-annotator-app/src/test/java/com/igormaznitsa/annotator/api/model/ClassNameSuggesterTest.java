package com.igormaznitsa.annotator.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClassNameSuggesterTest {

  @Test
  void usesStickyLabelWhenFree() {
    final AnnotationDocument document = new AnnotationDocument();
    assertEquals("car", ClassNameSuggester.suggest(document, Optional.of("car")));
  }

  @Test
  void suffixesWhenStickyLabelAlreadyTaken() {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "car",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));
    assertEquals("car-2", ClassNameSuggester.suggest(document, Optional.of("car")));
  }

  @Test
  void reusesLastEntryLabelBase() {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "person",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));
    assertEquals("person-2", ClassNameSuggester.suggest(document, Optional.empty()));
  }

  @Test
  void startsNumberedSeriesWhenEmpty() {
    final AnnotationDocument document = new AnnotationDocument();
    assertEquals("class-1", ClassNameSuggester.suggest(document, Optional.empty()));
  }
}
