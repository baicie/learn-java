-- Remove an unusable sensitive permission from the default normal-user role.
-- work-record:export:async depends on work-record:export, which normal_user does not own.
delete from iam.role_permission
where role_code = 'normal_user'
  and permission_code = 'work-record:export:async';
