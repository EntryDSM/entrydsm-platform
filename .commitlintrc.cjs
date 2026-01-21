module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      ['fix', 'feature', 'enhancement', 'docs', 'chore', 'hotfix', 'test', 'refactor']
    ],

    'scope-empty': [2, 'never'],
    'scope-enum': [
      2,
      'always',
      [
        'contracts',
        'documents',
        'packages',
        'admission',
        'analytics',
        'application',
        'document',
        'evaluation',
        'gateway',
        'identity',
        'notification',
        'observability',
        'schedule',
        'infra',
        'ci'
      ]
    ],

    'subject-empty': [2, 'never'],
    'subject-max-length': [2, 'always', 72],

    'subject-pattern': [2, 'always', '.*\\(#[0-9]+\\)$'],
    'subject-pattern-error-message': [
      2,
      'always',
      'Commit subject must end with an issue reference like "(#123)".'
    ]
  }
};
