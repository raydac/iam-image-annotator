package com.igormaznitsa.annotator.ui;

import com.igormaznitsa.annotator.api.json.AnnotationJsonCodec;
import com.igormaznitsa.annotator.api.png.AnnotatedPng;
import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.exporters.api.AnnotatedImagesExporter;
import com.igormaznitsa.annotator.exporters.api.ExportProgress;
import com.igormaznitsa.annotator.exporters.boundingyolo.BoundingYoloImageExporter;
import com.igormaznitsa.annotator.ui.api.TreeOperationContext;
import com.igormaznitsa.annotator.ui.component.ProgressGlassPane;
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
import com.igormaznitsa.annotator.ui.operations.RefreshTreeOperation;
import com.igormaznitsa.annotator.ui.tools.AddVertexAction;
import com.igormaznitsa.annotator.ui.tools.ClassNameToggle;
import com.igormaznitsa.annotator.ui.tools.GridToggle;
import com.igormaznitsa.annotator.ui.tools.LockSelectedToggle;
import com.igormaznitsa.annotator.ui.tools.MagicWandTool;
import com.igormaznitsa.annotator.ui.tools.MaskColorTool;
import com.igormaznitsa.annotator.ui.tools.ObboxTool;
import com.igormaznitsa.annotator.ui.tools.PolygonTool;
import com.igormaznitsa.annotator.ui.tools.RectangleTool;
import com.igormaznitsa.annotator.ui.tools.RemoveVertexAction;
import com.igormaznitsa.annotator.ui.tools.SelectTool;
import com.igormaznitsa.annotator.ui.tools.ZoomInAction;
import com.igormaznitsa.annotator.ui.tools.ZoomOutAction;
import com.igormaznitsa.annotator.ui.tree.FileTreePanel;
import com.igormaznitsa.annotator.ui.tree.TreeOperationBar;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class MainFrame extends JFrame implements TreeOperationContext {

  private static final String APP_TITLE = "IAM Image Annotator";
  private static final String VERSION = "1.0-SNAPSHOT";

  private final IconService icons = new IconService();
  private final EditorWorkspace workspace = new EditorWorkspace();
  private final int menuShortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
  private final FileTreePanel fileTree = new FileTreePanel(this.icons);
  private final TreeOperationBar treeOperations = new TreeOperationBar(this.icons, List.of(
      new DeleteTreeOperation(),
      new EditTreeOperation(),
      new RefreshTreeOperation(),
      new ExportTreeOperation()));
  private final List<AnnotatedImagesExporter> annotatedImageExporters =
      List.of(new BoundingYoloImageExporter(0));
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewAction> viewActions = List.of(
      new ZoomInAction(),
      new ZoomOutAction());
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewToggle> shapeToggles =
      List.of(new LockSelectedToggle());
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewAction> shapeActions = List.of(
      new AddVertexAction(),
      new RemoveVertexAction());
  private final List<com.igormaznitsa.annotator.ui.api.ImageViewToggle> viewToggles =
      List.of(new GridToggle(), new ClassNameToggle());
  private final EditorTabbedPane editorTabs;
  private final ProgressGlassPane progressGlassPane = new ProgressGlassPane();
  private final JLabel statusLabel = new JLabel("Ready");
  private int progressTaskCount;

  private final JMenuItem saveItem = new JMenuItem("Save");
  private final JMenuItem saveAsItem = new JMenuItem("Save as...");
  private final JMenuItem saveAllItem = new JMenuItem("Save All");
  private final JMenuItem exportAsItem = new JMenuItem("Export as...");
  private final JMenuItem exitItem = new JMenuItem("Exit");
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
                new MaskColorTool(),
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
    this.setGlassPane(this.progressGlassPane);
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
    this.updateMenuItems();
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
  public Component dialogParent() {
    return this;
  }

  @Override
  public void runWithProgress(final String title, final Runnable task,
                              final Consumer<Exception> onError) {
    this.runWithProgress(title, () -> {
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
    openFolder.setAccelerator(this.menuShortcut(KeyEvent.VK_O));
    this.saveItem.setAccelerator(this.menuShortcut(KeyEvent.VK_S));
    this.saveAsItem.setAccelerator(this.menuShortcut(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK));
    this.saveAllItem.setAccelerator(this.menuShortcut(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK));
    this.exitItem.setAccelerator(this.exitShortcut());
    openFolder.addActionListener(event -> this.chooseFolder());
    openFile.addActionListener(event -> this.chooseFile());
    this.saveItem.addActionListener(event -> this.saveActive());
    this.saveAsItem.addActionListener(event -> this.saveActiveAs());
    this.saveAllItem.addActionListener(event -> this.saveAll());
    this.exportAsItem.addActionListener(event -> this.exportAs());
    this.exitItem.addActionListener(event -> this.requestExit());
    file.add(openFolder);
    file.add(openFile);
    file.add(this.exportAsItem);
    file.addSeparator();
    file.add(this.saveItem);
    file.add(this.saveAsItem);
    file.add(this.saveAllItem);
    file.addSeparator();
    file.add(this.exitItem);
    return file;
  }

  private JMenu buildEditMenu() {
    final JMenu edit = new JMenu("Edit");
    this.undoItem.setAccelerator(this.menuShortcut(KeyEvent.VK_Z));
    this.redoItem.setAccelerator(this.menuShortcut(KeyEvent.VK_Y));
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
    this.fileTree.setFileOpenListener(this::openPath);
    this.fileTree.addSelectionListener(event -> {
      this.treeOperations.refreshState(this);
      this.updateMenuItems();
    });
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
    root.getInputMap(when).put(this.menuShortcut(KeyEvent.VK_Z), "undo");
    root.getInputMap(when).put(this.menuShortcut(KeyEvent.VK_Y), "redo");
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

  private KeyStroke menuShortcut(final int keyCode, final int... modifiers) {
    int mask = this.menuShortcutMask;
    for (final int modifier : modifiers) {
      mask |= modifier;
    }
    return KeyStroke.getKeyStroke(keyCode, mask);
  }

  private KeyStroke exitShortcut() {
    return this.isMacOs()
        ? this.menuShortcut(KeyEvent.VK_Q)
        : KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK);
  }

  private boolean isMacOs() {
    return System.getProperty("os.name", "")
        .toLowerCase(Locale.ROOT)
        .contains("mac");
  }

  private void chooseFolder() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("Open folder");
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        this.fileTree.openFolder(this.selectedFolder(chooser));
        this.treeOperations.refreshState(this);
        this.updateMenuItems();
      } catch (final IOException exception) {
        this.showError(exception.getMessage());
      }
    }
  }

  private Path selectedFolder(final JFileChooser chooser) {
    final Path selected = chooser.getSelectedFile().toPath();
    final Path current = chooser.getCurrentDirectory().toPath();
    if (Files.isDirectory(current)
        && !Files.isDirectory(selected)
        && current.getFileName() != null
        && current.getFileName().equals(selected.getFileName())) {
      return current;
    }
    return selected;
  }

  private void chooseFile() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      this.openPath(chooser.getSelectedFile().toPath());
    }
  }

  private void exportAs() {
    final List<Path> imageFiles = this.selectedExportableImages();
    if (imageFiles.isEmpty()) {
      this.showError("Select one or more images or folders to export.");
      return;
    }

    final JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Export annotated images as...");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    this.annotatedImageExporters.forEach(exporter -> chooser.addChoosableFileFilter(
        exporter.fileFilter()));
    chooser.setFileFilter(this.annotatedImageExporters.getFirst().fileFilter());
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }

    final AnnotatedImagesExporter selected = this.selectedExporter(chooser.getFileFilter());
    this.configuredExporter(selected).ifPresent(exporter -> this.exportImagesWithProgress(
        exporter,
        imageFiles,
        this.selectedFolder(chooser)));
  }

  private boolean canExportAs() {
    if (!this.fileTree.hasOpenFolder()) {
      return false;
    }
    return this.fileTree.selectedPaths().stream().anyMatch(this::isExportSelection);
  }

  private boolean isExportSelection(final Path path) {
    return Files.isDirectory(path)
        || (Files.isRegularFile(path) && AllowedImageFiles.isAllowed(path));
  }

  private List<Path> selectedExportableImages() {
    final Set<Path> result = new LinkedHashSet<>();
    for (final Path path : this.fileTree.selectedPaths()) {
      if (Files.isRegularFile(path) && AllowedImageFiles.isAllowed(path)) {
        result.add(path);
      } else if (Files.isDirectory(path)) {
        this.collectExportableImages(path, result);
      }
    }
    return result.stream().sorted(Comparator.comparing(Path::toString)).toList();
  }

  private void collectExportableImages(final Path folder, final Set<Path> result) {
    try (Stream<Path> walk = Files.walk(folder)) {
      walk.filter(Files::isRegularFile)
          .filter(AllowedImageFiles::isAllowed)
          .forEach(result::add);
    } catch (final IOException ignored) {
      // skip unreadable folder branch
    }
  }

  private AnnotatedImagesExporter selectedExporter(final FileFilter selectedFilter) {
    return this.annotatedImageExporters.stream()
        .filter(exporter -> exporter.fileFilter().equals(selectedFilter))
        .findFirst()
        .orElse(this.annotatedImageExporters.getFirst());
  }

  private Optional<AnnotatedImagesExporter> configuredExporter(
      final AnnotatedImagesExporter exporter) {
    if (exporter instanceof BoundingYoloImageExporter) {
      return this.promptBoundingYoloExporter();
    }
    return Optional.of(exporter);
  }

  private Optional<AnnotatedImagesExporter> promptBoundingYoloExporter() {
    while (true) {
      final String value = JOptionPane.showInputDialog(
          this,
          "First YOLO class id:",
          "0");
      if (value == null) {
        return Optional.empty();
      }
      try {
        return Optional.of(new BoundingYoloImageExporter(
            Integer.parseInt(value.strip()),
            this::confirmDetectedClasses));
      } catch (final IllegalArgumentException exception) {
        this.showError("Class id must be a non-negative integer.");
      }
    }
  }

  private boolean confirmDetectedClasses(final Map<String, Integer> classIds) {
    if (SwingUtilities.isEventDispatchThread()) {
      return this.showDetectedClassesDialog(classIds);
    }
    final boolean[] confirmed = {false};
    try {
      SwingUtilities.invokeAndWait(() -> confirmed[0] = this.showDetectedClassesDialog(classIds));
      return confirmed[0];
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    } catch (final InvocationTargetException exception) {
      throw new IllegalStateException("Unable to confirm detected classes", exception.getCause());
    }
  }

  private boolean showDetectedClassesDialog(final Map<String, Integer> classIds) {
    final JTextArea classes = new JTextArea(this.formatDetectedClasses(classIds), 12, 36);
    classes.setEditable(false);
    classes.setCaretPosition(0);
    final int choice = JOptionPane.showConfirmDialog(
        this,
        new JScrollPane(classes),
        "Confirm detected classes",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    return choice == JOptionPane.OK_OPTION;
  }

  private String formatDetectedClasses(final Map<String, Integer> classIds) {
    final StringBuilder builder = new StringBuilder();
    classIds.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .forEach(entry -> {
          if (!builder.isEmpty()) {
            builder.append('\n');
          }
          builder.append(entry.getValue()).append(": ").append(entry.getKey());
        });
    return builder.toString();
  }

  private void exportImagesWithProgress(
      final AnnotatedImagesExporter exporter,
      final List<Path> imageFiles,
      final Path destinationFolder) {
    this.showProgressOverlay("Exporting annotated images...");
    final SwingWorker<Integer, ExportProgress> worker = new SwingWorker<>() {
      @Override
      protected Integer doInBackground() throws Exception {
        exporter.exportImages(imageFiles, destinationFolder, this::publish);
        return imageFiles.size();
      }

      @Override
      protected void process(final List<ExportProgress> chunks) {
        final ExportProgress progress = chunks.getLast();
        MainFrame.this.updateExportProgress(progress);
      }

      @Override
      protected void done() {
        MainFrame.this.completeExportTask(this, exporter, destinationFolder);
      }
    };
    worker.execute();
  }

  private void updateExportProgress(final ExportProgress progress) {
    final String message = progress.stage() + " (" + progress.percent() + "%)";
    this.progressGlassPane.setMessage(message);
    this.statusLabel.setText(message);
  }

  private void completeExportTask(
      final SwingWorker<Integer, ExportProgress> worker,
      final AnnotatedImagesExporter exporter,
      final Path destinationFolder) {
    try {
      worker.get();
      this.hideProgressOverlay();
      this.statusLabel.setText("Exported " + exporter.title());
      this.showInfo("Exported " + exporter.title() + " to " + destinationFolder);
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      this.hideProgressOverlay();
      this.showError("Export interrupted: " + exception.getMessage());
    } catch (final ExecutionException exception) {
      this.hideProgressOverlay();
      if (exception.getCause() instanceof CancellationException) {
        this.statusLabel.setText("Export cancelled");
        return;
      }
      this.showError("Export failed: " + this.toException(exception.getCause()).getMessage());
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
        "Opening " + path.getFileName() + "...",
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
    if (AllowedImageFiles.isPng(path) && AnnotatedPng.hasAnnotationChunks(path)) {
      return EditorSession.open(path);
    }
    final BufferedImage image = ImageIO.read(path.toFile());
    if (image == null) {
      throw new IOException("Unable to decode image: " + path);
    }
    return EditorSession.fromImage(path, image);
  }

  private <T> void runWithProgress(final String message,
                                   final Callable<T> task,
                                   final Consumer<T> onSuccess,
                                   final Consumer<Exception> onError) {
    this.showProgressOverlay(message);
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
      this.hideProgressOverlay();
      onSuccess.accept(result);
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      this.hideProgressOverlay();
      onError.accept(exception);
    } catch (final ExecutionException exception) {
      this.hideProgressOverlay();
      onError.accept(this.toException(exception.getCause()));
    }
  }

  private Exception toException(final Throwable cause) {
    return cause instanceof Exception exception ? exception : new Exception(cause);
  }

  private void showProgressOverlay(final String message) {
    this.progressTaskCount++;
    this.progressGlassPane.start(message);
  }

  private void hideProgressOverlay() {
    this.progressTaskCount = Math.max(0, this.progressTaskCount - 1);
    if (this.progressTaskCount == 0) {
      this.progressGlassPane.stop();
    }
  }

  private SaveResult saveSessionToTarget(final EditorSession session) throws IOException {
    final Path source = session.filePath();
    final Path target = AllowedImageFiles.toAnnotatedPngPath(source);
    session.save(target);
    return new SaveResult(source, target, session);
  }

  private void saveActive() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    if (canvas == null) {
      return;
    }
    final EditorSession session = canvas.session();
    final Path target = AllowedImageFiles.toAnnotatedPngPath(session.filePath());
    this.statusLabel.setText("Saving " + target.getFileName() + "...");
    this.runWithProgress(
        "Saving " + target.getFileName() + "...",
        () -> this.saveSessionToTarget(session),
        saved -> this.afterSave(saved, "Saved " + saved.target().getFileName()),
        exception -> this.showError("Save failed: " + exception.getMessage()));
  }

  private void saveActiveAs() {
    final ImageCanvas canvas = this.editorTabs.activeCanvas();
    if (canvas == null) {
      return;
    }
    final JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(
        AllowedImageFiles.toAnnotatedPngPath(canvas.session().filePath()).toFile());
    chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    final EditorSession session = canvas.session();
    final Path source = session.filePath();
    final Path target = AllowedImageFiles.toAnnotatedPngPath(chooser.getSelectedFile().toPath());
    this.statusLabel.setText("Saving " + target.getFileName() + "...");
    this.runWithProgress(
        "Saving " + target.getFileName() + "...",
        () -> {
          session.save(target);
          return new SaveResult(source, target, session);
        },
        saved -> this.afterSave(saved, "Saved " + saved.target().getFileName()),
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
        "Saving " + dirtySessions.size() + " file(s)...",
        () -> {
          final List<SaveResult> results = new ArrayList<>();
          for (final EditorSession session : dirtySessions) {
            results.add(this.saveSessionToTarget(session));
          }
          return List.copyOf(results);
        },
        results -> {
          this.afterSave(results, "Saved " + results.size() + " file(s)");
          onSuccess.run();
        },
        exception -> this.showError("Save failed: " + exception.getMessage()));
  }

  private void afterSave(final List<SaveResult> results, final String statusText) {
    results.forEach(this::rekeySavedSession);
    this.afterSave(statusText);
  }

  private void afterSave(final SaveResult result, final String statusText) {
    this.rekeySavedSession(result);
    this.afterSave(statusText);
  }

  private void afterSave(final String statusText) {
    this.fileTree.refresh();
    this.statusLabel.setText(statusText);
    this.onEditorStateChanged();
  }

  private void rekeySavedSession(final SaveResult result) {
    if (result.source().equals(result.target())) {
      return;
    }
    this.workspace.close(result.source());
    this.editorTabs.rekeySession(result.source(), result.target());
    this.workspace.open(result.session());
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
    this.exportAsItem.setEnabled(this.canExportAs());
    this.undoItem.setEnabled(hasActiveEditor && canvas.session().canUndo());
    this.redoItem.setEnabled(hasActiveEditor && canvas.session().canRedo());
    this.showJsonItem.setEnabled(hasActiveEditor);
  }

  private record SaveResult(Path source, Path target, EditorSession session) {
  }
}
