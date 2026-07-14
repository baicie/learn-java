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
  'platform.users.createDialog.submit': 'Create',
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
