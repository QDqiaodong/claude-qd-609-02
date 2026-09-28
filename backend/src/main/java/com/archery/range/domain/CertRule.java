package com.archery.range.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 弓种能力认证规则（带版本）。
 *
 * 同一 {@code ruleCode}（如 RC-18）每次调整都新增一行、{@code versionNo} 递增，
 * 旧版置为 SUPERSEDED —— 历史认证在 cert_application 里冗余了规则快照，
 * 不回查本表，因此规则调整绝不会改写已发出的认证。
 */
@Entity
@Table(name = "cert_rule")
public class CertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则代码：同一弓种射距不同版本共用，如 RC-18 / CP-30 */
    @Column(name = "rule_code", nullable = false, length = 24)
    private String ruleCode;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "bow_type", nullable = false, length = 16)
    private String bowType;

    @Column(nullable = false)
    private Integer distance;

    /** 证据窗口内最少已完成回合数 */
    @Column(name = "min_rounds", nullable = false)
    private Integer minRounds;

    /** 要求的总箭支数 */
    @Column(name = "required_arrows", nullable = false)
    private Integer requiredArrows;

    /** 通过线：加权平均环最低值 */
    @Column(name = "min_average", nullable = false, precision = 5, scale = 2)
    private BigDecimal minAverage;

    /** 通过后有效期（月） */
    @Column(name = "valid_months", nullable = false)
    private Integer validMonths;

    /** ACTIVE 当前版本 / SUPERSEDED 已被新版本取代 */
    @Column(nullable = false, length = 12)
    private String status;

    @Column(length = 200)
    private String remark;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
}
