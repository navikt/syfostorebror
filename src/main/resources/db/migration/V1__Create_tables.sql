CREATE TABLE soknader (
    soknad_id VARCHAR(64) NOT NULL,
    innsendt_dato TIMESTAMP NOT NULL,
    soknad JSONB,
    PRIMARY KEY (soknad_id,innsendt_dato)
);
