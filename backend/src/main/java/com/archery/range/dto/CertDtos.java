package com.archery.range.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 弓种能力认证模块的入参与出参。
 */
public final class CertDtos {

    private CertDtos() {
    }

    // ---------------- 规则 ----------------

    /** 发布一版新规则（同一弓种 + 射距已有版本时自动作废旧版） */
    public record RuleSaveReq(
            @NotBlank(message = "请选择弓种") String bowType,
            @NotNull(message = "请选择射距") Integer distance,
            @NotNull(message = "请填写最少回合数") Integer minRounds,
            @NotNull(message = "请填写要求箭支数") Integer requiredArrows,
            @NotNull(message = "请填写通过线（平均环）") BigDecimal minAverage,
            @NotNull(message = "请填写有效期（月）") Integer validMonths,
            @Size(max = 200, message = "规则说明不能超过 200 字") String remark,
            @NotBlank(message = "请填写发布教练姓名") String operator) {
    }

    public record RuleView(
            Long id,
            String ruleCode,
            Integer versionNo,
            String bowType,
            String bowTypeName,
            Integer distance,
            Integer minRounds,
            Integer requiredArrows,
            BigDecimal minAverage,
            Integer validMonths,
            String status,
            String statusName,
            String remark,
            String createdBy,
            LocalDateTime createdAt) {
    }

    // ---------------- 证据窗口预检 / 申请 ----------------

    /** 证据窗口预检 & 发起申请共用：会员 + 规则版本 + 明确时间范围 + 圈定的回合 */
    public record ApplyReq(
            @NotNull(message = "请选择会员") Long memberId,
            @NotNull(message = "请选择认证规则版本") Long ruleId,
            @NotNull(message = "请选择证据窗口开始日期") LocalDate evidenceFrom,
            @NotNull(message = "请选择证据窗口结束日期") LocalDate evidenceTo,
            @NotEmpty(message = "请至少选择一个计分回合作为证据") List<Long> roundIds,
            @NotBlank(message = "请填写教练姓名") String operator) {
    }

    /** 补充证据后重新评定（NEED_MORE → 重新评定），窗口与回合以本次提交为准 */
    public record ResubmitReq(
            @NotNull(message = "请选择证据窗口开始日期") LocalDate evidenceFrom,
            @NotNull(message = "请选择证据窗口结束日期") LocalDate evidenceTo,
            @NotEmpty(message = "请至少选择一个计分回合作为证据") List<Long> roundIds,
            @NotBlank(message = "请填写教练姓名") String operator) {
    }

    /** 复核决定：APPROVE 通过 / REJECT 驳回 / REQUEST_MORE 要求补充证据 */
    public record DecideReq(
            @NotBlank(message = "请选择复核决定") String decision,
            @NotBlank(message = "请填写教练姓名") String operator,
            @Size(max = 500, message = "复核意见不能超过 500 字") String note,
            /** 页面打开详情时看到的乐观锁版本；后到的决定据此得到冲突反馈 */
            Integer expectedVersion) {
    }

    /** 撤回认证 / 重新评定操作人 */
    public record OperatorReq(
            @NotBlank(message = "请填写教练姓名") String operator) {
    }

    /** 证据回合行（计入 + 被排除都返回，排除必须带原因） */
    public record EvidenceView(
            Long roundId,
            String roundNo,
            Long memberId,
            String bowType,
            String bowTypeName,
            Integer distance,
            LocalDateTime roundDate,
            Integer arrowCount,
            Integer totalScore,
            BigDecimal averageScore,
            String status,
            String statusName,
            Boolean included,
            String excludeReason,
            String excludeReasonName) {
    }

    /** 流转轨迹行 */
    public record LogView(
            String action,
            String actionName,
            String operator,
            String role,
            String detail,
            LocalDateTime createdAt) {
    }

    /** 申请列表行（含规则快照与有效期） */
    public record ApplicationSummary(
            Long id,
            String certNo,
            Long memberId,
            String memberName,
            String memberCardNo,
            Long ruleId,
            String ruleCode,
            Integer versionNo,
            String bowType,
            String bowTypeName,
            Integer distance,
            Integer requiredArrows,
            BigDecimal minAverage,
            Integer validMonths,
            LocalDate evidenceFrom,
            LocalDate evidenceTo,
            Integer evidenceRounds,
            Integer evidenceArrows,
            BigDecimal evidenceAverage,
            String systemResult,
            String systemResultName,
            String status,
            String statusName,
            Integer revision,
            String createdBy,
            LocalDateTime createdAt,
            String reviewedBy,
            LocalDateTime reviewedAt,
            LocalDate validFrom,
            LocalDate validUntil,
            Boolean effective,
            Integer rowVersion) {
    }

    /** 申请详情：申请行 + 规则快照 + 证据明细 + 轨迹 */
    public record ApplicationView(
            ApplicationSummary application,
            RuleView rule,
            List<EvidenceView> evidences,
            List<LogView> logs) {
    }

    /** 预检结果：是否全部可采纳 + 逐回合结论 + 汇总（不允许把问题回合悄悄算进去） */
    public record PreviewView(
            Boolean allIncluded,
            Integer includedRounds,
            Integer includedArrows,
            BigDecimal includedAverage,
            Boolean meetsStandard,
            String reasonSummary,
            List<EvidenceView> evidences) {
    }

    /** 会员当前有效认证（会员详情 / 课程 / 箭道识别用） */
    public record MemberCertView(
            Long id,
            String certNo,
            String bowType,
            String bowTypeName,
            Integer distance,
            Integer requiredArrows,
            BigDecimal minAverage,
            LocalDate validFrom,
            LocalDate validUntil,
            String reviewedBy,
            Boolean effective) {
    }
}
