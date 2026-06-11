package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.ui.icons.IconService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

public final class EditorTabbedPane extends JTabbedPane {

  private final IconService icons;
  private final ImageToolBarFactory toolBarFactory;
  private final Frame frame;
  private final Map<Path, ImageCanvas> canvases = new LinkedHashMap<>();
  private final Map<Path, ImageToolBar> toolBars = new LinkedHashMap<>();
  private Consumer<Path> revealInTreeListener = ignored -> {
  };
  private Consumer<Path> sessionClosedListener = ignored -> {
  };
  private Runnable stateChangeListener = () -> {
  };

  public EditorTabbedPane(
      final IconService icons,
      final ImageToolBarFactory toolBarFactory,
      final Frame frame) {
    this.icons = icons;
    this.toolBarFactory = Objects.requireNonNull(toolBarFactory, "toolBarFactory");
    this.frame = Objects.requireNonNull(frame, "frame");
    this.addChangeListener(event -> {
      final ImageToolBar toolBar = this.activeToolBar();
      if (toolBar != null) {
        toolBar.reactivateSelectedTool();
        toolBar.refreshToolbarState();
      }
    });
  }

  public void setRevealInTreeListener(final Consumer<Path> listener) {
    this.revealInTreeListener = listener;
  }

  public void setSessionClosedListener(final Consumer<Path> listener) {
    this.sessionClosedListener = listener;
  }

  public void setStateChangeListener(final Runnable listener) {
    this.stateChangeListener = listener;
  }

  public boolean hasOpenTab(final Path path) {
    return this.canvases.containsKey(path);
  }

  public void openSession(final EditorSession session, final Consumer<String> statusConsumer) {
    if (this.canvases.containsKey(session.filePath())) {
      this.selectSession(session.filePath());
      return;
    }
    final ImageCanvas canvas = new ImageCanvas(session, statusConsumer, this::notifyStateChanged);
    final ImageToolBar toolBar = this.toolBarFactory.create(canvas, this.frame);
    canvas.setSelectionListener(toolBar::refreshToolbarState);
    canvas.setSelectToolActivator(() -> toolBar.activateTool("select"));
    canvas.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent event) {
        toolBar.refreshToolbarState();
      }
    });
    toolBar.selectDefaultTool();
    final JPanel editorPanel = new JPanel(new BorderLayout(4, 0));
    editorPanel.add(toolBar, BorderLayout.WEST);
    editorPanel.add(new JScrollPane(canvas), BorderLayout.CENTER);
    this.canvases.put(session.filePath(), canvas);
    this.toolBars.put(session.filePath(), toolBar);
    final String title = this.formatTitle(session);
    this.addTab(title, editorPanel);
    final int index = this.indexOfComponent(editorPanel);
    this.setTabComponentAt(index, this.createTabComponent(session, title));
    this.setSelectedComponent(editorPanel);
    this.notifyStateChanged();
  }

  public ImageToolBar activeToolBar() {
    final ImageCanvas canvas = this.activeCanvas();
    return canvas == null ? null : this.toolBars.get(canvas.session().filePath());
  }

  public ImageCanvas activeCanvas() {
    final int index = this.getSelectedIndex();
    if (index < 0) {
      return null;
    }
    return this.canvasAt(index);
  }

  public void selectSession(final Path path) {
    for (int i = 0; i < this.getTabCount(); i++) {
      final EditorSession session = this.sessionAt(i);
      if (session != null && session.filePath().equals(path)) {
        this.setSelectedIndex(i);
        return;
      }
    }
  }

  public void refreshTitles() {
    for (int i = 0; i < this.getTabCount(); i++) {
      final EditorSession session = this.sessionAt(i);
      if (session == null) {
        continue;
      }
      final String title = this.formatTitle(session);
      this.setTitleAt(i, title);
      if (this.getTabComponentAt(i) instanceof JPanel panel) {
        for (final java.awt.Component component : panel.getComponents()) {
          if (component instanceof JLabel label) {
            label.setText(title);
          }
        }
      }
    }
  }

  public void rekeySession(final Path oldPath, final Path newPath) {
    final ImageCanvas canvas = this.canvases.remove(oldPath);
    final ImageToolBar toolBar = this.toolBars.remove(oldPath);
    if (canvas == null) {
      return;
    }
    this.canvases.put(newPath, canvas);
    if (toolBar != null) {
      this.toolBars.put(newPath, toolBar);
    }
    this.notifyStateChanged();
  }

  public boolean closeSession(final Path path) {
    final ImageCanvas canvas = this.canvases.get(path);
    if (canvas == null) {
      return false;
    }
    if (canvas.session().isDirty()) {
      final int choice = JOptionPane.showConfirmDialog(
          this,
          "Discard unsaved changes?",
          "Unsaved changes",
          JOptionPane.OK_CANCEL_OPTION);
      if (choice != JOptionPane.OK_OPTION) {
        return false;
      }
    }
    for (int i = 0; i < this.getTabCount(); i++) {
      final EditorSession session = this.sessionAt(i);
      if (session != null && session.filePath().equals(path)) {
        this.removeTabAt(i);
        break;
      }
    }
    this.canvases.remove(path);
    this.toolBars.remove(path);
    this.sessionClosedListener.accept(path);
    this.notifyStateChanged();
    return true;
  }

  private void notifyStateChanged() {
    this.refreshTitles();
    this.stateChangeListener.run();
  }

  private JPanel createTabComponent(final EditorSession session, final String title) {
    final JPanel panel = new JPanel(new BorderLayout(6, 0));
    panel.setOpaque(false);
    final JLabel label = new JLabel(title);
    label.setBorder(new EmptyBorder(0, 0, 0, 6));
    panel.add(label, BorderLayout.CENTER);

    final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    buttons.setOpaque(false);
    final JButton reveal = this.createTabButton("folder_explorer.png");
    reveal.setToolTipText("Reveal in file tree");
    reveal.addActionListener(event -> this.revealInTreeListener.accept(session.filePath()));
    buttons.add(reveal);
    final JButton close = this.createTabButton("cross.png");
    close.setToolTipText("Close");
    close.addActionListener(event -> this.closeSession(session.filePath()));
    buttons.add(close);
    panel.add(buttons, BorderLayout.EAST);
    return panel;
  }

  private JButton createTabButton(final String iconFileName) {
    final JButton button = new JButton(this.icons.icon16(iconFileName));
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setFocusable(false);
    button.setMargin(new Insets(0, 0, 0, 0));
    button.setBorder(new EmptyBorder(0, 1, 0, 1));
    return button;
  }

  private EditorSession sessionAt(final int index) {
    final ImageCanvas canvas = this.canvasAt(index);
    return canvas == null ? null : canvas.session();
  }

  private ImageCanvas canvasAt(final int index) {
    if (this.getComponentAt(index) instanceof JPanel panel) {
      for (final java.awt.Component child : panel.getComponents()) {
        if (child instanceof JScrollPane scroll
            && scroll.getViewport().getView() instanceof ImageCanvas canvas) {
          return canvas;
        }
      }
    }
    return null;
  }

  private String formatTitle(final EditorSession session) {
    return (session.isDirty() ? "* " : "") + session.filePath().getFileName();
  }
}
