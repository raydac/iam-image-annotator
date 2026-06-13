package com.igormaznitsa.annotator.exporters.boundingyolo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class YoloDatasetSplitterTest {

  @Test
  void keepsValidationSetAtTwentyPercentWhenClustersFit() {
    final YoloDatasetSplit split = new YoloDatasetSplitter().split(List.of(
        this.sample("image-0.png", 0x0000_0000_0000_0000L, 0),
        this.sample("image-1.png", 0x0000_0000_FFFF_FFFFL, 1),
        this.sample("image-2.png", 0x0000_FFFF_0000_FFFFL, 2),
        this.sample("image-3.png", 0x0000_FFFF_FFFF_0000L, 3),
        this.sample("image-4.png", 0xFFFF_0000_0000_FFFFL, 4),
        this.sample("image-5.png", 0xFFFF_0000_FFFF_0000L, 5),
        this.sample("image-6.png", 0xFFFF_FFFF_0000_0000L, 6),
        this.sample("image-7.png", 0x00FF_00FF_00FF_00FFL, 7),
        this.sample("image-8.png", 0xFF00_FF00_FF00_FF00L, 8),
        this.sample("image-9.png", 0x0F0F_0F0F_0F0F_0F0FL, 9)));

    assertEquals(8, split.train().size());
    assertEquals(2, split.validation().size());
  }

  @Test
  void keepsVisuallySimilarSamplesInSameSplit() {
    final YoloImageSample nearDuplicateA = this.sample("near-a.png", 0x00AA_AAAA_AAAA_AAAAL, 0);
    final YoloImageSample nearDuplicateB = this.sample("near-b.png", 0x00AA_AAAA_AAAA_AAAAL, 0);
    final YoloDatasetSplit split = new YoloDatasetSplitter().split(List.of(
        nearDuplicateA,
        nearDuplicateB,
        this.sample("other-a.png", 0x0055_5555_5555_5555L, 1),
        this.sample("other-b.png", 0x0000_0000_0000_0000L, 1)));

    assertEquals(
        this.splitName(split, nearDuplicateA),
        this.splitName(split, nearDuplicateB));
  }

  private String splitName(final YoloDatasetSplit split, final YoloImageSample sample) {
    final Set<Path> trainPaths = split.train().stream()
        .map(YoloImageSample::imagePath)
        .collect(Collectors.toSet());
    return trainPaths.contains(sample.imagePath()) ? "train" : "validation";
  }

  private YoloImageSample sample(final String fileName, final long pHash, final int classId) {
    return new YoloImageSample(
        Path.of(fileName),
        new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB),
        pHash,
        List.of(YoloObjectLabel.of(
            "class-" + classId,
            classId,
            classId + " 0.400000 0.400000 0.400000 0.400000",
            new YoloBoundingBox.Bounds(0.2, 0.2, 0.6, 0.6))));
  }
}
