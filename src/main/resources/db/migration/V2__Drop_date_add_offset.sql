DROP TABLE soknader;

CREATE TABLE soknader (
    soknad_id VARCHAR(64) NOT NULL,
    topic_offset int NOT NULL,
    soknad JSONB,
    PRIMARY KEY (soknad_id,topic_offset)
);

