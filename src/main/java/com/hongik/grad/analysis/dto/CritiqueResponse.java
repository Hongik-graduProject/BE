package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 반박 모드 응답. {@code grade} 는 "상"/"중"/"하", {@code score} 는 0~8 점(제안서 §2-3). {@code items} 는 항상 세 가지
 * 구조적 한계를 포함하므로 비지 않는다.
 */
public record CritiqueResponse(
    @JsonProperty("grade") String grade,
    @JsonProperty("score") int score,
    @JsonProperty("items") List<CritiqueItem> items) {}
