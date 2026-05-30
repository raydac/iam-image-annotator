package com.igormaznitsa.annotator.ui.dialog;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;

public final class LayerOrderDialog extends JDialog {

  private static final int VISIBILITY_COLUMN_WIDTH = 30;

  private final EditorPanelContext context;
  private final EditorSession session;
  private final DefaultListModel<AnnotationEntry> model = new DefaultListModel<>();
  private final JList<AnnotationEntry> list = new JList<>(this.model);

  public LayerOrderDialog(final Frame owner, final EditorPanelContext context) {
    super(owner, "Annotation layers", true);
    this.context = context;
    this.session = context.session();
    this.setLayout(new BorderLayout(8, 8));
    this.reload();
    this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.list.setCellRenderer(new LayerCellRenderer());
    this.list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent event) {
        LayerOrderDialog.this.toggleVisibilityAt(event);
      }
    });
    this.add(new JScrollPane(this.list), BorderLayout.CENTER);
    this.add(this.buildButtons(), BorderLayout.SOUTH);
    this.setSize(420, 360);
    this.setLocationRelativeTo(owner);
  }

  public static void showFor(final Frame owner, final EditorPanelContext context) {
    new LayerOrderDialog(owner, context).setVisible(true);
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
    final String selectedName = this.selectedName();
    this.model.clear();
    for (final AnnotationEntry entry : this.session.document().entries()) {
      this.model.addElement(entry);
      if (entry.id().equals(selectedName)) {
        this.list.setSelectedIndex(this.model.size() - 1);
      }
    }
  }

  private String selectedName() {
    final AnnotationEntry entry = this.list.getSelectedValue();
    return entry == null ? null : entry.id();
  }

  private void toggleVisibilityAt(final MouseEvent event) {
    final int row = this.list.locationToIndex(event.getPoint());
    if (row < 0 || !this.isInsideVisibilityColumn(row, event.getPoint())) {
      return;
    }
    final AnnotationEntry entry = this.model.getElementAt(row);
    final boolean visible = !entry.visible();
    this.session.recordUndoCheckpoint();
    this.session.document().setVisible(entry.id(), visible);
    if (!visible && this.context.selectedAnnotation().filter(entry.id()::equals).isPresent()) {
      this.context.clearSelection();
    }
    this.context.markDirty();
    this.context.repaintCanvas();
    this.reload();
    this.list.setSelectedIndex(row);
  }

  private boolean isInsideVisibilityColumn(final int row, final Point point) {
    final java.awt.Rectangle bounds = this.list.getCellBounds(row, row);
    return bounds != null
        && bounds.contains(point)
        && point.x - bounds.x <= VISIBILITY_COLUMN_WIDTH;
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
    this.context.markDirty();
    this.context.repaintCanvas();
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
      this.context.markDirty();
      this.context.repaintCanvas();
      this.reload();
    } catch (final IllegalArgumentException | IllegalStateException exception) {
      JOptionPane.showMessageDialog(this, exception.getMessage(), "Rename",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private static final class LayerCellRenderer extends JPanel
      implements ListCellRenderer<AnnotationEntry> {

    private final JCheckBox visible = new JCheckBox();
    private final ColorSwatch swatch = new ColorSwatch();
    private final JLabel label = new JLabel();

    LayerCellRenderer() {
      super(new FlowLayout(FlowLayout.LEFT, 8, 2));
      this.setBorder(new EmptyBorder(2, 2, 2, 2));
      this.visible.setPreferredSize(new Dimension(20, 20));
      this.visible.setOpaque(false);
      this.swatch.setPreferredSize(new Dimension(26, 14));
      this.add(this.visible);
      this.add(this.swatch);
      this.add(this.label);
    }

    @Override
    public Component getListCellRendererComponent(
        final JList<? extends AnnotationEntry> list,
        final AnnotationEntry entry,
        final int index,
        final boolean selected,
        final boolean focused) {
      final String lockSuffix = entry.locked() ? "  [locked]" : "";
      this.visible.setSelected(entry.visible());
      this.swatch.setColor(entry.fillColor());
      this.label.setText(entry.id() + lockSuffix + "  [" + entry.type().jsonName() + "]");
      this.label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
      this.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
      this.setOpaque(selected);
      return this;
    }
  }

  private static final class ColorSwatch extends JComponent {

    private Color color = Color.GRAY;

    void setColor(final Color color) {
      this.color = color;
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
      super.paintComponent(graphics);
      graphics.setColor(this.color);
      graphics.fillRect(1, 1, this.getWidth() - 2, this.getHeight() - 2);
      graphics.setColor(Color.DARK_GRAY);
      graphics.drawRect(0, 0, this.getWidth() - 1, this.getHeight() - 1);
    }
  }
}
