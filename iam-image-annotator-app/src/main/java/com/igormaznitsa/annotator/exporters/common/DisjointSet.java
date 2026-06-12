package com.igormaznitsa.annotator.exporters.common;

public final class DisjointSet {

  private final int[] parent;
  private final int[] rank;

  public DisjointSet(final int size) {
    this.parent = new int[size];
    this.rank = new int[size];
    for (int i = 0; i < size; i++) {
      this.parent[i] = i;
    }
  }

  public int find(final int index) {
    if (this.parent[index] != index) {
      this.parent[index] = this.find(this.parent[index]);
    }
    return this.parent[index];
  }

  public void union(final int left, final int right) {
    final int leftRoot = this.find(left);
    final int rightRoot = this.find(right);
    if (leftRoot == rightRoot) {
      return;
    }
    if (this.rank[leftRoot] < this.rank[rightRoot]) {
      this.parent[leftRoot] = rightRoot;
      return;
    }
    this.parent[rightRoot] = leftRoot;
    if (this.rank[leftRoot] == this.rank[rightRoot]) {
      this.rank[leftRoot]++;
    }
  }
}
