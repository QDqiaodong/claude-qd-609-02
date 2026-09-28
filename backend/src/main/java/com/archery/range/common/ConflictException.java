package com.archery.range.common;

/**
 * 并发冲突：两个教练同时打开同一申请并各自作出决定时，
 * 后到的操作用 409 明确反馈「已有最终结论 / 页面已过期」，不会产生两条互相矛盾的认证。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
