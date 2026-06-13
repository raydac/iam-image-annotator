package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.ClassNames;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.render.AnnotationOverlayRenderer;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.ui.api.EditImageTool;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.RepaintManager;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

public final class ImageCanvas extends JPanel implements EditorPanelContext, Scrollable {

  private static final int GRID_STEP = 32;
  private static final int CHECKER_SIZE = 16;
  private static final Color CHECKER_LIGHT = new Color(88, 88, 88);
  private static final Color CHECKER_DARK = new Color(64, 64, 64);
  private static final Color IMAGE_BORDER = new Color(235, 235, 235, 180);
  private static final java.awt.Color SELECTION_GREEN = new java.awt.Color(80, 230, 90);
  private static final java.awt.Color SELECTION_GREEN_DARK = new java.awt.Color(0, 150, 40);
  private static final java.awt.Color LOCKED_HANDLE = new java.awt.Color(160, 160, 160);
  private static final java.awt.Color LOCKED_HANDLE_DARK = new java.awt.Color(90, 90, 90);
  private static final Color CLASS_LABEL_BACKGROUND = new Color(0, 0, 0, 170);
  private static final Color CLASS_LABEL_FOREGROUND = Color.WHITE;
  private final EditorSession session;
  private final AnnotationOverlayRenderer overlayRenderer = new AnnotationOverlayRenderer();
  private final Consumer<String> statusConsumer;
  private final Runnable dirtyListener;
  private MouseToolAdapter activeMouseTool;
  private EditImageTool activeEditTool;
  private EditorDraft draft;
  private Double rotationArmAngleRad;
  private double zoom = 1.0;
  private boolean gridVisible;
  private boolean classNamesVisible;
  private boolean addVertexPending;
  private Point lastImagePoint;
  private Runnable selectionListener = () -> {
  };
  private Runnable selectToolActivator = () -> {
  };

  public ImageCanvas(
      final EditorSession session,
      final Consumer<String> statusConsumer,
      final Runnable dirtyListener) {
    this.session = Objects.requireNonNull(session, "session");
    this.statusConsumer = Objects.requireNonNull(statusConsumer, "statusConsumer");
    this.dirtyListener = Objects.requireNonNull(dirtyListener, "dirtyListener");
    this.setBackground(new Color(48, 48, 48));
    this.setOpaque(true);
    this.setFocusable(true);
    this.registerMouseHandlers();
    this.registerKeyboardActions();
    this.recomputePreferredSize();
  }

  public void setActiveMouseTool(final MouseToolAdapter adapter) {
    this.activeMouseTool = adapter;
  }

  public void setActiveEditTool(final EditImageTool tool) {
    this.activeEditTool = tool;
  }

  public boolean hasDraft() {
    return this.draft != null;
  }

  public void cancelActiveDrawing() {
    if (this.addVertexPending) {
      this.setAddVertexPending(false);
      this.updateStatus("Add point cancelled");
      this.refreshToolbarState();
      return;
    }
    if (this.activeEditTool != null && this.activeEditTool.cancelDrawing(this)) {
      return;
    }
    if (this.draft != null) {
      this.clearDraft();
      this.updateStatus("Drawing cancelled");
    }
  }

  public boolean isAddVertexPending() {
    return this.addVertexPending;
  }

  public void setAddVertexPending(final boolean pending) {
    this.addVertexPending = pending;
  }

  public void setDraft(final EditorDraft draft) {
    this.draft = draft;
    this.repaint();
  }

  public void clearDraft() {
    this.draft = null;
    this.repaint();
  }

  public void setRotationArmAngleRad(final double angleRad) {
    this.rotationArmAngleRad = angleRad;
  }

  public void clearRotationArmAngleRad() {
    this.rotationArmAngleRad = null;
  }

  public void adjustZoom(final double factor) {
    this.zoom = Math.max(0.1, Math.min(8.0, this.zoom * factor));
    this.recomputePreferredSize();
    this.repaint();
    this.publishCursorStatus();
  }

  public void setSelectionListener(final Runnable selectionListener) {
    this.selectionListener = Objects.requireNonNull(selectionListener, "selectionListener");
  }

  public void setSelectToolActivator(final Runnable selectToolActivator) {
    this.selectToolActivator = Objects.requireNonNull(selectToolActivator, "selectToolActivator");
  }

  public void activateSelectTool() {
    this.selectToolActivator.run();
  }

  private void notifySelectionChanged() {
    this.selectionListener.run();
  }

  @Override
  public EditorSession session() {
    return this.session;
  }

  @Override
  public BufferedImage image() {
    return this.session.baseImage();
  }

  @Override
  public double zoom() {
    return this.zoom;
  }

  @Override
  public boolean isGridVisible() {
    return this.gridVisible;
  }

  @Override
  public boolean isClassNamesVisible() {
    return this.classNamesVisible;
  }

  public void setGridVisible(final boolean gridVisible) {
    this.gridVisible = gridVisible;
    this.repaint();
  }

  public void setClassNamesVisible(final boolean classNamesVisible) {
    this.classNamesVisible = classNamesVisible;
    this.repaint();
  }

  @Override
  public Optional<String> selectedAnnotation() {
    return this.session.selectedAnnotation();
  }

  @Override
  public void selectAnnotation(final String key) {
    this.session.selectAnnotation(key);
    this.notifySelectionChanged();
  }

  @Override
  public void clearSelection() {
    this.session.clearSelection();
    this.notifySelectionChanged();
  }

  @Override
  public void markDirty() {
    this.session.markDirty();
    this.dirtyListener.run();
  }

  @Override
  public void repaintCanvas() {
    this.repaint();
  }

  @Override
  public void refreshToolbarState() {
    this.notifySelectionChanged();
  }

  @Override
  public void refreshDisplay() {
    RepaintManager.currentManager(this).markCompletelyDirty(this);
    this.repaint();
    SwingUtilities.invokeLater(() -> {
      this.repaint();
      if (this.getParent() != null) {
        this.getParent().repaint();
      }
    });
  }

  @Override
  public void updateStatus(final String text) {
    this.statusConsumer.accept(text);
  }

  @Override
  public Point imagePointFromScreen(final Point screenPoint) {
    final Point origin = this.imageOrigin();
    return new Point(
        (int) Math.round((screenPoint.x - origin.x) / this.zoom),
        (int) Math.round((screenPoint.y - origin.y) / this.zoom));
  }

  @Override
  public Point screenPointFromImage(final double normX, final double normY) {
    final Point origin = this.imageOrigin();
    return new Point(
        origin.x + (int) Math.round(normX * this.image().getWidth() * this.zoom),
        origin.y + (int) Math.round(normY * this.image().getHeight() * this.zoom));
  }

  @Override
  public String askClassId(final String title) {
    return this.askClassId(title, "");
  }

  @Override
  public String askClassId(final String title, final String defaultValue) {
    this.refreshDisplay();
    final String input =
        JOptionPane.showInputDialog(this, "Class label (alphanumeric, _, ., -):", title,
            JOptionPane.QUESTION_MESSAGE);
    if (input == null || input.isBlank()) {
      return null;
    }
    try {
      return ClassNames.normalize(input.trim());
    } catch (final IllegalArgumentException exception) {
      JOptionPane.showMessageDialog(this, exception.getMessage(), "Invalid class",
          JOptionPane.ERROR_MESSAGE);
      return this.askClassId(title, defaultValue);
    }
  }

  @Override
  protected void paintComponent(final Graphics graphics) {
    super.paintComponent(graphics);
    final Graphics2D g2 = (Graphics2D) graphics.create();
    this.paintCheckerboard(g2);
    final Point imageOrigin = this.imageOrigin();
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.translate(imageOrigin.x, imageOrigin.y);
    g2.scale(this.zoom, this.zoom);
    g2.drawImage(this.image(), 0, 0, null);
    if (this.gridVisible) {
      this.paintGrid(g2);
    }
    final int imageWidth = this.image().getWidth();
    final int imageHeight = this.image().getHeight();
    for (final AnnotationEntry entry : this.session.document().entries()) {
      final boolean selected = this.session.selectedAnnotation()
          .map(key -> key.equals(entry.key()))
          .orElse(false);
      this.overlayRenderer.paintAnnotation(
          g2,
          imageWidth,
          imageHeight,
          entry,
          true,
          selected,
          this.zoom);
    }
    if (this.classNamesVisible) {
      this.paintClassNames(g2, imageWidth, imageHeight);
    }
    if (this.draft != null) {
      this.paintDraft(g2, imageWidth, imageHeight);
    }
    this.session.selectedAnnotation()
        .flatMap(key -> this.session.document().findByKey(key))
        .filter(AnnotationEntry::visible)
        .ifPresent(entry -> this.paintSelectionHandles(g2, imageWidth, imageHeight, entry));
    this.paintImageBorder(g2, imageWidth, imageHeight);
    g2.dispose();
  }

  private void paintCheckerboard(final Graphics2D graphics) {
    for (int y = 0; y < this.getHeight(); y += CHECKER_SIZE) {
      for (int x = 0; x < this.getWidth(); x += CHECKER_SIZE) {
        final boolean light = ((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0;
        graphics.setColor(light ? CHECKER_LIGHT : CHECKER_DARK);
        graphics.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
      }
    }
  }

  private void paintImageBorder(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight) {
    graphics.setColor(IMAGE_BORDER);
    graphics.setStroke(new BasicStroke((float) Math.max(1.0, 1.0 / this.zoom)));
    graphics.drawRect(0, 0, imageWidth - 1, imageHeight - 1);
  }

  private void paintClassNames(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight) {
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    graphics.setFont(graphics.getFont().deriveFont((float) Math.max(10.0, 13.0 / this.zoom)));

    for (final AnnotationEntry entry : this.session.document().entries()) {
      if (entry.visible()) {
        this.paintClassName(graphics, imageWidth, imageHeight, entry);
      }
    }
  }

  private void paintClassName(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationEntry entry) {
    final NormPoint center = Geometry.clampNormPoint(AnnotationSelectionEditor.centroid(entry));
    final FontMetrics metrics = graphics.getFontMetrics();
    final String text = entry.id();
    final int textWidth = metrics.stringWidth(text);
    final int textHeight = metrics.getAscent() + metrics.getDescent();
    final double paddingX = Math.max(4.0, 5.0 / this.zoom);
    final double paddingY = Math.max(2.0, 3.0 / this.zoom);
    final double gap = Math.max(3.0, 5.0 / this.zoom);
    final double x = center.x() * imageWidth - textWidth / 2.0 - paddingX;
    final double y = center.y() * imageHeight + gap;
    final double width = textWidth + paddingX * 2.0;
    final double height = textHeight + paddingY * 2.0;
    final double arc = 8.0 / this.zoom;

    graphics.setColor(CLASS_LABEL_BACKGROUND);
    graphics.fill(new RoundRectangle2D.Double(x, y, width, height, arc, arc));
    graphics.setColor(CLASS_LABEL_FOREGROUND);
    graphics.drawString(
        text,
        (float) (x + paddingX),
        (float) (y + paddingY + metrics.getAscent()));
  }

  private void paintSelectionHandles(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final com.igormaznitsa.annotator.api.model.AnnotationEntry entry) {
    final double vertexRadius = Math.max(4.0, 6.0 / this.zoom);
    if (!entry.locked()) {
      this.paintRotationHandle(graphics, imageWidth, imageHeight, entry, vertexRadius);
    }
    final List<NormPoint> handles = AnnotationSelectionEditor.handlePositions(entry);
    final int selectedVertex = this.session.selectedVertexIndex().orElse(-1);
    final float outline = (float) Math.max(1.0, 1.5 / this.zoom);
    graphics.setStroke(new BasicStroke(outline));
    for (int i = 0; i < handles.size(); i++) {
      final NormPoint point = handles.get(i);
      final double px = point.x() * imageWidth;
      final double py = point.y() * imageHeight;
      final boolean selected = !entry.locked()
          && i == selectedVertex
          && AnnotationSelectionEditor.supportsMutableVertices(entry.type());
      graphics.setColor(selected
          ? new java.awt.Color(255, 200, 60)
          : entry.locked() ? LOCKED_HANDLE : SELECTION_GREEN);
      graphics.fill(
          new java.awt.geom.Ellipse2D.Double(px - vertexRadius, py - vertexRadius, vertexRadius * 2,
              vertexRadius * 2));
      graphics.setColor(entry.locked() ? LOCKED_HANDLE_DARK : SELECTION_GREEN_DARK);
      graphics.draw(
          new java.awt.geom.Ellipse2D.Double(px - vertexRadius, py - vertexRadius, vertexRadius * 2,
              vertexRadius * 2));
    }
    if (entry.type() == com.igormaznitsa.annotator.api.model.AnnotationType.OBB &&
        handles.size() == 4) {
      graphics.setColor(java.awt.Color.WHITE);
      final float fontSize = (float) Math.max(10.0, 12.0 / this.zoom);
      graphics.setFont(graphics.getFont().deriveFont(fontSize));
      for (int i = 0; i < handles.size(); i++) {
        final NormPoint point = handles.get(i);
        final double px = point.x() * imageWidth;
        final double py = point.y() * imageHeight;
        graphics.drawString(String.valueOf(i + 1), (float) px + (float) vertexRadius + 2f,
            (float) py - 2f);
      }
    }
  }

  private void paintRotationHandle(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final com.igormaznitsa.annotator.api.model.AnnotationEntry entry,
      final double vertexRadius) {
    if (!AnnotationSelectionEditor.supportsRotation(entry.type())) {
      return;
    }
    final Optional<Double> armAngle = Optional.ofNullable(this.rotationArmAngleRad);
    final Optional<NormPoint> handle = AnnotationSelectionEditor.rotationHandlePosition(
        entry,
        imageWidth,
        imageHeight,
        armAngle);
    if (handle.isEmpty()) {
      return;
    }
    final NormPoint displayCentroid =
        Geometry.clampNormPoint(AnnotationSelectionEditor.centroid(entry));
    final double cx = displayCentroid.x() * imageWidth;
    final double cy = displayCentroid.y() * imageHeight;
    final double hx = handle.get().x() * imageWidth;
    final double hy = handle.get().y() * imageHeight;
    final float armStroke = (float) Math.max(2.0, 2.5 / this.zoom);
    final double pivotRadius = Math.max(3.5, 5.0 / this.zoom);
    final double knobRadius = Math.max(vertexRadius + 3.0, 9.0 / this.zoom);
    graphics.setStroke(new BasicStroke(armStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(SELECTION_GREEN);
    graphics.drawLine((int) Math.round(cx), (int) Math.round(cy), (int) Math.round(hx),
        (int) Math.round(hy));
    this.paintCentroidCross(graphics, cx, cy, pivotRadius, armStroke);
    this.paintRotationKnob(graphics, hx, hy, knobRadius, armStroke);
  }

  private void paintCentroidCross(
      final Graphics2D graphics,
      final double cx,
      final double cy,
      final double armHalfLength,
      final float strokeWidth) {
    final float outlineStroke = strokeWidth + (float) Math.max(0.5, 1.0 / this.zoom);
    graphics.setStroke(
        new BasicStroke(outlineStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(Color.WHITE);
    graphics.drawLine(
        (int) Math.round(cx - armHalfLength),
        (int) Math.round(cy),
        (int) Math.round(cx + armHalfLength),
        (int) Math.round(cy));
    graphics.drawLine(
        (int) Math.round(cx),
        (int) Math.round(cy - armHalfLength),
        (int) Math.round(cx),
        (int) Math.round(cy + armHalfLength));
    graphics.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(SELECTION_GREEN_DARK);
    graphics.drawLine(
        (int) Math.round(cx - armHalfLength),
        (int) Math.round(cy),
        (int) Math.round(cx + armHalfLength),
        (int) Math.round(cy));
    graphics.drawLine(
        (int) Math.round(cx),
        (int) Math.round(cy - armHalfLength),
        (int) Math.round(cx),
        (int) Math.round(cy + armHalfLength));
    graphics.setColor(SELECTION_GREEN);
    graphics.drawLine(
        (int) Math.round(cx - armHalfLength * 0.85),
        (int) Math.round(cy),
        (int) Math.round(cx + armHalfLength * 0.85),
        (int) Math.round(cy));
    graphics.drawLine(
        (int) Math.round(cx),
        (int) Math.round(cy - armHalfLength * 0.85),
        (int) Math.round(cx),
        (int) Math.round(cy + armHalfLength * 0.85));
  }

  private void paintRotationKnob(
      final Graphics2D graphics,
      final double hx,
      final double hy,
      final double halfSize,
      final float strokeWidth) {
    final Rectangle2D.Double square =
        new Rectangle2D.Double(hx - halfSize, hy - halfSize, halfSize * 2, halfSize * 2);
    graphics.setColor(SELECTION_GREEN);
    graphics.fill(square);
    graphics.setStroke(new BasicStroke(strokeWidth + (float) Math.max(0.5, 1.0 / this.zoom)));
    graphics.setColor(Color.WHITE);
    graphics.draw(square);
    graphics.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(SELECTION_GREEN_DARK);
    graphics.draw(square);
  }

  private void paintDraft(final Graphics2D graphics, final int imageWidth, final int imageHeight) {
    switch (this.draft) {
      case EditorDraft.Rectangle rectangle -> this.overlayRenderer.paintDraftRectangle(
          graphics,
          imageWidth,
          imageHeight,
          rectangle.from(),
          rectangle.to(),
          this.zoom);
      case EditorDraft.Polyline polyline -> this.overlayRenderer.paintDraftPolyline(
          graphics,
          imageWidth,
          imageHeight,
          polyline.points(),
          polyline.closed(),
          polyline.cursor(),
          this.zoom);
      case EditorDraft.Obbox obbox -> this.overlayRenderer.paintDraftObbox(
          graphics,
          imageWidth,
          imageHeight,
          obbox.corners(),
          obbox.cursor().orElse(null),
          this.zoom);
      case EditorDraft.PendingShape pending -> this.paintPendingShape(
          graphics,
          imageWidth,
          imageHeight,
          pending);
    }
  }

  private void paintPendingShape(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final EditorDraft.PendingShape pending) {
    final AnnotationEntry preview = new AnnotationEntry(
        "__pending__",
        pending.type(),
        "#FFD000",
        pending.coords());
    this.overlayRenderer.paintAnnotation(graphics, imageWidth, imageHeight, preview, true, true,
        this.zoom);
  }

  private void paintGrid(final Graphics2D graphics) {
    graphics.setColor(new Color(255, 255, 255, 48));
    graphics.setStroke(new BasicStroke((float) Math.max(1.0, 1.0 / this.zoom)));
    final int width = this.image().getWidth();
    final int height = this.image().getHeight();
    for (int x = 0; x < width; x += GRID_STEP) {
      graphics.drawLine(x, 0, x, height);
    }
    for (int y = 0; y < height; y += GRID_STEP) {
      graphics.drawLine(0, y, width, y);
    }
  }

  private void registerKeyboardActions() {
    final int when = JPanel.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
    final KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    this.getInputMap(when).put(escape, "cancelDrawing");
    this.getActionMap().put("cancelDrawing", new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent event) {
        ImageCanvas.this.cancelActiveDrawing();
      }
    });
    final AbstractAction completeDrawing = new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent event) {
        ImageCanvas.this.handleEditorKey(KeyEvent.VK_ENTER);
      }
    };
    this.getInputMap(when).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "completeDrawing");
    this.getActionMap().put("completeDrawing", completeDrawing);
    final int whenFocused = JComponent.WHEN_FOCUSED;
    final AbstractAction removeSelection = new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent event) {
        ImageCanvas.this.tryRemoveSelection();
      }
    };
    this.getInputMap(whenFocused)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeSelection");
    this.getInputMap(whenFocused)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "removeSelection");
    this.getActionMap().put("removeSelection", removeSelection);
    final AbstractAction insertVertexAfter = new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent event) {
        ImageCanvas.this.tryInsertVertexAfterSelection();
      }
    };
    this.getInputMap(whenFocused)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), "insertVertexAfter");
    this.getActionMap().put("insertVertexAfter", insertVertexAfter);
  }

  private void tryRemoveSelection() {
    if (VertexEditOperations.canRemoveVertex(this)) {
      VertexEditOperations.removeSelectedVertex(this, false);
      return;
    }
    if (VertexEditOperations.canRemoveSelectedShape(this)) {
      VertexEditOperations.removeSelectedShape(this, false);
    }
  }

  private void tryInsertVertexAfterSelection() {
    if (!VertexEditOperations.canInsertVertexAfterSelection(this)) {
      return;
    }
    VertexEditOperations.insertVertexAfterSelection(this);
  }

  private void handleEditorKey(final int keyCode) {
    if (this.activeEditTool == null) {
      return;
    }
    if (keyCode == KeyEvent.VK_ENTER && this.activeEditTool.completeDrawing(this)) {
      return;
    }
    this.activeEditTool.onKeyPress(this, keyCode);
  }

  private boolean tryHandleAddVertexClick(final MouseEvent event) {
    if (!this.addVertexPending || event.getButton() != MouseEvent.BUTTON1) {
      return false;
    }
    final Point imagePoint = this.imagePointFromScreen(event.getPoint());
    final int width = this.image().getWidth();
    final int height = this.image().getHeight();
    final double normX = Geometry.clamp01((double) imagePoint.x / width);
    final double normY = Geometry.clamp01((double) imagePoint.y / height);
    if (VertexEditOperations.addVertexAt(this, normX, normY, width, height)) {
      this.setAddVertexPending(false);
    }
    this.refreshToolbarState();
    return true;
  }

  private void registerMouseHandlers() {
    final MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent event) {
        ImageCanvas.this.requestFocusInWindow();
        if (ImageCanvas.this.tryHandleAddVertexClick(event)) {
          return;
        }
        if (event.getButton() == MouseEvent.BUTTON3
            && ImageCanvas.this.activeEditTool != null
            && ImageCanvas.this.activeEditTool.completeDrawing(ImageCanvas.this)) {
          return;
        }
        if (ImageCanvas.this.activeMouseTool != null) {
          ImageCanvas.this.activeMouseTool.mousePressed(ImageCanvas.this, event);
        }
      }

      @Override
      public void mouseReleased(final MouseEvent event) {
        if (ImageCanvas.this.activeMouseTool != null) {
          ImageCanvas.this.activeMouseTool.mouseReleased(ImageCanvas.this, event);
        }
      }

      @Override
      public void mouseDragged(final MouseEvent event) {
        ImageCanvas.this.trackCursor(event);
        if (ImageCanvas.this.activeMouseTool != null) {
          ImageCanvas.this.activeMouseTool.mouseDragged(ImageCanvas.this, event);
        }
      }

      @Override
      public void mouseMoved(final MouseEvent event) {
        ImageCanvas.this.trackCursor(event);
        if (ImageCanvas.this.activeMouseTool != null) {
          ImageCanvas.this.activeMouseTool.mouseMoved(ImageCanvas.this, event);
        }
      }

      @Override
      public void mouseClicked(final MouseEvent event) {
        if (ImageCanvas.this.activeMouseTool != null) {
          ImageCanvas.this.activeMouseTool.mouseClicked(ImageCanvas.this, event);
        }
      }
    };
    this.addMouseListener(adapter);
    this.addMouseMotionListener(adapter);
    this.addMouseWheelListener(event -> {
      if (ImageCanvas.this.activeMouseTool != null) {
        ImageCanvas.this.activeMouseTool.mouseWheelMoved(ImageCanvas.this, event);
      }
      if (!event.isConsumed()) {
        ImageCanvas.this.forwardWheelToScrollPane(event);
      }
    });
  }

  private void forwardWheelToScrollPane(final MouseWheelEvent event) {
    if (!(SwingUtilities.getAncestorOfClass(JScrollPane.class,
        this) instanceof JScrollPane scroll)) {
      return;
    }
    final Point point = SwingUtilities.convertPoint(this, event.getPoint(), scroll);
    final MouseWheelEvent forwarded = new MouseWheelEvent(
        scroll,
        event.getID(),
        event.getWhen(),
        event.getModifiersEx(),
        point.x,
        point.y,
        event.getXOnScreen(),
        event.getYOnScreen(),
        event.getClickCount(),
        event.isPopupTrigger(),
        event.getScrollType(),
        event.getScrollAmount(),
        event.getWheelRotation(),
        event.getPreciseWheelRotation());
    scroll.dispatchEvent(forwarded);
    if (forwarded.isConsumed()) {
      event.consume();
    }
  }

  private void trackCursor(final MouseEvent event) {
    this.lastImagePoint = this.imagePointFromScreen(event.getPoint());
    this.publishCursorStatus();
  }

  private void publishCursorStatus() {
    if (this.lastImagePoint == null) {
      return;
    }
    final int width = Math.max(1, this.image().getWidth());
    final int height = Math.max(1, this.image().getHeight());
    final double nx = this.lastImagePoint.x / (double) width;
    final double ny = this.lastImagePoint.y / (double) height;
    final String text = String.format(
        Locale.ROOT,
        "x=%d y=%d  norm=(%.3f, %.3f)  zoom=%.0f%%  annotations=%d",
        this.lastImagePoint.x,
        this.lastImagePoint.y,
        nx,
        ny,
        this.zoom * 100.0,
        this.session.document().entries().size());
    this.statusConsumer.accept(text);
  }

  private void recomputePreferredSize() {
    this.setPreferredSize(this.imageDisplaySize());
    this.revalidate();
  }

  private Dimension imageDisplaySize() {
    return new Dimension(
        (int) Math.round(this.image().getWidth() * this.zoom),
        (int) Math.round(this.image().getHeight() * this.zoom));
  }

  private Point imageOrigin() {
    final Dimension size = this.imageDisplaySize();
    return new Point(
        Math.max(0, (this.getWidth() - size.width) / 2),
        Math.max(0, (this.getHeight() - size.height) / 2));
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return this.getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(
      final Rectangle visibleRect,
      final int orientation,
      final int direction) {
    return CHECKER_SIZE;
  }

  @Override
  public int getScrollableBlockIncrement(
      final Rectangle visibleRect,
      final int orientation,
      final int direction) {
    return Math.max(CHECKER_SIZE, (orientation == SwingUtilities.VERTICAL
        ? visibleRect.height
        : visibleRect.width) - CHECKER_SIZE);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return this.isViewportWiderThanImage();
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return this.isViewportTallerThanImage();
  }

  private boolean isViewportWiderThanImage() {
    return this.getParent() instanceof JViewport viewport
        && viewport.getExtentSize().width > this.imageDisplaySize().width;
  }

  private boolean isViewportTallerThanImage() {
    return this.getParent() instanceof JViewport viewport
        && viewport.getExtentSize().height > this.imageDisplaySize().height;
  }
}
