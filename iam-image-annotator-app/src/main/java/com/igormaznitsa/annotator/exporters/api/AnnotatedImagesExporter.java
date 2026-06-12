package com.igormaznitsa.annotator.exporters.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.filechooser.FileFilter;

public interface AnnotatedImagesExporter {

  String title();

  FileFilter fileFilter();

  void exportImages(
      List<Path> imageFiles,
      Path destinationFolder,
      Consumer<ExportProgress> progressConsumer) throws IOException;
}
