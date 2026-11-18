'use client';

import { notFound, useParams } from 'next/navigation';
import AdminConsole, { ADMIN_SECTIONS, type AdminSectionKey } from '@/components/admin/AdminConsole';

export default function AdminSectionPage() {
  const { section } = useParams<{ section: string }>();
  if (!(section in ADMIN_SECTIONS)) notFound();
  return <AdminConsole section={section as AdminSectionKey} />;
}
