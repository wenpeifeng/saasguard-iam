-- 种子超管口令统一：Admin@123456 -> 123456（套件级账号规范，见套件 README；生产环境首登后必须改密）
UPDATE idm_user SET password = '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm' WHERE id = 1 AND username = 'admin';
