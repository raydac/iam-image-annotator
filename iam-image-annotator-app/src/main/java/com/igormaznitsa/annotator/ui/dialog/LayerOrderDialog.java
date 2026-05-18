package com.igormaznitsa.annotator.ui.dialog;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.service.EditorSession;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public final class LayerOrderDialog extends JDialog {

  private final EditorSession session;
  private final DefaultListModel<String> model = new DefaultListModel<>();
  private final JList<String> list = new JList<>(this.model);

  public LayerOrderDialog(final Frame owner, final EditorSession session) {
    super(owner, "Annotation layers", true);
    this.session = session;
    this.setLayout(new BorderLayout(8, 8));
    this.reload();
    this.add(new JScrollPane(this.list), BorderLayout.CENTER);
    this.add(this.buildButtons(), BorderLayout.SOUTH);
    this.setSize(420, 360);
    this.setLocationRelativeTo(owner);
  }

  public static void showFor(final Frame owner, final EditorSession session) {
    new LayerOrderDialog(owner, session).setVisible(true);
  }

  private JPanel buildButtons() {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    final JButton up = new JButton("Up");
    final JButton down = new JButton("Down");
    final JButton rename = new JButton("Rename");
    panel.add(up);
    panel.add(down);
    panel.add(rename);
    final JButton close = new JButton("Close");
    panel.add(close);
    up.addActionListener(event -> this.moveSelected(-1));
    down.addActionListener(event -> this.moveSelected(1));
    rename.addActionListener(event -> this.renameSelected());
    close.addActionListener(event -> this.dispose());
    return panel;
  }

  private void reload() {
    this.model.clear();
    for (final AnnotationEntry entry : this.session.document().entries()) {
      final String lockSuffix = entry.locked() ? "  [locked]" : "";
      this.model.addElement(entry.id() + lockSuffix + "  [" + entry.type().jsonName() + "]");
    }
  }

  private String selectedName() {
    final String label = this.list.getSelectedValue();
    if (label == null) {
      return null;
    }
    return label.split("  \\[", 2)[0];
  }

  private void moveSelected(final int direction) {
    final String name = this.selectedName();
    if (name == null) {
      return;
    }
    this.session.recordUndoCheckpoint();
    if (direction < 0) {
      this.session.document().moveUp(name);
    } else {
      this.session.document().moveDown(name);
    }
    this.session.markDirty();
    this.reload();
  }

  private void renameSelected() {
    final String oldName = this.selectedName();
    if (oldName == null) {
      return;
    }
    final String newName = JOptionPane.showInputDialog(this, "New unique label:", oldName);
    if (newName == null || newName.isBlank()) {
      return;
    }
    try {
      this.session.recordUndoCheckpoint();
      this.session.document().rename(oldName, newName.trim());
      this.session.markDirty();
      this.reload();
    } catch (final IllegalArgumentException | IllegalStateException exception) {
      JOptionPane.showMessageDialog(this, exception.getMessage(), "Rename",
          JOptionPane.ERROR_MESSAGE);
    }
  }
}
