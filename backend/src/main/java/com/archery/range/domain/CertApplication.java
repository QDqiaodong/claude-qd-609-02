package com.archery.range.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 弓种能力认证申请。
 *
 * 关键设计：
 * 1. 规则快照 —— ruleCode / versionNo / bowType / distance / minRounds / requiredArrows /
 *    minAverage / validMonths 全部冗余在申请行上。规则后来调整（cert_rule 新增版本）
 *    不会影响本申请与已发认证，历史详情永远看得出「按哪套标准评的」。
 * 2. 证据窗口 —— evidenceFrom / evidenceTo 是教练圈定的明确时间范围，
 *    证据回合明细（含被排除的回合及原因）见 cert_evidence_round。
 * 3. 状态流转 —— PENDING_REVIEW 待复核 → APPROVED / REJECTED / NEED_MORE；
 *    NEED_MORE 补证据后重新评定回 PENDING_REVIEW；APPROVED 之后可能
 *    EXPIRED（过期）/ REVOKED（撤回）/ SUPERSEDED（同范围重新评定通过）。
 * 4. 并发防双结论 —— {@code rowVersion} 为 JPA 乐观锁（@Version）。
 *    两个教练同时打开同一申请并各自决定时，只有一次提交能成功，后到者收到 409 冲突。
 */
@Entity
@Table(name = "cert_application")
public class CertApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cert_no", nullable = false, unique = true, length = 24)
    private String certNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    // ---------------- 规则快照（申请时冻结，事后规则调整不影响） ----------------
    @Column(name = "rule_code", nullable = false, length = 24)
    private String ruleCode;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "bow_type", nullable = false, length = 16)
    private String bowType;

    @Column(nullable = false)
    private Integer distance;

    @Column(name = "min_rounds", nullable = false)
    private Integer minRounds;

    @Column(name = "required_arrows", nullable = false)
    private Integer requiredArrows;

    @Column(name = "min_average", nullable = false, precision = 5, scale = 2)
    private BigDecimal minAverage;

    @Column(name = "valid_months", nullable = false)
    private Integer validMonths;
    // ---------------------------------------------------------------------------

    /** 证据窗口起（含当天） */
    @Column(name = "evidence_from", nullable = false)
    private LocalDate evidenceFrom;

    /** 证据窗口止（含当天） */
    @Column(name = "evidence_to", nullable = false)
    private LocalDate evidenceTo;

    /** 采纳的证据回合数 */
    @Column(name = "evidence_rounds", nullable = false)
    private Integer evidenceRounds;

    /** 采纳的证据总箭数 */
    @Column(name = "evidence_arrows", nullable = false)
    private Integer evidenceArrows;

    /** 采纳证据的加权平均环 */
    @Column(name = "evidence_average", nullable = false, precision = 5, scale = 2)
    private BigDecimal evidenceAverage;

    /** 系统初判：MEETS_STANDARD 达标待复核 / BELOW_STANDARD 未达标 */
    @Column(name = "system_result", nullable = false, length = 16)
    private String systemResult;

    /** PENDING_REVIEW / APPROVED / REJECTED / NEED_MORE / EXPIRED / REVOKED / SUPERSEDED */
    @Column(nullable = false, length = 16)
    private String status;

    /** 补充证据重新评定的轮次，首次为 1 */
    @Column(nullable = false)
    private Integer revision;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_by", length = 32)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** 有效期起（通过日） */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** 有效期止 */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    /** 乐观锁：并发决定同一申请时只有一次更新成功 */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getBowType() {
        return bowType;
    }

    public void setBowType(String bowType) {
        this.bowType = bowType;
    }

    public Integer getDistance() {
        return distance;
    }

    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    public Integer getMinRounds() {
        return minRounds;
    }

    public void setMinRounds(Integer minRounds) {
        this.minRounds = minRounds;
    }

    public Integer getRequiredArrows() {
        return requiredArrows;
    }

    public void setRequiredArrows(Integer requiredArrows) {
        this.requiredArrows = requiredArrows;
    }

    public BigDecimal getMinAverage() {
        return minAverage;
    }

    public void setMinAverage(BigDecimal minAverage) {
        this.minAverage = minAverage;
    }

    public Integer getValidMonths() {
        return validMonths;
    }

    public void setValidMonths(Integer validMonths) {
        this.validMonths = validMonths;
    }

    public LocalDate getEvidenceFrom() {
        return evidenceFrom;
    }

    public void setEvidenceFrom(LocalDate evidenceFrom) {
        this.evidenceFrom = evidenceFrom;
    }

    public LocalDate getEvidenceTo() {
        return evidenceTo;
    }

    public void setEvidenceTo(LocalDate evidenceTo) {
        this.evidenceTo = evidenceTo;
    }

    public Integer getEvidenceRounds() {
        return evidenceRounds;
    }

    public void setEvidenceRounds(Integer evidenceRounds) {
        this.evidenceRounds = evidenceRounds;
    }

    public Integer getEvidenceArrows() {
        return evidenceArrows;
    }

    public void setEvidenceArrows(Integer evidenceArrows) {
        this.evidenceArrows = evidenceArrows;
    }

    public BigDecimal getEvidenceAverage() {
        return evidenceAverage;
    }

    public void setEvidenceAverage(BigDecimal evidenceAverage) {
        this.evidenceAverage = evidenceAverage;
    }

    public String getSystemResult() {
        return systemResult;
    }

    public void setSystemResult(String systemResult) {
        this.systemResult = systemResult;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public Integer getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Integer rowVersion) {
        this.rowVersion = rowVersion;
    }
}
