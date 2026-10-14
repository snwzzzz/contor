#!/usr/bin/env bash
# Dumps what a running contor holds (proposals, every collection's records with their source mails, relations, mails)
# from its Postgres container into a folder, for live-compare.mjs. Demo mail is matched by its provider message id
# ("demo-m-0001"). Usage: scripts/business-dataset/dump-live.sh <out-dir> [container] (default: the newest pgvector
# container; database and user "test" as Testcontainers creates them, override with DB_NAME / DB_USER).
set -euo pipefail
OUT=${1:?usage: dump-live.sh <out-dir> [container]}
CONTAINER=${2:-$(docker ps --filter ancestor=pgvector/pgvector:0.8.7-pg18-trixie --format '{{.ID}}' | head -1)}
DB=${DB_NAME:-test}
USER_NAME=${DB_USER:-test}
mkdir -p "$OUT"
q() { docker exec "$CONTAINER" psql -U "$USER_NAME" -d "$DB" -tAc "$1"; }
mid="replace(e.provider_message_id, 'demo-', '')"

q "select coalesce(json_agg(json_build_object('id', p.id, 'kind', p.kind, 'status', p.status, 'payload', p.payload,
     'importance', p.importance, 'email', $mid, 'createdAt', p.created_at)), '[]')
   from proposal p left join email_message e on e.id = p.source_email_id" > "$OUT/proposals.json"
q "select coalesce(json_agg(json_build_object('id', id, 'slug', slug)), '[]') from collection" > "$OUT/collections.json"
q "select coalesce(json_agg(json_build_object('id', e.id, 'email', $mid, 'state', e.processing_state, 'receivedAt', e.received_at)), '[]')
   from email_message e" > "$OUT/emails.json"
q "select coalesce(json_agg(json_build_object('type', rt.slug, 'fromC', fc.slug, 'fromId', r.from_record_id, 'toC', tc.slug,
     'toId', r.to_record_id, 'email', $mid)), '[]')
   from relation r join relation_type rt on rt.id = r.relation_type_id join collection fc on fc.id = r.from_collection_id
   join collection tc on tc.id = r.to_collection_id left join email_message e on e.id = r.source_email_id" > "$OUT/relations.json"

{
  echo "{"
  first=1
  for slug in $(q "select slug from collection order by id"); do
    [ $first = 1 ] || echo ","
    first=0
    echo "\"$slug\":"
    q "select coalesce(json_agg(json_build_object('row', to_jsonb(r), 'sources',
         (select json_agg($mid) from record_source s join email_message e on e.id = s.email_id
          where s.collection_id = (select id from collection where slug = '$slug') and s.record_id = r.id))), '[]')
       from c_$slug r"
  done
  echo "}"
} > "$OUT/records.json"
echo "dumped to $OUT"
