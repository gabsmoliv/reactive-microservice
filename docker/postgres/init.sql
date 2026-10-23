-- Executed once by the postgres image entrypoint (empty data directory only).
-- One database and one dedicated role per service: neither service can read the other's data.

CREATE ROLE auth_user LOGIN PASSWORD 'auth_pass';
CREATE ROLE task_user LOGIN PASSWORD 'task_pass';

CREATE DATABASE auth_db OWNER auth_user;
CREATE DATABASE task_db OWNER task_user;

REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
REVOKE ALL ON DATABASE task_db FROM PUBLIC;
