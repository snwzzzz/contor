-- Read-only role for user-written SQL (later phase). It gets SELECT on generated collection tables only:
-- CollectionSchemaService grants it per table when a collection is created. No login, not wired to anything yet.
do
$$
begin
    if not exists (select 1 from pg_roles where rolname = 'app_readonly') then
        create role app_readonly nologin;
    end if;
end
$$;
