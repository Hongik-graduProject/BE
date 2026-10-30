-- Supabase Auth 제거. 이메일·비밀번호를 로컬 profiles 테이블에서 직접 관리한다.
-- 토큰 발급·검증도 백엔드가 직접 한다 (auth/provider/JwtProvider, JwtVerifier).
--
-- nullable 인 이유: V1~V8 시점에 생성된 기존 행은 Supabase 가 자격증명을 들고 있어서
-- 백필할 값이 없다. 그런 행은 로그인할 수 없고, 다시 가입하면 된다.
-- 부분 유니크 인덱스를 쓰므로 email 이 NULL 인 행은 여러 개 있어도 된다.

ALTER TABLE profiles ADD COLUMN email         VARCHAR(255) NULL;
ALTER TABLE profiles ADD COLUMN password_hash VARCHAR(100) NULL;

CREATE UNIQUE INDEX uk_profiles_email ON profiles (email) WHERE email IS NOT NULL;

COMMENT ON COLUMN profiles.email         IS '로그인 이메일. 소문자로 정규화해서 저장한다';
COMMENT ON COLUMN profiles.password_hash IS 'BCrypt 해시. 평문은 저장하지 않는다';
