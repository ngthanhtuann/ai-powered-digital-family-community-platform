type ToastVariant = 'success' | 'error' | 'info' | 'warning';

const STYLES: Record<ToastVariant, { box: string; text: string; icon: string }> = {
  success: {
    box: 'border-border bg-success-soft',
    text: 'text-text-primary',
    icon: '✓',
  },
  error: {
    box: 'border-error bg-error-soft',
    text: 'text-error',
    icon: '!',
  },
  info: {
    box: 'border-info bg-info-soft',
    text: 'text-info',
    icon: 'i',
  },
  warning: {
    box: 'border-warning bg-warning-soft',
    text: 'text-warning',
    icon: '!',
  },
};

interface ToastProps {
  variant: ToastVariant;
  title: string;
  message: string;
  onClose?: () => void;
}

export function Toast({ variant, title, message, onClose }: ToastProps) {
  const style = STYLES[variant];
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={`flex w-full items-start gap-3 rounded-card border p-4 shadow-card ${style.box}`}
    >
      <span
        aria-hidden="true"
        className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 text-label ${style.text}`}
      >
        {style.icon}
      </span>
      <div className={`flex min-w-0 flex-1 flex-col gap-1 text-[14px] ${style.text}`}>
        <p className="font-medium leading-5">{title}</p>
        <p className="leading-[22px]">{message}</p>
      </div>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Đóng thông báo"
          className="shrink-0 rounded-control px-2 py-1 text-label underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
        >
          Đóng
        </button>
      )}
    </div>
  );
}
