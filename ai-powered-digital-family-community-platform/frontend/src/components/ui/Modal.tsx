'use client';

import { useEffect, useId, useRef, type ReactNode } from 'react';

interface ModalProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
  actions?: ReactNode;
}

export function Modal({ open, title, description, onClose, children, actions }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault();
          onClose();
        }
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      className="m-auto max-h-[calc(100dvh-32px)] w-[calc(100%-32px)] max-w-[640px] overflow-y-auto rounded-card border-0 bg-surface p-0 text-text-primary shadow-overlay backdrop:bg-primary/50"
    >
      <div className="flex items-start justify-between gap-4 border-b border-border bg-primary px-5 py-4 text-cream-on-dark sm:px-6">
        <div>
          <h2 id={titleId} className="text-section-title">
            {title}
          </h2>
          {description && (
            <p id={descriptionId} className="mt-1 text-body text-cream-on-dark">
              {description}
            </p>
          )}
        </div>
        <button
          type="button"
          aria-label="Đóng hộp thoại"
          onClick={onClose}
          className="rounded-control px-2 py-1 text-label text-cream-on-dark underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
        >
          Đóng
        </button>
      </div>
      <div className="max-h-[calc(100dvh-180px)] overflow-y-auto p-5 sm:p-6">{children}</div>
      {actions && (
        <div className="flex flex-wrap justify-end gap-3 border-t border-border p-4 sm:px-6">
          {actions}
        </div>
      )}
    </dialog>
  );
}
