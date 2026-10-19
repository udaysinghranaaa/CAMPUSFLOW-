-- Only the account identity captured from this run's successful provisioning response.
-- Lock before checking ownership; child and parent deletion are one transaction.
BEGIN;
WITH locked AS (
    SELECT * FROM user_accounts WHERE id = :'account_id'::bigint FOR UPDATE
)
SELECT count(*) = 0 AS absent,
       coalesce(bool_and(email = lower(btrim(:'email')) AND role = :'role'
           AND ((role = 'STUDENT' AND student_id = :'profile_id'::bigint AND faculty_id IS NULL)
             OR (role = 'FACULTY' AND faculty_id = :'profile_id'::bigint AND student_id IS NULL))), false) AS owned
FROM locked
\gset

\if :absent
    -- A retry after a committed deletion is successful; the FK excludes orphan tokens.
    SELECT :'account_id'::bigint;
\elif :owned
    DELETE FROM refresh_tokens WHERE user_account_id = :'account_id'::bigint;
    DELETE FROM user_accounts WHERE id = :'account_id'::bigint RETURNING id;
\else
    DO $$ BEGIN RAISE EXCEPTION 'Verification account ownership mismatch; no records deleted'; END $$;
\endif
COMMIT;
