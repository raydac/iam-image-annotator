package com.igormaznitsa.annotator.ui.api;

public interface ImageViewToggle {

  String id();

  String tooltip();

  String iconFileName();

  boolean isSelected(EditorPanelContext context);

  void setSelected(EditorPanelContext context, boolean selected);

  default boolean isEnabled(final EditorPanelContext context) {
    return true;
  }

  default String iconFileName(final EditorPanelContext context) {
    return this.iconFileName();
  }

  default String tooltip(final EditorPanelContext context) {
    return this.tooltip();
  }
}
