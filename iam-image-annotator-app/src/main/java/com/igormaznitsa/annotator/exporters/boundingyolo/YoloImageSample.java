package com.igormaznitsa.annotator.exporters.boundingyolo;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

record YoloImageSample(Path imagePath, long pHash, List<YoloBoundingBox> boxes) {

  YoloImageSample {
    boxes = List.copyOf(boxes);
  }

  int objectCount() {
    return this.boxes.size();
  }

  Set<Integer> classesPresent() {
    return this.boxes.stream().map(YoloBoundingBox::classId).collect(Collectors.toSet());
  }

  Map<Integer, Integer> classCounts() {
    return this.boxes.stream()
        .collect(Collectors.toMap(YoloBoundingBox::classId, ignored -> 1, Integer::sum));
  }

  Map<ImageZone, Integer> zoneDistribution() {
    return this.boxes.stream()
        .collect(Collectors.toMap(
            YoloBoundingBox::zone,
            ignored -> 1,
            Integer::sum,
            () -> new EnumMap<>(ImageZone.class)));
  }

  Map<BoundingBoxSize, Integer> bboxSizeDistribution() {
    return this.boxes.stream()
        .collect(Collectors.toMap(
            YoloBoundingBox::size,
            ignored -> 1,
            Integer::sum,
            () -> new EnumMap<>(BoundingBoxSize.class)));
  }
}
