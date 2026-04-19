DROP SCHEMA IF EXISTS trailkarma CASCADE;
CREATE SCHEMA trailkarma;
CREATE TABLE trailkarma.trail_reports (report_id STRING PRIMARY KEY, user_id STRING NOT NULL, type STRING NOT NULL, title STRING NOT NULL, description STRING, lat DOUBLE NOT NULL, lng DOUBLE NOT NULL, timestamp STRING NOT NULL, species_name STRING, confidence FLOAT, source STRING NOT NULL, synced BOOLEAN);
CREATE TABLE trailkarma.location_updates (id LONG PRIMARY KEY, user_id STRING NOT NULL, timestamp STRING NOT NULL, lat DOUBLE NOT NULL, lng DOUBLE NOT NULL, synced BOOLEAN);
INSERT INTO trailkarma.trail_reports VALUES ('seed-1', 'seed-user', 'hazard', 'Rockslide ahead', 'Section near mile 24 has debris', 32.88, -117.24, '2026-04-18T17:00:00Z', NULL, NULL, 'self', true);
INSERT INTO trailkarma.trail_reports VALUES ('seed-2', 'seed-user', 'hazard', 'Rattlesnake spotted', 'Stay alert, seen near water source', 32.87, -117.25, '2026-04-18T17:05:00Z', NULL, NULL, 'relayed', true);
INSERT INTO trailkarma.trail_reports VALUES ('seed-3', 'seed-user', 'water', 'Water source confirmed', 'Spring flowing, fresh water tested', 32.89, -117.23, '2026-04-18T17:10:00Z', NULL, NULL, 'self', true);
