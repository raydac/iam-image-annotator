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
  void reusesStickyLabelWhenAlreadyTaken() {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "car",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));
    assertEquals("car", ClassNameSuggester.suggest(document, Optional.of("car")));
  }

  @Test
  void suggestsUniqueNumberedClassWhenDocumentHasOtherLabels() {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "coin",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));
    assertEquals("class_1", ClassNameSuggester.suggest(document, Optional.empty()));
  }

  @Test
  void advancesNumberedSeriesWhenAutoLabelsExist() {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "class_1",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));
    assertEquals("class_2", ClassNameSuggester.suggest(document, Optional.empty()));
  }

  @Test
  void advancesStickyAutoLabelWhenAlreadyTaken() {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "class_1",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));
    assertEquals("class_2", ClassNameSuggester.suggest(document, Optional.of("class_1")));
  }

  @Test
  void startsNumberedSeriesWhenEmpty() {
    final AnnotationDocument document = new AnnotationDocument();
    assertEquals("class_1", ClassNameSuggester.suggest(document, Optional.empty()));
  }
}
