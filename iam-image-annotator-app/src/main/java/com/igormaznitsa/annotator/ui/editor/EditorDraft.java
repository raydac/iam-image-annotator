package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;

import java.util.List;
import java.util.Optional;

public sealed interface EditorDraft {

  record Rectangle(NormPoint from, NormPoint to) implements EditorDraft {
  }

  record Polyline(List<NormPoint> points, boolean closed, java.util.Optional<NormPoint> cursor)
      implements EditorDraft {

    public Polyline(final List<NormPoint> points, final boolean closed) {
      this(points, closed, java.util.Optional.empty());
    }
  }

  /**
   * OBB preview: fixed corners plus optional cursor for in-progress edge or parallelogram.
   */
  record Obbox(List<NormPoint> corners, Optional<NormPoint> cursor) implements EditorDraft {
  }

  /**
   * Final geometry shown while the class-name dialog is open.
   */
  record PendingShape(AnnotationType type, AnnotationCoords coords) implements EditorDraft {
  }
}
