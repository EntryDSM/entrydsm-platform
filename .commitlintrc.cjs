module.exports = {
  extends: ['@commitlint/config-conventional'],
  plugins: [
    {
      rules: {
        'issue-number-required': (parsed) => {
          const { subject } = parsed;
          const issuePattern = /#\d+/;

          if (!subject || !issuePattern.test(subject)) {
            return [false, 'commit message must include issue number (e.g., #123)'];
          }

          return [true];
        }
      }
    }
  ],
  rules: {
    'issue-number-required': [2, 'always'],
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
    'subject-max-length': [2, 'always', 72]
  }
};
