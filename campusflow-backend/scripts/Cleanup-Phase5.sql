-- Only IDs/identities captured from this verification's successful creation responses.
-- No passwords/tokens in the manifest. Any mismatch rolls this entire cleanup back.
BEGIN;
SELECT set_config('campusflow.cleanup_manifest', :'manifest', true) AS stored_manifest
\gset
DO $$
DECLARE m JSONB:=current_setting('campusflow.cleanup_manifest')::jsonb; r RECORD;
BEGIN
 PERFORM 1 FROM departments WHERE id=(m->>'departmentId')::bigint AND code=m->>'code' FOR UPDATE;
 IF NOT FOUND THEN
  IF EXISTS(SELECT 1 FROM departments WHERE id=(m->>'departmentId')::bigint)
   OR EXISTS(SELECT 1 FROM timetable_sessions s JOIN jsonb_to_recordset(m->'sessions') AS x(id bigint) ON x.id=s.id)
   OR EXISTS(SELECT 1 FROM faculty_unavailability b JOIN jsonb_to_recordset(m->'blocks') AS x(id bigint) ON x.id=b.id)
   OR EXISTS(SELECT 1 FROM faculty_schedule_coverage c JOIN jsonb_to_recordset(m->'coverage') AS x(id bigint) ON x.id=c.id)
   OR EXISTS(SELECT 1 FROM subjects s JOIN jsonb_to_recordset(m->'subjects') AS x(id bigint) ON x.id=s.id)
   OR EXISTS(SELECT 1 FROM academic_terms t JOIN jsonb_to_recordset(m->'terms') AS x(id bigint) ON x.id=t.id)
   OR EXISTS(SELECT 1 FROM rooms r JOIN jsonb_to_recordset(m->'rooms') AS x(id bigint) ON x.id=r.id)
  THEN RAISE EXCEPTION 'Verification department ownership not proven; preserve all rows'; END IF;
  RETURN; -- Idempotent retry only when all captured Phase5 rows are already absent.
 END IF;
 FOR r IN SELECT * FROM jsonb_to_recordset(m->'sessions') AS x(id bigint,term_id bigint) LOOP
  IF EXISTS(SELECT 1 FROM timetable_sessions WHERE id=r.id AND academic_term_id<>r.term_id) THEN RAISE EXCEPTION 'Session ownership mismatch'; END IF;
  IF EXISTS(SELECT 1 FROM timetable_sessions s JOIN academic_terms t ON t.id=s.academic_term_id WHERE s.id=r.id AND t.department_id<>(m->>'departmentId')::bigint) THEN RAISE EXCEPTION 'Session department mismatch'; END IF;
  DELETE FROM schedule_allocations WHERE session_id=r.id;
  DELETE FROM session_exceptions WHERE timetable_session_id=r.id;
  DELETE FROM timetable_sessions WHERE id=r.id AND academic_term_id=r.term_id;
 END LOOP;
 FOR r IN SELECT * FROM jsonb_to_recordset(m->'blocks') AS x(id bigint,faculty_id bigint) LOOP
  IF EXISTS(SELECT 1 FROM faculty_unavailability WHERE id=r.id AND faculty_id<>r.faculty_id) THEN RAISE EXCEPTION 'Block ownership mismatch'; END IF;
  DELETE FROM schedule_allocations WHERE block_id=r.id;
  DELETE FROM faculty_unavailability WHERE id=r.id AND faculty_id=r.faculty_id;
 END LOOP;
 FOR r IN SELECT * FROM jsonb_to_recordset(m->'coverage') AS x(id bigint,term_id bigint,faculty_id bigint) LOOP
  IF EXISTS(SELECT 1 FROM faculty_schedule_coverage WHERE id=r.id AND (academic_term_id<>r.term_id OR faculty_id<>r.faculty_id)) THEN RAISE EXCEPTION 'Coverage ownership mismatch'; END IF;
  DELETE FROM faculty_schedule_coverage WHERE id=r.id AND academic_term_id=r.term_id AND faculty_id=r.faculty_id;
 END LOOP;
 FOR r IN SELECT * FROM jsonb_to_recordset(m->'subjects') AS x(id bigint,code text) LOOP
  IF EXISTS(SELECT 1 FROM subjects WHERE id=r.id AND (code<>r.code OR department_id<>(m->>'departmentId')::bigint)) THEN RAISE EXCEPTION 'Subject ownership mismatch'; END IF;
  DELETE FROM subjects WHERE id=r.id AND code=r.code AND department_id=(m->>'departmentId')::bigint;
 END LOOP;
 FOR r IN SELECT * FROM jsonb_to_recordset(m->'terms') AS x(id bigint,name text) LOOP
  IF EXISTS(SELECT 1 FROM academic_terms WHERE id=r.id AND (name<>r.name OR department_id<>(m->>'departmentId')::bigint)) THEN RAISE EXCEPTION 'Term ownership mismatch'; END IF;
  DELETE FROM academic_terms WHERE id=r.id AND name=r.name AND department_id=(m->>'departmentId')::bigint;
 END LOOP;
 FOR r IN SELECT * FROM jsonb_to_recordset(m->'rooms') AS x(id bigint,code text) LOOP
  IF EXISTS(SELECT 1 FROM rooms WHERE id=r.id AND code<>r.code) THEN RAISE EXCEPTION 'Room ownership mismatch'; END IF;
  DELETE FROM rooms WHERE id=r.id AND code=r.code;
 END LOOP;
END $$;
SELECT 'CLEANED';
COMMIT;