package com.hongik.grad.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Optional ticker filter for read-all — spec §6-3. */
public record ReadAllRequest(@JsonProperty("ticker") String ticker) {}
