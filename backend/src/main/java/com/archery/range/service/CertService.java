package com.archery.range.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archery.range.common.BizException;
import com.archery.range.common.ConflictException;
import com.archery.range.domain.CertApplication;
import com.archery.range.domain.CertEvidenceRound;
import com.archery.range.domain.CertLog;
import com.archery.range.domain.CertRule;
import com.archery.range.domain.Member;
import com.archery.range.domain.RangeDict;
import com.archery.range.domain.Round;
import com.archery.range.dto.CertDtos;
import com.archery.range.repository.CertApplicationRepository;
import com.archery.range.repository.CertEvidenceRoundRepository;
import com.archery.range.repository.CertLogRepository;
import com.archery.range.repository.CertRuleRepository;
import com.archery.range.repository.MemberRepository;
import com.archery.range.repository.RoundRepository;

/**
 * 弓种能力认证。
 *
 * 关键设计：
 * 1. 规则带版本 —— 调整规则只新增一版（旧版 SUPERSEDED），申请时把整版规则快照进申请行，
 *    历史认证永不被改写，详情永远看得出用了哪套标准。
 * 2. 证据窗口 —— 教练圈定明确的起止日期与回合；系统逐回合校验「同会员 / 已完成 /
 *    弓种匹配 / 在窗口内」，不合格的回合仍登记但带原因、不计入，绝不悄悄算进成绩。
 * 3. 状态可恢复 —— 申请、证据、初判、复核结论与轨迹全部持久化，刷新后原样恢复；
 *    过期由定时任务 + 查询兜底双重保证，过期认证不再被当作有效。
 * 4. 并发单结论 —— 复核决定先对申请行加悲观锁，再校验 @Version 乐观锁版本，
 *    两个教练同时决定时只有一次成功，后到者收到 409 冲突反馈，不会出现两条矛盾认证。
 */
@Service
public class CertService {

    private static final DateTimeFormatter NO_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CertRuleRepository ruleRepository;
    private final CertApplicationRepository applicationRepository;
    private final CertEvidenceRoundRepository evidenceRepository;
    private final CertLogRepository logRepository;
    private final RoundRepository roundRepository;
    private final MemberRepository memberRepository;

    public CertService(CertRuleRepository ruleRepository,
            CertApplicationRepository applicationRepository,
            CertEvidenceRoundRepository evidenceRepository,
            CertLogRepository logRepository,
            RoundRepository roundRepository,
            MemberRepository memberRepository) {
        this.ruleRepository = ruleRepository;
        this.applicationRepository = applicationRepository;
        this.evidenceRepository = evidenceRepository;
        this.logRepository = logRepository;
        this.roundRepository = roundRepository;
        this.memberRepository = memberRepository;
    }

    // ================= 规则（带版本） =================

    @Transactional(readOnly = true)
    public List<CertDtos.RuleView> listRules(String status) {
        List<CertRule> rules = (status == null || status.isBlank())
                ? ruleRepository.findAllByOrderByIdAsc()
                : ruleRepository.findByStatusOrderByIdAsc(status);
        return rules.stream().map(this::toRuleView).toList();
    }

    /**
     * 发布一版规则：同一弓种 + 射距已有 ACTIVE 版本时，旧版置 SUPERSEDED，版本号 +1。
     * 新版本只影响此后发起的申请；历史申请保存了自己的规则快照，不受影响。
     */
    @Transactional
    public CertDtos.RuleView publishRule(CertDtos.RuleSaveReq req) {
        String operator = requireName(req.operator(), "教练");
        if (!RangeDict.isValidBowType(req.bowType())) {
            throw new BizException("弓种只能是：反曲弓 / 复合弓 / 传统弓");
        }
        if (!RangeDict.isValidDistance(req.distance())) {
            throw new BizException("射距只能是 10 / 18 / 30 / 50 米");
        }
        if (req.minRounds() == null || req.minRounds() < 1 || req.minRounds() > 20) {
            throw new BizException("最少回合数需在 1 ~ 20 之间");
        }
        if (req.requiredArrows() == null || req.requiredArrows() < 1 || req.requiredArrows() > 240) {
            throw new BizException("要求箭支数需在 1 ~ 240 之间");
        }
        if (req.minAverage() == null
                || req.minAverage().compareTo(BigDecimal.ZERO) < 0
                || req.minAverage().compareTo(BigDecimal.TEN) > 0) {
            throw new BizException("通过线（平均环）需在 0.00 ~ 10.00 之间");
        }
        if (req.validMonths() == null || req.validMonths() < 1 || req.validMonths() > 60) {
            throw new BizException("有效期需在 1 ~ 60 个月之间");
        }

        String ruleCode = RangeDict.bowRulePrefix(req.bowType()) + "-" + req.distance();
        List<CertRule> versions = ruleRepository.findByRuleCodeOrderByVersionNoAsc(ruleCode);
        int nextVersion = versions.stream().mapToInt(CertRule::getVersionNo).max().orElse(0) + 1;
        for (CertRule old : versions) {
            if ("ACTIVE".equals(old.getStatus())) {
                CertRule locked = ruleRepository.findByIdForUpdate(old.getId()).orElse(old);
                locked.setStatus("SUPERSEDED");
                ruleRepository.save(locked);
            }
        }

        CertRule rule = new CertRule();
        rule.setRuleCode(ruleCode);
        rule.setVersionNo(nextVersion);
        rule.setBowType(req.bowType());
        rule.setDistance(req.distance());
        rule.setMinRounds(req.minRounds());
        rule.setRequiredArrows(req.requiredArrows());
        rule.setMinAverage(req.minAverage().setScale(2, RoundingMode.HALF_UP));
        rule.setValidMonths(req.validMonths());
        rule.setStatus("ACTIVE");
        rule.setRemark(req.remark() == null ? "" : req.remark().trim());
        rule.setCreatedBy(operator);
        rule.setCreatedAt(LocalDateTime.now());
        return toRuleView(ruleRepository.save(rule));
    }

    // ================= 证据窗口选回合 / 预检 =================

    /** 教练选回合用：该会员在给定窗口内的全部回合（含未完成，由前端标记不可选） */
    @Transactional(readOnly = true)
    public List<CertDtos.EvidenceView> windowRounds(Long memberId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BizException("请选择证据窗口的起止日期");
        }
        if (to.isBefore(from)) {
            throw new BizException("证据窗口结束日期不能早于开始日期");
        }
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BizException("会员不存在：id=" + memberId));
        List<Round> rounds = roundRepository.findByMemberIdAndStartTimeBetweenOrderByStartTimeAsc(
                memberId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return rounds.stream().map(r -> toEvidenceView(r, true, null)).toList();
    }

    /** 预检：不写库，逐回合给出是否计入与原因，并按规则快照汇总初判。 */
    @Transactional(readOnly = true)
    public CertDtos.PreviewView preview(CertDtos.ApplyReq req) {
        Member member = memberRepository.findById(req.memberId())
                .orElseThrow(() -> new BizException("会员不存在：id=" + req.memberId()));
        CertRule rule = requireRule(req.ruleId());
        return buildPreview(member, rule, req.evidenceFrom(), req.evidenceTo(), req.roundIds());
    }

    // ================= 申请 / 重新评定 =================

    @Transactional
    public CertDtos.ApplicationView apply(CertDtos.ApplyReq req) {
        String operator = requireName(req.operator(), "教练");
        Member member = memberRepository.findById(req.memberId())
                .orElseThrow(() -> new BizException("会员不存在：id=" + req.memberId()));
        CertRule rule = requireRule(req.ruleId());

        CertDtos.PreviewView preview = buildPreview(member, rule,
                req.evidenceFrom(), req.evidenceTo(), req.roundIds());
        // 证据回合必须同会员、已完成、弓种匹配、在窗口内：有一个不满足就要明确说明，不允许带病发起
        if (!preview.allIncluded()) {
            throw new BizException("证据校验未通过：" + preview.reasonSummary());
        }
        if (preview.includedRounds() < rule.getMinRounds()) {
            throw new BizException("证据回合不足：规则 " + rule.getRuleCode() + " v" + rule.getVersionNo()
                    + " 至少需要 " + rule.getMinRounds() + " 个已完成回合，当前仅 " + preview.includedRounds() + " 个");
        }
        if (preview.includedArrows() < rule.getRequiredArrows()) {
            throw new BizException("证据箭支数不足：规则要求累计 " + rule.getRequiredArrows()
                    + " 支，当前仅 " + preview.includedArrows() + " 支");
        }

        LocalDateTime now = LocalDateTime.now();
        CertApplication app = new CertApplication();
        app.setCertNo(nextCertNo());
        app.setMemberId(member.getId());
        snapshotRule(app, rule);
        app.setEvidenceFrom(req.evidenceFrom());
        app.setEvidenceTo(req.evidenceTo());
        app.setEvidenceRounds(preview.includedRounds());
        app.setEvidenceArrows(preview.includedArrows());
        app.setEvidenceAverage(preview.includedAverage());
        app.setSystemResult(preview.meetsStandard() ? "MEETS_STANDARD" : "BELOW_STANDARD");
        app.setStatus("PENDING_REVIEW");
        app.setRevision(1);
        app.setCreatedBy(operator);
        app.setCreatedAt(now);
        app.setRowVersion(0);
        // 先落库拿到申请主键，证据行才能挂上 application_id；
        // 带 @Version 的新实体 save 走 merge，返回的受管实例才有主键，必须接住返回值
        app = applicationRepository.saveAndFlush(app);
        saveEvidences(app.getId(), preview.evidences());
        log(app.getId(), "CREATE", operator, "COACH",
                "发起认证申请：" + RangeDict.bowTypeName(rule.getBowType()) + " " + rule.getDistance()
                        + " 米（" + rule.getRuleCode() + " v" + rule.getVersionNo()
                        + "），证据窗口 " + req.evidenceFrom() + " ~ " + req.evidenceTo()
                        + "，圈定回合 " + preview.evidences().size() + " 个。");
        log(app.getId(), "SYSTEM_RESULT", "认证系统", "SYSTEM",
                "采纳证据回合 " + preview.includedRounds() + " 个、箭 " + preview.includedArrows()
                        + " 支，加权平均 " + preview.includedAverage() + " 环，通过线 "
                        + rule.getMinAverage() + " 环，系统初判「"
                        + RangeDict.certSystemResultName(app.getSystemResult()) + "」，等待教练复核。");
        return detail(app.getId());
    }

    /** 待补证据状态下，教练重新圈定窗口与回合，系统重新评定，回到待复核。 */
    @Transactional
    public CertDtos.ApplicationView resubmit(Long id, CertDtos.ResubmitReq req) {
        String operator = requireName(req.operator(), "教练");
        CertApplication app = applicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("认证申请不存在：id=" + id));
        if (!"NEED_MORE".equals(app.getStatus())) {
            throw new BizException("申请当前为「" + RangeDict.certStatusName(app.getStatus())
                    + "」，只有「待补证据」的申请可以补充后重新评定");
        }
        Member member = memberRepository.findById(app.getMemberId())
                .orElseThrow(() -> new BizException("会员不存在：id=" + app.getMemberId()));
        // 新的评定必须使用当时选定的规则快照：用申请上冻结的参数重算，而不是查最新版规则
        CertDtos.PreviewView preview = buildPreviewWithSnapshot(member, app,
                req.evidenceFrom(), req.evidenceTo(), req.roundIds());
        if (!preview.allIncluded()) {
            throw new BizException("证据校验未通过：" + preview.reasonSummary());
        }
        if (preview.includedRounds() < app.getMinRounds()) {
            throw new BizException("证据回合不足：该规则至少需要 " + app.getMinRounds()
                    + " 个已完成回合，当前仅 " + preview.includedRounds() + " 个");
        }
        if (preview.includedArrows() < app.getRequiredArrows()) {
            throw new BizException("证据箭支数不足：该规则要求累计 " + app.getRequiredArrows()
                    + " 支，当前仅 " + preview.includedArrows() + " 支");
        }

        app.setEvidenceFrom(req.evidenceFrom());
        app.setEvidenceTo(req.evidenceTo());
        app.setEvidenceRounds(preview.includedRounds());
        app.setEvidenceArrows(preview.includedArrows());
        app.setEvidenceAverage(preview.includedAverage());
        app.setSystemResult(preview.meetsStandard() ? "MEETS_STANDARD" : "BELOW_STANDARD");
        app.setStatus("PENDING_REVIEW");
        app.setRevision(app.getRevision() + 1);
        applicationRepository.save(app);

        evidenceRepository.deleteByApplicationId(app.getId());
        evidenceRepository.flush();
        saveEvidences(app.getId(), preview.evidences());
        log(app.getId(), "RESUBMIT", operator, "COACH",
                "第 " + app.getRevision() + " 次评定：重新圈定证据窗口 " + req.evidenceFrom() + " ~ "
                        + req.evidenceTo() + "，采纳回合 " + preview.includedRounds() + " 个、箭 "
                        + preview.includedArrows() + " 支，加权平均 " + preview.includedAverage()
                        + " 环；仍按 " + app.getRuleCode() + " v" + app.getVersionNo()
                        + " 规则快照评定，回到待复核。");
        log(app.getId(), "SYSTEM_RESULT", "认证系统", "SYSTEM",
                "系统重新初判「" + RangeDict.certSystemResultName(app.getSystemResult()) + "」。");
        return detail(app.getId());
    }

    // ================= 复核决定（并发单结论） =================

    /**
     * 复核页决定：通过 / 驳回 / 要求补充证据。
     * 两个教练同时打开同一申请并发决定：悲观锁串行 + 乐观锁版本校验，
     * 只有一次决定能落库，后到者得到明确的 409 冲突反馈。
     */
    @Transactional
    public CertDtos.ApplicationView decide(Long id, CertDtos.DecideReq req) {
        String operator = requireName(req.operator(), "复核教练");
        if (!RangeDict.isValidCertDecision(req.decision())) {
            throw new BizException("复核决定只能是：通过 / 驳回 / 要求补充证据");
        }
        String note = req.note() == null ? "" : req.note().trim();
        if ("REJECT".equals(req.decision()) && note.isEmpty()) {
            throw new BizException("驳回时必须填写原因");
        }
        if ("REQUEST_MORE".equals(req.decision()) && note.isEmpty()) {
            throw new BizException("要求补充证据时必须说明需要补哪些材料");
        }

        CertApplication app = applicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("认证申请不存在：id=" + id));
        if (!"PENDING_REVIEW".equals(app.getStatus())) {
            throw new ConflictException("该认证申请已有最终结论："
                    + RangeDict.certStatusName(app.getStatus())
                    + "（" + (app.getReviewedBy() == null ? "" : app.getReviewedBy())
                    + "）。你提交的「" + RangeDict.certDecisionName(req.decision())
                    + "」未生效，请刷新查看最新结果");
        }
        // 乐观锁：页面带着打开详情时的 row_version 提交，期间被别人决定过则版本对不上
        if (req.expectedVersion() != null && !req.expectedVersion().equals(app.getRowVersion())) {
            throw new ConflictException("申请在你打开后已被更新（当前版本 " + app.getRowVersion()
                    + "，你的页面基于版本 " + req.expectedVersion() + "），请刷新后再决定");
        }

        LocalDateTime now = LocalDateTime.now();
        app.setReviewedBy(operator);
        app.setReviewedAt(now);
        app.setReviewNote(note);

        String action;
        switch (req.decision()) {
            case "APPROVE" -> {
                // 系统未达标时教练仍可破格通过，但必须写明复核依据
                if ("BELOW_STANDARD".equals(app.getSystemResult()) && note.isEmpty()) {
                    throw new BizException("系统初判未达标，若破格通过必须在复核意见中写明依据");
                }
                LocalDate from = now.toLocalDate();
                LocalDate until = from.plusMonths(app.getValidMonths());
                app.setStatus("APPROVED");
                app.setValidFrom(from);
                app.setValidUntil(until);
                action = "APPROVE";
                supersedeSameScope(app, operator);
                log(id, action, operator, "COACH",
                        "复核通过" + (note.isEmpty() ? "" : "：" + note)
                                + "。有效期 " + from + " ~ " + until
                                + "，适用范围：" + RangeDict.bowTypeName(app.getBowType())
                                + " " + app.getDistance() + " 米及以内射距。");
            }
            case "REJECT" -> {
                app.setStatus("REJECTED");
                action = "REJECT";
                log(id, action, operator, "COACH", "复核驳回：" + note);
            }
            default -> {
                app.setStatus("NEED_MORE");
                action = "REQUEST_MORE";
                log(id, action, operator, "COACH", "要求补充证据：" + note
                        + "。申请回到待补证据，教练可重新圈定窗口与回合后重新评定。");
            }
        }
        applicationRepository.saveAndFlush(app);
        return detail(app.getId());
    }

    /**
     * 撤回已通过的认证（教练发现材料不实等）：APPROVED → REVOKED，立即失效，
     * 会员在课程 / 箭道入口的可用状态随之变化。
     */
    @Transactional
    public CertDtos.ApplicationView revoke(Long id, CertDtos.OperatorReq req) {
        String operator = requireName(req.operator(), "教练");
        CertApplication app = applicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("认证申请不存在：id=" + id));
        if (!"APPROVED".equals(app.getStatus())) {
            throw new BizException("只有「已通过」的认证可以撤回，当前为「"
                    + RangeDict.certStatusName(app.getStatus()) + "」");
        }
        app.setStatus("REVOKED");
        applicationRepository.save(app);
        log(id, "REVOKE", operator, "COACH", "认证已撤回并立即失效，该弓种射距不再视为持证。");
        return detail(app.getId());
    }

    // ================= 查询 =================

    @Transactional(readOnly = true)
    public List<CertDtos.ApplicationSummary> list(String status, Long memberId) {
        List<CertApplication> apps;
        if (memberId != null) {
            apps = applicationRepository.findByMemberIdOrderByIdDesc(memberId);
        } else if (status != null && !status.isBlank()) {
            apps = applicationRepository.findByStatusOrderByIdDesc(status);
        } else {
            apps = applicationRepository.findAllByOrderByIdDesc();
        }
        Map<Long, Member> memberMap = loadMembers(apps.stream().map(CertApplication::getMemberId).toList());
        return apps.stream()
                .map(a -> toSummary(a, memberMap.get(a.getMemberId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CertDtos.ApplicationView detail(Long id) {
        CertApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new BizException("认证申请不存在：id=" + id));
        Member member = memberRepository.findById(app.getMemberId()).orElse(null);
        CertDtos.ApplicationSummary summary = toSummary(app, member);
        CertDtos.RuleView rule = ruleRepository.findById(app.getRuleId()).map(this::toRuleView).orElse(null);
        List<CertDtos.EvidenceView> evidences = evidenceRepository.findByApplicationIdOrderByIdAsc(id)
                .stream().map(this::toEvidenceView).toList();
        List<CertDtos.LogView> logs = logRepository.findByApplicationIdOrderByIdAsc(id)
                .stream().map(l -> new CertDtos.LogView(
                        l.getAction(),
                        RangeDict.certActionName(l.getAction()),
                        l.getOperator(),
                        l.getRole(),
                        l.getDetail(),
                        l.getCreatedAt()))
                .toList();
        return new CertDtos.ApplicationView(summary, rule, evidences, logs);
    }

    /**
     * 会员当前有效认证。过期双重兜底：库里已由定时任务收敛为 EXPIRED，
     * 这里对仍挂 APPROVED 但已过 validUntil 的也按失效返回。
     */
    @Transactional(readOnly = true)
    public List<CertDtos.MemberCertView> effectiveCerts(Long memberId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BizException("会员不存在：id=" + memberId));
        LocalDate today = LocalDate.now();
        return applicationRepository.findByMemberIdOrderByIdDesc(memberId).stream()
                .filter(a -> "APPROVED".equals(a.getStatus())
                        && a.getValidUntil() != null
                        && !a.getValidUntil().isBefore(today))
                .map(a -> new CertDtos.MemberCertView(
                        a.getId(),
                        a.getCertNo(),
                        a.getBowType(),
                        RangeDict.bowTypeName(a.getBowType()),
                        a.getDistance(),
                        a.getRequiredArrows(),
                        a.getMinAverage(),
                        a.getValidFrom(),
                        a.getValidUntil(),
                        a.getReviewedBy(),
                        true))
                .toList();
    }

    /**
     * 课程 / 箭道入口识别：该会员当前是否持有覆盖指定弓种与射距的有效认证。
     * 高射距认证覆盖低射距（50 米认证可用于 30 米），过期 / 撤回 / 驳回 / 待复核均不算。
     */
    @Transactional(readOnly = true)
    public boolean covers(Long memberId, String bowType, Integer distance) {
        return findCovering(memberId, bowType, distance).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<CertDtos.MemberCertView> findCovering(Long memberId, String bowType, Integer distance) {
        if (memberId == null || bowType == null || distance == null) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now();
        return applicationRepository.findByMemberIdOrderByIdDesc(memberId).stream()
                .filter(a -> "APPROVED".equals(a.getStatus()))
                .filter(a -> bowType.equals(a.getBowType()))
                .filter(a -> a.getDistance() >= distance)
                .filter(a -> a.getValidUntil() != null && !a.getValidUntil().isBefore(today))
                .map(a -> new CertDtos.MemberCertView(
                        a.getId(), a.getCertNo(), a.getBowType(), RangeDict.bowTypeName(a.getBowType()),
                        a.getDistance(), a.getRequiredArrows(), a.getMinAverage(),
                        a.getValidFrom(), a.getValidUntil(), a.getReviewedBy(), true))
                .findFirst();
    }

    /** 定时收敛过期认证（见 CertExpiryScheduler），返回被收敛的条数 */
    @Transactional
    public int expireOverdue() {
        LocalDate today = LocalDate.now();
        List<CertApplication> toExpire = applicationRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> "APPROVED".equals(a.getStatus())
                        && a.getValidUntil() != null
                        && a.getValidUntil().isBefore(today))
                .toList();
        for (CertApplication app : toExpire) {
            app.setStatus("EXPIRED");
            applicationRepository.save(app);
            log(app.getId(), "EXPIRE", "认证系统", "SYSTEM",
                    "认证已于 " + app.getValidUntil() + " 到期，自动失效；对应弓种射距不再视为持证。");
        }
        return toExpire.size();
    }

    // ================= 证据校验核心 =================

    private CertDtos.PreviewView buildPreview(Member member, CertRule rule,
            LocalDate from, LocalDate to, List<Long> roundIds) {
        validateWindow(from, to);
        List<Long> ids = distinct(roundIds);
        Map<Long, Round> roundMap = roundRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Round::getId, r -> r, (a, b) -> a, LinkedHashMap::new));

        List<CertDtos.EvidenceView> evidences = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int includedRounds = 0;
        int includedArrows = 0;
        int totalScore = 0;

        for (Long roundId : ids) {
            Round round = roundMap.get(roundId);
            if (round == null) {
                evidences.add(missingEvidence(roundId));
                problems.add("回合 id=" + roundId + " 不存在（可能已被删除）");
                continue;
            }
            String reason = excludeReason(round, member.getId(), rule.getBowType(), from, to);
            boolean included = reason == null;
            evidences.add(toEvidenceView(round, included, reason));
            if (!included) {
                problems.add("回合 [" + round.getRoundNo() + "] " + RangeDict.certExcludeReasonName(reason));
                continue;
            }
            includedRounds += 1;
            includedArrows += round.getArrows().size();
            totalScore += round.getTotalScore() == null ? 0 : round.getTotalScore();
        }

        BigDecimal average = includedArrows == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(totalScore)
                        .divide(BigDecimal.valueOf(includedArrows), 2, RoundingMode.HALF_UP);
        boolean meets = includedRounds >= rule.getMinRounds()
                && includedArrows >= rule.getRequiredArrows()
                && average.compareTo(rule.getMinAverage()) >= 0;
        String reasonSummary = problems.isEmpty()
                ? ("证据全部有效；" + (meets ? "达到" : "暂未达到") + "通过线")
                : String.join("；", problems);
        return new CertDtos.PreviewView(problems.isEmpty(), includedRounds, includedArrows,
                average, meets, reasonSummary, evidences);
    }

    /** 用申请上冻结的规则快照重算（重新评定场景） */
    private CertDtos.PreviewView buildPreviewWithSnapshot(Member member, CertApplication app,
            LocalDate from, LocalDate to, List<Long> roundIds) {
        validateWindow(from, to);
        List<Long> ids = distinct(roundIds);
        Map<Long, Round> roundMap = roundRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Round::getId, r -> r, (a, b) -> a, LinkedHashMap::new));

        List<CertDtos.EvidenceView> evidences = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int includedRounds = 0;
        int includedArrows = 0;
        int totalScore = 0;

        for (Long roundId : ids) {
            Round round = roundMap.get(roundId);
            if (round == null) {
                evidences.add(missingEvidence(roundId));
                problems.add("回合 id=" + roundId + " 不存在（可能已被删除）");
                continue;
            }
            String reason = excludeReason(round, member.getId(), app.getBowType(), from, to);
            boolean included = reason == null;
            evidences.add(toEvidenceView(round, included, reason));
            if (!included) {
                problems.add("回合 [" + round.getRoundNo() + "] " + RangeDict.certExcludeReasonName(reason));
                continue;
            }
            includedRounds += 1;
            includedArrows += round.getArrows().size();
            totalScore += round.getTotalScore() == null ? 0 : round.getTotalScore();
        }

        BigDecimal average = includedArrows == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(totalScore)
                        .divide(BigDecimal.valueOf(includedArrows), 2, RoundingMode.HALF_UP);
        boolean meets = includedRounds >= app.getMinRounds()
                && includedArrows >= app.getRequiredArrows()
                && average.compareTo(app.getMinAverage()) >= 0;
        String reasonSummary = problems.isEmpty()
                ? ("证据全部有效；" + (meets ? "达到" : "暂未达到") + "通过线")
                : String.join("；", problems);
        return new CertDtos.PreviewView(problems.isEmpty(), includedRounds, includedArrows,
                average, meets, reasonSummary, evidences);
    }

    /** 逐回合校验：不属于该会员 / 未完成 / 弓种不匹配 / 超出窗口，命中任一即排除并带原因 */
    private String excludeReason(Round round, Long memberId, String bowType, LocalDate from, LocalDate to) {
        if (round.getMember() == null || !memberId.equals(round.getMember().getId())) {
            return "MEMBER_MISMATCH";
        }
        if (!"SUBMITTED".equals(round.getStatus())) {
            return "NOT_SUBMITTED";
        }
        if (!bowType.equals(round.getBowType())) {
            return "BOW_MISMATCH";
        }
        LocalDate shot = round.getStartTime().toLocalDate();
        if (shot.isBefore(from) || shot.isAfter(to)) {
            return "OUT_OF_WINDOW";
        }
        return null;
    }

    private void validateWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BizException("请选择证据窗口的起止日期");
        }
        if (to.isBefore(from)) {
            throw new BizException("证据窗口结束日期不能早于开始日期");
        }
    }

    /** 通过后使同弓种同射距的旧有效认证失效（高射距覆盖低射距，不受影响） */
    private void supersedeSameScope(CertApplication current, String operator) {
        List<CertApplication> actives = applicationRepository
                .findByMemberIdAndStatusAndBowTypeOrderByValidUntilDesc(
                        current.getMemberId(), "APPROVED", current.getBowType());
        for (CertApplication old : actives) {
            if (old.getId().equals(current.getId())) {
                continue;
            }
            if (old.getDistance().equals(current.getDistance())) {
                old.setStatus("SUPERSEDED");
                applicationRepository.save(old);
                log(old.getId(), "SUPERSEDE", "认证系统", "SYSTEM",
                        "同弓种同射距的新认证 " + current.getCertNo() + " 已通过，本认证被重新评定结果取代。");
            }
        }
    }

    // ================= 映射与工具 =================

    /** 评定只能选用 ACTIVE 的当前规则版本；已废止版本仅供历史查看 */
    private CertRule requireRule(Long ruleId) {
        CertRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new BizException("认证规则版本不存在：id=" + ruleId));
        if (!"ACTIVE".equals(rule.getStatus())) {
            throw new BizException("规则 " + rule.getRuleCode() + " v" + rule.getVersionNo()
                    + " 已被新版本取代，请选用当前版本发起新评定（历史申请仍保留旧版快照）");
        }
        return rule;
    }

    private void snapshotRule(CertApplication app, CertRule rule) {
        app.setRuleId(rule.getId());
        app.setRuleCode(rule.getRuleCode());
        app.setVersionNo(rule.getVersionNo());
        app.setBowType(rule.getBowType());
        app.setDistance(rule.getDistance());
        app.setMinRounds(rule.getMinRounds());
        app.setRequiredArrows(rule.getRequiredArrows());
        app.setMinAverage(rule.getMinAverage());
        app.setValidMonths(rule.getValidMonths());
    }

    private void saveEvidences(Long appId, List<CertDtos.EvidenceView> evidences) {
        if (appId == null) {
            throw new IllegalStateException("申请未先持久化，证据回合无法关联申请（applicationId 为空）");
        }
        for (CertDtos.EvidenceView e : evidences) {
            CertEvidenceRound row = new CertEvidenceRound();
            row.setApplicationId(appId);
            row.setRoundId(e.roundId());
            row.setRoundNo(e.roundNo());
            row.setMemberId(e.memberId() == null ? 0L : e.memberId());
            row.setBowType(e.bowType() == null ? "" : e.bowType());
            row.setDistance(e.distance() == null ? 0 : e.distance());
            row.setRoundDate(e.roundDate() == null ? LocalDateTime.now() : e.roundDate());
            row.setArrowCount(e.arrowCount() == null ? 0 : e.arrowCount());
            row.setTotalScore(e.totalScore() == null ? 0 : e.totalScore());
            row.setIncluded(Boolean.TRUE.equals(e.included()));
            row.setExcludeReason(e.excludeReason());
            evidenceRepository.save(row);
        }
    }

    private void log(Long appId, String action, String operator, String role, String detail) {
        CertLog entry = new CertLog();
        entry.setApplicationId(appId);
        entry.setAction(action);
        entry.setOperator(operator);
        entry.setRole(role);
        entry.setDetail(detail);
        entry.setCreatedAt(LocalDateTime.now());
        logRepository.save(entry);
    }

    private String nextCertNo() {
        String no = "C" + LocalDateTime.now().format(NO_STAMP);
        int seq = 1;
        while (applicationRepository.existsByCertNo(no)) {
            no = "C" + LocalDateTime.now().format(NO_STAMP) + seq;
            seq += 1;
        }
        return no;
    }

    private String requireName(String name, String who) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BizException("请填写" + who + "姓名");
        }
        if (trimmed.length() > 32) {
            throw new BizException(who + "姓名过长");
        }
        return trimmed;
    }

    private static List<Long> distinct(List<Long> ids) {
        return new ArrayList<>(new LinkedHashSet<>(ids == null ? List.of() : ids));
    }

    private Map<Long, Member> loadMembers(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(new LinkedHashSet<>(ids)).stream()
                .collect(Collectors.toMap(Member::getId, m -> m));
    }

    private CertDtos.RuleView toRuleView(CertRule rule) {
        return new CertDtos.RuleView(
                rule.getId(),
                rule.getRuleCode(),
                rule.getVersionNo(),
                rule.getBowType(),
                RangeDict.bowTypeName(rule.getBowType()),
                rule.getDistance(),
                rule.getMinRounds(),
                rule.getRequiredArrows(),
                rule.getMinAverage(),
                rule.getValidMonths(),
                rule.getStatus(),
                RangeDict.certRuleStatusName(rule.getStatus()),
                rule.getRemark(),
                rule.getCreatedBy(),
                rule.getCreatedAt());
    }

    private CertDtos.ApplicationSummary toSummary(CertApplication a, Member member) {
        boolean effective = "APPROVED".equals(a.getStatus())
                && a.getValidUntil() != null
                && !a.getValidUntil().isBefore(LocalDate.now());
        return new CertDtos.ApplicationSummary(
                a.getId(),
                a.getCertNo(),
                a.getMemberId(),
                member == null ? "" : member.getName(),
                member == null ? "" : member.getCardNo(),
                a.getRuleId(),
                a.getRuleCode(),
                a.getVersionNo(),
                a.getBowType(),
                RangeDict.bowTypeName(a.getBowType()),
                a.getDistance(),
                a.getRequiredArrows(),
                a.getMinAverage(),
                a.getValidMonths(),
                a.getEvidenceFrom(),
                a.getEvidenceTo(),
                a.getEvidenceRounds(),
                a.getEvidenceArrows(),
                a.getEvidenceAverage(),
                a.getSystemResult(),
                RangeDict.certSystemResultName(a.getSystemResult()),
                a.getStatus(),
                RangeDict.certStatusName(a.getStatus()),
                a.getRevision(),
                a.getCreatedBy(),
                a.getCreatedAt(),
                a.getReviewedBy(),
                a.getReviewedAt(),
                a.getValidFrom(),
                a.getValidUntil(),
                effective,
                a.getRowVersion());
    }

    private CertDtos.EvidenceView toEvidenceView(Round round, boolean included, String excludeReason) {
        return new CertDtos.EvidenceView(
                round.getId(),
                round.getRoundNo(),
                round.getMember() == null ? null : round.getMember().getId(),
                round.getBowType(),
                RangeDict.bowTypeName(round.getBowType()),
                round.getLane() == null ? null : round.getLane().getDistance(),
                round.getStartTime(),
                round.getArrowCount(),
                round.getTotalScore(),
                round.getAverageScore(),
                round.getStatus(),
                RangeDict.roundStatusName(round.getStatus()),
                included,
                excludeReason,
                excludeReason == null ? "" : RangeDict.certExcludeReasonName(excludeReason));
    }

    /** 已持久化的证据快照（历史详情按快照展示，不回查会变的回合表） */
    private CertDtos.EvidenceView toEvidenceView(CertEvidenceRound e) {
        BigDecimal average = (e.getArrowCount() == null || e.getArrowCount() == 0)
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(e.getTotalScore() == null ? 0 : e.getTotalScore())
                        .divide(BigDecimal.valueOf(e.getArrowCount()), 2, RoundingMode.HALF_UP);
        return new CertDtos.EvidenceView(
                e.getRoundId(),
                e.getRoundNo(),
                e.getMemberId(),
                e.getBowType(),
                RangeDict.bowTypeName(e.getBowType()),
                e.getDistance(),
                e.getRoundDate(),
                e.getArrowCount(),
                e.getTotalScore(),
                average,
                "SUBMITTED",
                "已提交（证据快照）",
                e.getIncluded(),
                e.getExcludeReason(),
                e.getExcludeReason() == null ? "" : RangeDict.certExcludeReasonName(e.getExcludeReason()));
    }

    private CertDtos.EvidenceView missingEvidence(Long roundId) {
        return new CertDtos.EvidenceView(
                roundId, "#" + roundId, null, "", "未知", null, null, 0, 0,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), "", "回合已不存在",
                false, "MISSING", "回合不存在（可能已被删除），不能作为证据");
    }
}
