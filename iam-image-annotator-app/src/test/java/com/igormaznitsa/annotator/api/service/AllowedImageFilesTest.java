package com.igormaznitsa.annotator.api.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllowedImageFilesTest {

  @TempDir
  Path tempDir;

  @Test
  void allowsPngFiles() throws IOException {
    assertTrue(AllowedImageFiles.isAllowed(Files.createFile(tempDir.resolve("photo.png"))));
  }

  @Test
  void allowsJpegFiles() throws IOException {
    assertTrue(AllowedImageFiles.isAllowed(Files.createFile(tempDir.resolve("photo.jpg"))));
    assertTrue(AllowedImageFiles.isAllowed(Files.createFile(tempDir.resolve("photo.jpeg"))));
  }

  @Test
  void rejectsCompoundExtensionPng() throws IOException {
    assertFalse(AllowedImageFiles.isAllowed(Files.createFile(tempDir.resolve("photo.extra.png"))));
  }

  @Test
  void rejectsNonImageFiles() throws IOException {
    assertFalse(AllowedImageFiles.isAllowed(Files.createFile(tempDir.resolve("photo.gif"))));
  }

  @Test
  void resolvesAnnotatedPngPath() {
    assertTrue(AllowedImageFiles.toAnnotatedPngPath(tempDir.resolve("photo.jpg"))
        .endsWith("photo.png"));
    assertTrue(AllowedImageFiles.toAnnotatedPngPath(tempDir.resolve("photo.jpeg"))
        .endsWith("photo.png"));
    assertTrue(AllowedImageFiles.toAnnotatedPngPath(tempDir.resolve("photo.png"))
        .endsWith("photo.png"));
  }
}
