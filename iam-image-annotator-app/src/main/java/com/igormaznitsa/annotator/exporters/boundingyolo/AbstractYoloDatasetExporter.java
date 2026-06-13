package com.igormaznitsa.annotator.exporters.boundingyolo;

import static com.igormaznitsa.annotator.exporters.common.ExporterFileNames.uniqueFileName;
import static java.util.Objects.requireNonNull;

import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.png.AnnotatedPng;
import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.exporters.api.AnnotatedImagesExporter;
import com.igormaznitsa.annotator.exporters.api.ExportProgress;
import com.igormaznitsa.annotator.exporters.common.ExporterDirectoryFileFilter;
import com.igormaznitsa.annotator.exporters.common.ImagePerceptualHash;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.filechooser.FileFilter;

abstract class AbstractYoloDatasetExporter implements AnnotatedImagesExporter {

  private static final int DISCOVERY_START_PERCENT = 0;
  private static final int DISCOVERY_END_PERCENT = 45;
  private static final int SPLIT_PERCENT = 50;
  private static final int EXPORT_START_PERCENT = 55;
  private static final int EXPORT_END_PERCENT = 100;
  private static final float JPEG_QUALITY = 0.95f;
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final Pattern NON_YOLO_CLASS_NAME = Pattern.compile("[^a-z0-9_]+");
  private static final Pattern UNDERSCORES = Pattern.compile("_+");

  private final int firstClassId;
  private final String title;
  private final FileFilter fileFilter;
  private final ImagePerceptualHash perceptualHash = new ImagePerceptualHash();
  private final YoloDatasetSplitter splitter = new YoloDatasetSplitter();
  private final Predicate<Map<String, Integer>> classConfirmation;
  private final Function<Path, Optional<EditorSession>> openSessionResolver;

  protected AbstractYoloDatasetExporter(
      final String title,
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation,
      final Function<Path, Optional<EditorSession>> openSessionResolver) {
    if (firstClassId < 0) {
      throw new IllegalArgumentException("firstClassId must be zero or greater");
    }
    this.title = requireNonNull(title, "title");
    this.firstClassId = firstClassId;
    this.fileFilter = new ExporterDirectoryFileFilter(title);
    this.classConfirmation = requireNonNull(classConfirmation, "classConfirmation");
    this.openSessionResolver = requireNonNull(openSessionResolver, "openSessionResolver");
  }

  static String toYoloClassName(final String className) {
    final String normalized = UNDERSCORES.matcher(NON_YOLO_CLASS_NAME.matcher(
                SPACES.matcher(requireNonNull(className, "className").strip().toLowerCase(Locale.ROOT))
                    .replaceAll("_"))
            .replaceAll("_"))
        .replaceAll("_");
    return normalized.isEmpty() ? "class" : normalized;
  }

  @Override
  public final String title() {
    return this.title;
  }

  @Override
  public final FileFilter fileFilter() {
    return this.fileFilter;
  }

  @Override
  public final void exportImages(
      final List<Path> imageFiles,
      final Path destinationFolder,
      final Consumer<ExportProgress> progressConsumer) throws IOException {
    requireNonNull(imageFiles, "imageFiles");
    requireNonNull(destinationFolder, "destinationFolder");
    requireNonNull(progressConsumer, "progressConsumer");

    final List<Path> candidates = this.exportableCandidates(imageFiles);
    final List<YoloRawSample> rawSamples = this.loadRawSamples(candidates, progressConsumer);
    if (rawSamples.isEmpty()) {
      throw new IOException("No annotated images with exportable annotations were found");
    }

    this.report(progressConsumer, "Building class map", SPLIT_PERCENT);
    final Map<String, Integer> classIds = this.buildClassIds(rawSamples);
    this.report(progressConsumer, "Confirming detected classes", SPLIT_PERCENT);
    if (!this.classConfirmation.test(classIds)) {
      throw new CancellationException("Export cancelled by user");
    }

    final List<YoloImageSample> samples = rawSamples.stream()
        .map(sample -> sample.toYoloSample(classIds))
        .toList();
    this.report(progressConsumer, "Splitting train and validation sets", SPLIT_PERCENT);
    this.exportDataset(this.splitter.split(samples), classIds, destinationFolder, progressConsumer);
    this.report(progressConsumer, this.title + " export complete", EXPORT_END_PERCENT);
  }

  protected abstract List<YoloRawLabel> rawLabelsOf(AnnotationDocument document);

  protected final Optional<YoloBoundingBox.Bounds> pointBounds(final List<NormPoint> points) {
    if (points == null || points.isEmpty()) {
      return Optional.empty();
    }
    return this.createBounds(
        points.stream().mapToDouble(NormPoint::x).min().orElseThrow(),
        points.stream().mapToDouble(NormPoint::y).min().orElseThrow(),
        points.stream().mapToDouble(NormPoint::x).max().orElseThrow(),
        points.stream().mapToDouble(NormPoint::y).max().orElseThrow());
  }

  protected final String normalizedClassName(final AnnotationEntry entry) {
    return toYoloClassName(entry.id());
  }

  private List<Path> exportableCandidates(final List<Path> imageFiles) {
    return imageFiles.stream()
        .filter(Objects::nonNull)
        .filter(Files::isRegularFile)
        .filter(AllowedImageFiles::isAllowed)
        .sorted(Comparator.comparing(Path::toString))
        .toList();
  }

  private List<YoloRawSample> loadRawSamples(
      final List<Path> candidates,
      final Consumer<ExportProgress> progressConsumer) throws IOException {
    final List<YoloRawSample> result = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      final Path imageFile = candidates.get(i);
      this.report(progressConsumer, "Reading annotations from " + imageFile.getFileName(),
          this.percent(i, candidates.size(), DISCOVERY_START_PERCENT, DISCOVERY_END_PERCENT));
      this.loadRawSample(imageFile).ifPresent(result::add);
    }
    return List.copyOf(result);
  }

  private Optional<YoloRawSample> loadRawSample(final Path imageFile) throws IOException {
    final Optional<AnnotatedSource> source = this.annotatedSource(imageFile);
    if (source.isEmpty()) {
      return Optional.empty();
    }

    final List<YoloRawLabel> labels = this.rawLabelsOf(source.get().document());
    if (labels.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new YoloRawSample(
        imageFile,
        source.get().baseImage(),
        this.perceptualHash.hash(source.get().baseImage()),
        labels));
  }

  private Optional<AnnotatedSource> annotatedSource(final Path imageFile) throws IOException {
    final Optional<EditorSession> openSession = this.openSessionResolver.apply(imageFile);
    if (openSession.isPresent()) {
      return openSession.map(
          session -> new AnnotatedSource(session.baseImage(), session.document()));
    }
    if (!AnnotatedPng.hasAnnotationChunks(imageFile)) {
      return Optional.empty();
    }
    try (InputStream input = Files.newInputStream(imageFile)) {
      final AnnotatedPng annotated = AnnotatedPng.read(input);
      return Optional.of(new AnnotatedSource(annotated.baseImage(), annotated.document()));
    }
  }

  private Optional<YoloBoundingBox.Bounds> createBounds(
      final double minX,
      final double minY,
      final double maxX,
      final double maxY) {
    try {
      return Optional.of(new YoloBoundingBox.Bounds(minX, minY, maxX, maxY));
    } catch (final IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  private Map<String, Integer> buildClassIds(final List<YoloRawSample> samples) {
    final TreeSet<String> classes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    samples.stream().flatMap(sample -> sample.labels().stream()).map(YoloRawLabel::className)
        .forEach(classes::add);

    final Map<String, Integer> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    int classId = this.firstClassId;
    for (final String className : classes) {
      result.put(className, classId++);
    }
    return Map.copyOf(result);
  }

  private void exportDataset(
      final YoloDatasetSplit split,
      final Map<String, Integer> classIds,
      final Path destinationFolder,
      final Consumer<ExportProgress> progressConsumer) throws IOException {
    Files.createDirectories(destinationFolder);
    this.prepareSplitFolders(destinationFolder, "train");
    this.prepareSplitFolders(destinationFolder, "val");

    final int total = split.train().size() + split.validation().size();
    final int exported = this.exportSplit(split.train(), destinationFolder, "train", 0, total,
        progressConsumer);
    this.exportSplit(split.validation(), destinationFolder, "val", exported, total,
        progressConsumer);
    this.writeDataYaml(destinationFolder, classIds);
  }

  private void prepareSplitFolders(final Path destinationFolder, final String splitName)
      throws IOException {
    Files.createDirectories(destinationFolder.resolve("images").resolve(splitName));
    Files.createDirectories(destinationFolder.resolve("labels").resolve(splitName));
  }

  private int exportSplit(
      final List<YoloImageSample> samples,
      final Path destinationFolder,
      final String splitName,
      final int alreadyExported,
      final int total,
      final Consumer<ExportProgress> progressConsumer) throws IOException {
    final Path imageFolder = destinationFolder.resolve("images").resolve(splitName);
    final Path labelFolder = destinationFolder.resolve("labels").resolve(splitName);
    final Map<String, Integer> fileNameCounts = new java.util.HashMap<>();
    for (int i = 0; i < samples.size(); i++) {
      final YoloImageSample sample = samples.get(i);
      this.report(progressConsumer, "Writing " + splitName + " sample "
          + sample.imagePath().getFileName(), this.percent(alreadyExported + i, total,
          EXPORT_START_PERCENT, EXPORT_END_PERCENT));
      final String imageFileName = uniqueFileName(
          this.toJpegFileName(sample.imagePath().getFileName().toString()),
          fileNameCounts);
      this.writeJpeg(this.toJpegCompatibleImage(sample.baseImage()),
          imageFolder.resolve(imageFileName));
      Files.write(
          labelFolder.resolve(this.labelFileNameFor(imageFileName)),
          sample.labels().stream().map(YoloObjectLabel::labelLine).toList(),
          StandardCharsets.UTF_8);
    }
    return alreadyExported + samples.size();
  }

  private void writeDataYaml(final Path destinationFolder, final Map<String, Integer> classIds)
      throws IOException {
    Files.write(
        destinationFolder.resolve("data.yaml"),
        this.dataYamlLines(classIds),
        StandardCharsets.UTF_8);
  }

  private List<String> dataYamlLines(final Map<String, Integer> classIds) {
    final List<String> lines = new ArrayList<>();
    lines.add("path: .");
    lines.add("train: images/train");
    lines.add("val: images/val");
    lines.add("");
    lines.add("names:");
    classIds.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .map(entry -> "  " + entry.getValue() + ": " + this.yamlScalar(entry.getKey()))
        .forEach(lines::add);
    return List.copyOf(lines);
  }

  private String yamlScalar(final String value) {
    return value.matches("[A-Za-z0-9_-]+") ? value : "'" + value.replace("'", "''") + "'";
  }

  private void writeJpeg(final BufferedImage image, final Path target) throws IOException {
    final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IOException("JPEG encoder unavailable");
    }
    final ImageWriter writer = writers.next();
    try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), this.jpegWriteParam(writer));
    } finally {
      writer.dispose();
    }
  }

  private ImageWriteParam jpegWriteParam(final ImageWriter writer) {
    final ImageWriteParam param = writer.getDefaultWriteParam();
    if (param.canWriteCompressed()) {
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
    }
    return param;
  }

  private BufferedImage toJpegCompatibleImage(final BufferedImage source) {
    if (source.getType() == BufferedImage.TYPE_INT_RGB) {
      return source;
    }
    final BufferedImage target =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = target.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
      graphics.drawImage(source, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private String labelFileNameFor(final String imageFileName) {
    final int dot = imageFileName.lastIndexOf('.');
    return dot > 0 ? imageFileName.substring(0, dot) + ".txt" : imageFileName + ".txt";
  }

  private String toJpegFileName(final String fileName) {
    final int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) + ".jpg" : fileName + ".jpg";
  }

  private int percent(final int index, final int total, final int start, final int end) {
    return total <= 0 ? end : start + (int) Math.round((end - start) * (double) index / total);
  }

  private void report(
      final Consumer<ExportProgress> progressConsumer,
      final String stage,
      final int percent) {
    progressConsumer.accept(new ExportProgress(stage, percent));
  }

  private record AnnotatedSource(BufferedImage baseImage, AnnotationDocument document) {
  }
}
