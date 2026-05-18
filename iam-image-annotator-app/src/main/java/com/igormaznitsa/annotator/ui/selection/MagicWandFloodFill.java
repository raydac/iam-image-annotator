package com.igormaznitsa.annotator.ui.selection;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Optional;

/**
 * Builds a binary mask by flood fill: starting at the click pixel, visits each neighbor
 * that matches the seed color (BFS queue — classic magic-wand region grow).
 */
public final class MagicWandFloodFill {

  private static final double MAX_COLOR_DISTANCE = Math.sqrt(3.0);

  private static final int[] NEIGHBOR_DX = {1, 1, 0, -1, -1, -1, 0, 1};
  private static final int[] NEIGHBOR_DY = {0, 1, 1, 1, 0, -1, -1, -1};

  private MagicWandFloodFill() {
  }

  public static Optional<MagicWandMask> buildMask(
      final MagicWandSampleGrid grid,
      final int seedX,
      final int seedY,
      final MagicWandSettings settings) {
    final int width = grid.width();
    final int height = grid.height();
    if (!MagicWandMask.isInside(width, height, seedX, seedY)) {
      return Optional.empty();
    }
    final BitSet pixels = new BitSet(width * height);
    final int seedIndex = MagicWandMask.index(width, seedX, seedY);
    pixels.set(seedIndex);
    final ArrayDeque<Integer> queue = new ArrayDeque<>();
    queue.add(seedIndex);
    final float seedLuminance = grid.luminanceAt(seedX, seedY);
    final int seedRgb = grid.rgbAt(seedX, seedY);
    final double tolerance = settings.tolerance();
    while (!queue.isEmpty()) {
      final int current = queue.poll();
      final int x = current % width;
      final int y = current / width;
      for (int direction = 0; direction < NEIGHBOR_DX.length; direction++) {
        expandNeighbor(
            grid,
            width,
            height,
            pixels,
            queue,
            seedLuminance,
            seedRgb,
            settings.mode(),
            tolerance,
            x + NEIGHBOR_DX[direction],
            y + NEIGHBOR_DY[direction]);
      }
    }
    return Optional.of(new MagicWandMask(width, height, pixels));
  }

  private static void expandNeighbor(
      final MagicWandSampleGrid grid,
      final int width,
      final int height,
      final BitSet mask,
      final ArrayDeque<Integer> queue,
      final float seedLuminance,
      final int seedRgb,
      final MagicWandMode mode,
      final double tolerance,
      final int x,
      final int y) {
    if (!MagicWandMask.isInside(width, height, x, y)) {
      return;
    }
    final int neighbor = MagicWandMask.index(width, x, y);
    if (mask.get(neighbor)) {
      return;
    }
    if (!matches(grid, x, y, seedLuminance, seedRgb, mode, tolerance)) {
      return;
    }
    mask.set(neighbor);
    queue.add(neighbor);
  }

  private static boolean matches(
      final MagicWandSampleGrid grid,
      final int x,
      final int y,
      final float seedLuminance,
      final int seedRgb,
      final MagicWandMode mode,
      final double tolerance) {
    return switch (mode) {
      case LUMINANCE -> Math.abs(grid.luminanceAt(x, y) - seedLuminance) <= tolerance;
      case COLOR -> colorDistance(grid.rgbAt(x, y), seedRgb) <= tolerance * MAX_COLOR_DISTANCE;
    };
  }

  private static double colorDistance(final int rgbA, final int rgbB) {
    final double redA = ((rgbA >> 16) & 0xFF) / 255.0;
    final double greenA = ((rgbA >> 8) & 0xFF) / 255.0;
    final double blueA = (rgbA & 0xFF) / 255.0;
    final double redB = ((rgbB >> 16) & 0xFF) / 255.0;
    final double greenB = ((rgbB >> 8) & 0xFF) / 255.0;
    final double blueB = (rgbB & 0xFF) / 255.0;
    final double deltaRed = redA - redB;
    final double deltaGreen = greenA - greenB;
    final double deltaBlue = blueA - blueB;
    return Math.sqrt(deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue * deltaBlue);
  }
}
