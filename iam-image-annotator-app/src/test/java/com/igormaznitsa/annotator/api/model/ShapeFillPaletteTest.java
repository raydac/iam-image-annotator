package com.igormaznitsa.annotator.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ShapeFillPaletteTest {

  @Test
  void cyclesThroughSevenColors() {
    final String first = ShapeFillPalette.colorAt(0);
    final String seventh = ShapeFillPalette.colorAt(6);
    final String eighth = ShapeFillPalette.colorAt(7);
    assertEquals(7, ShapeFillPalette.size());
    assertNotEquals(first, seventh);
    assertEquals(first, eighth);
  }

  @Test
  void documentAssignsPaletteInOrder() {
    final AnnotationDocument document = new AnnotationDocument();
    assertEquals(ShapeFillPalette.colorAt(0), document.nextFillColor());
    assertEquals(ShapeFillPalette.colorAt(1), document.nextFillColor());
    document.create("a", AnnotationType.RECTANGLE, document.nextFillColor(),
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1));
    assertEquals(ShapeFillPalette.colorAt(3), document.nextFillColor());
  }
}
