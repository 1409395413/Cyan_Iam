package com.yuchen.portfolio.service;

import java.time.Instant;

/** 后台快照列表用的摘要。 */
public record RevisionItem(Long id, Instant createdAt, long bytes, int moduleCount) {}
