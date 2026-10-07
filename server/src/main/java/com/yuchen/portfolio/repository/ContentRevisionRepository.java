package com.yuchen.portfolio.repository;

import com.yuchen.portfolio.entity.ContentRevision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRevisionRepository extends JpaRepository<ContentRevision, Long> {

  /** 按时间倒序取最近若干份快照，后台可据此回滚。 */
  List<ContentRevision> findTop20ByOrderByCreatedAtDesc();
}
