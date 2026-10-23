import type { ButtonHTMLAttributes } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'danger';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
  fullWidth?: boolean;
}

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'border-secondary bg-secondary text-text-primary hover:border-gold-hover hover:bg-gold-hover',
  secondary: 'border-border-strong bg-surface text-text-primary hover:bg-surface-muted',
  danger: 'border-error bg-error text-white hover:bg-[#A61F1F]',
};

export function Button({
  className = '',
  type = 'button',
  variant = 'primary',
  loading = false,
  fullWidth = true,
  disabled,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={[
        'inline-flex h-11 items-center justify-center gap-2 rounded-control border px-4 text-label transition-colors',
        fullWidth ? 'w-full' : 'w-auto',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info',
        VARIANTS[variant],
        'disabled:cursor-not-allowed disabled:border-border disabled:bg-background disabled:text-text-secondary disabled:hover:bg-background',
        className,
      ].join(' ')}
      {...props}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent"
        />
      )}
      {children}
    </button>
  );
}
