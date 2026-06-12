package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.ClassNames;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.dialog.ColorChooserDialog;
import com.igormaznitsa.annotator.ui.editor.AnnotationSelectionEditor;
import com.igormaznitsa.annotator.ui.editor.Geometry;
import com.igormaznitsa.annotator.ui.editor.MouseToolAdapter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Optional;

public final class MaskColorTool extends AbstractMouseEditTool {

  @Override
  public String id() {
    return "mask-color";
  }

  @Override
  public String tooltip() {
    return "Mask color: double-click a mask to choose its color";
  }

  @Override
  public String iconFileName() {
    return "color_swatch.png";
  }

  @Override
  protected boolean clearsSelectionOnActivate() {
    return false;
  }

  @Override
  protected MouseToolAdapter createMouseAdapter() {
    return new MouseToolAdapter() {
      @Override
      public void mouseClicked(final EditorPanelContext context, final MouseEvent event) {
        if (event.getButton() != MouseEvent.BUTTON1 || event.getClickCount() < 2) {
          return;
        }
        MaskColorTool.this.findMaskAt(context, event)
            .ifPresentOrElse(
                entry -> MaskColorTool.this.chooseMaskColor(context, entry),
                () -> MaskColorTool.this.clearMissingSelection(context));
      }
    };
  }

  private Optional<AnnotationEntry> findMaskAt(
      final EditorPanelContext context,
      final MouseEvent event) {
    final Point imagePoint = context.imagePointFromScreen(event.getPoint());
    final int imageWidth = context.image().getWidth();
    final int imageHeight = context.image().getHeight();
    final double normX = Geometry.clamp01((double) imagePoint.x / imageWidth);
    final double normY = Geometry.clamp01((double) imagePoint.y / imageHeight);
    return AnnotationSelectionEditor.hitAnnotation(
            context.session().document().entries(),
            normX,
            normY)
        .flatMap(key -> context.session().document().findByKey(key));
  }

  private void chooseMaskColor(final EditorPanelContext context, final AnnotationEntry entry) {
    context.selectAnnotation(entry.key());
    context.repaintCanvas();
    if (entry.locked()) {
      context.updateStatus("Annotation is locked: " + entry.id());
      return;
    }
    final Optional<Color> chosen = ColorChooserDialog.show(
        this.parentComponent(context),
        "Mask color: " + entry.id(),
        entry.fillColor());
    if (chosen.isEmpty()) {
      context.updateStatus("Mask color change cancelled: " + entry.id());
      return;
    }
    final String fillColorHex = ClassNames.normalizeColor(ColorChooserDialog.toHex(chosen.get()));
    if (fillColorHex.equals(entry.fillColorHex())) {
      context.updateStatus("Mask color unchanged: " + entry.id());
      return;
    }
    context.session().recordUndoCheckpoint();
    context.session().document().updateFillColor(entry.key(), fillColorHex);
    context.markDirty();
    context.repaintCanvas();
    context.updateStatus("Mask color updated: " + entry.id());
  }

  private void clearMissingSelection(final EditorPanelContext context) {
    context.clearSelection();
    context.repaintCanvas();
    context.updateStatus("No mask under double-click");
  }

  private Component parentComponent(final EditorPanelContext context) {
    return context instanceof Component component ? component : null;
  }
}
