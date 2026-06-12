package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.editor.AnnotationHandle;
import com.igormaznitsa.annotator.ui.editor.AnnotationSelectionEditor;
import com.igormaznitsa.annotator.ui.editor.Geometry;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;
import com.igormaznitsa.annotator.ui.editor.MouseToolAdapter;
import com.igormaznitsa.annotator.ui.editor.ShapeEditFlow;
import com.igormaznitsa.annotator.ui.editor.ShapeTransformResult;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Optional;

public final class SelectTool extends AbstractMouseEditTool {

  private AnnotationHandle activeHandle;
  private String dragStartLabel;
  private AnnotationType dragStartType;
  private AnnotationCoords dragStartCoords;
  private int dragImageWidth;
  private int dragImageHeight;
  private double dragStartNormX;
  private double dragStartNormY;
  private double dragStartAngle;
  private NormPoint dragCentroid;
  private boolean moved;

  @Override
  public String id() {
    return "select";
  }

  @Override
  public String tooltip() {
    return "Select: drag to move; double-click selected shape to edit label and fill color";
  }

  @Override
  public String iconFileName() {
    return "cursor.png";
  }

  @Override
  public void deactivate(final EditorPanelContext context) {
    this.resetDrag(context);
    if (context instanceof ImageCanvas canvas) {
      canvas.setAddVertexPending(false);
    }
    super.deactivate(context);
  }

  @Override
  protected boolean clearsSelectionOnActivate() {
    return false;
  }

  @Override
  protected MouseToolAdapter createMouseAdapter() {
    return new MouseToolAdapter() {
      @Override
      public void mousePressed(final EditorPanelContext context, final MouseEvent event) {
        final Point imagePoint = this.imagePoint(context, event);
        final int width = context.image().getWidth();
        final int height = context.image().getHeight();
        final double normX = Geometry.clamp01((double) imagePoint.x / width);
        final double normY = Geometry.clamp01((double) imagePoint.y / height);
        SelectTool.this.resetDrag(context);
        final Optional<AnnotationEntry> selected = context.selectedAnnotation()
            .flatMap(key -> context.session().document().findByKey(key));
        if (selected.isPresent()) {
          final Optional<AnnotationHandle> handle = AnnotationSelectionEditor.hitHandle(
              selected.get(),
              normX,
              normY,
              width,
              height);
          if (handle.isPresent()) {
            if (selected.get().locked()) {
              context.updateStatus("Annotation is locked: " + selected.get().id());
              return;
            }
            SelectTool.this.beginDrag(context, handle.get(), selected.get(), normX, normY, width,
                height);
            return;
          }
        }
        final Optional<String> hit = AnnotationSelectionEditor.hitAnnotation(
            context.session().document().entries(),
            normX,
            normY);
        if (hit.isPresent()) {
          context.selectAnnotation(hit.get());
          context.session().document().findByKey(hit.get()).ifPresent(entry -> {
            if (entry.locked()) {
              return;
            }
            final Optional<AnnotationHandle> handle = AnnotationSelectionEditor.hitHandle(
                entry,
                normX,
                normY,
                width,
                height);
            handle.ifPresent(
                h -> SelectTool.this.beginDrag(context, h, entry, normX, normY, width, height));
          });
        } else {
          context.clearSelection();
        }
        context.repaintCanvas();
      }

      @Override
      public void mouseDragged(final EditorPanelContext context, final MouseEvent event) {
        if (SelectTool.this.activeHandle == null || SelectTool.this.dragStartCoords == null) {
          return;
        }
        final Point imagePoint = this.imagePoint(context, event);
        final int width = context.image().getWidth();
        final int height = context.image().getHeight();
        final double normX = Geometry.clamp01((double) imagePoint.x / width);
        final double normY = Geometry.clamp01((double) imagePoint.y / height);
        context.session().document().findByKey(SelectTool.this.activeHandle.annotationKey())
            .ifPresent(entry -> {
              if (entry.locked()) {
                return;
              }
              final ShapeTransformResult result = SelectTool.this.applyDrag(
                  entry,
                  normX,
                  normY,
                  width,
                  height);
              try {
                context.session().document().updateAnnotation(
                    entry.key(),
                    result.type(),
                    result.coords());
              } catch (final IllegalStateException exception) {
                context.updateStatus(exception.getMessage());
                return;
              }
              SelectTool.this.moved = true;
              SelectTool.this.updateRotationArmPreview(context, normX, normY, width, height);
              context.repaintCanvas();
            });
      }

      @Override
      public void mouseReleased(final EditorPanelContext context, final MouseEvent event) {
        if (SelectTool.this.moved) {
          context.markDirty();
          context.updateStatus("Annotation updated: " + SelectTool.this.dragStartLabel);
        }
        SelectTool.this.resetDrag(context);
        context.repaintCanvas();
      }

      @Override
      public void mouseClicked(final EditorPanelContext context, final MouseEvent event) {
        if (event.getButton() != MouseEvent.BUTTON1 || event.getClickCount() < 2) {
          return;
        }
        final Point imagePoint = this.imagePoint(context, event);
        final int width = context.image().getWidth();
        final int height = context.image().getHeight();
        final double normX = Geometry.clamp01((double) imagePoint.x / width);
        final double normY = Geometry.clamp01((double) imagePoint.y / height);
        context.selectedAnnotation()
            .flatMap(key -> context.session().document().findByKey(key))
            .ifPresent(entry -> {
              final Optional<AnnotationHandle> handle = AnnotationSelectionEditor.hitHandle(
                  entry,
                  normX,
                  normY,
                  width,
                  height);
              if (handle.isEmpty() || handle.get() instanceof AnnotationHandle.Rotate) {
                return;
              }
              ShapeEditFlow.editShape(context, entry);
            });
      }
    };
  }

  private void beginDrag(
      final EditorPanelContext context,
      final AnnotationHandle handle,
      final AnnotationEntry entry,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    context.session().recordUndoCheckpoint();
    this.activeHandle = handle;
    this.dragStartLabel = entry.id();
    this.dragStartType = entry.type();
    this.dragStartCoords = entry.coords();
    this.dragImageWidth = imageWidth;
    this.dragImageHeight = imageHeight;
    this.dragStartNormX = normX;
    this.dragStartNormY = normY;
    this.dragCentroid = AnnotationSelectionEditor.centroid(entry);
    this.dragStartAngle = Geometry.angleInImage(
        this.dragCentroid.x(),
        this.dragCentroid.y(),
        normX,
        normY,
        imageWidth,
        imageHeight);
    this.moved = false;
    if (handle instanceof AnnotationHandle.Vertex vertex) {
      context.session().selectVertex(vertex.index());
      context.refreshToolbarState();
    } else if (handle instanceof AnnotationHandle.Body) {
      context.session().clearVertexSelection();
      context.refreshToolbarState();
    } else if (handle instanceof AnnotationHandle.Rotate && context instanceof ImageCanvas canvas) {
      canvas.setRotationArmAngleRad(this.dragStartAngle);
    }
  }

  private ShapeTransformResult applyDrag(
      final AnnotationEntry entry,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    return switch (this.activeHandle) {
      case AnnotationHandle.Body body -> new ShapeTransformResult(
          AnnotationSelectionEditor.translate(
              this.dragStartCoords,
              this.dragStartType,
              normX - this.dragStartNormX,
              normY - this.dragStartNormY),
          this.dragStartType);
      case AnnotationHandle.Vertex vertex -> new ShapeTransformResult(
          AnnotationSelectionEditor.moveVertex(entry, vertex.index(), normX, normY),
          entry.type());
      case AnnotationHandle.Rotate rotate -> {
        final double currentAngle = Geometry.angleInImage(
            this.dragCentroid.x(),
            this.dragCentroid.y(),
            normX,
            normY,
            imageWidth,
            imageHeight);
        final double delta = currentAngle - this.dragStartAngle;
        yield AnnotationSelectionEditor.rotate(
            this.dragStartType,
            this.dragStartCoords,
            delta,
            this.dragImageWidth,
            this.dragImageHeight);
      }
    };
  }

  private void updateRotationArmPreview(
      final EditorPanelContext context,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    if (!(this.activeHandle instanceof AnnotationHandle.Rotate) || this.dragCentroid == null) {
      return;
    }
    if (context instanceof ImageCanvas canvas) {
      canvas.setRotationArmAngleRad(Geometry.angleInImage(
          this.dragCentroid.x(),
          this.dragCentroid.y(),
          normX,
          normY,
          imageWidth,
          imageHeight));
    }
  }

  private void resetDrag(final EditorPanelContext context) {
    if (context instanceof ImageCanvas canvas) {
      canvas.clearRotationArmAngleRad();
    }
    this.activeHandle = null;
    this.dragStartLabel = null;
    this.dragStartType = null;
    this.dragStartCoords = null;
    this.dragCentroid = null;
    this.moved = false;
  }
}
