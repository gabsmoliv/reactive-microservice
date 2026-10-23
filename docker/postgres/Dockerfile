FROM postgres:16-alpine
# Executed once by the entrypoint when the data directory is empty.
COPY init.sql /docker-entrypoint-initdb.d/init.sql
