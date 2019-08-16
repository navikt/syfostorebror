DROP TABLE soknader;

CREATE TABLE soknader (
    composit_key VARCHAR(250) PRIMARY KEY,
    soknad_id VARCHAR(64) NOT NULL,
    soknad JSONB
);