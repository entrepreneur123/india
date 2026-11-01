import { isItem, type FrameId } from '@/lib/looks'

interface FrameDesign {
  /** The ring's colours, turning round (a conic gradient; the first colour closes the circle). */
  ring: string[]
}

export const FRAME_DESIGNS: Record<FrameId, FrameDesign> = {
  frame_frost: { ring: ['#FFFFFF', '#BFEAFF', '#5CB8FF', '#2F7DE1', '#A9E2FF', '#FFFFFF', '#7CC8FF'] },
  frame_fire: { ring: ['#FFE66D', '#FF9A1F', '#FF4D00', '#C10000', '#FF6A00', '#FFD23F', '#FF3D00'] },
  frame_jewel: { ring: ['#FFF6CF', '#E8C252', '#9C7414', '#FFE9A1', '#C9971C', '#FFF3C4', '#8A6510'] },
  frame_sand: { ring: ['#FBE7C2', '#D9AE6C', '#9A6635', '#E9C792', '#7A4B24', '#F2D5A0'] },
  frame_neon: { ring: ['#00F0FF', '#7A5CFF', '#FF00E5', '#FF4FA3', '#00F0FF', '#00FFA3'] },
  frame_sakura: { ring: ['#FFE7EF', '#FFB3CC', '#FF7AA8', '#FFD6E4', '#FF92B8', '#FFF0F5'] },
}

export const conic = (d: FrameDesign) => `conic-gradient(from var(--look-a), ${d.ring.join(', ')}, ${d.ring[0]})`

/** A worn frame id, or null for none or an unknown one. */
export const frameOf = (id: string | null | undefined) => (isItem(id) && id! in FRAME_DESIGNS ? (id as FrameId) : null)
