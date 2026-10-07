package com.yuchen.portfolio.repository;

import com.yuchen.portfolio.entity.MediaAsset;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

  Optional<MediaAsset> findTopByChecksum(String checksum);

  Optional<MediaAsset> findByPath(String path);

  List<MediaAsset> findTop100ByOrderByCreatedAtDesc();
}
