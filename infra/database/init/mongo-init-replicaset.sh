#!/bin/sh
# O Transactional Outbox exige transação multi-documento, e o MongoDB só a
# oferece em replica set. Nó único basta em desenvolvimento — ADR-008/ADR-017.
set -e
until mongosh --host mongodb --quiet --eval 'db.adminCommand({ ping: 1 })' >/dev/null 2>&1; do
  echo "aguardando mongodb..."
  sleep 2
done

if mongosh --host mongodb --quiet --eval 'rs.status().ok' >/dev/null 2>&1; then
  echo "replica set ja iniciado"
else
  echo "iniciando replica set rs0"
  mongosh --host mongodb --quiet --eval \
    'rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "mongodb:27017" }] })'
fi
