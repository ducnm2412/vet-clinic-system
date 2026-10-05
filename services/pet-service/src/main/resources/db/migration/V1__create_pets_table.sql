-- Hồ sơ thú cưng, tách khỏi profile-service (VD-10).
-- owner_user_id là tài khoản khách bên auth-service, KHÔNG phải customer_profile_id: pet-service
-- không cần biết profile-service lưu hồ sơ khách thế nào, chỉ cần biết con vật này của ai.
CREATE TABLE pets (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id  UUID NOT NULL,
    name           VARCHAR(100) NOT NULL,
    species        VARCHAR(50) NOT NULL,
    breed          VARCHAR(100),
    gender         VARCHAR(10),
    date_of_birth  DATE,
    weight_kg      NUMERIC(5, 2),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pets_owner ON pets (owner_user_id);
