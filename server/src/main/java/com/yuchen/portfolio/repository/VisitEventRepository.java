package com.yuchen.portfolio.repository;

import com.yuchen.portfolio.entity.VisitEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VisitEventRepository extends JpaRepository<VisitEvent, Long> {

  /** 被点播最多的作品 TOP 10 */
  @Query("select e.target, count(e) from VisitEvent e where e.type = 'play' "
      + "group by e.target order by count(e) desc")
  List<Object[]> topPlayTargets(Pageable pageable);

  long countByTypeAndDay(String type, String day);
}
