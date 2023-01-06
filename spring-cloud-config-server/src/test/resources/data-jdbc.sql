INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('foo', 'bar', 'master', 'a.b.c', 'foo-bar');
INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('foo', 'default', 'master', 'a.b.c', 'foo-default');
INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('foo', null, 'master', 'a.b.c', 'foo-null');
INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('application', 'bar', 'master', 'a.b.c', 'application-bar');
INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('application', 'default', 'master', 'a.b.c', 'application-default');
INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('application', null, 'master', 'a.b.c', 'application-null');

INSERT into MY_PROPERTIES(APPLICATION, PROFILE, LABEL, MY_KEY, MY_VALUE) values ('foo', 'bar', 'master', 'a.b.c', 'foo-bar');
INSERT into MY_PROPERTIES(APPLICATION, PROFILE, LABEL, MY_KEY, MY_VALUE) values ('foo', 'default', 'master', 'a.b.c', 'foo-default');
INSERT into MY_PROPERTIES(APPLICATION, PROFILE, LABEL, MY_KEY, MY_VALUE) values ('foo', null, 'master', 'a.b.c', 'foo-null');
INSERT into MY_PROPERTIES(APPLICATION, PROFILE, LABEL, MY_KEY, MY_VALUE) values ('application', 'bar', 'master', 'a.b.c', 'application-bar');
INSERT into MY_PROPERTIES(APPLICATION, PROFILE, LABEL, MY_KEY, MY_VALUE) values ('application', 'default', 'master', 'a.b.c', 'application-default');
INSERT into MY_PROPERTIES(APPLICATION, PROFILE, LABEL, MY_KEY, MY_VALUE) values ('application', null, 'master', 'a.b.c', 'application-null');

INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('foo', 'bar', 'main', 'a.b.c', 'foo-bar');
INSERT into PROPERTIES(APPLICATION, PROFILE, LABEL, "KEY", "VALUE") values ('application', 'bar', 'main', 'a.b.c', 'application-bar');
