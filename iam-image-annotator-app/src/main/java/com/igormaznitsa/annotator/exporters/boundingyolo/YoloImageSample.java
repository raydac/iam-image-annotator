package com.igormaznitsa.annotator.exporters.boundingyolo;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

record YoloImageSample(Path imagePath, BufferedImage baseImage, long pHash,
                       List<YoloObjectLabel> labels) {

  YoloImageSample {
    labels = List.copyOf(labels);
  }

  int objectCount() {
    return this.labels.size();
  }

  Set<Integer> classesPresent() {
    return this.labels.stream().map(YoloObjectLabel::classId).collect(Collectors.toSet());
  }

  Map<Integer, Integer> classCounts() {
    return this.labels.stream()
        .collect(Collectors.toMap(YoloObjectLabel::classId, ignored -> 1, Integer::sum));
  }

  Map<ImageZone, Integer> zoneDistribution() {
    return this.labels.stream()
        .collect(Collectors.toMap(
            YoloObjectLabel::zone,
            ignored -> 1,
            Integer::sum,
            () -> new EnumMap<>(ImageZone.class)));
  }

  Map<BoundingBoxSize, Integer> bboxSizeDistribution() {
    return this.labels.stream()
        .collect(Collectors.toMap(
            YoloObjectLabel::size,
            ignored -> 1,
            Integer::sum,
            () -> new EnumMap<>(BoundingBoxSize.class)));
  }
}
