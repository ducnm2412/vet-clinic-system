CREATE TABLE customer_profiles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL UNIQUE,
    phone         VARCHAR(20),
    date_of_birth DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE addresses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_profile_id UUID NOT NULL REFERENCES customer_profiles (id) ON DELETE CASCADE,
    line1               VARCHAR(255) NOT NULL,
    line2               VARCHAR(255),
    ward                VARCHAR(100),
    city                VARCHAR(100) NOT NULL,
    is_default          BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_profile_id UUID NOT NULL REFERENCES customer_profiles (id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    species             VARCHAR(50) NOT NULL,
    breed               VARCHAR(100),
    gender              VARCHAR(10),
    date_of_birth       DATE,
    weight_kg           NUMERIC(5, 2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE,
    specialty           VARCHAR(100) NOT NULL,
    phone               VARCHAR(20),
    bio                 TEXT,
    years_of_experience INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_licenses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    license_number    VARCHAR(100) NOT NULL,
    issued_by         VARCHAR(255) NOT NULL,
    issued_date       DATE NOT NULL,
    expiry_date       DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE staff_profiles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL UNIQUE,
    position   VARCHAR(100) NOT NULL,
    phone      VARCHAR(20),
    hire_date  DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
