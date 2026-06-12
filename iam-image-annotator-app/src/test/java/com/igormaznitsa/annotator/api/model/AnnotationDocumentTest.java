package com.igormaznitsa.annotator.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    document.rename("wing", "tail");

    final AnnotationEntry renamed = document.entries().getFirst();
    assertEquals("tail", renamed.id());
    assertEquals("#FF00AA", renamed.fillColorHex());
  }
}
