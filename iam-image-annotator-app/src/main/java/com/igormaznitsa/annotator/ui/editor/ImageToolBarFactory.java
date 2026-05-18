package com.igormaznitsa.annotator.ui.editor;

import java.awt.Frame;

@FunctionalInterface
public interface ImageToolBarFactory {

  ImageToolBar create(ImageCanvas canvas, Frame frame);
}
