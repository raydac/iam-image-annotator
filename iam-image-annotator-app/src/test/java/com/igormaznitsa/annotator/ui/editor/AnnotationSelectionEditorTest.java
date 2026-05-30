package com.igormaznitsa.annotator.ui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnnotationSelectionEditorTest {

  @Test
  void insertsVertexOnPolygonEdge() {
    final AnnotationEntry entry = new AnnotationEntry(
        "cat",
        AnnotationType.POLYGON,
        "#ff0000",
        AnnotationCoords.polygon(List.of(
            NormPoint.of(0.2, 0.2),
            NormPoint.of(0.8, 0.2),
            NormPoint.of(0.8, 0.8),
            NormPoint.of(0.2, 0.8))));
    final Optional<AnnotationSelectionEditor.VertexInsertion> insertion =
        AnnotationSelectionEditor.findVertexInsertion(entry, 0.5, 0.2, 1000, 1000);
    assertTrue(insertion.isPresent());
    assertEquals(1, insertion.get().vertexIndex());
    final ShapeTransformResult updated =
        AnnotationSelectionEditor.insertVertex(entry, insertion.get()).orElseThrow();
    assertEquals(5, updated.coords().points().size());
  }

  @Test
  void removesPolygonVertexWhenMoreThanThreeRemain() {
    final AnnotationEntry entry = new AnnotationEntry(
        "cat",
        AnnotationType.POLYGON,
        "#ff0000",
        AnnotationCoords.polygon(List.of(
            NormPoint.of(0.2, 0.2),
            NormPoint.of(0.8, 0.2),
            NormPoint.of(0.8, 0.8),
            NormPoint.of(0.2, 0.8))));
    assertTrue(AnnotationSelectionEditor.canRemoveVertex(entry, 1));
    final ShapeTransformResult updated =
        AnnotationSelectionEditor.removeVertex(entry, 1).orElseThrow();
    assertEquals(3, updated.coords().points().size());
  }

  @Test
  void insertsVertexAfterSelectedPolygonPoint() {
    final AnnotationEntry entry = new AnnotationEntry(
        "cat",
        AnnotationType.POLYGON,
        "#ff0000",
        AnnotationCoords.polygon(List.of(
            NormPoint.of(0.2, 0.2),
            NormPoint.of(0.8, 0.2),
            NormPoint.of(0.8, 0.8),
            NormPoint.of(0.2, 0.8))));
    final AnnotationSelectionEditor.VertexInsertion insertion =
        AnnotationSelectionEditor.vertexInsertionAfter(entry, 1).orElseThrow();
    assertEquals(2, insertion.vertexIndex());
    assertEquals(0.8, insertion.point().x(), 1e-9);
    assertEquals(0.5, insertion.point().y(), 1e-9);
    final ShapeTransformResult updated =
        AnnotationSelectionEditor.insertVertex(entry, insertion).orElseThrow();
    assertEquals(5, updated.coords().points().size());
  }

  @Test
  void cannotRemoveBelowTriangle() {
    final AnnotationEntry entry = new AnnotationEntry(
        "cat",
        AnnotationType.POLYGON,
        "#ff0000",
        AnnotationCoords.polygon(List.of(
            NormPoint.of(0.2, 0.2),
            NormPoint.of(0.8, 0.2),
            NormPoint.of(0.5, 0.8))));
    assertFalse(AnnotationSelectionEditor.canRemoveVertex(entry, 0));
    assertTrue(AnnotationSelectionEditor.removeVertex(entry, 0).isEmpty());
  }

  @Test
  void hiddenAnnotationCannotBeHit() {
    final AnnotationEntry entry = new AnnotationEntry(
        "cat",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.2, 0.2, 0.6, 0.6),
        false,
        false);
    assertTrue(AnnotationSelectionEditor.hitAnnotation(List.of(entry), 0.5, 0.5).isEmpty());
    assertTrue(AnnotationSelectionEditor.hitHandle(entry, 0.5, 0.5, 1000, 1000).isEmpty());
  }
}
