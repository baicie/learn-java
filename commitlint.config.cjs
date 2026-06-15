const validTypes = ['feat', 'fix', 'refactor', 'perf', 'test', 'docs', 'build', 'ci', 'infra', 'db', 'chore', 'revert']
const validScopes = [
  'server',
  'worker',
  'runner',
  'common',
  'web',
  'security',
  'tenant',
  'user',
  'datasource',
  'asset',
  'alert',
  'incident',
  'rca',
  'ai',
  'runbook',
  'automation',
  'audit',
  'notification',
  'zabbix-adapter',
  'vm-adapter',
  'clickhouse-adapter',
  'otel-adapter',
  'rum',
  'console',
  'infra',
  'db',
  'deps',
  'config',
  'docs',
  'agent',
  'governance',
]

module.exports = {
  parserPreset: {
    parserOpts: {
      headerPattern: /^(\w+)\(([^)]+)\): (.+)$/,
      headerCorrespondence: ['type', 'scope', 'subject'],
    },
  },
  plugins: [
    {
      rules: {
        'aegisops-subject': ({ subject }) => {
          if (!subject || !/[\u4e00-\u9fff]/.test(subject)) {
            return [false, 'subject 必须使用中文主题']
          }
          if (subject.length > 50) {
            return [false, 'subject 不能超过 50 个字符']
          }
          if (/[。.!！]$/.test(subject)) {
            return [false, 'subject 不能以句号或感叹号结尾']
          }
          return [true]
        },
        'aegisops-scope-enum': ({ scope }) => {
          if (!scope) {
            return [false, 'scope 必填']
          }
          const invalid = scope.split(',').find((item) => !validScopes.includes(item))
          return invalid ? [false, `scope 不在固定枚举内: ${invalid}`] : [true]
        },
      },
    },
  ],
  rules: {
    'type-enum': [2, 'always', validTypes],
    'type-case': [2, 'always', 'lower-case'],
    'scope-empty': [2, 'never'],
    'subject-empty': [2, 'never'],
    'header-max-length': [2, 'always', 80],
    'aegisops-subject': [2, 'always'],
    'aegisops-scope-enum': [2, 'always'],
  },
}
