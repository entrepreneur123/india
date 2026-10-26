'use client'

import { Avatar, type AvatarProps } from '@/components/ui/avatar'
import { useLook } from './look-name'

/** Someone's photo with their photo gradient and photo effect when they have them (062): what every screen shows for a person. */
export function UserAvatar({ userId, ...props }: AvatarProps & { userId: string }) {
  const look = useLook(userId)
  return <Avatar {...props} srcs={look?.cycle} effect={look?.effect} />
}
