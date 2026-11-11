param([Parameter(Mandatory=$true)][string]$Email)
$ErrorActionPreference = 'Stop'
if ($Email -notmatch '^r2[01]-[0-9a-f]{32}@example\.test$') { throw 'Refusing cleanup: email is not a generated R20/R21 fixture identity.' }
$sql = @'
BEGIN;
CREATE TEMP TABLE r20_cleanup_target AS
SELECT u.id AS user_id, u.company_id
FROM app_user u JOIN company c ON c.id=u.company_id
WHERE u.email=:'fixture_email' AND u.demo=false AND c.demo=false
  AND c.name ~ '^FlowPay R2[01] disposable [0-9a-f]{32}$';
DO $cleanup$
BEGIN
  IF (SELECT count(*) FROM r20_cleanup_target) > 1 THEN
    RAISE EXCEPTION 'R20 cleanup identity is ambiguous';
  END IF;
  IF EXISTS (SELECT 1 FROM r20_cleanup_target t JOIN app_user u ON u.company_id=t.company_id AND u.id<>t.user_id) THEN
    RAISE EXCEPTION 'R20 cleanup found an unexpected second operator';
  END IF;
END $cleanup$;
DELETE FROM spring_session s USING r20_cleanup_target t WHERE s.principal_name=t.user_id::text;
DELETE FROM payment_event e USING r20_cleanup_target t WHERE e.company_id=t.company_id;
DELETE FROM idempotency_record i USING r20_cleanup_target t WHERE i.company_id=t.company_id;
DELETE FROM payment p USING r20_cleanup_target t WHERE p.company_id=t.company_id;
DELETE FROM customer c USING r20_cleanup_target t WHERE c.company_id=t.company_id;
DELETE FROM app_user u USING r20_cleanup_target t WHERE u.id=t.user_id AND u.company_id=t.company_id;
DELETE FROM company c USING r20_cleanup_target t WHERE c.id=t.company_id AND c.demo=false AND c.name ~ '^FlowPay R2[01] disposable [0-9a-f]{32}$';
COMMIT;
SELECT CASE WHEN EXISTS (SELECT 1 FROM app_user WHERE email=:'fixture_email') THEN 'cleanup_failed' ELSE 'cleanup_verified' END;
'@
Push-Location (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
try {
    $output = $sql | docker compose exec -T database psql -U flowpay -d flowpay --set ON_ERROR_STOP=1 --tuples-only --no-align --set "fixture_email=$Email"
    if ($LASTEXITCODE -ne 0 -or (($output -join '') -notmatch 'cleanup_verified')) { throw 'R20 fixture cleanup did not verify.' }
    Write-Output 'Disposable R20/R21 tenant cleanup verified.'
} finally { Pop-Location }
