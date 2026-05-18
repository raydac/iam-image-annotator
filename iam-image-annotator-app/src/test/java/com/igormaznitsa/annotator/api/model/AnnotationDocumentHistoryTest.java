package com.igormaznitsa.annotator.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotationDocumentHistoryTest {

  @Test
  void undoRestoresPreviousSnapshot() {
    final AnnotationDocumentHistory history = new AnnotationDocumentHistory();
    final AnnotationEntry first = new AnnotationEntry(
        "wing",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0, 0, 0.1, 0.1));
    final AnnotationEntry second = new AnnotationEntry(
        "tail",
        AnnotationType.RECTANGLE,
        "#00ff00",
        AnnotationCoords.rectangle(0.2, 0.2, 0.1, 0.1));
    history.recordCheckpoint(List.of(first));
    final List<AnnotationEntry> afterAdd = List.of(first, second);
    final List<AnnotationEntry> undone = history.undo(afterAdd).orElseThrow();
    assertEquals(1, undone.size());
    assertEquals("wing", undone.get(0).id());
  }
}
