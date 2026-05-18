package com.igormaznitsa.annotator.api.model;

import java.awt.Color;

/**
 * Derives a high-contrast border color from a shape fill color.
 */
public final class AnnotationColors {

  private static final String DARK_STROKE = "#141414";
  private static final String LIGHT_STROKE = "#F5F5F5";
  private static final double LUMINANCE_THRESHOLD = 0.45;

  private AnnotationColors() {
  }

  public static String contrastStrokeForFill(final String fillColorHex) {
    return relativeLuminance(fillColorHex) > LUMINANCE_THRESHOLD ? DARK_STROKE : LIGHT_STROKE;
  }

  public static Color contrastStrokeColor(final String fillColorHex) {
    return Color.decode(contrastStrokeForFill(fillColorHex));
  }

  public static double relativeLuminance(final String colorHex) {
    final Color color = Color.decode(ClassNames.normalizeColor(colorHex));
    return relativeLuminance(color.getRed(), color.getGreen(), color.getBlue());
  }

  private static double relativeLuminance(final int red, final int green, final int blue) {
    final double r = srgbToLinear(red / 255.0);
    final double g = srgbToLinear(green / 255.0);
    final double b = srgbToLinear(blue / 255.0);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double srgbToLinear(final double channel) {
    return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
  }
}
