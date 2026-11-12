package com.hongik.grad.timemachine.domain;

public enum ReplayStatus {
  /** 기준일 이후 60 거래일이 아직 지나지 않았다. */
  PENDING,
  /** |expected_car| < 0.5 — 모델이 사실상 방향을 말하지 않았다. */
  NEUTRAL,
  /** 시세가 없거나 창 안에서 거래가 멈췄다. */
  INSUFFICIENT_DATA,
  HIT,
  MISS
}
