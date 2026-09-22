CREATE TABLE app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(190) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email)
) ENGINE=InnoDB;

INSERT INTO app_users (id,email,display_name,password_hash,role,enabled,created_at)
VALUES (1,'legacy-disabled@gymtracker.invalid','Legacy data','!','USER',FALSE,CURRENT_TIMESTAMP(6));

ALTER TABLE exercises ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE schedules ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE schedule_exercises ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE workout_history ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE nutrition_entries ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE milestones ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE fitness_profiles ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE exercises DROP INDEX uk_exercise_name, ADD CONSTRAINT uk_exercise_owner_name UNIQUE (owner_id,name);
ALTER TABLE schedule_exercises DROP INDEX uk_schedule_exercise, ADD CONSTRAINT uk_schedule_exercise UNIQUE (owner_id,schedule_id,exercise_id);
ALTER TABLE fitness_profiles ADD CONSTRAINT uk_fitness_profile_owner UNIQUE (owner_id);

ALTER TABLE exercises ADD CONSTRAINT fk_exercise_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
ALTER TABLE schedules ADD CONSTRAINT fk_schedule_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
ALTER TABLE schedule_exercises ADD CONSTRAINT fk_schedule_exercise_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
ALTER TABLE workout_history ADD CONSTRAINT fk_history_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
ALTER TABLE nutrition_entries ADD CONSTRAINT fk_nutrition_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
ALTER TABLE milestones ADD CONSTRAINT fk_milestone_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
ALTER TABLE fitness_profiles ADD CONSTRAINT fk_fitness_profile_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);

CREATE INDEX idx_exercise_owner ON exercises(owner_id);
CREATE INDEX idx_schedule_owner ON schedules(owner_id);
CREATE INDEX idx_history_owner_date ON workout_history(owner_id,workout_date,id);
CREATE INDEX idx_nutrition_owner_date ON nutrition_entries(owner_id,entry_date,id);
CREATE INDEX idx_milestone_owner_date ON milestones(owner_id,milestone_date,id);
