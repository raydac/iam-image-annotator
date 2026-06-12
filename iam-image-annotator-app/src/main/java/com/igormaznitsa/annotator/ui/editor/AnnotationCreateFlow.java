package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.ClassNameSuggester;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import java.util.Locale;

public final class AnnotationCreateFlow {

  private AnnotationCreateFlow() {
  }

  public static AnnotationEntry createAndSelect(
      final EditorPanelContext context,
      final AnnotationType type,
      final AnnotationCoords coords) {
    if (context instanceof ImageCanvas canvas) {
      canvas.clearDraft();
    }
    final String id = ClassNameSuggester.suggest(
        context.session().document(),
        context.session().lastClassId());
    context.session().recordUndoCheckpoint();
    final AnnotationEntry entry = context.session().document().create(
        id,
        type,
        context.session().document().nextFillColor(),
        coords);
    context.session().rememberClassId(id);
    context.selectAnnotation(entry.key());
    context.markDirty();
    if (context instanceof ImageCanvas canvas) {
      canvas.activateSelectTool();
    }
    context.updateStatus(String.format(
        Locale.ROOT,
        "Added \"%s\" — double-click shape to edit label and fill",
        entry.id()));
    context.repaintCanvas();
    return entry;
  }
}
