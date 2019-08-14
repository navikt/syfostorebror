DROP TABLE soknader;

CREATE TABLE soknader (
    soknad_id VARCHAR(64) NOT NULL,
    soknad_status VARCHAR(64) NOT NULL,
    soknad JSONB,
    PRIMARY KEY (soknad_id,soknad_status)
);