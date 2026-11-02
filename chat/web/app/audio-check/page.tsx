import type { Metadata } from 'next'
import { AudioCheck } from '@/components/audio-check'

// The owner's device test for the voice path (docs/HANDOFF.md 2026-11-02): not linked from the app, not indexed.
export const metadata: Metadata = { title: 'Yaari 오디오 검사', robots: { index: false, follow: false } }

export default function Page() {
  return <AudioCheck />
}
