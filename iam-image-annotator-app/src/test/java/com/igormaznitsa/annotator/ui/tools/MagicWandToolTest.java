package com.igormaznitsa.annotator.ui.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.ui.editor.EditorDraft;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MagicWandToolTest {

  @Test
  void cancelActiveDrawingDelegatesToMagicWand() {
    final AtomicBoolean selectActivated = new AtomicBoolean();
    final ImageCanvas canvas = this.canvas(selectActivated);
    final MagicWandTool tool = new MagicWandTool();
    canvas.setActiveEditTool(tool);

    canvas.cancelActiveDrawing();

    assertTrue(selectActivated.get());
  }

  @Test
  void escapeCancellationActivatesSelectTool() {
    final AtomicBoolean selectActivated = new AtomicBoolean();
    final ImageCanvas canvas = this.canvas(selectActivated);

    assertTrue(new MagicWandTool().cancelDrawing(canvas));

    assertTrue(selectActivated.get());
  }

  @Test
  void escapeCancellationClearsLivePreviewDraft() {
    final ImageCanvas canvas = this.canvas(new AtomicBoolean());
    canvas.setDraft(new EditorDraft.Polyline(List.of(
        NormPoint.of(0.1, 0.1),
        NormPoint.of(0.9, 0.1),
        NormPoint.of(0.5, 0.9)), true));

    assertTrue(new MagicWandTool().cancelDrawing(canvas));

    assertFalse(canvas.hasDraft());
  }

  private ImageCanvas canvas(final AtomicBoolean selectActivated) {
    final BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
    final ImageCanvas canvas = new ImageCanvas(
        EditorSession.fromImage(Path.of("image.png"), image),
        status -> {
        },
        () -> {
        });
    canvas.setSelectToolActivator(() -> selectActivated.set(true));
    return canvas;
  }
}
