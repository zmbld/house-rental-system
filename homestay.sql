-- ============================================
-- 民宿在线预订平台 - 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS homestay DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE homestay;

-- 1. 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码(MD5加密)',
    `role` VARCHAR(20) NOT NULL DEFAULT 'TENANT' COMMENT '角色: ADMIN-管理员, LANDLORD-房东, TENANT-房客',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `audit_status` VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态: PENDING-待审核, APPROVED-已通过, REJECTED-已驳回',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 1.1 房东资质审核表
DROP TABLE IF EXISTS `landlord_audit`;
CREATE TABLE `landlord_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核ID',
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `id_card` VARCHAR(50) DEFAULT NULL COMMENT '身份证号',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `address` VARCHAR(255) DEFAULT NULL COMMENT '联系地址',
    `cert_image` VARCHAR(500) DEFAULT NULL COMMENT '房产证/资质证明图片URL',
    `house_images` VARCHAR(2000) DEFAULT NULL COMMENT '房屋图片URL，多个用逗号分隔',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待审核, APPROVED-已通过, REJECTED-已驳回',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '管理员审核意见',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房东资质审核表';

-- 2. 房源表
DROP TABLE IF EXISTS `house`;
CREATE TABLE `house` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '房源ID',
    `landlord_id` BIGINT NOT NULL COMMENT '房东用户ID',
    `name` VARCHAR(200) NOT NULL COMMENT '房源名称',
    `city` VARCHAR(50) NOT NULL COMMENT '城市',
    `address` VARCHAR(255) NOT NULL COMMENT '详细地址',
    `house_type` VARCHAR(20) NOT NULL COMMENT '房型: WHOLE-整套, PRIVATE-独立房间, SHARED-合住房间',
    `area` DOUBLE DEFAULT NULL COMMENT '面积(平方米)',
    `facilities` VARCHAR(500) DEFAULT NULL COMMENT '设施(逗号分隔)',
    `price` DECIMAL(10,2) NOT NULL COMMENT '每晚价格',
    `max_guests` INT NOT NULL DEFAULT 1 COMMENT '最大入住人数',
    `description` TEXT COMMENT '房源描述',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图片URL',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待审核, ONLINE-已上架, OFFLINE-已下架',
    `rating` DECIMAL(2,1) DEFAULT 0.0 COMMENT '平均评分',
    `review_count` INT DEFAULT 0 COMMENT '评论数量',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_city` (`city`),
    KEY `idx_price` (`price`),
    KEY `idx_rating` (`rating`),
    KEY `idx_landlord_id` (`landlord_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房源表';

-- 3. 房源图片表
DROP TABLE IF EXISTS `house_image`;
CREATE TABLE `house_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '图片ID',
    `house_id` BIGINT NOT NULL COMMENT '房源ID',
    `image_url` VARCHAR(500) NOT NULL COMMENT '图片URL',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    KEY `idx_house_id` (`house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房源图片表';

-- 房源图片数据（详情页轮播）
INSERT INTO `house_image` (`house_id`, `image_url`, `sort_order`) VALUES
(1, '/images/8526197_165417356101_2.jpg', 1),
(1, '/images/veer-310172970.jpg', 2),
(2, '/images/8084.jpg_wh860.jpg', 1),
(3, '/images/0223.jpg_wh860.jpg', 1),
(3, '/images/pki19871.jpg', 2),
(3, '/images/4269.jpg_wh860.jpg', 3),
(4, '/images/32761360_161153636286_2.jpg', 1),
(5, '/images/OIP-C.webp', 1),
(6, '/images/veer-310173216.jpg', 1);

-- 4. 房态日历表
DROP TABLE IF EXISTS `house_calendar`;
CREATE TABLE `house_calendar` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日历ID',
    `house_id` BIGINT NOT NULL COMMENT '房源ID',
    `date` DATE NOT NULL COMMENT '日期',
    `status` VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态: AVAILABLE-可订, BOOKED-已订, BLOCKED-已封锁',
    `special_price` DECIMAL(10,2) DEFAULT NULL COMMENT '特殊价格',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_house_date` (`house_id`, `date`),
    KEY `idx_house_id` (`house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房态日历表';

-- 5. 订单表
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单编号',
    `house_id` BIGINT NOT NULL COMMENT '房源ID',
    `tenant_id` BIGINT NOT NULL COMMENT '房客用户ID',
    `check_in_date` DATE NOT NULL COMMENT '入住日期',
    `check_out_date` DATE NOT NULL COMMENT '退房日期',
    `nights` INT NOT NULL COMMENT '入住天数',
    `guest_name` VARCHAR(50) DEFAULT NULL COMMENT '入住人姓名',
    `guest_phone` VARCHAR(20) DEFAULT NULL COMMENT '入住人电话',
    `total_price` DECIMAL(10,2) NOT NULL COMMENT '订单总价',
    `status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '状态: UNPAID-待支付, PAID-已支付, CHECKED_IN-已入住, COMPLETED-已完成, CANCELLED-已取消',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `cancel_time` DATETIME DEFAULT NULL COMMENT '取消时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_house_id` (`house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 6. 评价表
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `house_id` BIGINT NOT NULL COMMENT '房源ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `order_id` BIGINT DEFAULT NULL COMMENT '订单ID',
    `rating` TINYINT NOT NULL COMMENT '评分(1-5)',
    `content` TEXT COMMENT '评价内容',
    `reply` TEXT DEFAULT NULL COMMENT '房东回复',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_house_id` (`house_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- ============================================
-- 初始化测试数据
-- ============================================

-- 管理员账号: admin / 123456 (MD5: e10adc3949ba59abbe56e057f20f883e)
INSERT INTO `user` (`username`, `password`, `role`, `nickname`, `phone`) VALUES
('admin', 'e10adc3949ba59abbe56e057f20f883e', 'ADMIN', '系统管理员', '13800000000'),
('landlord1', 'e10adc3949ba59abbe56e057f20f883e', 'LANDLORD', '张房东', '13800000001'),
('landlord2', 'e10adc3949ba59abbe56e057f20f883e', 'LANDLORD', '李房东', '13800000002'),
('tenant1', 'e10adc3949ba59abbe56e057f20f883e', 'TENANT', '王小明', '13800000003'),
('tenant2', 'e10adc3949ba59abbe56e057f20f883e', 'TENANT', '陈小红', '13800000004');

-- 测试房源数据
INSERT INTO `house` (`landlord_id`, `name`, `city`, `address`, `house_type`, `area`, `facilities`, `price`, `max_guests`, `description`, `cover_image`, `status`, `rating`, `review_count`) VALUES
(2, '海边日出小院', '三亚', '天涯区三亚湾路168号', 'WHOLE', 120, 'WiFi,空调,洗衣机,厨房,海景阳台', 388.00, 4, '独栋海边小院，观日出最佳位置，步行3分钟到沙滩，周边有海鲜市场和超市，设施齐全，适合家庭度假。', '/images/8526197_165417356101_2.jpg', 'ONLINE', 4.5, 12),
(2, '市中心文艺公寓', '北京', '海淀区中关村大街88号', 'PRIVATE', 45, 'WiFi,空调,暖气,电视', 258.00, 2, '文艺范精装公寓，位于中关村核心地段，步行5分钟到地铁站，周边高校云集，适合商务出差和短途旅行。', '/images/8084.jpg_wh860.jpg', 'ONLINE', 4.8, 8),
(2, '西湖畔花园别墅', '杭州', '西湖区龙井路99号', 'WHOLE', 200, 'WiFi,空调,厨房,花园,停车位', 688.00, 6, '紧邻西湖的私家花园别墅，步行可至龙井茶园，独立庭院可烧烤聚会，适合团建和家庭出游。', '/images/0223.jpg_wh860.jpg', 'ONLINE', 4.2, 5),
(3, '宽窄巷子四合院', '成都', '青羊区宽窄巷子39号', 'WHOLE', 150, 'WiFi,空调,厨房,庭院,棋牌室', 458.00, 4, '正宗老成都四合院改造，位于宽窄巷子核心区域，品味地道成都慢生活，周边美食云集。', '/images/32761360_161153636286_2.jpg', 'ONLINE', 4.6, 15),
(3, '鼓浪屿海景房', '厦门', '思明区鼓浪屿龙头路3号', 'PRIVATE', 35, 'WiFi,空调,海景窗', 328.00, 2, '鼓浪屿岛上精品民宿，窗外就是大海，每天伴着海浪声入眠，步行可达日光岩和菽庄花园。', '/images/OIP-C.webp', 'ONLINE', 4.3, 6),
(2, '江南水乡枕水人家', '苏州', '姑苏区平江路200号', 'SHARED', 25, 'WiFi,空调', 168.00, 2, '位于平江路历史街区，推窗即见小桥流水，体验最原汁原味的江南水乡生活，含地道苏式早餐。', '/images/veer-310173216.jpg', 'ONLINE', 4.0, 3);

-- 测试评价数据
INSERT INTO `comment` (`house_id`, `user_id`, `order_id`, `rating`, `content`, `reply`) VALUES
(1, 4, 1, 5, '非常棒的住宿体验！房子干净整洁，离海滩很近，房东很热情，还给我们推荐了附近好吃的海鲜餐厅。', '感谢您的评价，欢迎下次再来！'),
(1, 5, 1, 4, '环境很好，设施齐全，就是小区入口有点难找。总体满意！', '谢谢提醒，我们会在入住指南中补充详细路线。'),
(3, 4, 1, 5, '位置太好了，出门就是西湖。别墅很宽敞，花园打理得很好，非常适合家庭聚会。', '很高兴您喜欢，欢迎再次光临！'),
(4, 5, 1, 5, '四合院很有味道，住着很舒服。旁边就是宽窄巷子各种美食，太方便了！', '感谢好评，成都欢迎您再来！');
