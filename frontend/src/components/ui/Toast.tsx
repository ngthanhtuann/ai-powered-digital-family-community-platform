import Image from 'next/image';

// Thông báo theo Figma component `Toast` (State Default = thành công, Error).
type ToastVariant = 'success' | 'error';

const STYLES: Record<ToastVariant, { box: string; text: string; icon: string; close: string }> = {
  success: {
    box: 'border-border bg-success-soft',
    text: 'text-text-primary',
    icon: '/icons/circle-check.svg',
    close: '/icons/x.svg',
  },
  error: {
    box: 'border-error bg-error-soft',
    text: 'text-error',
    icon: '/icons/circle-alert.svg',
    close: '/icons/x-error.svg',
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
      className={`flex w-full items-start gap-3 rounded-card border p-4 ${style.box}`}
    >
      <Image src={style.icon} alt="" width={22} height={22} unoptimized className="shrink-0" />
      <div className={`flex min-w-0 flex-1 flex-col gap-1 text-[14px] ${style.text}`}>
        <p className="font-medium leading-5">{title}</p>
        <p className="leading-[22px]">{message}</p>
      </div>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Đóng thông báo"
          className="shrink-0 rounded-control focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
        >
          <Image src={style.close} alt="" width={18} height={18} unoptimized />
        </button>
      )}
    </div>
  );
}
