package com.igormaznitsa.annotator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LastFileChooserFolderTest {

  @TempDir
  Path tempDir;

  private LastFileChooserFolder folders;

  @BeforeEach
  void setUp() {
    LastFileChooserFolder.clearRememberedFolders();
    this.folders = new LastFileChooserFolder();
  }

  @AfterEach
  void tearDown() {
    LastFileChooserFolder.clearRememberedFolders();
  }

  @Test
  void remembersFolderPerDialog() throws Exception {
    final Path openFolder = Files.createDirectory(this.tempDir.resolve("open"));
    final Path exportFolder = Files.createDirectory(this.tempDir.resolve("export"));

    this.folders.rememberFolder(LastFileChooserFolder.Dialog.OPEN_FOLDER, openFolder);
    this.folders.rememberFolder(LastFileChooserFolder.Dialog.EXPORT_IMAGES, exportFolder);

    assertEquals(
        openFolder,
        this.folders.rememberedFolder(LastFileChooserFolder.Dialog.OPEN_FOLDER).orElseThrow());
    assertEquals(
        exportFolder,
        this.folders.rememberedFolder(LastFileChooserFolder.Dialog.EXPORT_IMAGES).orElseThrow());
  }

  @Test
  void remembersFolderAcrossHelperInstances() throws Exception {
    final Path folder = Files.createDirectory(this.tempDir.resolve("runtime"));
    this.folders.rememberFolder(LastFileChooserFolder.Dialog.OPEN_FOLDER, folder);

    assertEquals(
        folder,
        new LastFileChooserFolder()
            .rememberedFolder(LastFileChooserFolder.Dialog.OPEN_FOLDER)
            .orElseThrow());
  }

  @Test
  void ignoresDeletedRememberedFolder() throws Exception {
    final Path folder = Files.createDirectory(this.tempDir.resolve("deleted"));
    this.folders.rememberFolder(LastFileChooserFolder.Dialog.OPEN_IMAGE, folder);
    Files.delete(folder);

    assertTrue(this.folders.rememberedFolder(LastFileChooserFolder.Dialog.OPEN_IMAGE).isEmpty());
  }

  @Test
  void directoryChooserSelectsRememberedFolderFromItsParent() throws Exception {
    final Path folder = Files.createDirectory(this.tempDir.resolve("dataset"));
    this.folders.rememberFolder(LastFileChooserFolder.Dialog.OPEN_FOLDER, folder);

    final JFileChooser chooser =
        this.folders.createDirectoryChooser(LastFileChooserFolder.Dialog.OPEN_FOLDER);

    assertEquals(this.tempDir.toFile(), chooser.getCurrentDirectory());
    assertEquals(folder.toFile(), chooser.getSelectedFile());
  }

  @Test
  void remembersSelectedFileParent() throws Exception {
    final Path image = Files.createFile(this.tempDir.resolve("image.png"));
    final JFileChooser chooser = new JFileChooser(this.tempDir.toFile());
    chooser.setSelectedFile(image.toFile());

    this.folders.rememberSelectedFileFolder(LastFileChooserFolder.Dialog.SAVE_IMAGE_AS, chooser);

    assertEquals(
        this.tempDir,
        this.folders.rememberedFolder(LastFileChooserFolder.Dialog.SAVE_IMAGE_AS).orElseThrow());
  }
}
