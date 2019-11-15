CREATE TABLE IF NOT EXISTS files
(
    id        BIGINT PRIMARY KEY,
    file_id   VARCHAR NOT NULL UNIQUE,
    file_path VARCHAR,
    file_name VARCHAR NOT NULL,
    header    TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS datasets
(
    id         BIGSERIAL PRIMARY KEY,
    dataset_id VARCHAR NOT NULL,
    file_id    VARCHAR NOT NULL,
    UNIQUE (dataset_id, file_id)
);

INSERT INTO files (id, file_id, file_path, file_name, header)
SELECT 1,
       'EGAF00000000014',
       NULL,
       'video.mov',
       '637279707434676801000000010000006c00000000000000ccebd38e28559448102969bbe944a80e8149e049891927d0ebb48e27f7653545736807e3f59bdafe08a9b2573505228097120df5f4078593d8414bbd320f02cd50b300f64b318ebee1f78f8b5e47e04f54baabecd3d19efbbaf74e4f2cf1c213ad6d2cdf'
WHERE NOT EXISTS(SELECT id from files WHERE id = 1);

INSERT INTO datasets (id, dataset_id, file_id)
SELECT 1, 'EGAD00010000919', 'EGAF00000000014'
WHERE NOT EXISTS(SELECT id from datasets WHERE id = 1);
