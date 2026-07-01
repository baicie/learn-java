import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import importX from 'eslint-plugin-import-x'
import tailwind from 'eslint-plugin-tailwindcss'
import prettier from 'eslint-config-prettier'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'src/components/ui/**', 'src/lib/utils.ts'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'import-x': importX,
      tailwindcss: tailwind,
    },
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        window: 'readonly',
        document: 'readonly',
        localStorage: 'readonly',
        fetch: 'readonly',
        Response: 'readonly',
        RequestInit: 'readonly',
        FormEvent: 'readonly',
        React: 'readonly',
        ReactNode: 'readonly',
        SVGSVGElement: 'readonly',
      },
    },
    settings: {
      tailwindcss: {
        cssConfigPath: resolve(__dirname, 'src/styles.css'),
      },
      'import-x': {
        typescript: { project: './tsconfig.json' },
      },
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': 'off',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports', fixStyle: 'inline-type-imports' },
      ],
      'import-x/order': [
        'warn',
        {
          groups: ['builtin', 'external', 'internal', ['parent', 'sibling', 'index'], 'type'],
          'newlines-between': 'always',
          alphabetize: { order: 'asc', caseInsensitive: true },
        },
      ],
      'import-x/no-duplicates': 'error',
      'tailwindcss/classnames-order': 'warn',
      'tailwindcss/no-custom-classname': [
        'error',
        {
          whitelist: [
            'bg-background',
            'bg-card',
            'bg-destructive',
            'bg-muted',
            'bg-popover',
            'bg-primary',
            'bg-secondary',
            'bg-sidebar',
            'border-border',
            'border-destructive',
            'border-input',
            'border-primary',
            'text-background',
            'text-card-foreground',
            'text-destructive',
            'text-foreground',
            'text-muted-foreground',
            'text-popover-foreground',
            'text-primary-foreground',
            'text-secondary-foreground',
            'ring-ring',
            'data-icon',
            // MarkdownPreview：自定义 typography 渲染样式，等正式接 @tailwindcss/typography 后再清理。
            'prose',
            'prose-sm',
            'prose-base',
            'prose-lg',
            'prose-xl',
            'max-w-none',
            // styles.css 里通过 @layer 定义的工具类，eslint-plugin-tailwindcss 默认不会扫到。
            'glass',
            'glass-card',
            'glass-card-active',
            'glass-header',
          ],
        },
      ],
      'no-console': ['warn', { allow: ['warn', 'error', 'info'] }],
    },
  },
  prettier,
)
