package com.igormaznitsa.annotator.exporters.common;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class ImagePerceptualHash {

  private static final int SAMPLE_SIZE = 32;
  private static final int HASH_SIZE = 8;
  private static final double INV_SQRT_2 = 1.0d / Math.sqrt(2.0d);

  private static double alpha(final int value) {
    return value == 0 ? INV_SQRT_2 : 1.0d;
  }

  public long hash(final BufferedImage image) {
    final double[][] pixels = this.toGrayscaleSamples(image);
    final double[][] coefficients = this.dct(pixels);
    final double median = this.medianLowFrequencies(coefficients);
    long hash = 0L;

    for (int y = 0; y < HASH_SIZE; y++) {
      for (int x = 0; x < HASH_SIZE; x++) {
        if (x != 0 || y != 0) {
          hash <<= 1;
          if (coefficients[y][x] > median) {
            hash |= 1L;
          }
        }
      }
    }
    return hash;
  }

  private double[][] toGrayscaleSamples(final BufferedImage image) {
    final BufferedImage scaled =
        new BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_BYTE_GRAY);
    final Graphics2D graphics = scaled.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(
          image.getScaledInstance(SAMPLE_SIZE, SAMPLE_SIZE, Image.SCALE_AREA_AVERAGING),
          0,
          0,
          null);
    } finally {
      graphics.dispose();
    }

    final double[][] samples = new double[SAMPLE_SIZE][SAMPLE_SIZE];
    for (int y = 0; y < SAMPLE_SIZE; y++) {
      for (int x = 0; x < SAMPLE_SIZE; x++) {
        samples[y][x] = scaled.getRaster().getSample(x, y, 0);
      }
    }
    return samples;
  }

  private double[][] dct(final double[][] pixels) {
    final double[][] result = new double[HASH_SIZE][HASH_SIZE];
    for (int v = 0; v < HASH_SIZE; v++) {
      for (int u = 0; u < HASH_SIZE; u++) {
        result[v][u] = this.coefficient(pixels, u, v);
      }
    }
    return result;
  }

  private double coefficient(final double[][] pixels, final int u, final int v) {
    double sum = 0.0d;
    for (int y = 0; y < SAMPLE_SIZE; y++) {
      for (int x = 0; x < SAMPLE_SIZE; x++) {
        sum += pixels[y][x]
            * Math.cos(((2 * x + 1) * u * Math.PI) / (2.0d * SAMPLE_SIZE))
            * Math.cos(((2 * y + 1) * v * Math.PI) / (2.0d * SAMPLE_SIZE));
      }
    }
    return 0.25d * alpha(u) * alpha(v) * sum;
  }

  private double medianLowFrequencies(final double[][] coefficients) {
    final double[] values = new double[HASH_SIZE * HASH_SIZE - 1];
    int index = 0;
    for (int y = 0; y < HASH_SIZE; y++) {
      for (int x = 0; x < HASH_SIZE; x++) {
        if (x != 0 || y != 0) {
          values[index++] = coefficients[y][x];
        }
      }
    }
    Arrays.sort(values);
    return values[values.length / 2];
  }
}
