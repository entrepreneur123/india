'use client'

import { Avatar, type AvatarProps } from '@/components/ui/avatar'
import { frameOf } from './frame-ring'
import { FrameOverlay } from './frames'
import { useLook } from './look-name'

/**
 * Someone's photo as every screen shows it: their photo gradient when they have one (062) and the frame they wear,
 * moving, round it (FrameOverlay: the ring under the photo, the decorations over it).
 */
export function UserAvatar({ userId, ...props }: AvatarProps & { userId: string }) {
  const look = useLook(userId)
  const worn = frameOf(look?.frame)
  const avatar = <Avatar {...props} srcs={look?.cycle} />
  if (!worn) return avatar
  const size = props.size ?? 40
  return (
    <span className="relative inline-flex shrink-0 rounded-full">
      <FrameOverlay frame={worn} size={size} part="under" />
      {avatar}
      <FrameOverlay frame={worn} size={size} part="over" />
    </span>
  )
}
