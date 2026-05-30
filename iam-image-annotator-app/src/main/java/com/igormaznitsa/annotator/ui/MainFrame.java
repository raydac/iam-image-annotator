package com.igormaznitsa.annotator.ui;

import com.igormaznitsa.annotator.api.json.AnnotationJsonCodec;
import com.igormaznitsa.annotator.api.png.AnnotatedPng;
import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.ui.api.TreeOperationContext;
import com.igormaznitsa.annotator.ui.dialog.SettingsDialog;
import com.igormaznitsa.annotator.ui.dialog.ShowJsonDialog;
import com.igormaznitsa.annotator.ui.editor.EditorTabbedPane;
import com.igormaznitsa.annotator.ui.editor.EditorWorkspace;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;
import com.igormaznitsa.annotator.ui.editor.ImageToolBar;
import com.igormaznitsa.annotator.ui.icons.IconService;
import com.igormaznitsa.annotator.ui.operations.DeleteTreeOperation;
import com.igormaznitsa.annotator.ui.operations.EditTreeOperation;
import com.igormaznitsa.annotator.ui.operations.ExportTreeOperation;
import com.igormaznitsa.annotator.ui.tools.AddVertexAction;
import com.igormaznitsa.annotator.ui.tools.GridToggle;
import com.igormaznitsa.annotator.ui.tools.LockSelectedToggle;
import com.igormaznitsa.annotator.ui.tools.MagicWandTool;
import com.igormaznitsa.annotator.ui.tools.ObboxTool;
import com.igormaznitsa.annotator.ui.tools.PolygonTool;
import com.igormaznitsa.annotator.ui.tools.RectangleTool;
import com.igormaznitsa.annotator.ui.tools.RemoveVertexAction;
import com.igormaznitsa.annotator.ui.tools.SelectTool;
import com.igormaznitsa.annotator.ui.tools.ZoomInAction;
import com.igormaznitsa.annotator.ui.tools.ZoomOutAction;
import com.igormaznitsa.annotator.ui.tree.FileTreePanel;
import com.igormaznitsa.annotator.ui.tree.TreeOperationBar;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class MainFrame extends JFrame implements TreeOperationContext {

  private static final String APP_TITLE = "IAM Image Annotator";
  private static final String VERSION = "1.0-SNAPSHOT";

  private final IconService icons = new IconService();
  private final EditorWorkspace workspace = new EditorWorkspace();
  private final FileTreePanel fileTree = new FileTreePanel(this.icons);
  private final TreeOperationBar treeOperations = new TreeOperationBar(this.icons, List.of(
      new DeleteTreeOperation(),
      new EditTreeOperation(),
      new ExportTreeOperation()));
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewAction> viewActions = List.of(
      new ZoomInAction(),
      new ZoomOutAction());
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewToggle> shapeToggles =
      List.of(new LockSelectedToggle());
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewAction> shapeActions = List.of(
      new AddVertexAction(),
      new RemoveVertexAction());
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewToggle> viewToggles =
      List.of(new GridToggle());
  private final EditorTabbedPane editorTabs;
  private final BusyGlassPane busyGlassPane = new BusyGlassPane();
  private final JLabel statusLabel = new JLabel("Ready");
  private int progressTaskCount;

  private final JMenuItem saveItem = new JMenuItem("Save");
  private final JMenuItem saveAsItem = new JMenuItem("Save as...");
  private final JMenuItem saveAllItem = new JMenuItem("Save All");
  private final JMenuItem undoItem = new JMenuItem("Undo");
  private final JMenuItem redoItem = new JMenuItem("Redo");
  private final JMenuItem showJsonItem = new JMenuItem("Show JSON");

  public MainFrame() {
    super(APP_TITLE);
    this.editorTabs = new EditorTabbedPane(
        this.icons,
        (canvas, frame) -> new ImageToolBar(
            this.icons,
            List.of(
                new SelectTool(),
                new RectangleTool(),
                new PolygonTool(),
                new ObboxTool(),
                new MagicWandTool()),
            this.shapeToggles,
            this.shapeActions,
            this.viewActions,
            this.viewToggles,
            canvas,
            frame),
        this);
    this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    this.setMinimumSize(new Dimension(1100, 700));
    this.setJMenuBar(this.buildMenuBar());
    this.setContentPane(this.buildContent());
    this.setGlassPane(this.busyGlassPane);
    this.wireEvents();
    this.treeOperations.bind(this);
    this.updateMenuItems();
  }

  @Override
  public boolean hasOpenFolder() {
    return this.fileTree.hasOpenFolder();
  }

  @Override
  public List<Path> selectedPaths() {
    return this.fileTree.selectedPaths();
  }

  @Override
  public void refreshTree() {
    this.fileTree.refresh();
    this.treeOperations.refreshState(this);
  }

  @Override
  public EditorWorkspace editorWorkspace() {
    return this.workspace;
  }

  @Override
  public void closeEditorTabs(final Path path) {
    this.editorTabs.closeSession(path);
  }

  @Override
  public void showError(final String message) {
    JOptionPane.showMessageDialog(this, message, APP_TITLE, JOptionPane.ERROR_MESSAGE);
  }

  @Override
  public void showInfo(final String message) {
    JOptionPane.showMessageDialog(this, message, APP_TITLE, JOptionPane.INFORMATION_MESSAGE);
  }

  @Override
  public boolean confirm(final String title, final String message) {
    return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION)
        == JOptionPane.YES_OPTION;
  }

  @Override
  public void runWithProgress(final String title, final Runnable task,
                              final Consumer<Exception> onError) {
    this.runWithProgress(() -> {
      task.run();
      return null;
    }, ignored -> {
    }, onError);
  }

  private JMenuBar buildMenuBar() {
    final JMenuBar bar = new JMenuBar();
    bar.add(this.buildFileMenu());
    bar.add(this.buildEditMenu());
    bar.add(this.buildHelpMenu());
    return bar;
  }

  private JMenu buildFileMenu() {
    final JMenu file = new JMenu("File");
    final JMenuItem openFolder = new JMenuItem("Open Folder");
    final JMenuItem openFile = new JMenuItem("Open File");
    openFolder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    openFolder.addActionListener(event -> this.chooseFolder());
    openFile.addActionListener(event -> this.chooseFile());
    this.saveItem.addActionListener(event -> this.saveActive());
    this.saveAsItem.addActionListener(event -> this.saveActiveAs());
    this.saveAllItem.addActionListener(event -> this.saveAll());
    file.add(openFolder);
    file.add(openFile);
    file.addSeparator();
    file.add(this.saveItem);
    file.add(this.saveAsItem);
    file.add(this.saveAllItem);
    return file;
  }

  private JMenu buildEditMenu() {
    final JMenu edit = new JMenu("Edit");
    this.undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
    this.redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
    this.undoItem.addActionListener(event -> this.undoActive());
    this.redoItem.addActionListener(event -> this.redoActive());
    final JMenuItem settings = new JMenuItem("Settings");
    settings.addActionListener(event -> SettingsDialog.show(this));
    this.showJsonItem.addActionListener(event -> this.showActiveJson());
    edit.add(this.undoItem);
    edit.add(this.redoItem);
    edit.addSeparator();
    edit.add(this.showJsonItem);
    edit.add(settings);
    return edit;
  }

  private JMenu buildHelpMenu() {
    final JMenu help = new JMenu("Help");
    final JMenuItem about = new JMenuItem("About");
    about.addActionListener(event -> JOptionPane.showMessageDialog(
        this,
        APP_TITLE + "\nVersion " + VERSION + "\nAuthor: Igor Maznitsa",
        "About",
        JOptionPane.INFORMATION_MESSAGE));
    help.add(about);
    return help;
  }

  private JPanel buildContent() {
    final JPanel left = new JPanel(new BorderLayout(4, 4));
    left.add(this.treeOperations, BorderLayout.NORTH);
    left.add(this.fileTree, BorderLayout.CENTER);
    left.setPreferredSize(new Dimension(280, 600));

    final JPanel status = new JPanel(new BorderLayout());
    status.add(this.statusLabel, BorderLayout.WEST);

    final JPanel right = new JPanel(new BorderLayout());
    right.add(this.editorTabs, BorderLayout.CENTER);
    right.add(status, BorderLayout.SOUTH);

    final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
    split.setResizeWeight(0.22);
    final JPanel root = new JPanel(new BorderLayout());
    root.add(split, BorderLayout.CENTER);
    return root;
  }

  private void wireEvents() {
    this.registerExitConfirmation();
    this.registerGlobalUndoRedo();
    this.workspace.setChangeListener(ignored -> this.onEditorStateChanged());
    this.editorTabs.setStateChangeListener(this::updateMenuItems);
    this.editorTabs.setRevealInTreeListener(path -> this.fileTree.reveal(path));
    this.editorTabs.setSessionClosedListener(this.workspace::close);
    this.fileTree.addSelectionListener(event -> this.treeOperations.refreshState(this));
    this.fileTree.addSelectionListener(event -> this.openSelectedImages());
  }

  private void registerExitConfirmation() {
    this.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(final WindowEvent event) {
        MainFrame.this.requestExit();
      }
    });
  }

  private void requestExit() {
    if (!this.workspace.hasDirtySessions()) {
      this.exitApplication();
      return;
    }
    final String files = this.formatDirtySessionList();
    final Object[] options = {"Save All", "Don't Save", "Cancel"};
    final int choice = JOptionPane.showOptionDialog(
        this,
        "You have unsaved changes in:\n\n" + files + "\n\nSave before closing?",
        "Unsaved changes",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.WARNING_MESSAGE,
        null,
        options,
        options[0]);
    if (choice == JOptionPane.CLOSED_OPTION || choice == 2) {
      return;
    }
    if (choice == 0) {
      this.saveDirtySessionsWithProgress(this::exitApplication);
      return;
    }
    this.exitApplication();
  }

  private void exitApplication() {
    this.dispose();
    System.exit(0);
  }

  private String formatDirtySessionList() {
    final StringBuilder builder = new StringBuilder();
    for (final EditorSession session : this.workspace.dirtySessions()) {
      if (!builder.isEmpty()) {
        builder.append('\n');
      }
      builder.append("• ").append(session.filePath().getFileName());
    }
    return builder.toString();
  }

  private void registerGlobalUndoRedo() {
    final JRootPane root = this.getRootPane();
    final int when = JRootPane.WHEN_IN_FOCUSED_WINDOW;
    root.getInputMap(when)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
    root.getInputMap(when)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
    root.getActionMap().put("undo", new AbstractAction() {
      @Override
      public void actionPerformed(final java.awt.event.ActionEvent event) {
        MainFrame.this.undoActive();
      }
    });
    root.getActionMap().put("redo", new AbstractAction() {
      @Override
      public void actionPerformed(final java.awt.event.ActionEvent event) {
        MainFrame.this.redoActive();
      }
    });
  }

  private void chooseFolder() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("Open folder");
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        this.fileTree.openFolder(chooser.getSelectedFile().toPath());
      } catch (final IOException exception) {
        this.showError(exception.getMessage());
      }
    }
  }

  private void chooseFile() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      this.openPath(chooser.getSelectedFile().toPath());
    }
  }

  private void openSelectedImages() {
    if (!this.fileTree.hasOpenFolder()) {
      return;
    }
    for (final Path path : this.selectedPaths()) {
      if (Files.isRegularFile(path) && AllowedImageFiles.isAllowed(path)) {
        this.openPath(path);
      }
    }
  }

  private void openPath(final Path path) {
    try {
      if (this.editorTabs.hasOpenTab(path)) {
        this.showSession(this.openSessionForExistingTab(path));
        return;
      }

      this.openNewSessionWithProgress(path);
    } catch (final Exception exception) {
      this.showError("Open failed: " + exception.getMessage());
    }
  }

  private EditorSession openSessionForExistingTab(final Path path) {
    return this.workspace.allSessions().stream()
        .filter(open -> open.filePath().equals(path))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Open tab without workspace session: " + path));
  }

  private void openNewSessionWithProgress(final Path path) {
    this.workspace.close(path);
    this.runWithProgress(
        () -> this.loadSession(path),
        this::showSession,
        exception -> this.showError("Open failed: " + exception.getMessage()));
  }

  private void showSession(final EditorSession session) {
    this.workspace.open(session);
    this.editorTabs.openSession(session, this.statusLabel::setText);
    this.editorTabs.selectSession(session.filePath());
  }

  private EditorSession loadSession(final Path path) throws IOException {
    if (!AllowedImageFiles.isAllowed(path)) {
      throw new IOException("Unsupported file: " + path);
    }
    if (AnnotatedPng.hasAnnotationChunks(path)) {
      return EditorSession.open(path);
    }
    try {
      return EditorSession.open(path);
    } catch (final IOException exception) {
      final BufferedImage image = ImageIO.read(path.toFile());
      if (image != null) {
        return EditorSession.fromImage(path, image);
      }
      throw exception;
    }
  }

  private <T> void runWithProgress(final Callable<T> task,
                                   final Consumer<T> onSuccess,
                                   final Consumer<Exception> onError) {
    this.showBusyOverlay();
    final SwingWorker<T, Void> worker = new SwingWorker<>() {
      @Override
      protected T doInBackground() throws Exception {
        return task.call();
      }

      @Override
      protected void done() {
        MainFrame.this.completeProgressTask(this, onSuccess, onError);
      }
    };
    worker.execute();
  }

  private <T> void completeProgressTask(final SwingWorker<T, Void> worker,
                                        final Consumer<T> onSuccess,
                                        final Consumer<Exception> onError) {
    try {
      final T result = worker.get();
      this.hideBusyOverlay();
      onSuccess.accept(result);
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      this.hideBusyOverlay();
      onError.accept(exception);
    } catch (final ExecutionException exception) {
      this.hideBusyOverlay();
      onError.accept(this.toException(exception.getCause()));
    }
  }

  private Exception toException(final Throwable cause) {
    return cause instanceof Exception exception ? exception : new Exception(cause);
  }

  private void showBusyOverlay() {
    this.progressTaskCount++;
    this.busyGlassPane.start();
  }

  private void hideBusyOverlay() {
    this.progressTaskCount = Math.max(0, this.progressTaskCount - 1);
    if (this.progressTaskCount == 0) {
      this.busyGlassPane.stop();
    }
  }

  private void saveSessionToTarget(final EditorSession session) throws IOException {
    session.save(session.filePath());
  }

  private void saveActive() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    if (canvas == null) {
      return;
    }
    final EditorSession session = canvas.session();
    this.statusLabel.setText("Saving " + session.filePath().getFileName() + "...");
    this.runWithProgress(
        () -> {
          this.saveSessionToTarget(session);
          return session;
        },
        saved -> this.afterSave("Saved " + saved.filePath().getFileName()),
        exception -> this.showError("Save failed: " + exception.getMessage()));
  }

  private void saveActiveAs() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    if (canvas == null) {
      return;
    }
    final JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(canvas.session().filePath().toFile());
    chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    final EditorSession session = canvas.session();
    final Path source = session.filePath();
    final Path target = chooser.getSelectedFile().toPath();
    this.statusLabel.setText("Saving " + target.getFileName() + "...");
    this.runWithProgress(
        () -> {
          session.save(target);
          return new SaveAsResult(source, target, session);
        },
        this::afterSaveAs,
        exception -> this.showError("Save failed: " + exception.getMessage()));
  }

  private void saveAll() {
    this.saveDirtySessionsWithProgress(() -> {
    });
  }

  private void saveDirtySessionsWithProgress(final Runnable onSuccess) {
    final List<EditorSession> dirtySessions = this.workspace.dirtySessions();
    if (dirtySessions.isEmpty()) {
      onSuccess.run();
      return;
    }
    this.statusLabel.setText("Saving " + dirtySessions.size() + " file(s)...");
    this.runWithProgress(
        () -> {
          for (final EditorSession session : dirtySessions) {
            this.saveSessionToTarget(session);
          }
          return dirtySessions.size();
        },
        count -> {
          this.afterSave("Saved " + count + " file(s)");
          onSuccess.run();
        },
        exception -> this.showError("Save failed: " + exception.getMessage()));
  }

  private void afterSave(final String statusText) {
    this.fileTree.refresh();
    this.statusLabel.setText(statusText);
    this.onEditorStateChanged();
  }

  private void afterSaveAs(final SaveAsResult result) {
    this.workspace.close(result.source());
    result.session().rekey(result.target());
    this.editorTabs.rekeySession(result.source(), result.target());
    this.workspace.open(result.session());
    this.afterSave("Saved " + result.target().getFileName());
  }

  private void onEditorStateChanged() {
    this.editorTabs.refreshTitles();
    this.updateMenuItems();
  }

  private void undoActive() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    if (canvas == null) {
      return;
    }
    if (canvas.session().undo()) {
      canvas.repaintCanvas();
      canvas.refreshToolbarState();
      this.statusLabel.setText("Undo");
      this.onEditorStateChanged();
      return;
    }
    this.statusLabel.setText("Nothing to undo");
  }

  private void redoActive() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    if (canvas == null) {
      return;
    }
    if (canvas.session().redo()) {
      canvas.repaintCanvas();
      canvas.refreshToolbarState();
      this.statusLabel.setText("Redo");
      this.onEditorStateChanged();
      return;
    }
    this.statusLabel.setText("Nothing to redo");
  }

  private void showActiveJson() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    if (canvas == null) {
      return;
    }
    final String json = new AnnotationJsonCodec().encodePretty(canvas.session().document());
    final String title = "Annotations JSON — " + canvas.session().filePath().getFileName();
    ShowJsonDialog.show(this, title, json);
  }

  private void updateMenuItems() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    final boolean hasActiveEditor = canvas != null;
    final boolean dirtyActive = hasActiveEditor && canvas.session().isDirty();
    this.saveItem.setEnabled(dirtyActive);
    this.saveAsItem.setEnabled(hasActiveEditor);
    this.saveAllItem.setEnabled(this.workspace.hasDirtySessions());
    this.undoItem.setEnabled(hasActiveEditor && canvas.session().canUndo());
    this.redoItem.setEnabled(hasActiveEditor && canvas.session().canRedo());
    this.showJsonItem.setEnabled(hasActiveEditor);
  }

  private static final class BusyGlassPane extends JPanel {

    private static final int FRAME_DELAY_MS = 40;
    private static final int OVERLAY_ALPHA = 128;
    private static final int SPINNER_SIZE = 64;
    private static final int SPINNER_STROKE = 7;
    private static final int ARC_COUNT = 12;
    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, OVERLAY_ALPHA);
    private static final Color SPINNER_COLOR = new Color(36, 196, 112);
    private int frame;
    private final Timer timer = new Timer(FRAME_DELAY_MS, event -> this.rotate());

    private BusyGlassPane() {
      this.setOpaque(false);
      this.setVisible(false);
      this.setFocusable(true);
      this.addMouseListener(new MouseAdapter() {
      });
      this.addMouseMotionListener(new MouseAdapter() {
      });
      this.addMouseWheelListener(event -> {
      });
      this.addKeyListener(new KeyAdapter() {
      });
    }

    private void start() {
      this.frame = 0;
      this.setVisible(true);
      this.requestFocusInWindow();
      this.timer.start();
      this.repaint();
    }

    private void stop() {
      this.timer.stop();
      this.setVisible(false);
    }

    private void rotate() {
      this.frame = (this.frame + 1) % ARC_COUNT;
      this.repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
      super.paintComponent(graphics);
      final Graphics2D canvas = (Graphics2D) graphics.create();
      try {
        this.paintOverlay(canvas);
        this.paintSpinner(canvas);
      } finally {
        canvas.dispose();
      }
    }

    private void paintOverlay(final Graphics2D canvas) {
      canvas.setColor(OVERLAY_COLOR);
      canvas.fillRect(0, 0, this.getWidth(), this.getHeight());
    }

    private void paintSpinner(final Graphics2D canvas) {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      canvas.setStroke(new BasicStroke(SPINNER_STROKE, BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));

      final int left = (this.getWidth() - SPINNER_SIZE) / 2;
      final int top = (this.getHeight() - SPINNER_SIZE) / 2;
      for (int i = 0; i < ARC_COUNT; i++) {
        final float alpha = (i + 1.0f) / ARC_COUNT;
        canvas.setComposite(AlphaComposite.SrcOver.derive(alpha));
        canvas.setColor(SPINNER_COLOR);
        canvas.drawArc(left, top, SPINNER_SIZE, SPINNER_SIZE,
            90 - ((this.frame + i) % ARC_COUNT) * 360 / ARC_COUNT,
            18);
      }
    }
  }

  private record SaveAsResult(Path source, Path target, EditorSession session) {
  }
}
