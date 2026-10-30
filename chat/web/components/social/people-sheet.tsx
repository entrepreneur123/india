'use client'

import useSWR from 'swr'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { NameText } from '@/components/looks/look-name'
import { UserAvatar } from '@/components/looks/user-avatar'
import { Segmented, Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'

export type PeopleKind = 'friends' | 'likers' | 'liked'

/**
 * A profile's people, as Instagram's followers / following (098): friends, who liked them, whom they liked, one tab
 * each. Anyone who may see the profile sees them; a tap opens that person's profile.
 */
export function PeopleSheet({ userId, owner, kind, onKind, onClose }: {
  userId: string; owner: string; kind: PeopleKind | null; onKind: (k: PeopleKind) => void; onClose: () => void
}) {
  const backend = useBackend()
  const { openProfile } = useApp()
  const { t } = useI18n()
  const { data } = useSWR(kind ? ['people', userId, kind] : null, () => backend.profilePeople(userId, kind!), { keepPreviousData: false })
  return (
    <Modal open={kind !== null} onClose={onClose} variant="sheet" title={owner} className="md:max-w-md">
      <div className="flex min-h-[50vh] flex-col gap-3 px-4 pb-4">
        <Segmented<PeopleKind>
          label={owner}
          className="w-full"
          value={kind ?? 'friends'}
          onChange={onKind}
          options={[
            { value: 'friends', label: t('profile.friends') },
            { value: 'likers', label: t('profile.likes') },
            { value: 'liked', label: t('profile.liked') },
          ]}
        />
        {!data ? (
          Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-12 w-full rounded-xl" />)
        ) : data.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">{t('explore.empty')}</p>
        ) : (
          <ul className="flex flex-col">
            {data.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => (onClose(), openProfile(p.id))}
                  className="flex w-full items-center gap-3 rounded-xl p-2 text-start transition-colors hover:bg-muted"
                >
                  <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={44} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">@{p.handle}</span>
                    <NameText userId={p.id} className="block truncate text-sm text-muted-foreground">{p.name}</NameText>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}
