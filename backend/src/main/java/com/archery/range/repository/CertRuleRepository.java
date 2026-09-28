package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.CertRule;

import jakarta.persistence.LockModeType;

public interface CertRuleRepository extends JpaRepository<CertRule, Long> {

    List<CertRule> findAllByOrderByIdAsc();

    List<CertRule> findByStatusOrderByIdAsc(String status);

    List<CertRule> findByRuleCodeOrderByVersionNoAsc(String ruleCode);

    Optional<CertRule> findFirstByBowTypeAndDistanceAndStatusOrderByVersionNoDesc(String bowType, Integer distance, String status);

    /** 悲观写锁：发布新版本、作废旧版本时串行化 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CertRule r where r.id = :id")
    Optional<CertRule> findByIdForUpdate(@Param("id") Long id);
}
