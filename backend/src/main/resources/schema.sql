-- 射箭馆（弓箭俱乐部）演示数据：建表 + 初始化数据
-- 回合的箭支走 round_arrow 这张有序集合连接表，shot_index 从 0 连续递增。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS lanes (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  lane_no       VARCHAR(16)  NOT NULL COMMENT '道号',
  distance      INT          NOT NULL COMMENT '距离（米）：10/18/30/50',
  target_type   VARCHAR(32)  NOT NULL COMMENT '箭靶类型',
  status        VARCHAR(16)  NOT NULL COMMENT 'OPEN 开放 / OCCUPIED 占用 / MAINTENANCE 维护 / LOCKED 安全锁定',
  hourly_price  DECIMAL(10,2) NOT NULL COMMENT '每小时单价',
  occupant_id   BIGINT       NULL COMMENT '占用会员',
  occupant_name VARCHAR(32)  NULL,
  opened_at     DATETIME     NULL COMMENT '开台时间',
  planned_hours INT          NULL COMMENT '预计时长（小时）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lane_no (lane_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS members (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  card_no       VARCHAR(24)  NOT NULL COMMENT '会员卡号',
  name          VARCHAR(32)  NOT NULL,
  phone         VARCHAR(20)  NOT NULL,
  level         VARCHAR(16)  NOT NULL COMMENT 'NORMAL 普通 / SILVER 银 / GOLD 金',
  balance       DECIMAL(10,2) NOT NULL COMMENT '余额',
  register_date DATE         NOT NULL COMMENT '注册日期',
  total_spend   DECIMAL(12,2) NOT NULL COMMENT '累计消费',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_no (card_no),
  UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rounds (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  round_no      VARCHAR(24)  NOT NULL COMMENT '回合号',
  member_id     BIGINT       NOT NULL,
  lane_id       BIGINT       NOT NULL,
  start_time    DATETIME     NOT NULL COMMENT '开始时间',
  arrow_count   INT          NOT NULL COMMENT '本组箭支数：6 或 12',
  bow_type      VARCHAR(16)  NOT NULL DEFAULT 'RECURVE' COMMENT '弓种：RECURVE 反曲弓 / COMPOUND 复合弓 / TRADITIONAL 传统弓',
  total_score   INT          NOT NULL COMMENT '总分',
  average_score DECIMAL(5,2) NOT NULL COMMENT '平均环',
  personal_best TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否个人最好成绩',
  status        VARCHAR(16)  NOT NULL COMMENT 'ONGOING 进行中 / PAUSED 暂停（安全联锁） / SUBMITTED 已提交',
  PRIMARY KEY (id),
  UNIQUE KEY uk_round_no (round_no),
  KEY idx_round_member (member_id),
  KEY idx_round_lane (lane_id),
  CONSTRAINT fk_round_member FOREIGN KEY (member_id) REFERENCES members (id),
  CONSTRAINT fk_round_lane   FOREIGN KEY (lane_id)   REFERENCES lanes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS arrow_score (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  ring       VARCHAR(4)  NOT NULL COMMENT '环数标记：X/10/9/.../1/M',
  ring_value INT         NOT NULL COMMENT '计分环值：X 与 10 均为 10，M 为 0',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Round 到 ArrowScore 的有序集合连接表：shot_index 由 Hibernate 的 @OrderColumn 维护，
-- 保证列表顺序 = 射箭顺序（第 0 支、第 1 支 ...）。
CREATE TABLE IF NOT EXISTS round_arrow (
  round_id   BIGINT NOT NULL,
  arrow_id   BIGINT NOT NULL,
  shot_index INT    NOT NULL,
  PRIMARY KEY (round_id, shot_index),
  UNIQUE KEY uk_round_arrow_arrow (arrow_id),
  CONSTRAINT fk_ra_round FOREIGN KEY (round_id) REFERENCES rounds (id),
  CONSTRAINT fk_ra_arrow FOREIGN KEY (arrow_id) REFERENCES arrow_score (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS courses (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  course_name VARCHAR(64) NOT NULL COMMENT '课程名',
  coach       VARCHAR(32) NOT NULL COMMENT '教练',
  level       VARCHAR(16) NOT NULL COMMENT 'BASIC 初级 / ADVANCED 进阶 / COMPETITION 竞技',
  class_time  DATETIME    NOT NULL COMMENT '上课时间',
  capacity    INT         NOT NULL COMMENT '人数上限',
  enrolled    INT         NOT NULL DEFAULT 0 COMMENT '已报名',
  venue       VARCHAR(32) NOT NULL COMMENT '教室/场地',
  req_bow_type VARCHAR(16) NULL COMMENT '入课要求的弓种认证；NULL 为不要求',
  req_distance INT        NULL COMMENT '入课要求的认证适用射距（米）；NULL 为不要求',
  PRIMARY KEY (id),
  KEY idx_course_time (class_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_enroll (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  course_id   BIGINT   NOT NULL,
  member_id   BIGINT   NOT NULL,
  enroll_time DATETIME NOT NULL COMMENT '报名时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_enroll (course_id, member_id),
  CONSTRAINT fk_enroll_course FOREIGN KEY (course_id) REFERENCES courses (id),
  CONSTRAINT fk_enroll_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS equipment (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  equip_code  VARCHAR(24)  NOT NULL COMMENT '器材编号',
  type        VARCHAR(16)  NOT NULL COMMENT 'RECURVE 反曲弓 / COMPOUND 复合弓 / TRADITIONAL 传统弓 / GEAR 护具 / ARROW 箭支',
  brand       VARCHAR(32)  NOT NULL COMMENT '品牌',
  rent_price  DECIMAL(10,2) NOT NULL COMMENT '租金（元/小时）',
  status      VARCHAR(16)  NOT NULL COMMENT 'INSTOCK 在库 / RENTED 租出 / REPAIR 维修 / LOCKED 安全锁定',
  renter_id   BIGINT       NULL,
  renter_name VARCHAR(32)  NULL,
  rented_at   DATETIME     NULL COMMENT '租出时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_equip_code (equip_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================= 安全停射联锁台 =================
-- 停射事件主表：值班经理创建后立即联锁箭道/回合/器材；
-- 状态机 PENDING 待处置 → REVIEWING 分项复核 → CLEARED 待放行 → RELEASED 已放行；
-- 任一复核不通过退回 PENDING（继续锁定），重新发起复核时 review_round +1。
CREATE TABLE IF NOT EXISTS safety_event (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  event_no     VARCHAR(24)  NOT NULL COMMENT '事件编号',
  reason       VARCHAR(24)  NOT NULL COMMENT '原因类别：LANE_DEVICE 箭道设备异常 / PERSON_INTRUSION 人员闯入射线 / EQUIP_SUSPECT 疑似器材故障 / OTHER 其他',
  severity     VARCHAR(16)  NOT NULL COMMENT '严重级别：NOTICE 一般 / MAJOR 严重 / CRITICAL 紧急',
  description  VARCHAR(500) NOT NULL COMMENT '现场说明',
  status       VARCHAR(16)  NOT NULL COMMENT 'PENDING / REVIEWING / CLEARED / RELEASED',
  review_round INT          NOT NULL DEFAULT 1 COMMENT '复核轮次',
  created_by   VARCHAR(32)  NOT NULL COMMENT '创建人（值班经理）',
  created_at   DATETIME     NOT NULL,
  released_by  VARCHAR(32)  NULL COMMENT '放行执行人（值班经理）',
  released_at  DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_event_no (event_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 事件-箭道快照：prev_status 记录锁定前业务状态，放行时按此恢复，绝不粗暴覆盖。
CREATE TABLE IF NOT EXISTS safety_event_lane (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  event_id    BIGINT      NOT NULL,
  lane_id     BIGINT      NOT NULL,
  prev_status VARCHAR(16) NOT NULL COMMENT '锁定前状态：OPEN / OCCUPIED / MAINTENANCE',
  restored    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '放行时是否已恢复',
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_lane (event_id, lane_id),
  CONSTRAINT fk_sl_event FOREIGN KEY (event_id) REFERENCES safety_event (id),
  CONSTRAINT fk_sl_lane  FOREIGN KEY (lane_id)  REFERENCES lanes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 事件-器材快照：同样保留锁定前状态（租出中的器材放行后仍归原会员，renter 字段锁定期间不动）。
CREATE TABLE IF NOT EXISTS safety_event_equipment (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  event_id     BIGINT      NOT NULL,
  equipment_id BIGINT      NOT NULL,
  prev_status  VARCHAR(16) NOT NULL COMMENT '锁定前状态：INSTOCK / RENTED / REPAIR',
  restored     TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_equip (event_id, equipment_id),
  CONSTRAINT fk_se_event FOREIGN KEY (event_id)     REFERENCES safety_event (id),
  CONSTRAINT fk_se_equip FOREIGN KEY (equipment_id) REFERENCES equipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 事件-回合快照：受影响箭道上进行中的回合被暂停，放行后从原进度继续。
CREATE TABLE IF NOT EXISTS safety_event_round (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  event_id    BIGINT      NOT NULL,
  round_id    BIGINT      NOT NULL,
  prev_status VARCHAR(16) NOT NULL COMMENT '锁定前状态：ONGOING',
  restored    TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_round (event_id, round_id),
  CONSTRAINT fk_srd_event FOREIGN KEY (event_id) REFERENCES safety_event (id),
  CONSTRAINT fk_srd_round FOREIGN KEY (round_id) REFERENCES rounds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分项复核：每个事件每一轮有 RANGE（教练）与 EQUIPMENT（器材管理员）两项，
-- (event_id, review_round, item) 唯一，同一复核项只能形成一份最终结论。
CREATE TABLE IF NOT EXISTS safety_review (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  event_id     BIGINT       NOT NULL,
  review_round INT          NOT NULL,
  item         VARCHAR(16)  NOT NULL COMMENT 'RANGE 射线与人员安全 / EQUIPMENT 器材检查',
  conclusion   VARCHAR(8)   NULL COMMENT 'PASS 通过 / FAIL 不通过，NULL 为待复核',
  reviewer     VARCHAR(32)  NULL COMMENT '复核人',
  reviewed_at  DATETIME     NULL,
  note         VARCHAR(500) NULL COMMENT '复核意见；不通过时必填原因',
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_review (event_id, review_round, item),
  CONSTRAINT fk_srv_event FOREIGN KEY (event_id) REFERENCES safety_event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 状态轨迹：创建、发起复核、每次复核结论、复核齐通过、退回待处置、复射放行，全程留痕。
CREATE TABLE IF NOT EXISTS safety_event_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  event_id   BIGINT       NOT NULL,
  action     VARCHAR(24)  NOT NULL COMMENT 'CREATE / START_REVIEW / REVIEW_PASS / REVIEW_FAIL / BACK_PENDING / CLEARED / RELEASE',
  operator   VARCHAR(32)  NOT NULL COMMENT '操作人',
  role       VARCHAR(16)  NOT NULL COMMENT 'MANAGER 值班经理 / COACH 教练 / KEEPER 器材管理员',
  detail     VARCHAR(500) NULL,
  created_at DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_safety_log_event (event_id),
  CONSTRAINT fk_slog_event FOREIGN KEY (event_id) REFERENCES safety_event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- lanes：12 行
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (1, 'A01', 10, '纸质靶', 'OPEN', 60.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (2, 'A02', 10, '纸质靶', 'OCCUPIED', 60.00, 3, '赵启明', '2026-09-22 10:20:00', 2);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (3, 'A03', 18, '三联靶', 'OPEN', 80.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (4, 'A04', 18, '三联靶', 'OPEN', 80.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (5, 'A05', 18, '三联靶', 'MAINTENANCE', 80.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (6, 'A06', 30, '复合靶', 'OPEN', 100.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (7, 'A07', 30, '复合靶', 'OCCUPIED', 100.00, 7, '郑楠', '2026-09-22 13:05:00', 3);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (8, 'A08', 30, '复合靶', 'OCCUPIED', 100.00, 5, '周然', '2026-09-22 15:40:00', 1);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (9, 'A09', 50, '三联靶', 'OPEN', 140.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (10, 'A10', 50, '三联靶', 'OPEN', 140.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (11, 'A11', 50, '3D动物靶', 'MAINTENANCE', 120.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (12, 'A12', 30, '复合靶', 'OPEN', 100.00, NULL, NULL, NULL, NULL);

-- members：12 行
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (1, 'AR-2023-0001', '陈子昂', '13800010001', 'GOLD', 2680.00, '2023-03-18', 12480.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (2, 'AR-2023-0002', '林悦', '13800010002', 'SILVER', 960.00, '2023-06-02', 3420.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (3, 'AR-2023-0003', '赵启明', '13800010003', 'NORMAL', 320.00, '2023-09-11', 680.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (4, 'AR-2024-0004', '孙嘉', '13800010004', 'GOLD', 4120.00, '2024-01-05', 15600.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (5, 'AR-2024-0005', '周然', '13800010005', 'NORMAL', 150.00, '2024-02-20', 240.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (6, 'AR-2024-0006', '吴桐', '13800010006', 'SILVER', 1280.00, '2024-04-09', 4100.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (7, 'AR-2024-0007', '郑楠', '13800010007', 'NORMAL', 480.00, '2024-05-16', 720.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (8, 'AR-2024-0008', '何清', '13800010008', 'GOLD', 3260.00, '2024-06-21', 10200.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (9, 'AR-2024-0009', '冯磊', '13800010009', 'SILVER', 880.00, '2024-07-30', 3200.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (10, 'AR-2025-0010', '许静', '13800010010', 'NORMAL', 260.00, '2025-01-12', 520.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (11, 'AR-2025-0011', '高翔', '13800010011', 'SILVER', 1560.00, '2025-03-08', 5200.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (12, 'AR-2025-0012', '唐宁', '13800010012', 'NORMAL', 100.00, '2025-05-27', 180.00);

-- rounds：13 行
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (1, 'R20260915001', 1, 1, '2026-09-15 19:10:00', 6, 'RECURVE', 58, 9.67, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (2, 'R20260916001', 2, 3, '2026-09-16 18:40:00', 6, 'RECURVE', 43, 7.17, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (3, 'R20260916002', 4, 6, '2026-09-16 20:05:00', 12, 'COMPOUND', 112, 9.33, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (4, 'R20260917001', 6, 4, '2026-09-17 19:30:00', 6, 'RECURVE', 45, 7.50, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (5, 'R20260918001', 8, 9, '2026-09-18 20:15:00', 12, 'COMPOUND', 109, 9.08, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (6, 'R20260919001', 1, 10, '2026-09-19 19:00:00', 6, 'RECURVE', 58, 9.67, 0, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (7, 'R20260920001', 9, 8, '2026-09-20 18:20:00', 6, 'COMPOUND', 37, 6.17, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (8, 'R20260920002', 11, 7, '2026-09-20 20:30:00', 12, 'COMPOUND', 104, 8.67, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (9, 'R20260921001', 4, 12, '2026-09-21 19:45:00', 6, 'COMPOUND', 59, 9.83, 0, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (10, 'R20260922001', 3, 2, '2026-09-22 10:20:00', 6, 'RECURVE', 0, 0.00, 0, 'ONGOING');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (11, 'R20260922002', 7, 7, '2026-09-22 13:05:00', 12, 'COMPOUND', 0, 0.00, 0, 'ONGOING');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (12, 'R20260922003', 5, 8, '2026-09-22 15:40:00', 6, 'COMPOUND', 0, 0.00, 0, 'ONGOING');
-- 历史证据回合：陈子昂 2025 年 50 米反曲弓考核成绩（其 50 米认证已于 2026-09-15 过期）
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `bow_type`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (13, 'R20250910001', 1, 10, '2025-09-10 15:00:00', 6, 'RECURVE', 58, 9.67, 0, 'SUBMITTED');

-- arrow_score：78 行
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (1, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (2, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (3, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (4, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (5, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (6, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (7, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (8, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (9, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (10, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (11, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (12, 'M', 0);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (13, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (14, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (15, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (16, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (17, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (18, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (19, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (20, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (21, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (22, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (23, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (24, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (25, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (26, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (27, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (28, '6', 6);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (29, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (30, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (31, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (32, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (33, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (34, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (35, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (36, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (37, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (38, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (39, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (40, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (41, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (42, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (43, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (44, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (45, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (46, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (47, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (48, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (49, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (50, '6', 6);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (51, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (52, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (53, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (54, 'M', 0);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (55, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (56, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (57, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (58, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (59, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (60, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (61, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (62, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (63, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (64, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (65, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (66, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (67, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (68, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (69, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (70, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (71, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (72, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (73, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (74, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (75, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (76, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (77, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (78, '9', 9);

-- round_arrow：78 行
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 1, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 2, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 3, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 4, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 5, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 6, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 7, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 8, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 9, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 10, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 11, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 12, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 13, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 14, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 15, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 16, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 17, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 18, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 19, 6);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 20, 7);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 21, 8);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 22, 9);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 23, 10);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 24, 11);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 25, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 26, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 27, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 28, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 29, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 30, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 31, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 32, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 33, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 34, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 35, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 36, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 37, 6);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 38, 7);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 39, 8);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 40, 9);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 41, 10);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 42, 11);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 43, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 44, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 45, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 46, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 47, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 48, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 49, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 50, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 51, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 52, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 53, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 54, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 55, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 56, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 57, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 58, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 59, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 60, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 61, 6);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 62, 7);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 63, 8);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 64, 9);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 65, 10);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 66, 11);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 67, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 68, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 69, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 70, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 71, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 72, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (13, 73, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (13, 74, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (13, 75, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (13, 76, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (13, 77, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (13, 78, 5);

-- courses：10 行
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (1, '反曲弓入门体验', '张岩', 'BASIC', '2026-09-23 19:00:00', 8, 5, '一号教学区', NULL, NULL);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (2, '站姿与撒放基础', '张岩', 'BASIC', '2026-09-24 19:30:00', 8, 4, '一号教学区', NULL, NULL);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (3, '瞄点与稳定训练', '李慕白', 'ADVANCED', '2026-09-25 20:00:00', 6, 3, '二号教学区', 'RECURVE', 18);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (4, '复合弓调校实操', '王铮', 'ADVANCED', '2026-09-26 19:00:00', 6, 3, '器材工坊', 'COMPOUND', 30);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (5, '30米定距强化', '李慕白', 'ADVANCED', '2026-09-27 20:00:00', 6, 6, '二号教学区', 'RECURVE', 30);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (6, '竞技反曲特训班', '陈亦驰', 'COMPETITION', '2026-09-28 19:00:00', 4, 3, '竞技馆', 'RECURVE', 50);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (7, '室内18米积分赛', '陈亦驰', 'COMPETITION', '2026-09-29 19:30:00', 4, 2, '竞技馆', 'RECURVE', 18);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (8, '传统弓礼仪与技法', '苏禾', 'BASIC', '2026-09-30 19:00:00', 10, 3, '三号教学区', NULL, NULL);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (9, '青少年安全射箭课', '苏禾', 'BASIC', '2026-10-01 10:00:00', 10, 5, '一号教学区', NULL, NULL);
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`, `req_bow_type`, `req_distance`) VALUES (10, '赛前心理与节奏', '陈亦驰', 'COMPETITION', '2026-10-02 19:00:00', 4, 2, '竞技馆', 'COMPOUND', 50);

-- course_enroll：36 行
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (1, 1, 1, '2026-09-13 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (2, 1, 2, '2026-09-13 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (3, 1, 3, '2026-09-13 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (4, 1, 5, '2026-09-13 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (5, 1, 7, '2026-09-13 09:38:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (6, 2, 3, '2026-09-14 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (7, 2, 5, '2026-09-14 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (8, 2, 10, '2026-09-14 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (9, 2, 12, '2026-09-14 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (10, 3, 2, '2026-09-15 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (11, 3, 6, '2026-09-15 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (12, 3, 11, '2026-09-15 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (13, 4, 4, '2026-09-16 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (14, 4, 6, '2026-09-16 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (15, 4, 9, '2026-09-16 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (16, 5, 1, '2026-09-17 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (17, 5, 2, '2026-09-17 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (18, 5, 4, '2026-09-17 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (19, 5, 6, '2026-09-17 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (20, 5, 9, '2026-09-17 09:38:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (21, 5, 11, '2026-09-17 09:45:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (22, 6, 1, '2026-09-18 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (23, 6, 4, '2026-09-18 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (24, 6, 8, '2026-09-18 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (25, 7, 4, '2026-09-19 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (26, 7, 8, '2026-09-19 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (27, 8, 7, '2026-09-20 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (28, 8, 10, '2026-09-20 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (29, 8, 12, '2026-09-20 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (30, 9, 3, '2026-09-12 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (31, 9, 5, '2026-09-12 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (32, 9, 7, '2026-09-12 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (33, 9, 10, '2026-09-12 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (34, 9, 12, '2026-09-12 09:38:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (35, 10, 1, '2026-09-13 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (36, 10, 8, '2026-09-13 09:17:00');

-- equipment：12 行
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (1, 'E-RC-01', 'RECURVE', 'Hoyt', 45.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (2, 'E-RC-02', 'RECURVE', 'W&W', 40.00, 'RENTED', 3, '赵启明', '2026-09-22 10:25:00');
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (3, 'E-RC-03', 'RECURVE', 'KAP', 38.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (4, 'E-CP-01', 'COMPOUND', 'Mathews', 80.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (5, 'E-CP-02', 'COMPOUND', 'Hoyt', 75.00, 'RENTED', 7, '郑楠', '2026-09-22 13:10:00');
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (6, 'E-CP-03', 'COMPOUND', 'PSE', 70.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (7, 'E-TR-01', 'TRADITIONAL', '三利达', 35.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (8, 'E-TR-02', 'TRADITIONAL', '黑石', 30.00, 'REPAIR', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (9, 'E-GR-01', 'GEAR', 'Fivics', 12.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (10, 'E-GR-02', 'GEAR', 'Cartel', 15.00, 'RENTED', 5, '周然', '2026-09-22 15:45:00');
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (11, 'E-AR-01', 'ARROW', 'Easton', 20.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (12, 'E-AR-02', 'ARROW', 'Gold Tip', 18.00, 'INSTOCK', NULL, NULL, NULL);

-- ================= 模块七：馆内团体淘汰赛计分台 =================
-- 赛事：值班经理建立，选择 4 或 8 支队（每队固定三名现有会员），设置每场局数。
-- 状态机 DRAFT 报名中（可换队/换队员）→ ONGOING 已开赛（队伍与对阵冻结）→ FINISHED 已完赛。
CREATE TABLE IF NOT EXISTS tournament (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL COMMENT '赛事名称',
  team_size       INT         NOT NULL COMMENT '参赛队数：4 或 8',
  ends_per_match  INT         NOT NULL COMMENT '每场局数（每局三名队员各射规定箭数）',
  arrows_per_end  INT         NOT NULL COMMENT '每名队员每局规定箭数',
  status          VARCHAR(16) NOT NULL COMMENT 'DRAFT 报名中 / ONGOING 进行中 / FINISHED 已完赛',
  current_round   INT         NULL COMMENT '当前轮次：最早存在未确认场次的那一轮',
  champion_team_id BIGINT     NULL COMMENT '冠军队',
  created_by      VARCHAR(32) NOT NULL COMMENT '建立人（值班经理）',
  created_at      DATETIME    NOT NULL,
  started_at      DATETIME    NULL COMMENT '开赛时间（开赛即冻结队伍与对阵）',
  finished_at     DATETIME    NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 参赛队：seed_no 为开赛后固定的种子位（决定首轮对阵与晋级走向）
CREATE TABLE IF NOT EXISTS tournament_team (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  tournament_id BIGINT      NOT NULL,
  team_name     VARCHAR(48) NOT NULL,
  seed_no       INT         NULL COMMENT '种子位（开赛时按建队顺序固定，1..team_size）',
  final_rank    INT         NULL COMMENT '最终名次（冠军=1）',
  created_at    DATETIME    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_team_tournament (tournament_id),
  CONSTRAINT fk_team_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 队-会员：一队固定三名会员；(tournament_id, member_id) 唯一，同一会员不能同时出现在两支队伍
CREATE TABLE IF NOT EXISTS tournament_team_member (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  team_id       BIGINT      NOT NULL,
  tournament_id BIGINT      NOT NULL,
  member_id     BIGINT      NOT NULL,
  position      INT         NOT NULL COMMENT '队内站位 0/1/2',
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_member (team_id, member_id),
  UNIQUE KEY uk_tournament_member (tournament_id, member_id),
  KEY idx_tmem_member (member_id),
  CONSTRAINT fk_tmem_team FOREIGN KEY (team_id) REFERENCES tournament_team (id) ON DELETE CASCADE,
  CONSTRAINT fk_tmem_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE,
  CONSTRAINT fk_tmem_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对阵场次：round_no 从 1 开始（1=首轮，越靠后越深），slot 为该轮场次序号。
-- 首轮主客队直接落种子队；后续轮靠 home_from_match_id / away_from_match_id 指明晋级来源，
-- 上一场确认胜者后由后端自动带入对应槽位（不靠前端拼接）。
-- 状态 PENDING 待开赛 → ONGOING 进行中 → AWAIT_CONFIRM 待确认胜者 → CONFIRMED 已确认。
-- 阶段 stage：REGULATION 规定局 / SHOOTOFF 加赛箭。
CREATE TABLE IF NOT EXISTS tournament_match (
  id                   BIGINT      NOT NULL AUTO_INCREMENT,
  tournament_id        BIGINT      NOT NULL,
  round_no             INT         NOT NULL,
  slot                 INT         NOT NULL,
  match_no             INT         NOT NULL COMMENT '场次号：全赛事按轮次统一编号',
  home_team_id         BIGINT      NULL,
  away_team_id         BIGINT      NULL,
  winner_team_id       BIGINT      NULL,
  status               VARCHAR(16) NOT NULL,
  stage                VARCHAR(16) NOT NULL DEFAULT 'REGULATION',
  home_score           INT         NOT NULL DEFAULT 0 COMMENT '规定局主队总分',
  away_score           INT         NOT NULL DEFAULT 0,
  home_x_count         INT         NOT NULL DEFAULT 0,
  away_x_count         INT         NOT NULL DEFAULT 0,
  shootoff_round_count INT         NOT NULL DEFAULT 0 COMMENT '已进行的加赛轮数',
  home_from_match_id   BIGINT      NULL COMMENT '主队晋级来源场次',
  away_from_match_id   BIGINT      NULL COMMENT '客队晋级来源场次',
  confirmed_by         VARCHAR(32) NULL,
  confirmed_at         DATETIME    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_round_slot (tournament_id, round_no, slot),
  KEY idx_match_tournament (tournament_id),
  KEY idx_match_home_team (home_team_id),
  KEY idx_match_away_team (away_team_id),
  CONSTRAINT fk_match_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 局：确认后不可改写；撤回即物理删除最后一局并在 tournament_log 留撤回人/时间/原因
CREATE TABLE IF NOT EXISTS match_end (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  match_id      BIGINT      NOT NULL,
  end_no        INT         NOT NULL COMMENT '局号，从 1 连续',
  home_score    INT         NOT NULL,
  away_score    INT         NOT NULL,
  home_x_count  INT         NOT NULL,
  away_x_count  INT         NOT NULL,
  status        VARCHAR(12) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 录入中 / CONFIRMED 已确认',
  confirmed_by  VARCHAR(32) NOT NULL,
  confirmed_at  DATETIME    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_end (match_id, end_no),
  KEY idx_end_match (match_id),
  CONSTRAINT fk_end_match FOREIGN KEY (match_id) REFERENCES tournament_match (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 局内箭值：唯一键 (end_id, member_id, shot_index)，未确认局可就地覆盖更正
CREATE TABLE IF NOT EXISTS match_arrow (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  end_id      BIGINT      NOT NULL,
  match_id    BIGINT      NOT NULL,
  team_id     BIGINT      NOT NULL,
  member_id   BIGINT      NOT NULL,
  shot_index  INT         NOT NULL COMMENT '该局该队员第几支，0..arrows_per_end-1',
  ring        VARCHAR(4)  NOT NULL COMMENT 'X/10..1/M',
  ring_value  INT         NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_end_member_shot (end_id, member_id, shot_index),
  KEY idx_arrow_match (match_id),
  KEY idx_arrow_member (member_id),
  CONSTRAINT fk_arrow_end FOREIGN KEY (end_id) REFERENCES match_end (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 加赛轮：一轮每名队员各射一支；先比加赛总分，再比 X 数，仍平则开下一轮
CREATE TABLE IF NOT EXISTS shootoff_round (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  match_id     BIGINT      NOT NULL,
  round_no     INT         NOT NULL COMMENT '加赛轮号，从 1 连续',
  home_score   INT         NOT NULL DEFAULT 0,
  away_score   INT         NOT NULL DEFAULT 0,
  home_x_count INT         NOT NULL DEFAULT 0,
  away_x_count INT         NOT NULL DEFAULT 0,
  winner_team_id BIGINT    NULL COMMENT '本轮产生的胜者；NULL 表示仍平、需继续加赛',
  status       VARCHAR(12) NOT NULL COMMENT 'DRAFT 录入中 / LOCKED 已锁定',
  locked_by    VARCHAR(32) NULL,
  locked_at    DATETIME    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shootoff_match_round (match_id, round_no),
  KEY idx_shootoff_match (match_id),
  CONSTRAINT fk_shootoff_match FOREIGN KEY (match_id) REFERENCES tournament_match (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shootoff_arrow (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  round_id    BIGINT      NOT NULL,
  match_id    BIGINT      NOT NULL,
  team_id     BIGINT      NOT NULL,
  member_id   BIGINT      NOT NULL,
  ring        VARCHAR(4)  NOT NULL,
  ring_value  INT         NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shootoff_arrow (round_id, member_id),
  KEY idx_soarrow_match (match_id),
  CONSTRAINT fk_soarrow_round FOREIGN KEY (round_id) REFERENCES shootoff_round (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 完整操作轨迹：建队/换队/开赛/确认局/撤回局/锁定加赛轮/确认胜者等全程留痕
CREATE TABLE IF NOT EXISTS tournament_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tournament_id BIGINT       NOT NULL,
  match_id      BIGINT       NULL,
  action        VARCHAR(28)  NOT NULL,
  operator      VARCHAR(32)  NOT NULL,
  role          VARCHAR(16)  NOT NULL COMMENT 'MANAGER 值班经理 / REFEREE 裁判 / SYSTEM 系统',
  detail        VARCHAR(500) NULL,
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tlog_tournament (tournament_id),
  KEY idx_tlog_match (match_id),
  CONSTRAINT fk_tlog_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================= 模块八：弓种能力认证 =================
-- 认证规则（带版本）：每个「弓种 + 射距」可有多版规则。
-- ACTIVE 为当前评定选用的版本；调整规则时把旧版置为 SUPERSEDED 再新建一版，
-- 已发出的认证在 cert_application 中冗余了完整规则快照，不依赖本表，历史永不被改写。
CREATE TABLE IF NOT EXISTS cert_rule (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  rule_code        VARCHAR(24)  NOT NULL COMMENT '规则代码，同一弓种射距不同版本共用：RC-18 / CP-30 …',
  version_no       INT          NOT NULL COMMENT '版本号，从 1 连续',
  bow_type         VARCHAR(16)  NOT NULL COMMENT 'RECURVE / COMPOUND / TRADITIONAL',
  distance         INT          NOT NULL COMMENT '射距（米）：10/18/30/50',
  min_rounds       INT          NOT NULL COMMENT '证据窗口内最少回合数',
  required_arrows  INT          NOT NULL COMMENT '要求的总箭支数（如 12）',
  min_average      DECIMAL(5,2) NOT NULL COMMENT '通过线：加权平均环最低值',
  valid_months     INT          NOT NULL COMMENT '通过后有效期（月）',
  status           VARCHAR(12)  NOT NULL COMMENT 'ACTIVE 当前版本 / SUPERSEDED 已被新版本取代',
  remark           VARCHAR(200) NULL COMMENT '规则说明 / 调整原因',
  created_by       VARCHAR(32)  NOT NULL,
  created_at       DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cert_rule_version (rule_code, version_no),
  KEY idx_cert_rule_bow (bow_type, distance, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 认证申请：教练从会员已完成计分回合中圈定证据窗口发起。
-- 申请行冗余规则快照（rule_* 列），规则后来调整不影响本申请与已发认证的历史；
-- evidence_from / evidence_to 为证据窗口边界；system_result 为系统按规则快照给出的待复核初判。
-- 状态机：PENDING_REVIEW 待复核 → APPROVED 通过 / REJECTED 驳回 / NEED_MORE 待补证据；
--         NEED_MORE 补充证据后重新评定回到 PENDING_REVIEW（revision +1）；
--         APPROVED 可被 EXPIRED（过期）/ REVOKED（撤回）/ SUPERSEDED（同范围重新评定通过）。
-- version 为乐观锁：两个教练同时打开同一申请并发决定时，只有一次更新能成功。
CREATE TABLE IF NOT EXISTS cert_application (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  cert_no            VARCHAR(24)  NOT NULL COMMENT '认证编号',
  member_id          BIGINT       NOT NULL,
  rule_id            BIGINT       NOT NULL COMMENT '选定的规则版本（cert_rule.id）',
  rule_code          VARCHAR(24)  NOT NULL COMMENT '规则快照：代码',
  version_no         INT          NOT NULL COMMENT '规则快照：版本号',
  bow_type           VARCHAR(16)  NOT NULL COMMENT '规则快照：弓种',
  distance           INT          NOT NULL COMMENT '规则快照：射距',
  min_rounds         INT          NOT NULL COMMENT '规则快照：最少回合数',
  required_arrows    INT          NOT NULL COMMENT '规则快照：要求箭支数',
  min_average        DECIMAL(5,2) NOT NULL COMMENT '规则快照：通过线',
  valid_months       INT          NOT NULL COMMENT '规则快照：有效期月数',
  evidence_from      DATE         NOT NULL COMMENT '证据窗口起（含）',
  evidence_to        DATE         NOT NULL COMMENT '证据窗口止（含）',
  evidence_rounds    INT          NOT NULL COMMENT '采纳的证据回合数',
  evidence_arrows    INT          NOT NULL COMMENT '采纳的证据总箭数',
  evidence_average   DECIMAL(5,2) NOT NULL COMMENT '采纳证据的加权平均环',
  system_result      VARCHAR(16)  NOT NULL COMMENT 'MEETS_STANDARD 达标待复核 / BELOW_STANDARD 未达标',
  status             VARCHAR(16)  NOT NULL COMMENT 'PENDING_REVIEW / APPROVED / REJECTED / NEED_MORE / EXPIRED / REVOKED / SUPERSEDED',
  revision           INT          NOT NULL DEFAULT 1 COMMENT '补充证据重新评定的轮次',
  created_by         VARCHAR(32)  NOT NULL COMMENT '发起教练',
  created_at         DATETIME     NOT NULL,
  reviewed_by        VARCHAR(32)  NULL COMMENT '最近复核教练',
  reviewed_at        DATETIME     NULL,
  review_note        VARCHAR(500) NULL,
  valid_from         DATE         NULL COMMENT '有效期起（通过日）',
  valid_until        DATE         NULL COMMENT '有效期止',
  row_version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本，决定动作据此防并发双结论',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cert_no (cert_no),
  KEY idx_cert_member (member_id),
  KEY idx_cert_status (status),
  CONSTRAINT fk_cert_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 证据回合明细：教练圈进窗口的每个回合都落一行，包含但不计入的回合也要保留并写明原因，
-- 绝不把未完成 / 弓种不符 / 越窗回合悄悄算进去。*_snapshot 为申请时刻的成绩快照，
-- 回合日后若被更正，认证历史仍展示当时采用的分数。
CREATE TABLE IF NOT EXISTS cert_evidence_round (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  application_id   BIGINT       NOT NULL,
  round_id         BIGINT       NOT NULL,
  round_no         VARCHAR(24)  NOT NULL COMMENT '回合号快照',
  member_id        BIGINT       NOT NULL,
  bow_type         VARCHAR(16)  NOT NULL COMMENT '回合实际弓种快照',
  distance         INT          NOT NULL COMMENT '回合实际射距快照',
  round_date       DATETIME     NOT NULL COMMENT '回合开始时间快照',
  arrow_count      INT          NOT NULL COMMENT '回合箭支数快照',
  total_score      INT          NOT NULL COMMENT '回合总分快照',
  included         TINYINT(1)   NOT NULL COMMENT '是否计入评定：1 计入 / 0 仅登记不计入',
  exclude_reason   VARCHAR(120) NULL COMMENT '不计入原因：未完成 / 弓种不匹配 / 超出证据窗口 / 不属于该会员',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cert_evidence (application_id, round_id),
  KEY idx_cert_ev_app (application_id),
  CONSTRAINT fk_cev_app FOREIGN KEY (application_id) REFERENCES cert_application (id) ON DELETE CASCADE,
  CONSTRAINT fk_cev_round FOREIGN KEY (round_id) REFERENCES rounds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 认证流转轨迹：发起 / 系统初判 / 通过 / 驳回 / 补证据 / 重新评定 / 撤回 / 过期 / 重新评定取代，全程留痕。
CREATE TABLE IF NOT EXISTS cert_log (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  application_id BIGINT       NOT NULL,
  action         VARCHAR(24)  NOT NULL COMMENT 'CREATE / SYSTEM_RESULT / APPROVE / REJECT / REQUEST_MORE / RESUBMIT / REVOKE / EXPIRE / SUPERSEDE',
  operator       VARCHAR(32)  NOT NULL COMMENT '操作人（系统动作为 认证系统）',
  role           VARCHAR(16)  NOT NULL COMMENT 'COACH 教练 / SYSTEM 认证系统',
  detail         VARCHAR(500) NULL,
  created_at     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cert_log_app (application_id),
  CONSTRAINT fk_clog_app FOREIGN KEY (application_id) REFERENCES cert_application (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- cert_rule：8 行（RECURVE / COMPOUND 各四档射距，TRADITIONAL 18m）
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (1, 'RC-10', 1, 'RECURVE', 10, 1, 6, 8.00, 12, 'ACTIVE', '反曲弓 10 米基础认证：窗口内至少 1 个已完成回合、满 6 支，平均环不低于 8.0', '张岩', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (2, 'RC-18', 1, 'RECURVE', 18, 1, 6, 7.00, 12, 'ACTIVE', '反曲弓 18 米认证：至少 1 个已完成回合、满 6 支，平均环不低于 7.0', '张岩', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (3, 'RC-30', 1, 'RECURVE', 30, 2, 12, 8.00, 6, 'ACTIVE', '反曲弓 30 米进阶认证：至少 2 个已完成回合、累计满 12 支，平均环不低于 8.0', '李慕白', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (4, 'RC-50', 1, 'RECURVE', 50, 1, 6, 8.50, 6, 'ACTIVE', '反曲弓 50 米竞技认证：至少 1 个已完成回合、满 6 支，平均环不低于 8.5', '陈亦驰', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (5, 'CP-10', 1, 'COMPOUND', 10, 1, 6, 8.50, 12, 'ACTIVE', '复合弓 10 米基础认证：平均环不低于 8.5', '王铮', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (6, 'CP-18', 1, 'COMPOUND', 18, 1, 6, 8.00, 12, 'ACTIVE', '复合弓 18 米认证：平均环不低于 8.0', '王铮', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (7, 'CP-30', 1, 'COMPOUND', 30, 2, 18, 9.00, 6, 'ACTIVE', '复合弓 30 米进阶认证：至少 2 个已完成回合、累计满 18 支，平均环不低于 9.0', '王铮', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (8, 'CP-50', 1, 'COMPOUND', 50, 1, 12, 8.50, 6, 'ACTIVE', '复合弓 50 米竞技认证：至少 1 个已完成 12 支回合，平均环不低于 8.5', '王铮', '2026-09-01 09:00:00');
INSERT INTO cert_rule (`id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `status`, `remark`, `created_by`, `created_at`) VALUES (9, 'TD-18', 1, 'TRADITIONAL', 18, 1, 6, 6.50, 12, 'ACTIVE', '传统弓 18 米礼仪技法认证：平均环不低于 6.5', '苏禾', '2026-09-01 09:00:00');

-- 历史认证（演示会员详情的当前认证与历史）：
-- C20260916001：吴桐（6）反曲弓 18 米已通过，有效期内（证据回合 4：45/6=7.50 ≥ 7.00）。
INSERT INTO cert_application (`id`, `cert_no`, `member_id`, `rule_id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `evidence_from`, `evidence_to`, `evidence_rounds`, `evidence_arrows`, `evidence_average`, `system_result`, `status`, `revision`, `created_by`, `created_at`, `reviewed_by`, `reviewed_at`, `review_note`, `valid_from`, `valid_until`, `row_version`) VALUES (1, 'C20260916001', 6, 2, 'RC-18', 1, 'RECURVE', 18, 1, 6, 7.00, 12, '2026-09-10', '2026-09-18', 1, 6, 7.50, 'MEETS_STANDARD', 'APPROVED', 1, '李慕白', '2026-09-18 09:10:00', '李慕白', '2026-09-18 09:40:00', '动作稳定，18 米动作框架达标，准予认证。', '2026-09-18', '2027-09-18', 1);
INSERT INTO cert_evidence_round (`id`, `application_id`, `round_id`, `round_no`, `member_id`, `bow_type`, `distance`, `round_date`, `arrow_count`, `total_score`, `included`, `exclude_reason`) VALUES (1, 1, 4, 'R20260917001', 6, 'RECURVE', 18, '2026-09-17 19:30:00', 6, 45, 1, NULL);
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (1, 1, 'CREATE', '李慕白', 'COACH', '发起认证申请：反曲弓 18 米（RC-18 v1），证据窗口 2026-09-10 ~ 2026-09-18。', '2026-09-18 09:10:00');
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (2, 1, 'SYSTEM_RESULT', '认证系统', 'SYSTEM', '采纳证据回合 1 个、箭 6 支，加权平均 7.50 环，达到通过线 7.00，给出「达标待复核」。', '2026-09-18 09:10:00');
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (3, 1, 'APPROVE', '李慕白', 'COACH', '复核通过：动作稳定，18 米动作框架达标。有效期 12 个月（至 2027-09-18）。', '2026-09-18 09:40:00');

-- C20260916002：林悦（2）反曲弓 18 米曾被驳回（证据回合 2：43/6=7.17 ≥ 7.00 实际达标，复核认为发挥不稳驳回，留历史）。
INSERT INTO cert_application (`id`, `cert_no`, `member_id`, `rule_id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `evidence_from`, `evidence_to`, `evidence_rounds`, `evidence_arrows`, `evidence_average`, `system_result`, `status`, `revision`, `created_by`, `created_at`, `reviewed_by`, `reviewed_at`, `review_note`, `valid_from`, `valid_until`, `row_version`) VALUES (2, 'C20260916002', 2, 2, 'RC-18', 1, 'RECURVE', 18, 1, 6, 7.00, 12, '2026-09-10', '2026-09-17', 1, 6, 7.17, 'MEETS_STANDARD', 'REJECTED', 1, '张岩', '2026-09-17 10:00:00', '张岩', '2026-09-17 10:30:00', '成绩刚过线且含一支脱靶，动作一致性不足，驳回；请补足训练后重新申请。', NULL, NULL, 1);
INSERT INTO cert_evidence_round (`id`, `application_id`, `round_id`, `round_no`, `member_id`, `bow_type`, `distance`, `round_date`, `arrow_count`, `total_score`, `included`, `exclude_reason`) VALUES (2, 2, 2, 'R20260916001', 2, 'RECURVE', 18, '2026-09-16 18:40:00', 6, 43, 1, NULL);
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (4, 2, 'CREATE', '张岩', 'COACH', '发起认证申请：反曲弓 18 米（RC-18 v1），证据窗口 2026-09-10 ~ 2026-09-17。', '2026-09-17 10:00:00');
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (5, 2, 'SYSTEM_RESULT', '认证系统', 'SYSTEM', '采纳证据回合 1 个、箭 6 支，加权平均 7.17 环，达到通过线 7.00，给出「达标待复核」。', '2026-09-17 10:00:00');
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (6, 2, 'REJECT', '张岩', 'COACH', '复核驳回：成绩刚过线且含一支脱靶，动作一致性不足。', '2026-09-17 10:30:00');

-- C20250915001：陈子昂（1）反曲弓 50 米已过期认证（证据回合 13：58/6=9.67；2025 年通过，2026-09-15 到期）。
INSERT INTO cert_application (`id`, `cert_no`, `member_id`, `rule_id`, `rule_code`, `version_no`, `bow_type`, `distance`, `min_rounds`, `required_arrows`, `min_average`, `valid_months`, `evidence_from`, `evidence_to`, `evidence_rounds`, `evidence_arrows`, `evidence_average`, `system_result`, `status`, `revision`, `created_by`, `created_at`, `reviewed_by`, `reviewed_at`, `review_note`, `valid_from`, `valid_until`, `row_version`) VALUES (3, 'C20250915001', 1, 4, 'RC-50', 1, 'RECURVE', 50, 1, 6, 8.50, 12, '2025-09-01', '2025-09-12', 1, 6, 9.67, 'MEETS_STANDARD', 'EXPIRED', 1, '陈亦驰', '2025-09-12 14:00:00', '陈亦驰', '2025-09-12 14:20:00', '50 米成绩优秀，准予竞技认证。', '2025-09-12', '2026-09-12', 2);
INSERT INTO cert_evidence_round (`id`, `application_id`, `round_id`, `round_no`, `member_id`, `bow_type`, `distance`, `round_date`, `arrow_count`, `total_score`, `included`, `exclude_reason`) VALUES (3, 3, 13, 'R20250910001', 1, 'RECURVE', 50, '2025-09-10 15:00:00', 6, 58, 1, NULL);
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (7, 3, 'APPROVE', '陈亦驰', 'COACH', '复核通过，有效期 12 个月（至 2026-09-12）。', '2025-09-12 14:20:00');
INSERT INTO cert_log (`id`, `application_id`, `action`, `operator`, `role`, `detail`, `created_at`) VALUES (8, 3, 'EXPIRE', '认证系统', 'SYSTEM', '认证已于 2026-09-12 到期，自动失效；50 米通道不再视为持证。', '2026-09-13 00:00:00');

