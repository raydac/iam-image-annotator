package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.dialog.MagicWandSettingsDialog;
import com.igormaznitsa.annotator.ui.editor.AnnotationCreateFlow;
import com.igormaznitsa.annotator.ui.editor.EditorDraft;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;
import com.igormaznitsa.annotator.ui.editor.MouseToolAdapter;
import com.igormaznitsa.annotator.ui.selection.MagicWandSampleGrid;
import com.igormaznitsa.annotator.ui.selection.MagicWandSelector;
import com.igormaznitsa.annotator.ui.selection.MagicWandSettings;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class MagicWandTool extends AbstractMouseEditTool {

  private static final double TOLERANCE_WHEEL_STEP = 0.02;
  private static MagicWandSettings lastSettings = MagicWandSettings.defaults();

  private MagicWandSettings settings = lastSettings;
  private MagicWandSampleGrid sampleGrid;
  private boolean armed;
  private Point lastPreviewPoint;

  @Override
  public String id() {
    return "magic-wand";
  }

  @Override
  public String tooltip() {
    return "Magic wand: click to add region; wheel adjusts tolerance; Shift+click settings";
  }

  @Override
  public String iconFileName() {
    return "wand.png";
  }

  @Override
  public void activate(final EditorPanelContext context) {
    final Component parent = context instanceof Component component ? component : null;
    final Optional<MagicWandSettings> chosen = MagicWandSettingsDialog.show(parent, lastSettings);
    if (chosen.isEmpty()) {
      context.updateStatus("Magic wand cancelled");
      if (context instanceof ImageCanvas canvas) {
        canvas.activateSelectTool();
      }
      return;
    }
    this.applySettings(context, chosen.get());
    super.activate(context);
    this.requestCanvasFocus(context);
  }

  @Override
  public void deactivate(final EditorPanelContext context) {
    this.armed = false;
    this.sampleGrid = null;
    this.lastPreviewPoint = null;
    super.deactivate(context);
  }

  @Override
  public boolean cancelDrawing(final EditorPanelContext context) {
    if (!(context instanceof ImageCanvas canvas)) {
      return super.cancelDrawing(context);
    }
    this.armed = false;
    this.sampleGrid = null;
    this.clearDrawingState(context);
    canvas.activateSelectTool();
    context.updateStatus("Magic wand cancelled");
    context.repaintCanvas();
    return true;
  }

  @Override
  protected void clearDrawingState(final EditorPanelContext context) {
    this.lastPreviewPoint = null;
    if (context instanceof ImageCanvas canvas) {
      canvas.clearDraft();
    }
  }

  @Override
  protected MouseToolAdapter createMouseAdapter() {
    return new MouseToolAdapter() {
      @Override
      public void mousePressed(final EditorPanelContext context, final MouseEvent event) {
        if (event.getButton() != MouseEvent.BUTTON1 || !MagicWandTool.this.armed) {
          return;
        }
        if (event.isShiftDown()) {
          MagicWandTool.this.openSettings(context);
          return;
        }
        MagicWandTool.this.selectAt(context, this.imagePoint(context, event));
      }

      @Override
      public void mouseMoved(final EditorPanelContext context, final MouseEvent event) {
        if (!MagicWandTool.this.armed || !MagicWandTool.this.settings.livePreview()) {
          return;
        }
        MagicWandTool.this.previewAt(context, this.imagePoint(context, event));
      }

      @Override
      public void mouseWheelMoved(final EditorPanelContext context, final MouseWheelEvent event) {
        if (!MagicWandTool.this.armed) {
          return;
        }
        final int rotation = event.getWheelRotation();
        if (rotation == 0) {
          return;
        }
        final double delta = -rotation * TOLERANCE_WHEEL_STEP;
        final double tolerance =
            Math.max(0.0, Math.min(1.0, MagicWandTool.this.settings.tolerance() + delta));
        event.consume();
        MagicWandTool.this.applySettings(context, new MagicWandSettings(
            tolerance,
            MagicWandTool.this.settings.mode(),
            MagicWandTool.this.settings.smoothness(),
            MagicWandTool.this.settings.livePreview()));
        if (MagicWandTool.this.settings.livePreview()) {
          MagicWandTool.this.previewAt(context, this.imagePoint(context, event));
        }
      }
    };
  }

  private void openSettings(final EditorPanelContext context) {
    final Component parent = context instanceof Component component ? component : null;
    MagicWandSettingsDialog.show(parent, this.settings)
        .ifPresent(chosen -> this.applySettings(context, chosen));
  }

  private void applySettings(final EditorPanelContext context,
                             final MagicWandSettings newSettings) {
    this.settings = newSettings;
    lastSettings = newSettings;
    this.sampleGrid = MagicWandSampleGrid.from(context.image());
    this.armed = true;
    this.lastPreviewPoint = null;
    if (!newSettings.livePreview() && context instanceof ImageCanvas canvas) {
      canvas.clearDraft();
    }
    final String previewHint = newSettings.livePreview()
        ? "live preview on"
        : "live preview off";
    context.updateStatus(this.statusLine(String.format(
        Locale.ROOT,
        "%s — Shift+click settings, scroll wheel tolerance",
        previewHint)));
    this.requestCanvasFocus(context);
  }

  private void previewAt(final EditorPanelContext context, final Point imagePoint) {
    if (!this.settings.livePreview() || !(context instanceof ImageCanvas canvas) ||
        this.sampleGrid == null) {
      return;
    }
    final Point clamped = this.clampToImage(context, imagePoint);
    if (Objects.equals(this.lastPreviewPoint, clamped)) {
      return;
    }
    this.lastPreviewPoint = clamped;
    final Optional<List<NormPoint>> polygon = MagicWandSelector.selectPolygon(
        this.sampleGrid,
        clamped.x,
        clamped.y,
        this.settings);
    if (polygon.isPresent()) {
      canvas.setDraft(new EditorDraft.Polyline(polygon.get(), true));
    } else {
      canvas.clearDraft();
    }
    context.repaintCanvas();
  }

  private void selectAt(final EditorPanelContext context, final Point imagePoint) {
    if (this.sampleGrid == null) {
      this.sampleGrid = MagicWandSampleGrid.from(context.image());
    }
    final Point clamped = this.clampToImage(context, imagePoint);
    context.updateStatus("Magic wand: selecting region…");
    final Optional<List<NormPoint>> polygon = MagicWandSelector.selectPolygon(
        this.sampleGrid,
        clamped.x,
        clamped.y,
        this.settings);
    if (polygon.isEmpty()) {
      context.updateStatus("No region found — increase tolerance or try another point");
      if (context instanceof ImageCanvas canvas) {
        canvas.clearDraft();
      }
      context.repaintCanvas();
      return;
    }
    this.lastPreviewPoint = null;
    this.finish(context, polygon.get());
  }

  private void finish(final EditorPanelContext context, final List<NormPoint> points) {
    AnnotationCreateFlow.createAndSelect(context, AnnotationType.POLYGON,
        AnnotationCoords.polygon(points));
  }

  private Point clampToImage(final EditorPanelContext context, final Point imagePoint) {
    final int width = context.image().getWidth();
    final int height = context.image().getHeight();
    return new Point(
        Math.max(0, Math.min(width - 1, imagePoint.x)),
        Math.max(0, Math.min(height - 1, imagePoint.y)));
  }

  private void requestCanvasFocus(final EditorPanelContext context) {
    if (context instanceof ImageCanvas canvas) {
      canvas.requestFocusInWindow();
    }
  }

  private String statusLine(final String hint) {
    return String.format(
        Locale.ROOT,
        "Magic wand [%s, tol %.2f, smooth %.2f]: %s",
        this.settings.mode().name().toLowerCase(Locale.ROOT),
        this.settings.tolerance(),
        this.settings.smoothness(),
        hint);
  }
}
