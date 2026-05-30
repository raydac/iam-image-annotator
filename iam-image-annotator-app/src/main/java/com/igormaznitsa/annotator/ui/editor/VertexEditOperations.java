package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import java.awt.Component;
import java.awt.Window;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class VertexEditOperations {

  private VertexEditOperations() {
  }

  public static boolean canAddVertex(final EditorPanelContext context) {
    return selectedEntry(context)
        .filter(entry -> !entry.locked())
        .filter(entry -> AnnotationSelectionEditor.supportsMutableVertices(entry.type()))
        .isPresent();
  }

  public static boolean canRemoveVertex(final EditorPanelContext context) {
    final Optional<AnnotationEntry> entry = selectedEntry(context);
    if (entry.isEmpty() || entry.get().locked()) {
      return false;
    }
    if (!AnnotationSelectionEditor.supportsMutableVertices(entry.get().type())) {
      return false;
    }
    final int vertexIndex = context.session().selectedVertexIndex().orElse(-1);
    return vertexIndex >= 0 && AnnotationSelectionEditor.canRemoveVertex(entry.get(), vertexIndex);
  }

  public static boolean canRemoveSelectedShape(final EditorPanelContext context) {
    return selectedEntry(context).isPresent() && !hasSelectedMutableVertex(context);
  }

  public static boolean canInsertVertexAfterSelection(final EditorPanelContext context) {
    if (!canAddVertex(context)) {
      return false;
    }
    final int vertexIndex = context.session().selectedVertexIndex().orElse(-1);
    if (vertexIndex < 0) {
      return false;
    }
    return selectedEntry(context)
        .flatMap(entry -> AnnotationSelectionEditor.vertexInsertionAfter(entry, vertexIndex))
        .isPresent();
  }

  public static void armAddVertex(final EditorPanelContext context) {
    if (!canAddVertex(context)) {
      context.updateStatus("Select an unlocked polygon or OBB to add points");
      return;
    }
    if (context instanceof ImageCanvas canvas) {
      canvas.setAddVertexPending(true);
      canvas.requestFocusInWindow();
      canvas.activateSelectTool();
    }
    context.updateStatus("Click on a shape edge to add a point (Esc to cancel)");
    context.refreshToolbarState();
  }

  public static boolean addVertexAt(
      final EditorPanelContext context,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    final Optional<AnnotationEntry> selected = selectedEntry(context);
    if (selected.isEmpty()) {
      context.updateStatus("Select a shape first");
      return false;
    }
    final AnnotationEntry entry = selected.get();
    if (entry.locked()) {
      context.updateStatus("Annotation is locked: " + entry.id());
      return false;
    }
    if (!AnnotationSelectionEditor.supportsMutableVertices(entry.type())) {
      context.updateStatus("This shape type does not support point editing");
      return false;
    }
    final Optional<AnnotationSelectionEditor.VertexInsertion> insertion =
        AnnotationSelectionEditor.findVertexInsertion(entry, normX, normY, imageWidth, imageHeight);
    if (insertion.isEmpty()) {
      context.updateStatus("Click closer to an edge of the selected shape");
      return false;
    }
    return AnnotationSelectionEditor.insertVertex(entry, insertion.get())
        .map(result -> {
          if (!applyShapeEdit(context, entry, result)) {
            return false;
          }
          context.session().selectVertex(insertion.get().vertexIndex());
          context.updateStatus("Point added to " + entry.id());
          context.refreshToolbarState();
          context.refreshDisplay();
          return true;
        })
        .orElse(false);
  }

  public static boolean insertVertexAfterSelection(final EditorPanelContext context) {
    final Optional<AnnotationEntry> selected = selectedEntry(context);
    if (selected.isEmpty()) {
      context.updateStatus("Select a shape first");
      return false;
    }
    final AnnotationEntry entry = selected.get();
    if (entry.locked()) {
      context.updateStatus("Annotation is locked: " + entry.id());
      return false;
    }
    if (!AnnotationSelectionEditor.supportsMutableVertices(entry.type())) {
      context.updateStatus("This shape type does not support point editing");
      return false;
    }
    final int vertexIndex = context.session().selectedVertexIndex().orElse(-1);
    if (vertexIndex < 0) {
      context.updateStatus("Select a point handle first, then press Insert");
      return false;
    }
    final Optional<AnnotationSelectionEditor.VertexInsertion> insertion =
        AnnotationSelectionEditor.vertexInsertionAfter(entry, vertexIndex);
    if (insertion.isEmpty()) {
      context.updateStatus("Cannot add a point next to the selected handle");
      return false;
    }
    if (context instanceof ImageCanvas canvas) {
      canvas.setAddVertexPending(false);
    }
    return AnnotationSelectionEditor.insertVertex(entry, insertion.get())
        .map(result -> {
          if (!applyShapeEdit(context, entry, result)) {
            return false;
          }
          context.session().selectVertex(insertion.get().vertexIndex());
          context.updateStatus("Point added to " + entry.id());
          context.refreshToolbarState();
          context.refreshDisplay();
          return true;
        })
        .orElse(false);
  }

  public static void removeSelectedShape(final EditorPanelContext context) {
    removeSelectedShape(context, true);
  }

  public static void removeSelectedShape(final EditorPanelContext context, final boolean confirm) {
    final Optional<AnnotationEntry> selected = selectedEntry(context);
    if (selected.isEmpty()) {
      context.updateStatus("Select a shape first");
      return;
    }
    if (hasSelectedMutableVertex(context)) {
      return;
    }
    if (confirm && !confirmShapeRemove(context, selected.get())) {
      context.updateStatus("Shape removal cancelled");
      return;
    }
    final String shapeId = selected.get().id();
    context.session().recordUndoCheckpoint();
    context.session().document().remove(shapeId);
    context.session().clearSelection();
    context.markDirty();
    context.updateStatus("Shape removed: " + shapeId);
    context.refreshToolbarState();
    context.refreshDisplay();
  }

  public static void removeSelectedVertex(final EditorPanelContext context) {
    removeSelectedVertex(context, true);
  }

  public static void removeSelectedVertex(final EditorPanelContext context, final boolean confirm) {
    final Optional<AnnotationEntry> selected = selectedEntry(context);
    if (selected.isEmpty()) {
      context.updateStatus("Select a shape first");
      return;
    }
    if (confirm && !confirmRemove(context, selected.get())) {
      context.updateStatus("Point removal cancelled");
      return;
    }
    final Optional<AnnotationEntry> entry = selectedEntry(context);
    if (entry.isEmpty()) {
      context.updateStatus("Select a shape first");
      return;
    }
    if (entry.get().locked()) {
      context.updateStatus("Annotation is locked: " + entry.get().id());
      return;
    }
    if (!AnnotationSelectionEditor.supportsMutableVertices(entry.get().type())) {
      context.updateStatus("This shape type does not support point editing");
      return;
    }
    final int vertexIndex = context.session().selectedVertexIndex().orElse(-1);
    if (vertexIndex < 0) {
      context.updateStatus("Select a point handle first, then use Remove point");
      return;
    }
    if (!AnnotationSelectionEditor.canRemoveVertex(entry.get(), vertexIndex)) {
      context.updateStatus("Cannot remove this point (minimum vertices required)");
      return;
    }
    AnnotationSelectionEditor.removeVertex(entry.get(), vertexIndex).ifPresentOrElse(
        result -> {
          if (!applyShapeEdit(context, entry.get(), result)) {
            return;
          }
          context.session().clearVertexSelection();
          context.updateStatus("Point removed from " + entry.get().id());
          context.refreshToolbarState();
          context.refreshDisplay();
        },
        () -> context.updateStatus("Cannot remove this point"));
  }

  private static boolean applyShapeEdit(
      final EditorPanelContext context,
      final AnnotationEntry entry,
      final ShapeTransformResult result) {
    context.session().recordUndoCheckpoint();
    try {
      if (result.type() == entry.type()) {
        context.session().document().updateCoords(entry.id(), result.coords());
      } else {
        context.session().document().updateAnnotation(entry.id(), result.type(), result.coords());
      }
      context.markDirty();
      return true;
    } catch (final IllegalStateException exception) {
      context.updateStatus(exception.getMessage());
      return false;
    }
  }

  private static boolean hasSelectedMutableVertex(final EditorPanelContext context) {
    if (context.session().selectedVertexIndex().isEmpty()) {
      return false;
    }
    return selectedEntry(context)
        .map(entry -> AnnotationSelectionEditor.supportsMutableVertices(entry.type()))
        .orElse(false);
  }

  private static boolean confirmShapeRemove(final EditorPanelContext context,
                                            final AnnotationEntry entry) {
    final Component component = context instanceof Component canvas ? canvas : null;
    final Window owner = component == null ? null : SwingUtilities.getWindowAncestor(component);
    return JOptionPane.showConfirmDialog(
        owner,
        String.format(Locale.ROOT, "Remove annotation \"%s\"?", entry.id()),
        "Remove shape",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
  }

  private static boolean confirmRemove(final EditorPanelContext context,
                                       final AnnotationEntry entry) {
    final Component component = context instanceof Component canvas ? canvas : null;
    final Window owner = component == null ? null : SwingUtilities.getWindowAncestor(component);
    return JOptionPane.showConfirmDialog(
        owner,
        String.format(Locale.ROOT, "Remove the selected point from annotation \"%s\"?", entry.id()),
        "Remove point",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
  }

  private static Optional<AnnotationEntry> selectedEntry(final EditorPanelContext context) {
    return context.selectedAnnotation()
        .flatMap(name -> context.session().document().findById(name));
  }
}
