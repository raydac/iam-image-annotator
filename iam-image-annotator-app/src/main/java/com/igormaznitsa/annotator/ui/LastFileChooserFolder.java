package com.igormaznitsa.annotator.ui;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.JFileChooser;

public final class LastFileChooserFolder {

  private static final Map<Dialog, Path> REMEMBERED_FOLDERS = new EnumMap<>(Dialog.class);

  static void clearRememberedFolders() {
    synchronized (REMEMBERED_FOLDERS) {
      REMEMBERED_FOLDERS.clear();
    }
  }

  public JFileChooser createChooser(final Dialog dialog) {
    return this.rememberedFolder(dialog)
        .map(Path::toFile)
        .map(JFileChooser::new)
        .orElseGet(JFileChooser::new);
  }

  public JFileChooser createDirectoryChooser(final Dialog dialog) {
    final JFileChooser chooser = new JFileChooser();
    this.rememberedFolder(dialog)
        .ifPresent(folder -> this.selectRememberedDirectory(chooser, folder));
    return chooser;
  }

  public void rememberFolder(final Dialog dialog, final Path folder) {
    requireNonNull(dialog, "dialog");
    requireNonNull(folder, "folder");
    if (Files.isDirectory(folder)) {
      synchronized (REMEMBERED_FOLDERS) {
        REMEMBERED_FOLDERS.put(dialog, folder.toAbsolutePath().normalize());
      }
    }
  }

  public void rememberSelectedFileFolder(final Dialog dialog, final JFileChooser chooser) {
    requireNonNull(chooser, "chooser");
    this.selectedFileFolder(chooser).ifPresent(folder -> this.rememberFolder(dialog, folder));
  }

  Optional<Path> rememberedFolder(final Dialog dialog) {
    requireNonNull(dialog, "dialog");
    synchronized (REMEMBERED_FOLDERS) {
      return ofNullable(REMEMBERED_FOLDERS.get(dialog))
          .filter(Files::isDirectory);
    }
  }

  private Optional<Path> selectedFileFolder(final JFileChooser chooser) {
    return ofNullable(chooser.getSelectedFile())
        .map(File::toPath)
        .map(path -> ofNullable(path.getParent())
            .orElseGet(() -> chooser.getCurrentDirectory().toPath()));
  }

  private void selectRememberedDirectory(final JFileChooser chooser, final Path folder) {
    ofNullable(folder.getParent()).ifPresentOrElse(
        parent -> {
          chooser.setCurrentDirectory(parent.toFile());
          chooser.setSelectedFile(folder.toFile());
        },
        () -> {
          chooser.setCurrentDirectory(folder.toFile());
          chooser.setSelectedFile(folder.toFile());
        });
  }

  public enum Dialog {
    OPEN_FOLDER,
    OPEN_IMAGE,
    EXPORT_IMAGES,
    SAVE_IMAGE_AS
  }
}
