package com.igormaznitsa.annotator.ui.icons;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IconServiceTest {

  private static final List<String> REQUIRED_ICONS = List.of(
      "cursor.png",
      "chart_curve_delete.png",
      "wand.png",
      "lock.png",
      "lock_open.png",
      "shape_square.png",
      "box_resize.png",
      "draw_polygon.png",
      "layer_arrange.png",
      "chart_curve_add.png",
      "folder_explorer.png",
      "cross.png",
      "layer_export.png",
      "folder_edit.png",
      "box_open.png",
      "folder.png",
      "image.png",
      "delete.png",
      "grid.png",
      "zoom_out.png",
      "zoom_in.png");

  @Test
  void bundledIconsAreOnClasspath() {
    final IconService icons = new IconService();
    for (final String fileName : REQUIRED_ICONS) {
      assertTrue(icons.exists(fileName), "missing resource: icons/" + fileName);
      assertNotNull(icons.icon16(fileName));
    }
  }
}
