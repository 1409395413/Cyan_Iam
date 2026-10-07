package com.yuchen.portfolio.repository;

import com.yuchen.portfolio.entity.Inquiry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

  List<Inquiry> findTop200ByOrderByCreatedAtDesc();

  long countByStatus(String status);
}
