package com.igormaznitsa.annotator.ui.api;

public interface ImageViewAction {

  String id();

  String tooltip();

  String iconFileName();

  void execute(EditorPanelContext context);

  default boolean isEnabled(final EditorPanelContext context) {
    return true;
  }

  default String tooltip(final EditorPanelContext context) {
    return this.tooltip();
  }
}
