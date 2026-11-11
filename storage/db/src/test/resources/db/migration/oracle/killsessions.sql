DECLARE
   lc_username VARCHAR2 (32) := '${schema}';
BEGIN
   FOR ln_cur IN (SELECT sid, serial# FROM v$session WHERE username = lc_username)
   LOOP
      EXECUTE IMMEDIATE ('ALTER SYSTEM KILL SESSION ''' || ln_cur.sid || ',' || ln_cur.serial# || ''' IMMEDIATE');
   END LOOP;
END;