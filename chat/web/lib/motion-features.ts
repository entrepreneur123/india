// Motion's animation features (animate, exit, hover/tap, no layout or drag), loaded after first paint by
// <LazyMotion> so the first download only carries the small `m` components. The call screen loads drag itself.
export { domAnimation as default } from 'motion/react'
