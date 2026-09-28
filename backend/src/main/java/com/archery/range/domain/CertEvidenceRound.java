package com.archery.range.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 认证证据回合明细。
 *
 * 教练圈进窗口的每个回合都落一行：{@code included}=1 计入评定；
 * =0 仅登记并写明 {@code excludeReason}（回合未完成 / 弓种不匹配 / 超出证据窗口 /
 * 不属于该会员），绝不把问题回合悄悄算进成绩。
 * 成绩列是申请时刻的快照，回合日后被更正也不影响认证历史。
 */
@Entity
@Table(name = "cert_evidence_round")
public class CertEvidenceRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "round_no", nullable = false, length = 24)
    private String roundNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "bow_type", nullable = false, length = 16)
    private String bowType;

    @Column(nullable = false)
    private Integer distance;

    @Column(name = "round_date", nullable = false)
    private LocalDateTime roundDate;

    @Column(name = "arrow_count", nullable = false)
    private Integer arrowCount;

    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Column(nullable = false)
    private Boolean included;

    /** NOT_SUBMITTED 未完成 / BOW_MISMATCH 弓种不匹配 / OUT_OF_WINDOW 超出窗口 / MEMBER_MISMATCH 非本会员 */
    @Column(name = "exclude_reason", length = 120)
    private String excludeReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getRoundId() {
        return roundId;
    }

    public void setRoundId(Long roundId) {
        this.roundId = roundId;
    }

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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

    public LocalDateTime getRoundDate() {
        return roundDate;
    }

    public void setRoundDate(LocalDateTime roundDate) {
        this.roundDate = roundDate;
    }

    public Integer getArrowCount() {
        return arrowCount;
    }

    public void setArrowCount(Integer arrowCount) {
        this.arrowCount = arrowCount;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Boolean getIncluded() {
        return included;
    }

    public void setIncluded(Boolean included) {
        this.included = included;
    }

    public String getExcludeReason() {
        return excludeReason;
    }

    public void setExcludeReason(String excludeReason) {
        this.excludeReason = excludeReason;
    }
}
