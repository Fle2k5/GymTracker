CREATE TABLE nutrition_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_date DATE NOT NULL,
    meal_type VARCHAR(40) NOT NULL,
    food_name VARCHAR(160) NOT NULL,
    calories INT NOT NULL,
    protein INT NOT NULL,
    carbs INT NOT NULL,
    fat INT NOT NULL,
    notes VARCHAR(400),
    CONSTRAINT ck_nutrition_values CHECK (calories >= 0 AND protein >= 0 AND carbs >= 0 AND fat >= 0),
    INDEX idx_nutrition_date (entry_date, id)
) ENGINE=InnoDB;

CREATE TABLE milestones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(140) NOT NULL,
    milestone_date DATE NOT NULL,
    description VARCHAR(600),
    image_name VARCHAR(180) NOT NULL,
    image_type VARCHAR(80) NOT NULL,
    image_data LONGBLOB NOT NULL,
    INDEX idx_milestone_date (milestone_date, id)
) ENGINE=InnoDB;
