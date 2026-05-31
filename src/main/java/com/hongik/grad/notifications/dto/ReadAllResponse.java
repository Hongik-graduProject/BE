package com.hongik.grad.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Bulk read-all result — spec §6-3. */
public record ReadAllResponse(@JsonProperty("read_count") int readCount) {}
