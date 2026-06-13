package com.igormaznitsa.annotator.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public final class ColorChooserDialog {

  private static final Dimension PURE_COLOR_BUTTON_SIZE = new Dimension(42, 26);
  private static final int SWATCH_WIDTH = 30;
  private static final int SWATCH_HEIGHT = 16;
  private static final PureColor[] PURE_COLORS = {
      new PureColor("Black", Color.BLACK),
      new PureColor("White", Color.WHITE),
      new PureColor("Red", Color.RED),
      new PureColor("Green", Color.GREEN),
      new PureColor("Blue", Color.BLUE),
      new PureColor("Cyan", Color.CYAN),
      new PureColor("Magenta", Color.MAGENTA),
      new PureColor("Yellow", Color.YELLOW)
  };

  private ColorChooserDialog() {
  }

  public static Optional<Color> show(
      final Component parent,
      final String title,
      final Color initialColor) {
    final Component owner = DialogParents.frameOrSelf(parent);
    final JColorChooser chooser = new JColorChooser(initialColor);
    final JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    panel.add(createPureColorPanel(chooser), BorderLayout.NORTH);
    panel.add(chooser, BorderLayout.CENTER);

    final int choice = JOptionPane.showConfirmDialog(
        owner,
        panel,
        title,
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE);
    return choice == JOptionPane.OK_OPTION ? Optional.of(chooser.getColor()) : Optional.empty();
  }

  public static String toHex(final Color color) {
    return String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xFFFFFF);
  }

  private static JPanel createPureColorPanel(final JColorChooser chooser) {
    final JPanel wrapper = new JPanel(new BorderLayout(6, 0));
    wrapper.add(createPureColorButtons(chooser), BorderLayout.CENTER);
    return wrapper;
  }

  private static JPanel createPureColorButtons(final JColorChooser chooser) {
    final JPanel buttons = new JPanel(new GridLayout(1, PURE_COLORS.length, 4, 0));
    for (final PureColor pureColor : PURE_COLORS) {
      buttons.add(createPureColorButton(chooser, pureColor));
    }
    return buttons;
  }

  private static JButton createPureColorButton(
      final JColorChooser chooser,
      final PureColor pureColor) {
    final JButton button = new JButton();
    button.setPreferredSize(PURE_COLOR_BUTTON_SIZE);
    button.setIcon(new ColorSwatchIcon(pureColor.color()));
    button.setMargin(new Insets(1, 1, 1, 1));
    button.setToolTipText(pureColor.name() + " " + toHex(pureColor.color()));
    button.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
    button.setFocusPainted(false);
    button.setFocusable(false);
    button.addActionListener(event -> chooser.setColor(pureColor.color()));
    return button;
  }

  private record PureColor(String name, Color color) {
  }

  private record ColorSwatchIcon(Color color) implements Icon {

    @Override
    public void paintIcon(final Component component, final Graphics graphics, final int x,
                          final int y) {
      graphics.setColor(this.color);
      graphics.fillRect(x, y, SWATCH_WIDTH, SWATCH_HEIGHT);
      graphics.setColor(Color.DARK_GRAY);
      graphics.drawRect(x, y, SWATCH_WIDTH - 1, SWATCH_HEIGHT - 1);
    }

    @Override
    public int getIconWidth() {
      return SWATCH_WIDTH;
    }

    @Override
    public int getIconHeight() {
      return SWATCH_HEIGHT;
    }
  }
}
