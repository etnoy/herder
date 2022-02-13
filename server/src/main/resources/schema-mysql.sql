SET GLOBAL sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''));

DROP
    SCHEMA IF EXISTS herder;

CREATE
    SCHEMA herder;

USE herder;

CREATE
    TABLE
        user(
            id BIGINT AUTO_INCREMENT,
            display_name VARCHAR(191) NOT NULL UNIQUE,
            class_id INT NULL,
            account_created DATETIME NOT NULL,
			is_enabled BOOLEAN DEFAULT FALSE,
            is_admin BOOLEAN DEFAULT FALSE NOT NULL,
            suspended_until DATETIME NULL DEFAULT NULL,
            suspension_message VARCHAR(191),
            user_key BINARY(16) NOT NULL,
            PRIMARY KEY(id),
            INDEX class_id(
                class_id ASC
            )
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        class(
            id BIGINT AUTO_INCREMENT,
            name VARCHAR(191) NOT NULL UNIQUE,
            PRIMARY KEY(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        module(
            id BIGINT AUTO_INCREMENT,
            name VARCHAR(191) NOT NULL UNIQUE,
            display_name VARCHAR(191) NULL,
            is_flag_static BOOLEAN DEFAULT FALSE,
            static_flag VARCHAR(128) NULL,
            module_key BINARY(64) NOT NULL,
            is_open BOOLEAN DEFAULT FALSE,
            PRIMARY KEY(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        csrf_attack(
            id BIGINT AUTO_INCREMENT,
            pseudonym VARCHAR(128) NOT NULL,
            module_name VARCHAR(191) NOT NULL,
            started TIMESTAMP NOT NULL,
            finished TIMESTAMP,
            PRIMARY KEY(id),
            FOREIGN KEY(module_name) REFERENCES module(name)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        correction(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL,
            amount INT NOT NULL,
            TIME TIMESTAMP NOT NULL,
            description VARCHAR(191),
            PRIMARY KEY(id),
            FOREIGN KEY(user_id) REFERENCES user(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        module_tag(
            id BIGINT AUTO_INCREMENT,
            module_name VARCHAR(191) NOT NULL,
            tag_name VARCHAR(64) NOT NULL,
            tag_value VARCHAR(64) NOT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(
                module_name
            ) REFERENCES module(name)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        module_point(
            id BIGINT AUTO_INCREMENT,
            module_name VARCHAR(191) NOT NULL,
            submission_rank INT NOT NULL,
            points INT NOT NULL,
            PRIMARY KEY(id),
            UNIQUE KEY(
                module_name,
                submission_rank
            ),
            FOREIGN KEY(
                module_name
            ) REFERENCES module(name)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        saml_auth(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL UNIQUE,
            saml_id VARCHAR(40) NOT NULL UNIQUE,
            PRIMARY KEY(id),
            FOREIGN KEY(user_id) REFERENCES user(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        password_auth(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL UNIQUE,
            login_name VARCHAR(191) NOT NULL UNIQUE,
            hashed_password VARCHAR(191) NOT NULL,
            is_password_non_expired BOOLEAN DEFAULT FALSE,
            PRIMARY KEY(id),
            FOREIGN KEY(user_id) REFERENCES user(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        submission(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL,
            module_name VARCHAR(191) NOT NULL,
            TIME DATETIME NULL DEFAULT NULL,
            is_valid BOOLEAN NOT NULL,
            flag VARCHAR(191) NOT NULL,
            valid_or_null BOOLEAN AS(
                IF(
                    is_valid = TRUE,
                    TRUE,
                    NULL
                )
            ) stored,
            PRIMARY KEY(id),
            UNIQUE KEY(
                user_id,
                module_name,
                valid_or_null
            ),
            FOREIGN KEY(
                `user_id`
            ) REFERENCES user(id),
            FOREIGN KEY(
                `module_name`
            ) REFERENCES module(name)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        configuration(
            id BIGINT AUTO_INCREMENT,
            config_key VARCHAR(191) NOT NULL UNIQUE,
            value VARCHAR(191) NOT NULL,
            PRIMARY KEY(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    VIEW ranked_submission AS WITH ranks AS(
        SELECT
            RANK() OVER(
                PARTITION BY module_name
            ORDER BY
                TIME
            ) AS 'rank',
            id AS submission_id,
            user_id,
            module_name,
            TIME,
            flag
        FROM
            submission
        WHERE
            is_valid = TRUE
    ) SELECT
        submission_id,
        user_id,
        `rank`,
        ranks.module_name,
        TIME,
        flag,
        base_score.points AS base_score,
        COALESCE(
            bonus_score.points,
            0
        ) AS bonus_score,
        base_score.points + COALESCE(
            bonus_score.points,
            0
        ) AS score
    FROM
        ranks
    LEFT JOIN module_point AS bonus_score ON
        (
            ranks.module_name = bonus_score.module_name
            AND `rank`= bonus_score.submission_rank
        )
    INNER JOIN module_point AS base_score ON
        (
            ranks.module_name = base_score.module_name
            AND base_score.submission_rank = 0
        );

CREATE
    VIEW scoreboard AS SELECT
        RANK() OVER(
        ORDER BY
            SUM( score ) DESC,
            SUM( CASE WHEN `rank`= 1 THEN 1 ELSE 0 END ) DESC,
            SUM( CASE WHEN `rank`= 2 THEN 1 ELSE 0 END ) DESC,
            SUM( CASE WHEN `rank`= 3 THEN 1 ELSE 0 END ) DESC
        ) AS 'rank',
        user_id,
        display_name,
        CAST(
            SUM( score ) AS SIGNED
        ) AS score,
        SUM( CASE WHEN `rank`= 1 THEN 1 ELSE 0 END ) AS gold_medals,
        SUM( CASE WHEN `rank`= 2 THEN 1 ELSE 0 END ) AS silver_medals,
        SUM( CASE WHEN `rank`= 3 THEN 1 ELSE 0 END ) AS bronze_medals
    FROM
        (
	select user_id, score, `rank`, display_name from
                   (SELECT
                user_id,
                score,
                `rank`
            FROM
                ranked_submission
                UNION ALL SELECT
                user_id,
                amount AS score,
                0
            FROM
                correction) scores
                        JOIN 
        user on user_id = user.id UNION ALL SELECT id, 0, 0, display_name from user
        ) AS all_scores
    GROUP BY
        user_id
    ORDER BY
        'rank' DESC;