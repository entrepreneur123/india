'use client'

import { Avatar, type AvatarProps } from '@/components/ui/avatar'
import { FrameRing, frameOf } from './frame-ring'
import { useLook } from './look-name'

/**
 * Someone's photo as every screen shows it: their photo gradient when they have one (062) and the frame they wear
 * round it. `frame={false}` where the frame is already drawn bigger around it (FrameAvatar).
 */
export function UserAvatar({ userId, frame = true, ...props }: AvatarProps & { userId: string; frame?: boolean }) {
  const look = useLook(userId)
  const worn = frame ? frameOf(look?.frame) : null
  const avatar = <Avatar {...props} srcs={look?.cycle} />
  if (!worn) return avatar
  return (
    <span className="relative inline-flex shrink-0 rounded-full">
      <FrameRing frame={worn} size={props.size ?? 40} />
      {avatar}
    </span>
  )
}
