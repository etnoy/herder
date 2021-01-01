DROP ALL OBJECTS;
CREATE SCHEMA IF NOT EXISTS sqlinjection;

CREATE TABLE sqlinjection.users (
  name VARCHAR(255) PRIMARY KEY,
  comment VARCHAR(255));

INSERT INTO sqlinjection.users values ('Jonathan Jogenfors', 'System Author');
INSERT INTO sqlinjection.users values ('Niklas Johansson', 'Teacher');
INSERT INTO sqlinjection.users values ('Jan-Åke Larsson', 'Professor');
INSERT INTO sqlinjection.users values ('Guilherme B. Xavier', 'Examiner');
INSERT INTO sqlinjection.users values ('OR 1=1', 'You''re close! Surround the query with single quotes so that your code is interpreted');