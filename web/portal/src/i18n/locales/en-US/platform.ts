export const platform = {
  'nav.dashboard': 'Dashboard',
  'nav.workRecords.group': 'Work Records',
  'nav.workRecords.records': 'Records',
  'nav.workRecords.designer': 'Form Designer',
  'nav.workRecords.tasks': 'Import / Export Tasks',
  'nav.workRecords.operations': 'Operations',
  'nav.platform.group': 'Platform',
  'nav.platform.users': 'Users',
  'nav.platform.roles': 'Roles & Permissions',
  'nav.platform.dictionaries': 'Dictionaries',
  'nav.platform.calendars': 'Calendars',
  'nav.platform.audit': 'Audit Logs',
  'platform.nav.root': 'Platform',
  'platform.nav.users': 'Users',
  'platform.nav.roles': 'Roles & Permissions',
  'platform.nav.dictionaries': 'Dictionaries',
  'platform.dictionaries.title': 'Dictionaries',
  'platform.dictionaries.description':
    'Maintain work record and platform enumerations.',
  'platform.roles.title': 'Roles & Permissions',
  'platform.roles.description': 'Manage roles and permission assignments.',
  'platform.users.title': 'Users',
  'platform.users.description':
    'Manage tenant users, status, roles and data scopes.',
  'platform.users.create': 'New user',
  'platform.users.empty.title': 'No users yet',
  'platform.users.empty.description': 'Create the first real platform user.',
  'platform.users.error.version_conflict':
    'The user was modified concurrently. Please refresh and try again.',
  'platform.users.error.username_conflict': 'Username is already taken.',
  'platform.users.error.not_found':
    'User does not exist or has already been deleted.',
  'platform.users.confirm.disable.title': 'Disable user {name}',
  'platform.users.confirm.disable.description':
    'After disabling, the user will no longer be able to sign in.',
  'platform.users.confirm.disable.confirmLabel': 'Confirm disable',
  'platform.users.createDialog.title': 'Create user',
  'platform.users.createDialog.description':
    'New users are active by default. Email is optional and the initial password must be at least 8 characters.',
  'platform.users.createDialog.username': 'Username',
  'platform.users.createDialog.roles': 'Initial roles',
  'platform.users.createDialog.displayName': 'Display name',
  'platform.users.createDialog.email': 'Email',
  'platform.users.createDialog.emailHint':
    'Optional; enter a valid email address when provided.',
  'platform.users.createDialog.password': 'Initial password',
  'platform.users.createDialog.cancel': 'Cancel',
  'platform.users.createDialog.submit': 'Create',
  'platform.users.createDialog.validation.username':
    'Enter a username (3-64 characters)',
  'platform.users.createDialog.validation.displayName': 'Enter a display name',
  'platform.users.createDialog.validation.email': 'Enter a valid email address',
  'platform.users.createDialog.validation.password':
    'Enter an initial password (8-128 characters)',
  'platform.user.status.active': 'Active',
  'platform.user.status.disabled': 'Disabled',
  'platform.user.status.locked': 'Locked',
  'platform.user.status.pending': 'Pending',
  'platform.roles.create': 'New role',
  'platform.roles.delete.system': 'System roles cannot be deleted',
  'platform.roles.dirty.confirmLeave':
    'Unsaved changes on the current role will be lost when switching.',
  'platform.roles.danger.title': 'Dangerous permission confirmation',
  'platform.roles.danger.description':
    'The following permissions are high-risk. A change reason and explicit acknowledgement are required.',
  'platform.roles.error.protected':
    'System roles cannot be modified or deleted.',
  'platform.roles.error.hasActiveUsers':
    'Cannot delete a role with active users.',
  'platform.roles.error.permissionRemoved':
    'Cannot remove permissions that are still held by active users.',
  'platform.roles.error.versionConflict':
    'The role was modified concurrently. Please refresh and try again.',
  'platform.roles.error.notFound':
    'Role does not exist or has already been deleted.',
} as const
