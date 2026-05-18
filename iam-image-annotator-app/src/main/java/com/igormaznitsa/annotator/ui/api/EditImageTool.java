package com.igormaznitsa.annotator.ui.api;

import javax.swing.JButton;
import javax.swing.JToggleButton;

public interface EditImageTool {

  String id();

  String tooltip();

  String iconFileName();

  boolean isExclusive();

  void activate(EditorPanelContext context);

  void deactivate(EditorPanelContext context);

  /**
   * @return true if an in-progress drawing was cancelled
   */
  default boolean cancelDrawing(final EditorPanelContext context) {
    return false;
  }

  /**
   * @return true if an in-progress drawing was completed (or the attempt was consumed)
   */
  default boolean completeDrawing(final EditorPanelContext context) {
    return false;
  }

  /**
   * @return true if the key was handled
   */
  default boolean onKeyPress(final EditorPanelContext context, final int keyCode) {
    return false;
  }

  default JToggleButton createToggleButton(final Runnable onSelected) {
    final JToggleButton button = new JToggleButton();
    button.setToolTipText(this.tooltip());
    button.setFocusable(false);
    button.addActionListener(event -> onSelected.run());
    return button;
  }

  default JButton createActionButton(final Runnable onClick) {
    final JButton button = new JButton();
    button.setToolTipText(this.tooltip());
    button.setFocusable(false);
    button.addActionListener(event -> onClick.run());
    return button;
  }
}
