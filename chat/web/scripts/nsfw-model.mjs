// Before `next build`: the nudity model for video calls (nsfwjs MobileNetV2, 224 px) as static files in
// public/models/nsfw/. nsfwjs ships its models as base64 inside JS; served as a real .bin they are 25% smaller
// and cached by the browser. Only a video call downloads them.
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'

const require = createRequire(import.meta.url)
const dir = new URL('../node_modules/nsfwjs/dist/models/mobilenet_v2/', import.meta.url).pathname // not in the package's exports
const model = require(`${dir}model.min.js`)
const weights = require(`${dir}group1-shard1of1.min.js`)
const out = new URL('../public/models/nsfw/', import.meta.url)
mkdirSync(out, { recursive: true })
writeFileSync(new URL('model.json', out), JSON.stringify(model))
writeFileSync(new URL('group1-shard1of1', out), Buffer.from(weights, 'base64'))
console.log('nsfw-model: model.json + weights written')
