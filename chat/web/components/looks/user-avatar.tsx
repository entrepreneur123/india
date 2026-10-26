'use client'

import { Avatar, type AvatarProps } from '@/components/ui/avatar'
import { useLook } from './look-name'

/** Someone's photo with their photo gradient when they have one (062): what every screen shows for a person. */
export function UserAvatar({ userId, ...props }: AvatarProps & { userId: string }) {
  return <Avatar {...props} srcs={useLook(userId)?.cycle} />
}
