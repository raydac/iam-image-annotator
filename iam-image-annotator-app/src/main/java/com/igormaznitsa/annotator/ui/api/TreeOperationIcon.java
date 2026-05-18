package com.igormaznitsa.annotator.ui.api;

import javax.swing.JButton;

public interface TreeOperationIcon {

  String id();

  String tooltip();

  String iconFileName();

  boolean isEnabled(TreeOperationContext context);

  void execute(TreeOperationContext context);

  default JButton createButton(final TreeOperationContext context, final Runnable onStateChange) {
    final JButton button = new JButton();
    button.setToolTipText(this.tooltip());
    button.setFocusable(false);
    button.addActionListener(event -> this.execute(context));
    button.addChangeListener(event -> onStateChange.run());
    return button;
  }
}
