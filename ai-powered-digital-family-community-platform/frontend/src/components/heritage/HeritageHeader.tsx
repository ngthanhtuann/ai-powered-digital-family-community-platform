import Link from 'next/link';

export function HeritageHeader({ children }: { children?: React.ReactNode }) {
  return (
    <header className="flex flex-col gap-4 border-b border-border pb-5 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <Link href="/heritage" className="text-caption text-text-secondary hover:underline">
          Di sản gia đình
        </Link>
        <h1 className="mt-1 text-page-title">Kho di sản</h1>
      </div>
      {children}
    </header>
  );
}

export function HeritagePage({ children }: { children: React.ReactNode }) {
  return <main className="mx-auto min-h-screen max-w-5xl px-4 py-8 sm:px-6">{children}</main>;
}
