package com.igormaznitsa.annotator.ui.selection;

import com.igormaznitsa.annotator.api.model.NormPoint;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * Magic wand: flood-fill a binary mask from the click point, then trace that mask into a polygon.
 */
public final class MagicWandSelector {

  private MagicWandSelector() {
  }

  public static Optional<List<NormPoint>> selectPolygon(
      final BufferedImage image,
      final int seedX,
      final int seedY,
      final double maxBrightnessDelta) {
    return selectPolygon(image, seedX, seedY,
        new MagicWandSettings(maxBrightnessDelta, MagicWandMode.LUMINANCE, 0.35, false));
  }

  public static Optional<List<NormPoint>> selectPolygon(
      final BufferedImage image,
      final int seedX,
      final int seedY,
      final MagicWandSettings settings) {
    return selectPolygon(MagicWandSampleGrid.from(image), seedX, seedY, settings);
  }

  public static Optional<List<NormPoint>> selectPolygon(
      final MagicWandSampleGrid grid,
      final int seedX,
      final int seedY,
      final MagicWandSettings settings) {
    return MagicWandFloodFill.buildMask(grid, seedX, seedY, settings)
        .map(mask -> mask.withoutThinBridges(seedX, seedY))
        .filter(MagicWandMask::hasMinimumSize)
        .flatMap(mask -> MagicWandMaskContour.toPolygon(mask, seedX, seedY, settings));
  }
}
