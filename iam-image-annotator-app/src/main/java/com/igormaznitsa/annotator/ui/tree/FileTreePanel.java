package com.igormaznitsa.annotator.ui.tree;

import static java.util.Objects.nonNull;

import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.ui.icons.IconService;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public final class FileTreePanel extends JScrollPane {

  static final String NO_FOLDER_MESSAGE = "No folder selected";

  private final IconService icons;
  private final JTree tree = new JTree();
  private Path rootFolder;
  private Consumer<Path> fileOpenListener = ignored -> {
  };

  public FileTreePanel(final IconService icons) {
    super();
    this.icons = icons;
    this.tree.setRootVisible(true);
    this.tree.setShowsRootHandles(true);
    this.tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    this.tree.setCellRenderer(new FileTreeCellRenderer(icons));
    this.tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent event) {
        FileTreePanel.this.openFileAt(event);
      }
    });
    this.registerActivationActions();
    this.showNoFolderPlaceholder();
  }

  public void setFileOpenListener(final Consumer<Path> listener) {
    this.fileOpenListener = listener;
  }

  public boolean hasOpenFolder() {
    return this.rootFolder != null;
  }

  public void addSelectionListener(final TreeSelectionListener listener) {
    this.tree.addTreeSelectionListener(listener);
  }

  public List<Path> selectedPaths() {
    if (this.rootFolder == null) {
      return List.of();
    }
    final TreePath[] paths = this.tree.getSelectionPaths();
    if (paths == null) {
      return List.of();
    }
    final List<Path> result = new ArrayList<>();
    for (final TreePath path : paths) {
      final Object last = path.getLastPathComponent();
      if (last instanceof DefaultMutableTreeNode mutable
          && mutable.getUserObject() instanceof FileTreeNode node) {
        result.add(node.path());
      }
    }
    return result;
  }

  public void openFolder(final Path folder) throws IOException {
    if (!Files.isDirectory(folder)) {
      throw new IOException("Not a directory: " + folder);
    }
    this.rootFolder = folder;
    this.rebuildTree(Set.of(folder), Set.of());
    this.tree.expandRow(0);
  }

  public void showNoFolderPlaceholder() {
    this.rootFolder = null;
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(NO_FOLDER_MESSAGE);
    this.tree.setModel(new DefaultTreeModel(root));
    this.tree.setEnabled(false);
    this.setViewportView(this.tree);
  }

  public void refresh() {
    if (this.rootFolder != null) {
      try {
        this.rebuildTree(this.expandedFolders(), this.selectedPathSet());
      } catch (final IOException ignored) {
        // keep current tree
      }
    } else {
      this.showNoFolderPlaceholder();
    }
  }

  private void openFileAt(final MouseEvent event) {
    if (event.getClickCount() != 2 || event.getButton() != MouseEvent.BUTTON1) {
      return;
    }
    final int row = this.tree.getRowForLocation(event.getX(), event.getY());
    if (row < 0) {
      return;
    }
    final TreePath treePath = this.tree.getPathForRow(row);
    if (treePath == null) {
      return;
    }
    final Object last = treePath.getLastPathComponent();
    if (last instanceof DefaultMutableTreeNode mutable
        && mutable.getUserObject() instanceof FileTreeNode node) {
      this.openFile(node.path());
    }
  }

  private void registerActivationActions() {
    this.tree.getInputMap(JTree.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openSelectedFile");
    this.tree.getActionMap().put("openSelectedFile", new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent event) {
        FileTreePanel.this.selectedPaths().forEach(FileTreePanel.this::openFile);
      }
    });
  }

  private void openFile(final Path path) {
    if (Files.isRegularFile(path) && AllowedImageFiles.isAllowed(path)) {
      this.fileOpenListener.accept(path);
    }
  }

  public void reveal(final Path filePath) {
    if (this.rootFolder == null) {
      return;
    }
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.tree.getModel().getRoot();
    final DefaultMutableTreeNode node = this.findNode(root, filePath);
    if (node != null) {
      final TreePath path = new TreePath(node.getPath());
      this.tree.setSelectionPath(path);
      this.tree.scrollPathToVisible(path);
    }
  }

  private DefaultMutableTreeNode findNode(final DefaultMutableTreeNode parent, final Path target) {
    if (parent.getUserObject() instanceof FileTreeNode node && node.path().equals(target)) {
      return parent;
    }
    for (int i = 0; i < parent.getChildCount(); i++) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
      final DefaultMutableTreeNode found = this.findNode(child, target);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private void rebuildTree(final Set<Path> expandedFolders, final Set<Path> selectedPaths)
      throws IOException {
    final DefaultMutableTreeNode root = this.buildNode(this.rootFolder);
    this.tree.setEnabled(true);
    this.tree.setModel(new DefaultTreeModel(root));
    this.setViewportView(this.tree);
    this.restoreExpandedFolders(root, expandedFolders);
    this.restoreSelectedPaths(root, selectedPaths);
  }

  private Set<Path> expandedFolders() {
    final Object root = this.tree.getModel().getRoot();
    if (!(root instanceof DefaultMutableTreeNode mutable)) {
      return Set.of();
    }
    final TreePath rootPath = new TreePath(mutable.getPath());
    final Enumeration<TreePath> expanded = this.tree.getExpandedDescendants(rootPath);
    if (expanded == null) {
      return Set.of();
    }
    final Set<Path> result = new HashSet<>();
    while (expanded.hasMoreElements()) {
      this.pathFrom(expanded.nextElement()).filter(Files::isDirectory).ifPresent(result::add);
    }
    return result;
  }

  private Set<Path> selectedPathSet() {
    return new HashSet<>(this.selectedPaths());
  }

  private Optional<Path> pathFrom(final TreePath treePath) {
    final Object last = treePath.getLastPathComponent();
    if (last instanceof DefaultMutableTreeNode mutable
        && mutable.getUserObject() instanceof FileTreeNode node) {
      return Optional.of(node.path());
    }
    return Optional.empty();
  }

  private void restoreExpandedFolders(
      final DefaultMutableTreeNode root,
      final Set<Path> expandedFolders) {
    for (final Path path : expandedFolders) {
      final DefaultMutableTreeNode node = this.findNode(root, path);
      if (node != null) {
        this.tree.expandPath(new TreePath(node.getPath()));
      }
    }
  }

  private void restoreSelectedPaths(
      final DefaultMutableTreeNode root,
      final Set<Path> selectedPaths) {
    final List<TreePath> restored = selectedPaths.stream()
        .map(path -> this.findNode(root, path))
        .filter(node -> nonNull(node))
        .map(node -> new TreePath(node.getPath()))
        .toList();
    this.tree.setSelectionPaths(restored.toArray(TreePath[]::new));
  }

  private static String normalizedFileName(final Path path) {
    return path.getFileName().toString().toLowerCase(Locale.ROOT);
  }

  private DefaultMutableTreeNode buildNode(final Path path) throws IOException {
    final FileTreeNode user = new FileTreeNode(path);
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode(user);
    if (Files.isDirectory(path)) {
      try (Stream<Path> children = Files.list(path)) {
        children.sorted(Comparator.comparing(FileTreePanel::normalizedFileName))
            .filter(child -> Files.isDirectory(child) || AllowedImageFiles.isAllowed(child))
            .forEach(child -> {
              try {
                node.add(this.buildNode(child));
              } catch (final IOException exception) {
                // skip unreadable entry
              }
            });
      }
    }
    return node;
  }

  private static final class FileTreeCellRenderer extends DefaultTreeCellRenderer {

    private final IconService icons;
    private final Icon closedFolderIcon = UIManager.getIcon("Tree.closedIcon");
    private final Icon openFolderIcon = UIManager.getIcon("Tree.openIcon");

    private FileTreeCellRenderer(final IconService icons) {
      this.icons = icons;
    }

    @Override
    public java.awt.Component getTreeCellRendererComponent(
        final JTree tree,
        final Object value,
        final boolean selected,
        final boolean expanded,
        final boolean leaf,
        final int row,
        final boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (value instanceof DefaultMutableTreeNode mutable) {
        final Object user = mutable.getUserObject();
        if (user instanceof String message) {
          this.setText(message);
          this.setIcon(null);
          this.setFont(this.getFont().deriveFont(Font.ITALIC));
          return this;
        }
        if (user instanceof FileTreeNode node) {
          this.setText(node.path().getFileName().toString());
          if (Files.isDirectory(node.path())) {
            this.setIcon(this.folderIcon(expanded));
          } else {
            this.setIcon(this.icons.icon16("image.png"));
          }
        }
      }
      return this;
    }

    private Icon folderIcon(final boolean expanded) {
      final Icon icon = expanded ? this.openFolderIcon : this.closedFolderIcon;
      return icon == null ? this.icons.icon16("folder.png") : icon;
    }
  }
}
