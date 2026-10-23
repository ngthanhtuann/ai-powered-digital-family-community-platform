import { forwardRef, useId, type InputHTMLAttributes } from 'react';

// Ô nhập theo design-system.md mục 4 và Figma component `Input` (Default / Hover / Focus / Error).
interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, TextFieldProps>(function Input(
  { label, error, id, className = '', ...inputProps },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const errorId = `${inputId}-error`;

  return (
    <div className="flex w-full flex-col gap-1.5">
      <label htmlFor={inputId} className="text-label text-text-primary">
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={[
          'h-[42px] w-full rounded-control border px-3 text-body text-text-primary outline-none',
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info',
          'disabled:cursor-not-allowed disabled:opacity-60',
          error
            ? 'border-error bg-surface'
            : 'border-border-strong bg-surface-muted hover:border-secondary hover:shadow-[inset_0_0_0_1px_#C9A84C]',
          className,
        ].join(' ')}
        {...inputProps}
      />
      {error && (
        <p id={errorId} className="text-caption text-error">
          {error}
        </p>
      )}
    </div>
  );
});

export const TextField = Input;
