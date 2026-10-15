import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}', './lib/**/*.{ts,tsx}'],
  theme: {
    screens: { md: '768px', lg: '1024px' },
    extend: {
      colors: {
        primary: '#2C1810',
        secondary: '#C9A84C',
        success: '#2E7D32',
        error: '#C62828',
        info: '#1565C0',
        warning: '#BF4400',
        background: '#F5F0E8',
        surface: '#FFFFFF',
        'surface-muted': '#F9F6F0',
        'text-primary': '#2C1810',
        'text-secondary': '#7A6348',
        border: '#D4C4A8',
        'gold-hover': '#B79538',
        'cream-on-dark': '#F5E6C8',
        'border-strong': '#9C8560',
        'success-soft': '#E8F5E9',
        'error-soft': '#FCE4EC',
        'warning-soft': '#FFF3E0',
        'info-soft': '#E3F2FD',
      },
      fontSize: {
        'page-title': ['24px', { lineHeight: '32px', fontWeight: '700' }],
        'section-title': ['18px', { lineHeight: '26px', fontWeight: '600' }],
        body: ['14px', { lineHeight: '22px' }],
        label: ['14px', { lineHeight: '20px', fontWeight: '500' }],
        caption: ['12px', { lineHeight: '18px' }],
        metric: ['28px', { lineHeight: '36px', fontWeight: '700' }],
      },
      fontFamily: { sans: ['var(--font-inter)', 'system-ui', 'sans-serif'] },
      borderRadius: { control: '6px', card: '12px' },
      boxShadow: {
        card: '0 1px 3px rgba(44,24,16,0.08)',
        overlay: '0 8px 24px rgba(44,24,16,0.16)',
      },
      spacing: { sidebar: '240px', header: '64px' },
    },
  },
  plugins: [],
};

export default config;
