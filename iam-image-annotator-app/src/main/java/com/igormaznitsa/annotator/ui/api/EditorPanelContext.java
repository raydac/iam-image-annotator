package com.igormaznitsa.annotator.ui.api;

import com.igormaznitsa.annotator.api.service.EditorSession;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Optional;

public interface EditorPanelContext {

  EditorSession session();

  BufferedImage image();

  double zoom();

  boolean isGridVisible();

  Optional<String> selectedAnnotation();

  void selectAnnotation(String name);

  void clearSelection();

  void markDirty();

  void repaintCanvas();

  default void refreshToolbarState() {
  }

  default void refreshDisplay() {
    this.repaintCanvas();
  }

  void updateStatus(String text);

  Point imagePointFromScreen(Point screenPoint);

  Point screenPointFromImage(double normX, double normY);

  String askClassId(String title);

  String askClassId(String title, String defaultValue);
}
