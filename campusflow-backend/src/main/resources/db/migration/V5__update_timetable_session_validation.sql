CREATE OR REPLACE FUNCTION check_timetable_session() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE term academic_terms%ROWTYPE; dep BIGINT; size INTEGER; enrolled INTEGER; batch_start INTEGER; batch_end INTEGER;
BEGIN
 IF NEW.status='CANCELLED' THEN RETURN NEW; END IF;
 SELECT * INTO STRICT term FROM academic_terms WHERE id=NEW.academic_term_id FOR SHARE;
 IF NOT term.active OR NEW.effective_start_date<term.start_date OR NEW.effective_end_date>term.end_date THEN
  RAISE EXCEPTION 'Invalid active term or session date range' USING ERRCODE='23514',CONSTRAINT='ck_session_term'; END IF;
 SELECT b.department_id,s.capacity,b.start_year,b.end_year INTO dep,size,batch_start,batch_end FROM academic_sections s JOIN academic_batches b ON b.id=s.batch_id
 WHERE s.id=NEW.section_id AND s.active AND b.active FOR SHARE OF s,b;
 IF dep IS NULL OR dep<>term.department_id OR NOT EXISTS(SELECT 1 FROM subjects WHERE id=NEW.subject_id AND active AND department_id=dep)
 OR NOT EXISTS(SELECT 1 FROM faculty WHERE id=NEW.faculty_id AND active AND department_id=dep) THEN
  RAISE EXCEPTION 'Invalid active academic references or department scope' USING ERRCODE='23514',CONSTRAINT='ck_session_scope'; END IF;
 IF extract(year FROM NEW.effective_start_date)<batch_start OR extract(year FROM NEW.effective_end_date)>batch_end THEN RAISE EXCEPTION 'Session outside batch academic years' USING ERRCODE='23514',CONSTRAINT='ck_session_batch_year'; END IF;
 SELECT count(*) INTO enrolled FROM students WHERE section_id=NEW.section_id AND status='ACTIVE';
 IF NOT EXISTS(SELECT 1 FROM rooms WHERE id=NEW.room_id AND active AND capacity>=greatest(coalesce(size,0),enrolled)) THEN
  RAISE EXCEPTION 'Inactive or undersized room' USING ERRCODE='23514',CONSTRAINT='ck_session_capacity'; END IF;
 RETURN NEW;
END $$;
