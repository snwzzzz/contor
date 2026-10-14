-- Number of consecutive lockouts of a user; the lock duration doubles with every repeated lockout. Reset on successful login.
alter table "app_user" add column lockout_count int not null default 0;
