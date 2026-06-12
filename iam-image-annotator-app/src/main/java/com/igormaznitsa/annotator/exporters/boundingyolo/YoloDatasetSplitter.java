package com.igormaznitsa.annotator.exporters.boundingyolo;

import static java.util.Comparator.comparingInt;

import com.igormaznitsa.annotator.exporters.common.DisjointSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class YoloDatasetSplitter {

  private static final double TRAIN_RATIO = 0.80d;
  private static final double VALIDATION_RATIO = 0.20d;
  private static final double SIMILARITY_THRESHOLD = 0.86d;
  private static final double VISUAL_WEIGHT = 0.60d;
  private static final double CLASS_WEIGHT = 0.20d;
  private static final double ZONE_WEIGHT = 0.10d;
  private static final double SIZE_WEIGHT = 0.10d;

  YoloDatasetSplit split(final List<YoloImageSample> samples) {
    if (samples.isEmpty()) {
      return new YoloDatasetSplit(List.of(), List.of());
    }

    final List<ClusterStats> clusters = this.sortedClusters(samples);
    final Map<Integer, Integer> totalClassCounts = this.totalClassCounts(samples);
    final SplitStats train = SplitStats.forRatio(samples, TRAIN_RATIO);
    final SplitStats validation = SplitStats.forRatio(samples, VALIDATION_RATIO);
    final List<ClusterStats> trainClusters = new ArrayList<>();
    final List<ClusterStats> validationClusters = new ArrayList<>();

    for (final ClusterStats cluster : clusters) {
      this.assignCluster(cluster, totalClassCounts, train, validation, trainClusters,
          validationClusters);
    }

    this.rebalanceEmptySplits(train, validation, trainClusters, validationClusters);
    this.ensureClassCoverage(totalClassCounts.keySet(), train, validation, trainClusters,
        validationClusters);
    return new YoloDatasetSplit(this.samplesOf(trainClusters), this.samplesOf(validationClusters));
  }

  private List<ClusterStats> sortedClusters(final List<YoloImageSample> samples) {
    final Map<Integer, Integer> totalClassCounts = this.totalClassCounts(samples);
    return this.buildClusters(samples).stream()
        .sorted(Comparator
            .comparingInt((ClusterStats cluster) -> cluster.rarestClassCount(totalClassCounts))
            .thenComparing(comparingInt(ClusterStats::distinctClassCount).reversed())
            .thenComparing(comparingInt(ClusterStats::objectCount).reversed()))
        .toList();
  }

  private List<ClusterStats> buildClusters(final List<YoloImageSample> samples) {
    final DisjointSet disjointSet = new DisjointSet(samples.size());
    for (int left = 0; left < samples.size(); left++) {
      for (int right = left + 1; right < samples.size(); right++) {
        if (this.totalSimilarity(samples.get(left), samples.get(right)) >= SIMILARITY_THRESHOLD) {
          disjointSet.union(left, right);
        }
      }
    }

    final Map<Integer, List<YoloImageSample>> grouped = new HashMap<>();
    for (int i = 0; i < samples.size(); i++) {
      grouped.computeIfAbsent(disjointSet.find(i), ignored -> new ArrayList<>())
          .add(samples.get(i));
    }
    return grouped.values().stream().map(ClusterStats::new).toList();
  }

  private double totalSimilarity(final YoloImageSample left, final YoloImageSample right) {
    return VISUAL_WEIGHT * this.visualSimilarity(left, right)
        + CLASS_WEIGHT * this.jaccard(left.classesPresent(), right.classesPresent())
        + ZONE_WEIGHT * this.cosine(left.zoneDistribution(), right.zoneDistribution())
        + SIZE_WEIGHT * this.cosine(left.bboxSizeDistribution(), right.bboxSizeDistribution());
  }

  private double visualSimilarity(final YoloImageSample left, final YoloImageSample right) {
    return 1.0d - Long.bitCount(left.pHash() ^ right.pHash()) / 63.0d;
  }

  private double jaccard(final Set<Integer> left, final Set<Integer> right) {
    if (left.isEmpty() && right.isEmpty()) {
      return 1.0d;
    }
    final Set<Integer> union = new HashSet<>(left);
    union.addAll(right);
    final Set<Integer> intersection = new HashSet<>(left);
    intersection.retainAll(right);
    return (double) intersection.size() / union.size();
  }

  private double cosine(final Map<? extends Enum<?>, Integer> left,
                        final Map<? extends Enum<?>, Integer> right) {
    if (left.isEmpty() && right.isEmpty()) {
      return 1.0d;
    }
    final Set<Enum<?>> keys = new HashSet<>(left.keySet());
    keys.addAll(right.keySet());
    final double dot = keys.stream().mapToDouble(key -> left.getOrDefault(key, 0)
        * right.getOrDefault(key, 0)).sum();
    final double leftNorm =
        Math.sqrt(left.values().stream().mapToDouble(value -> value * value).sum());
    final double rightNorm =
        Math.sqrt(right.values().stream().mapToDouble(value -> value * value).sum());
    return leftNorm == 0.0d || rightNorm == 0.0d ? 0.0d : dot / (leftNorm * rightNorm);
  }

  private void assignCluster(final ClusterStats cluster,
                             final Map<Integer, Integer> totalClassCounts,
                             final SplitStats train,
                             final SplitStats validation,
                             final List<ClusterStats> trainClusters,
                             final List<ClusterStats> validationClusters) {
    if (this.benefit(cluster, train, totalClassCounts) >= this.benefit(
        cluster, validation, totalClassCounts)) {
      train.add(cluster);
      trainClusters.add(cluster);
      return;
    }
    validation.add(cluster);
    validationClusters.add(cluster);
  }

  private double benefit(final ClusterStats cluster, final SplitStats split,
                         final Map<Integer, Integer> totalClassCounts) {
    double score = -Math.abs(split.targetImages - split.imageCount - cluster.imageCount) * 4.0d;
    if (split.imageCount + cluster.imageCount > split.targetImages) {
      score -= (split.imageCount + cluster.imageCount - split.targetImages) * 8.0d;
    }

    for (final Map.Entry<Integer, Integer> entry : cluster.classCounts.entrySet()) {
      final double rarityWeight = 1.0d / totalClassCounts.get(entry.getKey());
      final int target = split.targetClassCounts.getOrDefault(entry.getKey(), 0);
      final int current = split.classCounts.getOrDefault(entry.getKey(), 0);
      score += Math.clamp(target - current, 0, entry.getValue()) * rarityWeight * 40.0d;
      score -= Math.max(0, current + entry.getValue() - target) * rarityWeight * 8.0d;
      if (current == 0) {
        score += rarityWeight * 20.0d;
      }
    }

    score += this.distributionBenefit(cluster.zoneDistribution, split.zoneCounts,
        split.targetZoneCounts);
    score += this.distributionBenefit(cluster.sizeDistribution, split.sizeCounts,
        split.targetSizeCounts);
    return score;
  }

  private <E extends Enum<E>> double distributionBenefit(
      final Map<E, Integer> cluster,
      final Map<E, Integer> current,
      final Map<E, Integer> target) {
    return cluster.entrySet().stream()
        .mapToDouble(entry -> Math.clamp(target.getOrDefault(entry.getKey(), 0)
                - current.getOrDefault(entry.getKey(), 0), 0,
            entry.getValue()))
        .sum();
  }

  private void rebalanceEmptySplits(final SplitStats train,
                                    final SplitStats validation,
                                    final List<ClusterStats> trainClusters,
                                    final List<ClusterStats> validationClusters) {
    if (validationClusters.isEmpty() && trainClusters.size() > 1) {
      this.moveSmallestCluster(train, validation, trainClusters, validationClusters);
    }
    if (trainClusters.isEmpty() && validationClusters.size() > 1) {
      this.moveSmallestCluster(validation, train, validationClusters, trainClusters);
    }
  }

  private void ensureClassCoverage(final Set<Integer> classes,
                                   final SplitStats train,
                                   final SplitStats validation,
                                   final List<ClusterStats> trainClusters,
                                   final List<ClusterStats> validationClusters) {
    for (final Integer classId : classes) {
      if (!validation.classCounts.containsKey(classId)
          && train.classCounts.getOrDefault(classId, 0) > 1) {
        this.moveClassCluster(classId, train, validation, trainClusters, validationClusters);
      }
      if (!train.classCounts.containsKey(classId)
          && validation.classCounts.getOrDefault(classId, 0) > 1) {
        this.moveClassCluster(classId, validation, train, validationClusters, trainClusters);
      }
    }
  }

  private void moveSmallestCluster(final SplitStats source,
                                   final SplitStats target,
                                   final List<ClusterStats> sourceClusters,
                                   final List<ClusterStats> targetClusters) {
    final ClusterStats cluster = sourceClusters.stream()
        .min(Comparator.comparingInt(ClusterStats::imageCount))
        .orElseThrow();
    source.remove(cluster);
    target.add(cluster);
    sourceClusters.remove(cluster);
    targetClusters.add(cluster);
  }

  private void moveClassCluster(final int classId,
                                final SplitStats source,
                                final SplitStats target,
                                final List<ClusterStats> sourceClusters,
                                final List<ClusterStats> targetClusters) {
    sourceClusters.stream()
        .filter(cluster -> cluster.classCounts.containsKey(classId))
        .filter(cluster -> source.classCounts.getOrDefault(classId, 0)
            - cluster.classCounts.getOrDefault(classId, 0) > 0)
        .min(Comparator.comparingInt(ClusterStats::imageCount))
        .ifPresent(cluster -> {
          source.remove(cluster);
          target.add(cluster);
          sourceClusters.remove(cluster);
          targetClusters.add(cluster);
        });
  }

  private Map<Integer, Integer> totalClassCounts(final List<YoloImageSample> samples) {
    return samples.stream()
        .flatMap(sample -> sample.boxes().stream())
        .collect(Collectors.toMap(YoloBoundingBox::classId, ignored -> 1, Integer::sum));
  }

  private List<YoloImageSample> samplesOf(final List<ClusterStats> clusters) {
    return clusters.stream().flatMap(cluster -> cluster.samples.stream()).toList();
  }

  private static final class SplitStats {

    private final int targetImages;
    private final Map<Integer, Integer> targetClassCounts;
    private final Map<ImageZone, Integer> targetZoneCounts;
    private final Map<BoundingBoxSize, Integer> targetSizeCounts;
    private final Map<Integer, Integer> classCounts = new HashMap<>();
    private final Map<ImageZone, Integer> zoneCounts = new EnumMap<>(ImageZone.class);
    private final Map<BoundingBoxSize, Integer> sizeCounts = new EnumMap<>(BoundingBoxSize.class);
    private int imageCount;

    private SplitStats(final List<YoloImageSample> samples, final double ratio) {
      this.targetImages = Math.max(1, (int) Math.round(samples.size() * ratio));
      this.targetClassCounts = targetCounts(samples, ratio, YoloImageSample::classCounts);
      this.targetZoneCounts = targetCounts(samples, ratio, YoloImageSample::zoneDistribution);
      this.targetSizeCounts = targetCounts(samples, ratio, YoloImageSample::bboxSizeDistribution);
    }

    static SplitStats forRatio(final List<YoloImageSample> samples, final double ratio) {
      return new SplitStats(samples, ratio);
    }

    private static <K> void addAll(final Map<K, Integer> target, final Map<K, Integer> source) {
      source.forEach((key, value) -> target.merge(key, value, Integer::sum));
    }

    private static <K> void subtractAll(final Map<K, Integer> target,
                                        final Map<K, Integer> source) {
      source.forEach((key, value) -> target.computeIfPresent(
          key,
          (ignored, current) -> current > value ? current - value : null));
    }

    private static <K> Map<K, Integer> targetCounts(
        final List<YoloImageSample> samples,
        final double ratio,
        final java.util.function.Function<YoloImageSample, Map<K, Integer>> extractor) {
      final Map<K, Integer> result = new HashMap<>();
      samples.stream().map(extractor).forEach(map -> addAll(result, map));
      result.replaceAll((ignored, value) -> Math.max(1, (int) Math.round(value * ratio)));
      return result;
    }

    void add(final ClusterStats cluster) {
      this.imageCount += cluster.imageCount;
      addAll(this.classCounts, cluster.classCounts);
      addAll(this.zoneCounts, cluster.zoneDistribution);
      addAll(this.sizeCounts, cluster.sizeDistribution);
    }

    void remove(final ClusterStats cluster) {
      this.imageCount -= cluster.imageCount;
      subtractAll(this.classCounts, cluster.classCounts);
      subtractAll(this.zoneCounts, cluster.zoneDistribution);
      subtractAll(this.sizeCounts, cluster.sizeDistribution);
    }
  }

  private static final class ClusterStats {

    private final List<YoloImageSample> samples;
    private final int imageCount;
    private final int objectCount;
    private final Map<Integer, Integer> classCounts;
    private final Map<ImageZone, Integer> zoneDistribution;
    private final Map<BoundingBoxSize, Integer> sizeDistribution;

    private ClusterStats(final List<YoloImageSample> samples) {
      this.samples = List.copyOf(samples);
      this.imageCount = samples.size();
      this.objectCount = samples.stream().mapToInt(YoloImageSample::objectCount).sum();
      this.classCounts = aggregate(samples, YoloImageSample::classCounts);
      this.zoneDistribution = aggregate(samples, YoloImageSample::zoneDistribution);
      this.sizeDistribution = aggregate(samples, YoloImageSample::bboxSizeDistribution);
    }

    private static <K> Map<K, Integer> aggregate(
        final List<YoloImageSample> samples,
        final java.util.function.Function<YoloImageSample, Map<K, Integer>> extractor) {
      final Map<K, Integer> result = new HashMap<>();
      samples.stream().map(extractor).forEach(map -> SplitStats.addAll(result, map));
      return result;
    }

    int imageCount() {
      return this.imageCount;
    }

    int objectCount() {
      return this.objectCount;
    }

    int distinctClassCount() {
      return this.classCounts.size();
    }

    int rarestClassCount(final Map<Integer, Integer> totalClassCounts) {
      return this.classCounts.keySet().stream()
          .mapToInt(classId -> totalClassCounts.getOrDefault(classId, Integer.MAX_VALUE))
          .min()
          .orElse(Integer.MAX_VALUE);
    }
  }
}
