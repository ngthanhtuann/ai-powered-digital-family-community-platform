import type { ButtonHTMLAttributes } from 'react';

// Nút theo design-system.md mục 4 và Figma component `Button`: cao 44, bo 6.
// Hiện có kiểu Primary; Disabled dùng khi đang gửi request (Figma chưa có biến thể Loading).
type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement>;

export function Button({ className = '', type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={[
        'inline-flex h-11 w-full items-center justify-center gap-2 rounded-control border px-4 text-label',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info',
        'border-secondary bg-secondary text-text-primary hover:border-gold-hover hover:bg-gold-hover',
        'disabled:cursor-not-allowed disabled:border-border disabled:bg-background disabled:text-text-secondary',
        className,
      ].join(' ')}
      {...props}
    />
  );
}
