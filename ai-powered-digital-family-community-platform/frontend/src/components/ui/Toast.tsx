import { CircleAlert, CircleCheck, Info, TriangleAlert, X, type LucideIcon } from 'lucide-react';

type ToastVariant = 'success' | 'error' | 'info' | 'warning';

const STYLES: Record<ToastVariant, { box: string; text: string; icon: LucideIcon }> = {
  success: {
    box: 'border-border bg-success-soft',
    text: 'text-text-primary',
    icon: CircleCheck,
  },
  error: {
    box: 'border-error bg-error-soft',
    text: 'text-error',
    icon: CircleAlert,
  },
  info: {
    box: 'border-info bg-info-soft',
    text: 'text-info',
    icon: Info,
  },
  warning: {
    box: 'border-warning bg-warning-soft',
    text: 'text-warning',
    icon: TriangleAlert,
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
  const Icon = style.icon;
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={`flex w-full items-start gap-3 rounded-card border p-4 shadow-card ${style.box}`}
    >
      <Icon aria-hidden="true" size={20} strokeWidth={1.5} className={`mt-0.5 shrink-0 ${style.text}`} />
      <div className={`flex min-w-0 flex-1 flex-col gap-1 text-[14px] ${style.text}`}>
        <p className="font-medium leading-5">{title}</p>
        <p className="leading-[22px]">{message}</p>
      </div>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Đóng thông báo"
          className={`shrink-0 rounded-control focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info ${style.text}`}
        >
          <X aria-hidden="true" size={20} strokeWidth={1.5} />
        </button>
      )}
    </div>
  );
}
