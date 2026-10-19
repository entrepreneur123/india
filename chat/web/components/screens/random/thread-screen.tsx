'use client'

import type { Thread } from '@/components/app-state'
import { ChatView } from '@/components/screens/chat/chat-view'

/** Messages with a friend or someone met before (opened from Recent, a profile or the friends panel). */
export function ThreadScreen({ thread, onClose }: { thread: Thread; onClose: () => void }) {
  return (
    <ChatView matchId={thread.matchId} partner={thread.partner} mode="thread" onClose={onClose} onReported={onClose} />
  )
}
