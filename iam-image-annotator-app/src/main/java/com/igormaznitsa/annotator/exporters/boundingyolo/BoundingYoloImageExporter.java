package com.igormaznitsa.annotator.exporters.boundingyolo;

import static java.util.Objects.requireNonNull;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.png.AnnotatedPng;
import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.filechooser.FileFilter;

public final class BoundingYoloImageExporter implements AnnotatedImagesExporter {

  private static final String TITLE = "YOLO bounding boxes dataset";
  private static final int DISCOVERY_START_PERCENT = 0;
  private static final int DISCOVERY_END_PERCENT = 45;
  private static final int SPLIT_PERCENT = 50;
  private static final int EXPORT_START_PERCENT = 55;
  private static final int EXPORT_END_PERCENT = 100;
  private static final float JPEG_QUALITY = 0.95f;

  private final int firstClassId;
  private final FileFilter fileFilter = new ExporterDirectoryFileFilter(TITLE);
  private final ImagePerceptualHash perceptualHash = new ImagePerceptualHash();
  private final YoloDatasetSplitter splitter = new YoloDatasetSplitter();
  private final Predicate<Map<String, Integer>> classConfirmation;

  public BoundingYoloImageExporter(final int firstClassId) {
    this(firstClassId, ignored -> true);
  }

  public BoundingYoloImageExporter(
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation) {
    if (firstClassId < 0) {
      throw new IllegalArgumentException("firstClassId must be zero or greater");
    }
    this.firstClassId = firstClassId;
    this.classConfirmation = requireNonNull(classConfirmation, "classConfirmation");
  }

  private static String labelFileNameFor(final String imageFileName) {
    final int dot = imageFileName.lastIndexOf('.');
    return dot > 0 ? imageFileName.substring(0, dot) + ".txt" : imageFileName + ".txt";
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
    final List<RawSample> rawSamples = this.loadRawSamples(candidates, progressConsumer);
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
    final YoloDatasetSplit split = this.splitter.split(samples);
    this.exportDataset(split, classIds, destinationFolder, progressConsumer);
    this.report(progressConsumer, "YOLO dataset export complete", EXPORT_END_PERCENT);
  }

  private List<Path> exportableCandidates(final List<Path> imageFiles) {
    return imageFiles.stream()
        .filter(Objects::nonNull)
        .filter(Files::isRegularFile)
        .filter(AllowedImageFiles::isAllowed)
        .sorted(Comparator.comparing(Path::toString))
        .toList();
  }

  private List<RawSample> loadRawSamples(
      final List<Path> candidates,
      final Consumer<ExportProgress> progressConsumer) throws IOException {
    final List<RawSample> result = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      final Path imageFile = candidates.get(i);
      this.report(progressConsumer, "Reading annotations from " + imageFile.getFileName(),
          this.percent(i, candidates.size(), DISCOVERY_START_PERCENT, DISCOVERY_END_PERCENT));
      final Optional<RawSample> sample = this.loadRawSample(imageFile);
      sample.ifPresent(result::add);
    }
    return List.copyOf(result);
  }

  private Optional<RawSample> loadRawSample(final Path imageFile) throws IOException {
    if (!AnnotatedPng.hasAnnotationChunks(imageFile)) {
      return Optional.empty();
    }
    try (InputStream input = Files.newInputStream(imageFile)) {
      final AnnotatedPng annotated = AnnotatedPng.read(input);
      final List<RawBox> boxes = annotated.document().entries().stream()
          .filter(AnnotationEntry::visible)
          .map(this::toRawBox)
          .flatMap(Optional::stream)
          .toList();
      if (boxes.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new RawSample(
          imageFile,
          this.perceptualHash.hash(annotated.baseImage()),
          boxes));
    }
  }

  private Optional<RawBox> toRawBox(final AnnotationEntry entry) {
    return this.boundsOf(entry).map(bounds -> new RawBox(entry.id(), bounds));
  }

  private Optional<YoloBoundingBox.Bounds> boundsOf(final AnnotationEntry entry) {
    final AnnotationCoords coords = entry.coords();
    return switch (entry.type()) {
      case RECTANGLE, POSE2D -> this.rectangleBounds(coords);
      case POLYGON -> this.pointBounds(coords.points());
      case OBB -> this.pointBounds(coords.corners());
    };
  }

  private Optional<YoloBoundingBox.Bounds> rectangleBounds(final AnnotationCoords coords) {
    if (coords.x() == null || coords.y() == null || coords.width() == null
        || coords.height() == null || coords.width() <= 0.0d || coords.height() <= 0.0d) {
      return Optional.empty();
    }
    return this.createBounds(
        coords.x(),
        coords.y(),
        coords.x() + coords.width(),
        coords.y() + coords.height());
  }

  private Optional<YoloBoundingBox.Bounds> pointBounds(final List<NormPoint> points) {
    if (points == null || points.isEmpty()) {
      return Optional.empty();
    }
    final double minX = points.stream().mapToDouble(NormPoint::x).min().orElseThrow();
    final double minY = points.stream().mapToDouble(NormPoint::y).min().orElseThrow();
    final double maxX = points.stream().mapToDouble(NormPoint::x).max().orElseThrow();
    final double maxY = points.stream().mapToDouble(NormPoint::y).max().orElseThrow();
    return this.createBounds(minX, minY, maxX, maxY);
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

  private Map<String, Integer> buildClassIds(final List<RawSample> samples) {
    final TreeSet<String> classes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    samples.stream().flatMap(sample -> sample.boxes().stream()).map(RawBox::className)
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
    int exported = this.exportSplit(split.train(), destinationFolder, "train", 0, total,
        progressConsumer);
    this.exportSplit(split.validation(), destinationFolder, "val", exported, total,
        progressConsumer);
    this.writeDataYaml(destinationFolder, classIds);
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
    final Map<String, Integer> fileNameCounts = new HashMap<>();
    for (int i = 0; i < samples.size(); i++) {
      final YoloImageSample sample = samples.get(i);
      this.report(progressConsumer, "Writing " + splitName + " sample "
          + sample.imagePath().getFileName(), this.percent(alreadyExported + i, total,
          EXPORT_START_PERCENT, EXPORT_END_PERCENT));
      final String imageFileName = this.uniqueJpegFileName(
          sample.imagePath().getFileName().toString(),
          fileNameCounts);
      this.writeBaseImageAsJpeg(
          sample.imagePath(),
          imageFolder.resolve(imageFileName));
      Files.write(
          labelFolder.resolve(labelFileNameFor(imageFileName)),
          sample.boxes().stream().map(YoloBoundingBox::toLabelLine).toList(),
          StandardCharsets.UTF_8);
    }
    return alreadyExported + samples.size();
  }

  private void writeBaseImageAsJpeg(final Path source, final Path target) throws IOException {
    try (InputStream input = Files.newInputStream(source)) {
      final AnnotatedPng annotated = AnnotatedPng.read(input);
      this.writeJpeg(this.toJpegCompatibleImage(annotated.baseImage()), target);
    }
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

  private String uniqueJpegFileName(
      final String fileName,
      final Map<String, Integer> fileNameCounts) {
    final String jpegFileName = this.toJpegFileName(fileName);
    final int count = fileNameCounts.merge(jpegFileName, 1, Integer::sum);
    if (count == 1) {
      return jpegFileName;
    }
    final int dot = jpegFileName.lastIndexOf('.');
    return dot > 0
        ? jpegFileName.substring(0, dot) + '_' + count + jpegFileName.substring(dot)
        : jpegFileName + '_' + count;
  }

  private String toJpegFileName(final String fileName) {
    final int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) + ".jpg" : fileName + ".jpg";
  }

  private int percent(final int index, final int total, final int start, final int end) {
    if (total <= 0) {
      return end;
    }
    return start + (int) Math.round((end - start) * (double) index / total);
  }

  private void report(
      final Consumer<ExportProgress> progressConsumer,
      final String stage,
      final int percent) {
    progressConsumer.accept(new ExportProgress(stage, percent));
  }

  private record RawSample(Path imagePath, long pHash, List<RawBox> boxes) {

    RawSample {
      boxes = List.copyOf(boxes);
    }

    YoloImageSample toYoloSample(final Map<String, Integer> classIds) {
      return new YoloImageSample(
          this.imagePath,
          this.pHash,
          this.boxes.stream()
              .map(box -> box.toYoloBoundingBox(classIds.get(box.className())))
              .toList());
    }
  }

  private record RawBox(String className, YoloBoundingBox.Bounds bounds) {

    YoloBoundingBox toYoloBoundingBox(final int classId) {
      return YoloBoundingBox.of(this.className, classId, this.bounds);
    }
  }
}
