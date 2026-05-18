package com.igormaznitsa.annotator.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnnotationColorsTest {

  @Test
  void lightFillGetsDarkStroke() {
    assertEquals("#141414", AnnotationColors.contrastStrokeForFill("#FFFF00"));
  }

  @Test
  void darkFillGetsLightStroke() {
    assertEquals("#F5F5F5", AnnotationColors.contrastStrokeForFill("#101040"));
  }
}
