FROM postgres:17

# Install pgvector
RUN apt-get update && \
    apt-get install -y build-essential git postgresql-server-dev-$PG_MAJOR && \
    git clone https://github.com/pgvector/pgvector.git /pgvector && \
    cd /pgvector && \
    make && make install && \
    rm -rf /pgvector && \
    apt-get remove -y build-essential git postgresql-server-dev-$PG_MAJOR && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*
