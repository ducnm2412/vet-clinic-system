-- VD-05: refresh token luu o server de thu hoi duoc.
--
-- Chi luu ban bam SHA-256 (64 ky tu hex), khong bao gio luu chuoi goc: lo bang nay thi
-- nguoi doc duoc cung khong co token nao dung duoc.
--
-- Moi lan lam moi, dong cu bi danh dau revoked_at va sinh dong moi (xoay vong). Dong da
-- thu hoi van giu lai de nhan ra khi co ke dem token cu ra dung lai.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Thu hoi toan bo phien cua mot nguoi (khi phat hien token bi dung lai) quet theo user_id.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
