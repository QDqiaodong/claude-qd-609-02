package com.archery.range.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.archery.range.common.ApiResponse;
import com.archery.range.domain.RangeDict;
import com.archery.range.dto.CertDtos;
import com.archery.range.service.CertService;

import jakarta.validation.Valid;

/**
 * 模块八：弓种能力认证。
 *
 * 教练从会员已完成计分回合中圈定「证据窗口」发起认证 → 系统按所选规则版本快照
 * （箭数 / 射距 / 通过线）给出达标待复核初判 → 复核页通过 / 驳回 / 要求补充证据；
 * 通过后带有效期与适用范围，过期、撤回、重新评定都会改变会员可用状态。
 */
@RestController
@RequestMapping("/api/cert")
public class CertController {

    private final CertService certService;

    public CertController(CertService certService) {
        this.certService = certService;
    }

    // ---------------- 规则（带版本） ----------------

    @GetMapping("/rules")
    public ApiResponse<List<CertDtos.RuleView>> rules(@RequestParam(required = false) String status) {
        return ApiResponse.success(certService.listRules(status));
    }

    /** 发布一版规则：同弓种射距旧版自动废止；历史认证不受影响 */
    @PostMapping("/rules")
    public ApiResponse<CertDtos.RuleView> publishRule(@Valid @RequestBody CertDtos.RuleSaveReq req) {
        return ApiResponse.success("规则版本已发布，之后发起的申请按新版本评定", certService.publishRule(req));
    }

    // ---------------- 证据窗口 ----------------

    /** 窗口内候选回合（未完成回合也返回，前端置灰且预检会明确排除） */
    @GetMapping("/window-rounds")
    public ApiResponse<List<CertDtos.EvidenceView>> windowRounds(@RequestParam Long memberId,
            @RequestParam String from,
            @RequestParam String to) {
        return ApiResponse.success(certService.windowRounds(memberId, java.time.LocalDate.parse(from),
                java.time.LocalDate.parse(to)));
    }

    /** 预检：逐回合是否计入 + 原因 + 按规则快照的达标初判（不落库） */
    @PostMapping("/preview")
    public ApiResponse<CertDtos.PreviewView> preview(@Valid @RequestBody CertDtos.ApplyReq req) {
        return ApiResponse.success(certService.preview(req));
    }

    // ---------------- 申请 / 重新评定 ----------------

    @PostMapping("/applications")
    public ApiResponse<CertDtos.ApplicationView> apply(@Valid @RequestBody CertDtos.ApplyReq req) {
        return ApiResponse.success("认证申请已建立，系统按所选规则版本给出待复核结果", certService.apply(req));
    }

    /** 待补证据 → 重新圈定窗口与回合 → 重新评定回待复核 */
    @PostMapping("/applications/{id}/resubmit")
    public ApiResponse<CertDtos.ApplicationView> resubmit(@PathVariable Long id,
            @Valid @RequestBody CertDtos.ResubmitReq req) {
        return ApiResponse.success("已按原规则快照重新评定，回到待复核", certService.resubmit(id, req));
    }

    // ---------------- 复核（并发单结论） ----------------

    /** 通过 / 驳回 / 要求补充证据；并发决定只产生一个最终结论，后到者收到 409 */
    @PostMapping("/applications/{id}/decide")
    public ApiResponse<CertDtos.ApplicationView> decide(@PathVariable Long id,
            @Valid @RequestBody CertDtos.DecideReq req) {
        return ApiResponse.success("复核结论已记录", certService.decide(id, req));
    }

    /** 撤回已通过认证，立即失效 */
    @PostMapping("/applications/{id}/revoke")
    public ApiResponse<CertDtos.ApplicationView> revoke(@PathVariable Long id,
            @Valid @RequestBody CertDtos.OperatorReq req) {
        return ApiResponse.success("认证已撤回并立即失效", certService.revoke(id, req));
    }

    // ---------------- 查询 ----------------

    @GetMapping("/applications")
    public ApiResponse<List<CertDtos.ApplicationSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long memberId) {
        return ApiResponse.success(certService.list(status, memberId));
    }

    @GetMapping("/applications/{id}")
    public ApiResponse<CertDtos.ApplicationView> detail(@PathVariable Long id) {
        return ApiResponse.success(certService.detail(id));
    }

    /** 会员当前有效认证（会员详情、课程、箭道入口识别用） */
    @GetMapping("/members/{memberId}/effective")
    public ApiResponse<List<CertDtos.MemberCertView>> effective(@PathVariable Long memberId) {
        return ApiResponse.success(certService.effectiveCerts(memberId));
    }

    /** 适用范围识别：会员当前认证是否覆盖某弓种 + 射距 */
    @GetMapping("/members/{memberId}/covers")
    public ApiResponse<Map<String, Object>> covers(@PathVariable Long memberId,
            @RequestParam String bowType,
            @RequestParam Integer distance) {
        java.util.Optional<CertDtos.MemberCertView> hit = certService.findCovering(memberId, bowType, distance);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bowType", bowType);
        result.put("bowTypeName", RangeDict.bowTypeName(bowType));
        result.put("distance", distance);
        result.put("covered", hit.isPresent());
        result.put("cert", hit.orElse(null));
        return ApiResponse.success(result);
    }

    /** 认证台词典：弓种 / 射距 / 申请状态 / 复核决定 */
    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("bowTypes", List.of(
                Map.of("code", "RECURVE", "name", RangeDict.bowTypeName("RECURVE")),
                Map.of("code", "COMPOUND", "name", RangeDict.bowTypeName("COMPOUND")),
                Map.of("code", "TRADITIONAL", "name", RangeDict.bowTypeName("TRADITIONAL"))));
        options.put("distances", RangeDict.DISTANCES);
        options.put("decisions", List.of(
                Map.of("code", "APPROVE", "name", RangeDict.certDecisionName("APPROVE")),
                Map.of("code", "REJECT", "name", RangeDict.certDecisionName("REJECT")),
                Map.of("code", "REQUEST_MORE", "name", RangeDict.certDecisionName("REQUEST_MORE"))));
        return ApiResponse.success(options);
    }
}
