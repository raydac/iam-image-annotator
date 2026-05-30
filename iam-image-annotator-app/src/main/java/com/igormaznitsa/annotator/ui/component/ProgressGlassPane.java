package com.igormaznitsa.annotator.ui.component;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Reusable blocking glass-pane progress indicator with optional stage text.
 */
public final class ProgressGlassPane extends JPanel {

  private static final int FRAME_DELAY_MS = 40;
  private static final int OVERLAY_ALPHA = 128;
  private static final int SPINNER_SIZE = 64;
  private static final int SPINNER_STROKE = 7;
  private static final int ARC_COUNT = 12;
  private static final int TEXT_GAP = 18;
  private static final int TEXT_SHADOW_OFFSET = 2;
  private static final Color OVERLAY_COLOR = new Color(0, 0, 0, OVERLAY_ALPHA);
  private static final Color SPINNER_COLOR = new Color(36, 196, 112);
  private static final Color TEXT_COLOR = SPINNER_COLOR;
  private static final Color TEXT_SHADOW_COLOR = new Color(0, 0, 0, 220);
  private String message = "";
  private int frame;
  private final Timer timer = new Timer(FRAME_DELAY_MS, event -> this.rotate());

  public ProgressGlassPane() {
    this.setOpaque(false);
    this.setVisible(false);
    this.setFocusable(true);
    this.addMouseListener(new MouseAdapter() {
    });
    this.addMouseMotionListener(new MouseAdapter() {
    });
    this.addMouseWheelListener(event -> {
    });
    this.addKeyListener(new KeyAdapter() {
    });
  }

  public void start() {
    this.start("");
  }

  public void start(final String message) {
    this.message = Objects.requireNonNull(message, "message");
    this.frame = 0;
    this.setVisible(true);
    this.requestFocusInWindow();
    this.timer.start();
    this.repaint();
  }

  public void setMessage(final String message) {
    this.message = Objects.requireNonNull(message, "message");
    this.repaint();
  }

  public void clearMessage() {
    this.setMessage("");
  }

  public void stop() {
    this.timer.stop();
    this.clearMessage();
    this.setVisible(false);
  }

  private void rotate() {
    this.frame = (this.frame + 1) % ARC_COUNT;
    this.repaint();
  }

  @Override
  protected void paintComponent(final Graphics graphics) {
    super.paintComponent(graphics);
    final Graphics2D canvas = (Graphics2D) graphics.create();
    try {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      this.paintOverlay(canvas);
      this.paintSpinner(canvas);
      this.paintMessage(canvas);
    } finally {
      canvas.dispose();
    }
  }

  private void paintOverlay(final Graphics2D canvas) {
    canvas.setColor(OVERLAY_COLOR);
    canvas.fillRect(0, 0, this.getWidth(), this.getHeight());
  }

  private void paintSpinner(final Graphics2D canvas) {
    canvas.setStroke(new BasicStroke(SPINNER_STROKE, BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND));

    final int left = (this.getWidth() - SPINNER_SIZE) / 2;
    final int top = (this.getHeight() - SPINNER_SIZE) / 2;
    for (int i = 0; i < ARC_COUNT; i++) {
      final float alpha = (i + 1.0f) / ARC_COUNT;
      canvas.setComposite(AlphaComposite.SrcOver.derive(alpha));
      canvas.setColor(SPINNER_COLOR);
      canvas.drawArc(left, top, SPINNER_SIZE, SPINNER_SIZE,
          90 - ((this.frame + i) % ARC_COUNT) * 360 / ARC_COUNT,
          18);
    }
  }

  private void paintMessage(final Graphics2D canvas) {
    if (this.message.isBlank()) {
      return;
    }
    canvas.setComposite(AlphaComposite.SrcOver);
    final FontMetrics metrics = canvas.getFontMetrics();
    final int x = (this.getWidth() - metrics.stringWidth(this.message)) / 2;
    final int y = (this.getHeight() + SPINNER_SIZE) / 2 + TEXT_GAP + metrics.getAscent();

    canvas.setColor(TEXT_SHADOW_COLOR);
    canvas.drawString(this.message, x + TEXT_SHADOW_OFFSET, y + TEXT_SHADOW_OFFSET);
    canvas.setColor(TEXT_COLOR);
    canvas.drawString(this.message, x, y);
  }
}
