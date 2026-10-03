-- Quen mat khau: token dat lai mat khau, dung mot lan, het han sau 1 gio.
--
-- Giong refresh_tokens, chi luu ban bam SHA-256 (64 ky tu hex), khong bao gio luu chuoi goc:
-- lo bang nay thi nguoi doc duoc cung khong co link dat lai nao dung duoc.
--
-- used_at ghi nhan token da dung (hoac da bi huy vi co yeu cau moi hon). Dong cu van giu lai,
-- vua de nhan ra token da dung, vua de chan spam theo created_at (cooldown moi user).
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
