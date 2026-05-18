package com.igormaznitsa.annotator.ui.selection;

import java.util.ArrayDeque;
import java.util.BitSet;

/**
 * Binary pixel mask produced by magic-wand flood fill (one bit per image pixel).
 */
public final class MagicWandMask {

  private static final int MIN_PIXELS = 16;

  private final int width;
  private final int height;
  private final BitSet pixels;

  MagicWandMask(final int width, final int height, final BitSet pixels) {
    this.width = width;
    this.height = height;
    this.pixels = pixels;
  }

  private static BitSet connectedComponentContaining(
      final BitSet mask,
      final int width,
      final int height,
      final int seedX,
      final int seedY) {
    final BitSet component = new BitSet(width * height);
    if (!isInside(width, height, seedX, seedY) || !mask.get(index(width, seedX, seedY))) {
      return component;
    }
    final ArrayDeque<Integer> queue = new ArrayDeque<>();
    final int seedIndex = index(width, seedX, seedY);
    queue.add(seedIndex);
    component.set(seedIndex);
    while (!queue.isEmpty()) {
      final int current = queue.poll();
      final int x = current % width;
      final int y = current / width;
      visitMaskPixel(mask, width, height, component, queue, x + 1, y);
      visitMaskPixel(mask, width, height, component, queue, x - 1, y);
      visitMaskPixel(mask, width, height, component, queue, x, y + 1);
      visitMaskPixel(mask, width, height, component, queue, x, y - 1);
    }
    return component;
  }

  private static void visitMaskPixel(
      final BitSet mask,
      final int width,
      final int height,
      final BitSet component,
      final ArrayDeque<Integer> queue,
      final int x,
      final int y) {
    if (!isInside(width, height, x, y)) {
      return;
    }
    final int neighbor = index(width, x, y);
    if (!mask.get(neighbor) || component.get(neighbor)) {
      return;
    }
    component.set(neighbor);
    queue.add(neighbor);
  }

  private static BitSet erodeCardinal(final BitSet region, final int width, final int height) {
    final BitSet eroded = new BitSet(width * height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        final int pixel = index(width, x, y);
        if (!region.get(pixel)) {
          continue;
        }
        if (hasMaskNeighbor(region, width, height, x, y, -1, 0)
            && hasMaskNeighbor(region, width, height, x, y, 1, 0)
            && hasMaskNeighbor(region, width, height, x, y, 0, -1)
            && hasMaskNeighbor(region, width, height, x, y, 0, 1)) {
          eroded.set(pixel);
        }
      }
    }
    return eroded;
  }

  private static BitSet dilateCardinal(final BitSet region, final int width, final int height) {
    final BitSet dilated = new BitSet(width * height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (!region.get(index(width, x, y))) {
          continue;
        }
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) {
              continue;
            }
            final int neighborX = x + dx;
            final int neighborY = y + dy;
            if (isInside(width, height, neighborX, neighborY)) {
              dilated.set(index(width, neighborX, neighborY));
            }
          }
        }
        dilated.set(index(width, x, y));
      }
    }
    return dilated;
  }

  private static boolean hasMaskNeighbor(
      final BitSet region,
      final int width,
      final int height,
      final int x,
      final int y,
      final int deltaX,
      final int deltaY) {
    final int neighborX = x + deltaX;
    final int neighborY = y + deltaY;
    return isInside(width, height, neighborX, neighborY)
        && region.get(index(width, neighborX, neighborY));
  }

  private static BitSet intersect(final BitSet left, final BitSet right) {
    final BitSet both = (BitSet) left.clone();
    both.and(right);
    return both;
  }

  static int index(final int width, final int x, final int y) {
    return y * width + x;
  }

  static boolean isInside(final int width, final int height, final int x, final int y) {
    return x >= 0 && y >= 0 && x < width && y < height;
  }

  public int width() {
    return this.width;
  }

  public int height() {
    return this.height;
  }

  public int pixelCount() {
    return this.pixels.cardinality();
  }

  public boolean contains(final int x, final int y) {
    return isInside(this.width, this.height, x, y) && this.pixels.get(index(this.width, x, y));
  }

  BitSet pixels() {
    return this.pixels;
  }

  /**
   * Removes spurious 1–2 px bridges while keeping the connected component under the seed.
   */
  MagicWandMask withoutThinBridges(final int seedX, final int seedY) {
    final BitSet eroded = erodeCardinal(this.pixels, this.width, this.height);
    final BitSet core = connectedComponentContaining(eroded, this.width, this.height, seedX, seedY);
    if (core.cardinality() < MIN_PIXELS) {
      return this;
    }
    if (core.cardinality() * 3 >= this.pixels.cardinality()) {
      return this;
    }
    return new MagicWandMask(this.width, this.height,
        intersect(dilateCardinal(core, this.width, this.height), this.pixels));
  }

  boolean hasMinimumSize() {
    return this.pixelCount() >= MIN_PIXELS;
  }
}
