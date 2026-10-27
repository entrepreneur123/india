'use client'

import { lazy, Suspense, useEffect, useState } from 'react'
import { KeyRound, Plus, Search, X } from 'lucide-react'
import type { RoomSort } from '@/lib/backend'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Segmented } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { VoiceRooms } from '@/components/screens/voice/voice-list'
import { retryImport } from '@/lib/retry-import'
import { RoomLangSelect } from '@/components/social/room-lang'
import { useSocialT } from '@/components/social/social-text'
import { RoomFiltersButton, type RoomFilters } from './room-filters'

// The create / enter-code forms download when first opened.
const loadDialogs = () => retryImport(() => import('./room-dialogs'))
const CreateRoomDialog = lazy(() => loadDialogs().then((mod) => ({ default: mod.CreateRoomDialog })))
const JoinCodeDialog = lazy(() => loadDialogs().then((mod) => ({ default: mod.JoinCodeDialog })))

/** The last search, for coming back to the tab in this visit. */
let lastSearch = { text: '', lang: '' }
let lastFilters: RoomFilters = { countries: [], locked: true }
let lastSort: RoomSort = 'members'

/** One kind of room (chat, voice and music); the search narrows by title, language, countries and passwords. */
export function RoomsScreen() {
  const toast = useToast()
  const { t } = useI18n()
  const { openVoice } = useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [codeOpen, setCodeOpen] = useState(false)
  const [dialogsUsed, setDialogsUsed] = useState(false)
  const [filters, setFiltersState] = useState(lastFilters)
  const [sort, setSortState] = useState(lastSort)
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
  const setFilters = (f: RoomFilters) => {
    lastFilters = f
    setFiltersState(f)
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
            <Search className="pointer-events-none absolute inset-s-3.5 size-4.5 text-muted-foreground" aria-hidden />
            <span className="sr-only">{st('rooms.search')}</span>
            <input
              type="search"
              value={text}
              maxLength={40}
              onChange={(e) => setText(e.target.value)}
              placeholder={st('rooms.searchPlaceholder')}
              className="h-full min-w-0 flex-1 rounded-full bg-transparent pe-10 ps-10 text-[15px] outline-none [&::-webkit-search-cancel-button]:hidden"
            />
            {text ? (
              <button
                type="button"
                onClick={() => setText('')}
                className="absolute inset-e-1.5 grid size-8 place-items-center rounded-full text-muted-foreground hover:bg-muted"
                aria-label={st('rooms.clear')}
              >
                <X className="size-4" aria-hidden />
              </button>
            ) : null}
          </label>
          <RoomLangSelect value={lang} onChange={setLang} label={st('rooms.lang')} anyLabel={st('rooms.anyLang')} className="w-36 shrink-0 sm:w-56" />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <RoomFiltersButton value={filters} onChange={setFilters} />
          {/* 072: most people, best for me, or busiest. */}
          <Segmented
            label={st('rooms.sort')}
            value={sort}
            onChange={(v) => setSortState((lastSort = v))}
            options={[
              { value: 'members', label: st('rooms.sortMembers') },
              { value: 'relevant', label: st('rooms.sortRelevant') },
              { value: 'popular', label: st('rooms.sortPopular') },
            ]}
          />
        </div>

        <VoiceRooms query={{ ...query, ...filters, sort }} onCreate={openCreate} onTag={(tag) => setText(`#${tag}`)} />
      </div>
      {dialogsUsed ? (
        <Suspense fallback={null}>
          <CreateRoomDialog
            open={createOpen}
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
