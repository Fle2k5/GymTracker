CREATE TABLE exercises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    weight INT NOT NULL,
    reps INT NOT NULL,
    workout_date DATE NOT NULL,
    CONSTRAINT uk_exercise_name UNIQUE (name),
    CONSTRAINT ck_exercise_weight CHECK (weight >= 0),
    CONSTRAINT ck_exercise_reps CHECK (reps > 0)
) ENGINE=InnoDB;

CREATE TABLE schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_day VARCHAR(80) NOT NULL,
    muscle_group VARCHAR(160) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE schedule_exercises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,
    CONSTRAINT fk_schedule_item_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_item_exercise FOREIGN KEY (exercise_id) REFERENCES exercises (id) ON DELETE CASCADE,
    CONSTRAINT uk_schedule_exercise UNIQUE (schedule_id, exercise_id)
) ENGINE=InnoDB;

CREATE TABLE workout_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exercise_name VARCHAR(120) NOT NULL,
    weight INT NOT NULL,
    reps INT NOT NULL,
    workout_date DATE NOT NULL,
    CONSTRAINT ck_history_weight CHECK (weight >= 0),
    CONSTRAINT ck_history_reps CHECK (reps > 0),
    INDEX idx_history_exercise_date (exercise_name, workout_date, id)
) ENGINE=InnoDB;

CREATE INDEX idx_schedule_exercise_schedule ON schedule_exercises (schedule_id, id);
