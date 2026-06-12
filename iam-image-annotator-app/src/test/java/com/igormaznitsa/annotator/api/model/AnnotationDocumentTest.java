package com.igormaznitsa.annotator.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class AnnotationDocumentTest {

  @Test
  void renamePreservesFillColor() {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "wing",
        AnnotationType.RECTANGLE,
        "#ff00aa",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));

    document.rename(document.entries().getFirst().key(), "tail");

    final AnnotationEntry renamed = document.entries().getFirst();
    assertEquals("tail", renamed.id());
    assertEquals("#FF00AA", renamed.fillColorHex());
  }

  @Test
  void allowsDuplicateLabelsAndUpdatesByKey() {
    final AnnotationDocument document = new AnnotationDocument();
    final AnnotationEntry first = document.add(new AnnotationEntry(
        "car",
        AnnotationType.RECTANGLE,
        "#ff00aa",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1)));
    final AnnotationEntry second = document.add(new AnnotationEntry(
        "car",
        AnnotationType.RECTANGLE,
        "#00ffaa",
        AnnotationCoords.rectangle(0.2, 0.2, 0.1, 0.1)));

    document.updateFillColor(second.key(), "#0000ff");

    assertEquals("car", first.id());
    assertEquals("car", second.id());
    assertNotEquals(first.key(), second.key());
    assertEquals("#FF00AA", document.entries().getFirst().fillColorHex());
    assertEquals("#0000FF", document.entries().get(1).fillColorHex());
  }
}
