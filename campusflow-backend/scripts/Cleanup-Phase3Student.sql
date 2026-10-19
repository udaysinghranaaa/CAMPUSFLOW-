-- Internal verification cleanup only; there is deliberately no Student DELETE API.
-- Both values must match the row created by this verification run.
BEGIN;
DELETE FROM students
WHERE id = :'student_id'::bigint AND enrollment_number = :'enrollment_number';
-- Return success for an already absent row, but never for an existing mismatched row.
SELECT :'student_id'::bigint WHERE NOT EXISTS (
    SELECT 1 FROM students WHERE id = :'student_id'::bigint
);
COMMIT;
