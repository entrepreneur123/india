// The Recent tab's segment shown last (back from a thread or a group, the list is where I left it). Outside the
// Recent chunk so another tab can send me to a segment (Random's "party with friends" link opens Groups).
export type RecentSegment = 'friends' | 'matches' | 'groups' | 'sent'
let last: RecentSegment = 'friends'
export const lastRecentSegment = () => last
export const showRecentSegment = (s: RecentSegment) => void (last = s)
