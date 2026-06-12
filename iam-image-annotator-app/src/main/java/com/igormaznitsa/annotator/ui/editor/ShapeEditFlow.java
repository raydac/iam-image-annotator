package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.ClassNames;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.dialog.ShapeEditDialog;

import java.awt.Component;

public final class ShapeEditFlow {

  private ShapeEditFlow() {
  }

  public static void editShape(final EditorPanelContext context, final AnnotationEntry entry) {
    if (entry.locked()) {
      context.updateStatus("Annotation is locked: " + entry.id());
      return;
    }
    final Component parent = context instanceof Component component ? component : null;
    final var edited = ShapeEditDialog.show(parent, entry);
    if (edited.isEmpty()) {
      context.updateStatus("Shape edit cancelled");
      return;
    }
    final ShapeEditDialog.Result values = edited.get();
    final boolean idChanged = !ClassNames.matchesIgnoreCase(values.id(), entry.id());
    final boolean fillChanged = !values.fillColorHex().equalsIgnoreCase(entry.fillColorHex());
    if (!idChanged && !fillChanged) {
      return;
    }
    context.session().recordUndoCheckpoint();
    if (idChanged) {
      context.session().document().rename(entry.key(), values.id());
      context.session().rememberClassId(values.id());
      context.selectAnnotation(entry.key());
    }
    if (fillChanged) {
      context.session().document().updateFillColor(
          entry.key(),
          values.fillColorHex());
    }
    context.markDirty();
    context.repaintCanvas();
    if (idChanged && fillChanged) {
      context.updateStatus("Shape updated: " + entry.id() + " → " + values.id());
    } else if (idChanged) {
      context.updateStatus("Label updated: " + entry.id() + " → " + values.id());
    } else {
      context.updateStatus("Fill color updated: " + entry.id());
    }
  }
}
