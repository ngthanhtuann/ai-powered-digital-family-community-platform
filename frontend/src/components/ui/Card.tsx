import type { HTMLAttributes, ReactNode } from 'react';

interface CardProps extends HTMLAttributes<HTMLElement> {
  title?: string;
  description?: string;
  actions?: ReactNode;
}

export function Card({
  title,
  description,
  actions,
  className = '',
  children,
  ...props
}: CardProps) {
  return (
    <section
      className={`rounded-card border border-border bg-surface p-5 shadow-card sm:p-6 ${className}`}
      {...props}
    >
      {(title || description || actions) && (
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            {title && <h2 className="text-section-title">{title}</h2>}
            {description && <p className="mt-1 text-body text-text-secondary">{description}</p>}
          </div>
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}
