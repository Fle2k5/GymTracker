CREATE TABLE fitness_profiles (
    id BIGINT PRIMARY KEY,
    sex VARCHAR(16) NOT NULL,
    height_cm DOUBLE PRECISION NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    CONSTRAINT ck_profile_sex CHECK (sex IN ('male', 'female')),
    CONSTRAINT ck_profile_height CHECK (height_cm BETWEEN 100 AND 250),
    CONSTRAINT ck_profile_weight CHECK (weight_kg BETWEEN 25 AND 400)
);
