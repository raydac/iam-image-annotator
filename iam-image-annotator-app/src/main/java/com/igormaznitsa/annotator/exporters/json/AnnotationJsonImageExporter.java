package com.igormaznitsa.annotator.exporters.json;

import static com.igormaznitsa.annotator.exporters.common.ExporterFileNames.replaceExtension;
import static com.igormaznitsa.annotator.exporters.common.ExporterFileNames.uniqueFileName;
import static java.util.Objects.requireNonNull;

import com.igormaznitsa.annotator.api.export.AnnotationExporter;
import com.igormaznitsa.annotator.api.png.AnnotatedPng;
import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.exporters.api.AnnotatedImagesExporter;
import com.igormaznitsa.annotator.exporters.api.ExportProgress;
import com.igormaznitsa.annotator.exporters.common.ExporterDirectoryFileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.filechooser.FileFilter;

public final class AnnotationJsonImageExporter implements AnnotatedImagesExporter {

  private static final String TITLE = "Annotations JSON files";

  private final FileFilter fileFilter = new ExporterDirectoryFileFilter(TITLE);
  private final AnnotationExporter exporter = new AnnotationExporter();
  private final Function<Path, Optional<EditorSession>> openSessionResolver;

  public AnnotationJsonImageExporter() {
    this(ignored -> Optional.empty());
  }

  public AnnotationJsonImageExporter(
      final Function<Path, Optional<EditorSession>> openSessionResolver) {
    this.openSessionResolver = requireNonNull(openSessionResolver, "openSessionResolver");
  }

  @Override
  public String title() {
    return TITLE;
  }

  @Override
  public FileFilter fileFilter() {
    return this.fileFilter;
  }

  @Override
  public void exportImages(
      final List<Path> imageFiles,
      final Path destinationFolder,
      final Consumer<ExportProgress> progressConsumer) throws IOException {
    requireNonNull(imageFiles, "imageFiles");
    requireNonNull(destinationFolder, "destinationFolder");
    requireNonNull(progressConsumer, "progressConsumer");

    final List<Path> candidates = this.exportableCandidates(imageFiles);
    Files.createDirectories(destinationFolder);

    int exported = 0;
    final Map<String, Integer> fileNameCounts = new HashMap<>();
    for (int i = 0; i < candidates.size(); i++) {
      final Path imageFile = candidates.get(i);
      this.report(progressConsumer, "Exporting JSON for " + imageFile.getFileName(),
          this.percent(i, candidates.size()));
      final Optional<EditorSession> session = this.annotationSession(imageFile);
      if (session.isEmpty() || session.get().document().entries().isEmpty()) {
        continue;
      }
      this.exporter.exportJson(session.get(), destinationFolder.resolve(
          uniqueFileName(replaceExtension(imageFile, ".json"), fileNameCounts)));
      exported++;
    }

    if (exported == 0) {
      throw new IOException("No images with annotations were found");
    }
    this.report(progressConsumer, "JSON export complete", 100);
  }

  private List<Path> exportableCandidates(final List<Path> imageFiles) {
    return imageFiles.stream()
        .filter(Objects::nonNull)
        .filter(Files::isRegularFile)
        .filter(AllowedImageFiles::isAllowed)
        .sorted(Comparator.comparing(Path::toString))
        .toList();
  }

  private Optional<EditorSession> annotationSession(final Path imageFile) throws IOException {
    final Optional<EditorSession> openSession = this.openSessionResolver.apply(imageFile);
    if (openSession.isPresent()) {
      return openSession;
    }
    return AnnotatedPng.hasAnnotationChunks(imageFile)
        ? Optional.of(EditorSession.open(imageFile))
        : Optional.empty();
  }

  private int percent(final int index, final int total) {
    return total <= 0 ? 100 : (int) Math.round(100.0d * index / total);
  }

  private void report(
      final Consumer<ExportProgress> progressConsumer,
      final String stage,
      final int percent) {
    progressConsumer.accept(new ExportProgress(stage, percent));
  }
}
