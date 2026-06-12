package com.igormaznitsa.annotator.exporters.boundingyolo;

import java.util.List;

record YoloDatasetSplit(List<YoloImageSample> train, List<YoloImageSample> validation) {

  YoloDatasetSplit {
    train = List.copyOf(train);
    validation = List.copyOf(validation);
  }
}
