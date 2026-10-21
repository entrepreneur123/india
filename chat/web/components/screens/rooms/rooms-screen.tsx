'use client'

import { lazy, Suspense, useEffect, useState } from 'react'
import { KeyRound, Plus, Search, X } from 'lucide-react'
import type { RoomKind } from '@/lib/backend'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Segmented } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { TopicIcon } from '@/components/ui/topic-icons'
import { VoiceRooms } from '@/components/screens/voice/voice-list'
import { retryImport } from '@/lib/retry-import'
import { RoomLangSelect } from '@/components/social/room-lang'
import { useSocialT } from '@/components/social/social-text'
import { CATEGORIES, categoryLabel, kindTopic } from './room-text'

// The create / enter-code forms download when first opened.
const loadDialogs = () => retryImport(() => import('./room-dialogs'))
const CreateRoomDialog = lazy(() => loadDialogs().then((mod) => ({ default: mod.CreateRoomDialog })))
const JoinCodeDialog = lazy(() => loadDialogs().then((mod) => ({ default: mod.JoinCodeDialog })))

export type RoomFilter = RoomKind | 'all'
/** The last filters, for coming back to the tab in this visit. */
let lastFilter: RoomFilter = 'all'
let lastSearch = { text: '', lang: '' }

/** One kind of room: every room has chat, voice, music and Ludo; its category sets the layout. */
export function RoomsScreen() {
  const toast = useToast()
  const { t } = useI18n()
  const { openVoice } = useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [codeOpen, setCodeOpen] = useState(false)
  const [dialogsUsed, setDialogsUsed] = useState(false)
  const [filter, setFilterState] = useState<RoomFilter>(lastFilter)
  const { st } = useSocialT()
  const [text, setText] = useState(lastSearch.text)
  const [lang, setLang] = useState(lastSearch.lang)
  // The server is asked once typing pauses.
  const [query, setQuery] = useState(lastSearch)
  useEffect(() => {
    const id = setTimeout(() => {
      lastSearch = { text: text.trim(), lang }
      setQuery(lastSearch)
    }, 300)
    return () => clearTimeout(id)
  }, [text, lang])
  const setFilter = (f: RoomFilter) => {
    lastFilter = f
    setFilterState(f)
  }
  const openCreate = () => {
    setDialogsUsed(true)
    setCreateOpen(true)
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-4xl flex-col gap-6 px-4 py-6 sm:px-8 sm:py-10">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-[28px] leading-tight font-bold tracking-tight">{t('rooms.title')}</h1>
            <p className="mt-1 text-sm text-muted-foreground">{t('rooms.subtitle')}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              onPointerEnter={() => void loadDialogs()}
              onClick={() => {
                setDialogsUsed(true)
                setCodeOpen(true)
              }}
            >
              <KeyRound className="size-4.5" aria-hidden /> {t('rooms.enterCode')}
            </Button>
            <Button variant="brand" onPointerEnter={() => void loadDialogs()} onClick={openCreate}>
              <Plus className="size-4.5" aria-hidden /> {t('rooms.create')}
            </Button>
          </div>
        </header>

        <div className="flex gap-2 sm:gap-3" role="search">
          <label className="relative flex h-11 min-w-0 flex-1 items-center rounded-full border border-input bg-card transition-colors duration-150 focus-within:border-primary focus-within:ring-1 focus-within:ring-primary">
            <Search className="pointer-events-none absolute left-3.5 size-4.5 text-muted-foreground" aria-hidden />
            <span className="sr-only">{st('rooms.search')}</span>
            <input
              type="search"
              value={text}
              maxLength={40}
              onChange={(e) => setText(e.target.value)}
              placeholder={st('rooms.searchPlaceholder')}
              className="h-full min-w-0 flex-1 rounded-full bg-transparent pr-10 pl-10 text-[15px] outline-none [&::-webkit-search-cancel-button]:hidden"
            />
            {text ? (
              <button
                type="button"
                onClick={() => setText('')}
                className="absolute right-1.5 grid size-8 place-items-center rounded-full text-muted-foreground hover:bg-muted"
                aria-label={st('rooms.clear')}
              >
                <X className="size-4" aria-hidden />
              </button>
            ) : null}
          </label>
          <RoomLangSelect value={lang} onChange={setLang} label={st('rooms.lang')} anyLabel={st('rooms.anyLang')} className="w-36 shrink-0 sm:w-56" />
        </div>

        <div id="rooms-kind">
          <Segmented
            className="w-full sm:w-[28rem]"
            label={t('rooms.filter')}
            value={filter}
            onChange={setFilter}
            options={[
              { value: 'all', label: t('rooms.all') },
              ...CATEGORIES.map((c) => ({
                value: c,
                label: (
                  <span className="flex min-w-0 items-center gap-1.5">
                    <TopicIcon topic={kindTopic(c)} size={16} className="max-sm:hidden" />
                    <span className="truncate">{categoryLabel(c, t)}</span>
                  </span>
                ),
              })),
            ]}
          />
        </div>

        <VoiceRooms filter={filter} query={query} onCreate={openCreate} />
      </div>
      {dialogsUsed ? (
        <Suspense fallback={null}>
          <CreateRoomDialog
            open={createOpen}
            initialCategory={filter === 'all' ? undefined : filter}
            onClose={() => setCreateOpen(false)}
            onCreated={(id, code, isPrivate) => {
              setCreateOpen(false)
              if (isPrivate) toast.show(t('rooms.code', { code }), 'success')
              openVoice(id)
            }}
          />
          <JoinCodeDialog
            open={codeOpen}
            onClose={() => setCodeOpen(false)}
            onJoined={(id) => {
              setCodeOpen(false)
              openVoice(id)
            }}
          />
        </Suspense>
      ) : null}
    </div>
  )
}
