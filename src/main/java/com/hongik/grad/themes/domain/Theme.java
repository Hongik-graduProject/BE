package com.hongik.grad.themes.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "themes")
public class Theme extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(length = 100)
  private String source;

  @Column(name = "centroid_embedding")
  private byte[] centroidEmbedding;

  @Column(name = "stage1_class_idx")
  private Integer stage1ClassIdx;

  protected Theme() {}

  public Theme(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getSource() {
    return source;
  }

  public byte[] getCentroidEmbedding() {
    return centroidEmbedding;
  }

  public Integer getStage1ClassIdx() {
    return stage1ClassIdx;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public void setStage1ClassIdx(Integer stage1ClassIdx) {
    this.stage1ClassIdx = stage1ClassIdx;
  }
}
