package com.igormaznitsa.annotator.api.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotationJsonCodecTest {

  @Test
  void encodePrettyIsIndented() {
    final AnnotationDocument source = new AnnotationDocument();
    source.add(new AnnotationEntry(
        "car",
        AnnotationType.RECTANGLE,
        "#FF0000",
        AnnotationCoords.rectangle(0.1, 0.2, 0.3, 0.4)));
    final String pretty = new AnnotationJsonCodec().encodePretty(source);
    assertTrue(pretty.contains("\n"));
    assertTrue(pretty.contains("  \"annotations\""));
  }

  @Test
  void roundTripRectangle() {
    final AnnotationDocument source = new AnnotationDocument();
    source.add(new AnnotationEntry(
        "aircraft",
        AnnotationType.RECTANGLE,
        "#FF0000",
        AnnotationCoords.rectangle(0.1, 0.2, 0.3, 0.4)));
    final AnnotationJsonCodec codec = new AnnotationJsonCodec();
    final AnnotationDocument restored = codec.decode(codec.encode(source));
    assertEquals(1, restored.entries().size());
    assertEquals("aircraft", restored.entries().get(0).id());
    assertEquals(0.3, restored.entries().get(0).coords().width());
  }

  @Test
  void roundTripLockFlag() {
    final AnnotationDocument source = new AnnotationDocument();
    source.add(new AnnotationEntry(
        "car",
        AnnotationType.RECTANGLE,
        "#00FF00",
        AnnotationCoords.rectangle(0, 0, 0.5, 0.5),
        true));
    final AnnotationJsonCodec codec = new AnnotationJsonCodec();
    final String json = new String(codec.encode(source), StandardCharsets.UTF_8);
    assertTrue(json.contains("\"lock\":true"));
    final AnnotationDocument restored = codec.decode(codec.encode(source));
    assertTrue(restored.entries().get(0).locked());
  }

  @Test
  void roundTripFillColor() {
    final AnnotationDocument source = new AnnotationDocument();
    source.add(new AnnotationEntry(
        "boat",
        AnnotationType.POLYGON,
        "#00FF88",
        AnnotationCoords.polygon(List.of(
            NormPoint.of(0.1, 0.1),
            NormPoint.of(0.5, 0.1),
            NormPoint.of(0.3, 0.4))),
        false));
    final AnnotationJsonCodec codec = new AnnotationJsonCodec();
    final String json = new String(codec.encode(source), StandardCharsets.UTF_8);
    assertTrue(json.contains("\"fillColor\":\"#00FF88\""));
    final AnnotationDocument restored = codec.decode(codec.encode(source));
    assertEquals("#00FF88", restored.entries().get(0).fillColorHex());
  }

  @Test
  void missingLockDefaultsToFalse() {
    final AnnotationJsonCodec codec = new AnnotationJsonCodec();
    final String json = """
        {"annotations":[{"id":"wing","type":"rectangle","fillColor":"#808080",\
        "coords":{"x":0.1,"y":0.2,"width":0.3,"height":0.4}}]}""";
    final AnnotationDocument document = codec.decode(json.getBytes(StandardCharsets.UTF_8));
    assertFalse(document.entries().get(0).locked());
  }

}
