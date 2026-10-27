// Demo pictures are drawn here as small SVGs (no third-party image hosts; works offline and under the CSP).
const ART_COLORS = [
  ['#fde2e4', '#c8174a'], ['#e3f0ff', '#1a73e8'], ['#e6f4ea', '#188038'], ['#f3e8fd', '#8430ce'],
  ['#fef7e0', '#b06000'], ['#e4f7f8', '#007b83'],
]
export function art(seed: string, w = 720, h = 720) {
  let n = 0
  for (const ch of seed) n = (n * 31 + ch.charCodeAt(0)) | 0
  const [bg, fg] = ART_COLORS[Math.abs(n) % ART_COLORS.length]
  const sun = 20 + (Math.abs(n >> 3) % 60)
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}" fill="${bg}"/>` +
    `<circle cx="${(sun / 100) * w}" cy="${h * 0.3}" r="${w * 0.09}" fill="${fg}" opacity=".35"/>` +
    `<path d="M0 ${h * 0.75} L${w * 0.3} ${h * 0.5} L${w * 0.55} ${h * 0.7} L${w * 0.75} ${h * 0.55} L${w} ${h * 0.72} V${h} H0Z" fill="${fg}" opacity=".55"/>` +
    `<path d="M0 ${h * 0.88} L${w * 0.4} ${h * 0.68} L${w} ${h * 0.9} V${h} H0Z" fill="${fg}"/></svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}
