-- V6: 로그인 계정. ADMIN = 창고 관리자(작업자 계정 생성), WORKER = 현장 작업자
CREATE TABLE user_account (
  user_id     INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  login_id    VARCHAR(50)  NOT NULL UNIQUE,
  password    VARCHAR(100) NOT NULL,          -- BCrypt 해시(60자). 평문 저장 금지
  user_name   VARCHAR(50)  NOT NULL,
  role        VARCHAR(20)  NOT NULL,          -- ADMIN / WORKER
  created_at  DATETIME     NOT NULL DEFAULT NOW(),
  updated_at  DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  CHECK (role IN ('ADMIN', 'WORKER'))
);

-- 최초 관리자 1명. 비밀번호 1234 (BCrypt 해시). 작업자는 관리자가 화면에서 생성
INSERT INTO user_account (login_id, password, user_name, role) VALUES
('admin', '$2a$10$dq5YRcvVNd9stYj70NQr0eP7RPrG3lABnP3VSbAquVBboDqiSPwdC', '창고관리자', 'ADMIN');