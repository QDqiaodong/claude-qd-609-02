package com.archery.range.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.CertEvidenceRound;

public interface CertEvidenceRoundRepository extends JpaRepository<CertEvidenceRound, Long> {

    List<CertEvidenceRound> findByApplicationIdOrderByIdAsc(Long applicationId);

    /** 重新评定时清空旧证据：先删后插，配合 flush 保证同事务内重建顺序正确 */
    @Modifying
    @Query("delete from CertEvidenceRound e where e.applicationId = :applicationId")
    void deleteByApplicationId(@Param("applicationId") Long applicationId);
}
