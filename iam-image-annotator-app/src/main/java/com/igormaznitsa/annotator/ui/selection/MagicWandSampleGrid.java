package com.igormaznitsa.annotator.ui.selection;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Per-image luminance and RGB samples built once for fast repeated wand picks / previews.
 */
public final class MagicWandSampleGrid {

  private final int width;
  private final int height;
  private final float[] luminance;
  private final int[] rgb;

  private MagicWandSampleGrid(
      final int width,
      final int height,
      final float[] luminance,
      final int[] rgb) {
    this.width = width;
    this.height = height;
    this.luminance = luminance;
    this.rgb = rgb;
  }

  public static MagicWandSampleGrid from(final BufferedImage image) {
    Objects.requireNonNull(image, "image");
    final int width = image.getWidth();
    final int height = image.getHeight();
    final float[] luminance = new float[width * height];
    final int[] rgb = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        final int pixel = image.getRGB(x, y);
        final int index = index(width, x, y);
        rgb[index] = pixel & 0xFFFFFF;
        final int red = (pixel >> 16) & 0xFF;
        final int green = (pixel >> 8) & 0xFF;
        final int blue = pixel & 0xFF;
        luminance[index] = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f;
      }
    }
    return new MagicWandSampleGrid(width, height, luminance, rgb);
  }

  private static int index(final int width, final int x, final int y) {
    return y * width + x;
  }

  public int width() {
    return this.width;
  }

  public int height() {
    return this.height;
  }

  public float luminanceAt(final int x, final int y) {
    return this.luminance[index(this.width, x, y)];
  }

  public int rgbAt(final int x, final int y) {
    return this.rgb[index(this.width, x, y)];
  }
}
