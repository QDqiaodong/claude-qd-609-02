package com.archery.range.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 认证过期收敛：每天 00:05 扫描已过有效期的 APPROVED 认证并置为 EXPIRED、留痕。
 * 查询口径（CertService.effectiveCerts / findCovering）同时按 validUntil 兜底，
 * 因此即使定时任务尚未跑到，过期认证也不会被课程 / 箭道入口当作有效。
 */
@Component
public class CertExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertExpiryScheduler.class);

    private final CertService certService;

    public CertExpiryScheduler(CertService certService) {
        this.certService = certService;
    }

    /** 每天 00:05 收敛；服务启动 10 秒后也跑一次，保证演示环境刚起就一致 */
    @Scheduled(cron = "0 5 0 * * ?")
    public void dailyExpire() {
        int changed = certService.expireOverdue();
        if (changed > 0) {
            log.info("弓种能力认证过期收敛：{} 张认证已置为过期", changed);
        }
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = Long.MAX_VALUE)
    public void startupExpire() {
        int changed = certService.expireOverdue();
        if (changed > 0) {
            log.info("启动后认证过期收敛：{} 张认证已置为过期", changed);
        }
    }
}
