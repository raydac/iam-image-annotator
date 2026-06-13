package com.igormaznitsa.annotator.exporters.boundingyolo;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

record YoloRawSample(Path imagePath, BufferedImage baseImage, long pHash,
                     List<YoloRawLabel> labels) {

  YoloRawSample {
    labels = List.copyOf(labels);
  }

  YoloImageSample toYoloSample(final Map<String, Integer> classIds) {
    return new YoloImageSample(
        this.imagePath,
        this.baseImage,
        this.pHash,
        this.labels.stream()
            .map(label -> label.toObjectLabel(classIds.get(label.className())))
            .toList());
  }
}
