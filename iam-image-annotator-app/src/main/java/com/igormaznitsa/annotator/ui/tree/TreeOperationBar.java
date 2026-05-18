package com.igormaznitsa.annotator.ui.tree;

import com.igormaznitsa.annotator.ui.api.TreeOperationContext;
import com.igormaznitsa.annotator.ui.api.TreeOperationIcon;
import com.igormaznitsa.annotator.ui.icons.IconService;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;

public final class TreeOperationBar extends JPanel {

  private final IconService icons;
  private final List<TreeOperationIcon> operations;

  public TreeOperationBar(final IconService icons, final List<TreeOperationIcon> operations) {
    super(new FlowLayout(FlowLayout.LEFT, 2, 2));
    this.icons = icons;
    this.operations = List.copyOf(operations);
  }

  public void bind(final TreeOperationContext context) {
    this.removeAll();
    for (final TreeOperationIcon operation : this.operations) {
      final JButton button = operation.createButton(context, () -> this.refreshState(context));
      button.setIcon(this.icons.icon16(operation.iconFileName()));
      button.setText(null);
      button.setEnabled(operation.isEnabled(context));
      this.add(button);
    }
    this.revalidate();
    this.repaint();
  }

  public void refreshState(final TreeOperationContext context) {
    for (int i = 0; i < this.getComponentCount(); i++) {
      if (this.getComponent(i) instanceof JButton button) {
        final TreeOperationIcon operation = this.operations.get(i);
        button.setEnabled(operation.isEnabled(context));
      }
    }
  }
}
