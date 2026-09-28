package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.CertApplication;

import jakarta.persistence.LockModeType;

public interface CertApplicationRepository extends JpaRepository<CertApplication, Long> {

    List<CertApplication> findAllByOrderByIdDesc();

    List<CertApplication> findByMemberIdOrderByIdDesc(Long memberId);

    List<CertApplication> findByStatusOrderByIdDesc(String status);

    /** 会员某弓种当前有效的认证（过期由 valid_until 一并兜底） */
    List<CertApplication> findByMemberIdAndStatusAndBowTypeOrderByValidUntilDesc(
            Long memberId, String status, String bowType);

    boolean existsByCertNo(String certNo);

    long countByStatus(String status);

    /**
     * 悲观写锁：复核决定 / 撤回 / 重新评定先锁住申请行。
     * 与 JPA 乐观锁（row_version）双保险 —— 两个教练同时决定时在此串行，
     * 且同一行的陈旧快照更新会被 row_version 拒绝，保证只有一个最终结论。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CertApplication a where a.id = :id")
    Optional<CertApplication> findByIdForUpdate(@Param("id") Long id);
}
