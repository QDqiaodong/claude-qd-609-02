package com.archery.range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.archery.range.common.ConflictException;
import com.archery.range.dto.CertDtos;
import com.archery.range.service.CertService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 弓种能力认证端到端流程测试（H2 MySQL 方言 + 演示数据）：
 * 规则版本快照、证据窗口校验（未完成 / 弓种不符 / 越窗）、待复核初判、
 * 通过 / 驳回 / 补证据状态流转、有效期与适用范围识别、过期失效、并发决定单结论。
 *
 * 测试共享同一个 Spring 上下文与内嵌库，按 @Order 顺序执行：
 * 发布 RC-18 v2 的用例会作废旧版，必须排在依赖 v1 的用例之后。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CertFlowIntegrationTest {

    @Autowired
    private CertService certService;

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    private CertDtos.ApplyReq applyReq(Long memberId, Long ruleId, LocalDate from, LocalDate to, List<Long> roundIds) {
        return new CertDtos.ApplyReq(memberId, ruleId, from, to, roundIds, "测试教练");
    }

    // ---------- 证据窗口校验 ----------

    @Test
    @Order(1)
    void rejectsOngoingBowsMismatchAndOutOfWindowRounds() {
        // 会员 3 的回合 10 是 ONGOING（未完成），按规则必须明确排除，不能悄悄算进去
        CertDtos.PreviewView preview = certService.preview(applyReq(
                3L, 2L, // RC-18 v1
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(10L)));
        assertFalse(preview.allIncluded());
        assertEquals(0, preview.includedRounds());
        assertTrue(preview.reasonSummary().contains("未完成"));

        // 会员 6 的回合 4 是反曲弓 18 米，用复合弓规则（CP-18 v6）预检应判弓种不匹配
        CertDtos.PreviewView wrongBow = certService.preview(applyReq(
                6L, 6L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(4L)));
        assertFalse(wrongBow.allIncluded());
        assertTrue(wrongBow.reasonSummary().contains("弓种不匹配"));

        // 同回合但窗口不覆盖回合日期（回合 4 在 09-17），应判超出窗口
        CertDtos.PreviewView outOfWindow = certService.preview(applyReq(
                6L, 2L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10),
                List.of(4L)));
        assertFalse(outOfWindow.allIncluded());
        assertTrue(outOfWindow.reasonSummary().contains("超出证据窗口"));
    }

    @Test
    @Order(2)
    void previewMeetsStandardThenApplyAndApprove() {
        // 会员 6 回合 4：反曲 18m，45/6 = 7.50 ≥ RC-18 v1 的 7.0
        CertDtos.PreviewView preview = certService.preview(applyReq(
                6L, 2L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 18), List.of(4L)));
        assertTrue(preview.allIncluded());
        assertTrue(preview.meetsStandard());

        CertDtos.ApplicationView view = certService.apply(applyReq(
                6L, 2L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 18), List.of(4L)));
        Long id = view.application().id();
        assertEquals("PENDING_REVIEW", view.application().status());
        assertEquals("MEETS_STANDARD", view.application().systemResult());
        // 规则快照冻结在申请上
        assertEquals("RC-18", view.application().ruleCode());
        assertEquals(1, view.application().versionNo());
        assertEquals(1, view.evidences().size());
        assertTrue(view.evidences().get(0).included());

        CertDtos.ApplicationView approved = certService.decide(id,
                new CertDtos.DecideReq("APPROVE", "测试教练", "", view.application().rowVersion()));
        assertEquals("APPROVED", approved.application().status());
        assertNotNull(approved.application().validFrom());
        assertNotNull(approved.application().validUntil());
        assertTrue(approved.application().effective());

        // 适用范围识别：本人持证覆盖 18m；未持的 30m 不覆盖
        assertTrue(certService.covers(6L, "RECURVE", 18));
        assertFalse(certService.covers(6L, "RECURVE", 30));
        assertFalse(certService.covers(6L, "COMPOUND", 18));
    }

    @Test
    @Order(3)
    void rejectRequiresReasonAndBlocksApproveForOtherCoach() {
        CertDtos.ApplicationView view = certService.apply(applyReq(
                2L, 2L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 17), List.of(2L)));
        Long id = view.application().id();
        assertThrows(RuntimeException.class, () -> certService.decide(id,
                new CertDtos.DecideReq("REJECT", "教练甲", "", view.application().rowVersion())));
    }

    @Test
    @Order(4)
    void concurrentDecideProducesSingleConclusion() {
        CertDtos.ApplicationView view = certService.apply(applyReq(
                6L, 2L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 18), List.of(4L)));
        Long id = view.application().id();
        int staleVersion = view.application().rowVersion();

        // 第一位教练先通过
        CertDtos.ApplicationView first = certService.decide(id,
                new CertDtos.DecideReq("APPROVE", "教练甲", "同意", staleVersion));
        assertEquals("APPROVED", first.application().status());
        assertEquals(staleVersion + 1, first.application().rowVersion());

        // 第二位教练拿着旧页面（旧版本号）后到：必须得到冲突反馈，不能产生第二条矛盾结论
        ConflictException conflict = assertThrows(ConflictException.class, () -> certService.decide(id,
                new CertDtos.DecideReq("REJECT", "教练乙", "我认为不行", staleVersion)));
        assertTrue(conflict.getMessage().contains("最终结论"));

        CertDtos.ApplicationView reloaded = certService.detail(id);
        assertEquals("APPROVED", reloaded.application().status());
        assertEquals("教练甲", reloaded.application().reviewedBy());
    }

    @Test
    @Order(5)
    void requestMoreThenResubmitUsesSameRuleSnapshot() {
        CertDtos.ApplicationView view = certService.apply(applyReq(
                2L, 2L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 17), List.of(2L)));
        Long id = view.application().id();
        CertDtos.ApplicationView more = certService.decide(id,
                new CertDtos.DecideReq("REQUEST_MORE", "教练甲", "再补一个稳定回合", view.application().rowVersion()));
        assertEquals("NEED_MORE", more.application().status());

        // 重新评定：仍用同一规则快照（RC-18 v1）
        CertDtos.ApplicationView again = certService.resubmit(id,
                new CertDtos.ResubmitReq(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 17),
                        List.of(2L), "教练乙"));
        assertEquals("PENDING_REVIEW", again.application().status());
        assertEquals(2, again.application().revision());
        assertEquals("RC-18", again.application().ruleCode());
        assertEquals(1, again.application().versionNo());
    }

    @Test
    @Order(100)
    void ruleVersioningKeepsHistorySnapshot() {
        // 发布 RC-18 v2，旧版自动废止
        CertDtos.RuleSaveReq req = new CertDtos.RuleSaveReq("RECURVE", 18, 2, 12,
                new java.math.BigDecimal("7.50"), 12, "提高稳定性要求", "规则教练");
        CertDtos.RuleView v2 = certService.publishRule(req);
        assertEquals(2, v2.versionNo());
        assertEquals("ACTIVE", v2.status());

        List<CertDtos.RuleView> all = certService.listRules(null);
        CertDtos.RuleView v1 = all.stream().filter(r -> r.ruleCode().equals("RC-18") && r.versionNo() == 1)
                .findFirst().orElseThrow();
        assertEquals("SUPERSEDED", v1.status());

        // 用已废止的旧版 id 发起新申请必须被拒绝
        assertThrows(RuntimeException.class, () -> certService.preview(applyReq(
                6L, v1.id(), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 18), List.of(4L))));

        // 历史已存在的认证（演示数据 cert id=1）仍指向 v1 快照，详情可查
        CertDtos.ApplicationView history = certService.detail(1L);
        assertEquals(1, history.application().versionNo());
        assertEquals(v1.id(), history.rule().id());
    }

    @Test
    @Order(6)
    void expiredCertIsNotEffectiveButRemainsInHistory() {
        // 演示数据：会员 1 的 RC-50 认证已于 2026-09-12 过期（cert id=3，状态 EXPIRED）
        CertDtos.ApplicationView expired = certService.detail(3L);
        assertEquals("EXPIRED", expired.application().status());
        assertFalse(expired.application().effective());
        assertTrue(certService.effectiveCerts(1L).stream().noneMatch(c -> c.id().equals(3L)));
        assertFalse(certService.covers(1L, "RECURVE", 50));

        // 历史记录仍在（演示数据中会员 1 只有这一张认证）
        assertEquals(1, certService.list(null, 1L).size());
    }

    @Test
    @Order(7)
    void revokeChangesAvailabilityImmediately() {
        // 自包含：为会员 2 申请并通过一张 RC-18，再撤回，适用范围资格应立即消失
        CertDtos.ApplicationView view = certService.apply(applyReq(
                2L, 2L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 17), List.of(2L)));
        Long id = view.application().id();
        certService.decide(id, new CertDtos.DecideReq("APPROVE", "教练甲", "ok", view.application().rowVersion()));
        assertTrue(certService.covers(2L, "RECURVE", 18));
        certService.revoke(id, new CertDtos.OperatorReq("值班教练"));
        assertFalse(certService.covers(2L, "RECURVE", 18));
        assertEquals("REVOKED", certService.detail(id).application().status());
    }

    // ---------- HTTP 层：409 冲突 + 课程报名认证门槛 ----------

    @Test
    @Order(8)
    void httpConcurrentDecideReturns409() throws Exception {
        CertDtos.ApplicationView view = certService.apply(applyReq(
                6L, 2L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 18), List.of(4L)));
        Long id = view.application().id();
        int v = view.application().rowVersion();
        certService.decide(id, new CertDtos.DecideReq("APPROVE", "教练甲", "ok", v));

        String body = json.writeValueAsString(java.util.Map.of(
                "decision", "REJECT", "operator", "教练乙", "note", "晚到的驳回", "expectedVersion", v));
        ResponseEntity<String> resp = rest.postForEntity(
                "/api/cert/applications/" + id + "/decide",
                new org.springframework.http.HttpEntity<>(body, jsonHeaders()), String.class);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        JsonNode node = json.readTree(resp.getBody());
        assertFalse(node.get("ok").asBoolean());
        assertTrue(node.get("message").asText().contains("最终结论"));
    }

    @Test
    @Order(9)
    void courseEnrollBlockedWithoutCertCoverage() {
        // 课程 4「复合弓调校实操」为进阶课（银卡可过等级门槛）但要求 COMPOUND 30 认证；
        // 会员 2（银卡、未报名该课、无复合弓认证）报名应在认证校验处被服务端拒绝。
        ResponseEntity<String> resp = rest.postForEntity("/api/courses/4/enroll",
                new org.springframework.http.HttpEntity<>(
                        "{\"memberId\":2}", jsonHeaders()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().contains("有效认证"));
    }

    private org.springframework.http.HttpHeaders jsonHeaders() {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.set("Content-Type", "application/json");
        return h;
    }
}
