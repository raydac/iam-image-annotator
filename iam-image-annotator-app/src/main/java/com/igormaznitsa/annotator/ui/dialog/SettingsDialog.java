package com.igormaznitsa.annotator.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class SettingsDialog {

  private static final String LOOK_AND_FEEL_KEY = "lookAndFeelClassName";
  private static final Preferences PREFERENCES =
      Preferences.userNodeForPackage(SettingsDialog.class);

  private SettingsDialog() {
  }

  public static void installStartupLookAndFeel() {
    try {
      applyLookAndFeel(preferredLookAndFeelClassName()
          .orElseGet(UIManager::getSystemLookAndFeelClassName));
    } catch (final RuntimeException ignored) {
      // Keep Swing default Look & Feel if the configured one is unavailable.
    }
  }

  public static void show(final Component parent) {
    final Component owner = DialogParents.frameOrSelf(parent);
    final JComboBox<UIManager.LookAndFeelInfo> lookAndFeel =
        new JComboBox<>(installedLookAndFeels());
    lookAndFeel.setRenderer(new LookAndFeelRenderer());
    selectCurrentLookAndFeel(lookAndFeel);

    final JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.add(new JLabel("Look & Feel:"), BorderLayout.WEST);
    panel.add(lookAndFeel, BorderLayout.CENTER);

    final int choice = JOptionPane.showConfirmDialog(
        owner,
        panel,
        "Settings",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION || lookAndFeel.getSelectedItem() == null) {
      return;
    }

    applySelectedLookAndFeel(owner, (UIManager.LookAndFeelInfo) lookAndFeel.getSelectedItem());
  }

  private static UIManager.LookAndFeelInfo[] installedLookAndFeels() {
    return UIManager.getInstalledLookAndFeels();
  }

  private static Optional<String> preferredLookAndFeelClassName() {
    return Optional.ofNullable(PREFERENCES.get(LOOK_AND_FEEL_KEY, null))
        .filter(className -> Arrays.stream(installedLookAndFeels())
            .map(UIManager.LookAndFeelInfo::getClassName)
            .anyMatch(className::equals));
  }

  private static void selectCurrentLookAndFeel(
      final JComboBox<UIManager.LookAndFeelInfo> lookAndFeel) {
    final String current = UIManager.getLookAndFeel().getClass().getName();
    for (int i = 0; i < lookAndFeel.getItemCount(); i++) {
      if (lookAndFeel.getItemAt(i).getClassName().equals(current)) {
        lookAndFeel.setSelectedIndex(i);
        return;
      }
    }
  }

  private static void applySelectedLookAndFeel(
      final Component parent,
      final UIManager.LookAndFeelInfo lookAndFeel) {
    try {
      applyLookAndFeel(lookAndFeel.getClassName());
      PREFERENCES.put(LOOK_AND_FEEL_KEY, lookAndFeel.getClassName());
      updateOpenWindows();
    } catch (final RuntimeException exception) {
      JOptionPane.showMessageDialog(
          parent,
          "Unable to apply Look & Feel: " + exception.getMessage(),
          "Settings",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private static void applyLookAndFeel(final String className) {
    try {
      UIManager.setLookAndFeel(Objects.requireNonNull(className, "className"));
    } catch (final Exception exception) {
      throw new IllegalStateException(exception.getMessage(), exception);
    }
  }

  private static void updateOpenWindows() {
    Arrays.stream(Window.getWindows()).forEach(window -> {
      SwingUtilities.updateComponentTreeUI(window);
      window.invalidate();
      window.validate();
      window.repaint();
    });
  }

  private static final class LookAndFeelRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(
        final javax.swing.JList<?> list,
        final Object value,
        final int index,
        final boolean selected,
        final boolean focused) {
      final Component component =
          super.getListCellRendererComponent(list, value, index, selected, focused);
      if (component instanceof JLabel label && value instanceof UIManager.LookAndFeelInfo info) {
        label.setText(info.getName());
      }
      return component;
    }
  }
}
