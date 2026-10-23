import type { Metadata } from 'next'
import { AdminConsole } from '@/components/admin/admin-console'

// The moderation console (021). Not linked from the app; only accounts in public.admins get past sign-in.
export const metadata: Metadata = {
  title: 'Yaari 운영 콘솔',
  robots: { index: false, follow: false },
}

export default function AdminPage() {
  return <AdminConsole />
}
