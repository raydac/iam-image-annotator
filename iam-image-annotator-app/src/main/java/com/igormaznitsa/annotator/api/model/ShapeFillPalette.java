package com.igormaznitsa.annotator.api.model;

import java.util.List;

/**
 * Seven distinct fill colors assigned in order when new shapes are created.
 */
public final class ShapeFillPalette {

  private static final List<String> COLORS = List.of(
      "#E53935",
      "#FB8C00",
      "#FDD835",
      "#43A047",
      "#00ACC1",
      "#1E88E5",
      "#8E24AA");

  private ShapeFillPalette() {
  }

  public static int size() {
    return COLORS.size();
  }

  public static String colorAt(final int index) {
    return COLORS.get(Math.floorMod(index, COLORS.size()));
  }
}
