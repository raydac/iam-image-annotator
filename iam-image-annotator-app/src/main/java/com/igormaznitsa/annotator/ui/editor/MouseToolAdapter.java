package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.ui.api.EditorPanelContext;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public abstract class MouseToolAdapter {

  public void activate(final EditorPanelContext context) {
  }

  public void deactivate(final EditorPanelContext context) {
  }

  public void mousePressed(final EditorPanelContext context, final MouseEvent event) {
  }

  public void mouseReleased(final EditorPanelContext context, final MouseEvent event) {
  }

  public void mouseDragged(final EditorPanelContext context, final MouseEvent event) {
  }

  public void mouseMoved(final EditorPanelContext context, final MouseEvent event) {
  }

  public void mouseClicked(final EditorPanelContext context, final MouseEvent event) {
  }

  public void mouseWheelMoved(final EditorPanelContext context, final MouseWheelEvent event) {
  }

  protected Point imagePoint(final EditorPanelContext context, final MouseEvent event) {
    return context.imagePointFromScreen(event.getPoint());
  }
}
