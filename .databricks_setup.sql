CREATE SCHEMA IF NOT EXISTS main.trailkarma;
DROP TABLE IF EXISTS main.trailkarma.users;
DROP TABLE IF EXISTS main.trailkarma.trail_reports;
DROP TABLE IF EXISTS main.trailkarma.location_updates;
DROP TABLE IF EXISTS main.trailkarma.relay_packets;
CREATE TABLE main.trailkarma.users (
        user_id STRING NOT NULL,
        display_name STRING NOT NULL
    ) USING DELTA;
CREATE TABLE main.trailkarma.trail_reports (
        report_id STRING NOT NULL,
        type STRING NOT NULL,
        title STRING NOT NULL,
        description STRING,
        lat DOUBLE NOT NULL,
        lng DOUBLE NOT NULL,
        timestamp STRING NOT NULL,
        species_name STRING,
        confidence DOUBLE,
        source STRING NOT NULL,
        synced BOOLEAN
    ) USING DELTA;
CREATE TABLE main.trailkarma.location_updates (
        id STRING NOT NULL,
        timestamp STRING NOT NULL,
        lat DOUBLE NOT NULL,
        lng DOUBLE NOT NULL,
        synced BOOLEAN
    ) USING DELTA;
CREATE TABLE main.trailkarma.relay_packets (
        packet_id STRING NOT NULL,
        payload_json STRING NOT NULL,
        received_at STRING NOT NULL,
        sender_device STRING,
        uploaded BOOLEAN
    ) USING DELTA;
INSERT INTO main.trailkarma.users VALUES ('f08c4260-c92c-4a46-9de5-4328dfbe5d48', 'Aldan');
INSERT INTO main.trailkarma.users VALUES ('60a31ea2-b477-445b-a46b-ec1d768b5aaf', 'Alex');
INSERT INTO main.trailkarma.users VALUES ('825f71c1-98e3-4424-ae5c-32418cf93c2f', 'Jordan');
INSERT INTO main.trailkarma.trail_reports VALUES ('mock-1', 'hazard', 'Rockslide ahead', 'Section near mile 24 has debris and loose rocks', 32.88, -117.24, '2026-04-19T00:06:07.050182Z', NULL, NULL, 'self', true);
INSERT INTO main.trailkarma.trail_reports VALUES ('mock-2', 'hazard', 'Rattlesnake spotted', 'Stay alert, seen near water source at dusk', 32.870000000000005, -117.25, '2026-04-18T22:06:07.050182Z', NULL, NULL, 'relayed', true);
INSERT INTO main.trailkarma.trail_reports VALUES ('mock-3', 'water', 'Water source confirmed', 'Spring flowing, fresh water tested and safe', 32.89, -117.25, '2026-04-18T20:06:07.050182Z', NULL, NULL, 'self', true);
INSERT INTO main.trailkarma.trail_reports VALUES ('mock-4', 'species', 'Mule deer herd', 'About 6-8 deer spotted grazing at sunrise', 32.885000000000005, -117.235, '2026-04-18T18:06:07.050182Z', 'Mule Deer', 0.92, 'self', true);
INSERT INTO main.trailkarma.trail_reports VALUES ('mock-5', 'species', 'California quail', 'Small covey moving through brush', 32.875, -117.235, '2026-04-18T16:06:07.050182Z', 'California Quail', 0.87, 'relayed', true);
INSERT INTO main.trailkarma.location_updates VALUES ('82e6418a-675b-4f59-bc8a-6ec78f8e5bb9', '2026-04-19T00:06:07.050182Z', 32.88, -117.24, true);
INSERT INTO main.trailkarma.location_updates VALUES ('b7a6ca87-a0ae-4500-8df7-3a2187961b41', '2026-04-18T23:36:07.050182Z', 32.882000000000005, -117.24199999999999, true);
INSERT INTO main.trailkarma.location_updates VALUES ('01599d7b-f4b3-4bb3-a396-340e5caedd31', '2026-04-18T23:06:07.050182Z', 32.878, -117.238, true);
INSERT INTO main.trailkarma.relay_packets VALUES ('582477a7-8fe2-453f-b45d-28e3148022a9', "{"type": "report", "report_id": "mock-2", "created_at": "2026-04-18T22:06:07.050182Z"}", '2026-04-18T22:06:07.050182Z', 'device-123', true);