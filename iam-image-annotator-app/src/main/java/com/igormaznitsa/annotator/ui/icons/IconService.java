package com.igormaznitsa.annotator.ui.icons;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.ImageIcon;

public final class IconService {

  public static final int ICON_PX = 16;

  private static final String ICON_ROOT = "/icons/";

  private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();
  private final Map<String, ImageIcon> rawCache = new ConcurrentHashMap<>();

  private static ImageIcon loadIcon(final String fileName) {
    final URL url = IconService.class.getResource(ICON_ROOT + fileName);
    if (url == null) {
      return emptyIcon(ICON_PX);
    }
    return scaleToSquare(new ImageIcon(url), ICON_PX);
  }

  private static ImageIcon scaleToSquare(final ImageIcon source, final int size) {
    if (source.getIconWidth() == size && source.getIconHeight() == size) {
      return source;
    }
    final BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(source.getImage(), 0, 0, size, size, null);
    } finally {
      graphics.dispose();
    }
    return new ImageIcon(target);
  }

  private static ImageIcon emptyIcon(final int size) {
    return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
  }

  public ImageIcon icon16(final String fileName) {
    return this.cache.computeIfAbsent(fileName, IconService::loadIcon);
  }

  private static ImageIcon loadRawIcon(final String fileName) {
    final URL url = IconService.class.getResource(ICON_ROOT + fileName);
    return url == null ? emptyIcon(ICON_PX) : new ImageIcon(url);
  }

  public ImageIcon icon(final String fileName) {
    return this.rawCache.computeIfAbsent(fileName, IconService::loadRawIcon);
  }

  public boolean exists(final String fileName) {
    return IconService.class.getResource(ICON_ROOT + fileName) != null;
  }

  public ImageIcon scaled(final String fileName, final int size) {
    return scaleToSquare(this.icon(fileName), size);
  }
}
