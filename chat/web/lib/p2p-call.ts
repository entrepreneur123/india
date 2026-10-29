// A 1:1 call straight between the two friends' browsers (P2P): no LiveKit minutes or data. Calls are only between
// friends (call_open, 052), so showing each other's network address is the price friends pay, as in Signal's
// default for contacts.
//
// Clean-room of Jitsi's two-person P2P (lib-jitsi-meet JitsiConference.ts, read in source, Apache-2.0):
// - one side starts (Jitsi: the smaller id; the other waits), so the two never send offers at once;
// - when the direct path fails, the call moves to the server and stays there (Jitsi's latchFallback): here the call
//   screen joins LiveKit and tells the other side ('relay') to follow.
// Signalling: the call's private topic (095), only the two people can listen or send. STUN only (no TURN of our own):
// a network that blocks a direct path goes through LiveKit, whose TURN covers it.
import type { CallMsg, CallSignal } from './backend'
import type { Call, CallEvents } from './livekit'
import { deviceProblem, type DeviceProblem, pathQuality } from './rtc'

const ICE: RTCIceServer[] = [{ urls: ['stun:stun.cloudflare.com:3478', 'stun:stun.l.google.com:19302'] }]
/** No direct path by then: the server carries the call. */
const CONNECT_MS = 12_000
/** A dropped path gets this long to come back by itself before the server takes over. */
const RECOVER_MS = 5_000
const AUDIO = { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
const camera = (facingMode: 'user' | 'environment') => ({ width: { ideal: 1280 }, height: { ideal: 720 }, facingMode })

/**
 * Connects straight to the partner. Resolves once media flows; rejects when no direct path comes within CONNECT_MS
 * (or the partner went to the server). After that, a path that is lost for good reports `state('lost')`.
 */
export async function joinP2P(
  open: (on: (m: CallMsg) => void) => Promise<CallSignal | null>,
  partnerId: string,
  els: { remote: HTMLVideoElement; local: HTMLVideoElement; audio: HTMLAudioElement },
  ev: CallEvents,
  signal: AbortSignal,
  video: boolean,
): Promise<Call> {
  // Messages that come while my microphone is still being asked for wait here; then one at a time, in order.
  const inbox: CallMsg[] = []
  let onMsg: (m: CallMsg) => void = (m) => inbox.push(m)
  const sig = await open((m) => onMsg(m))
  if (!sig) throw new Error('no_signal')
  const ended = () => new DOMException('call ended', 'AbortError')
  if (signal.aborted) {
    sig.close()
    throw ended()
  }

  // My microphone (and camera) first: the answer has to carry them.
  const tracks: { audio: MediaStreamTrack | null; video: MediaStreamTrack | null } = { audio: null, video: null }
  let facing: 'user' | 'environment' = 'user'
  let cameraProblem: DeviceProblem | null = null
  let micProblem: DeviceProblem | null = null
  const get = async (c: MediaStreamConstraints) => (await navigator.mediaDevices.getUserMedia(c)).getTracks()
  try {
    for (const t of await get({ audio: AUDIO, video: video && camera(facing) })) tracks[t.kind as 'audio' | 'video'] = t
  } catch {
    // One prompt for both failed: each on its own, so a busy camera still leaves the microphone (and the other way).
    try { tracks.audio = (await get({ audio: AUDIO }))[0] } catch (e) { micProblem = deviceProblem(e) }
    if (video) try { tracks.video = (await get({ video: camera(facing) }))[0] } catch (e) { cameraProblem = deviceProblem(e) }
  }
  if (signal.aborted) {
    sig.close()
    tracks.audio?.stop()
    tracks.video?.stop()
    throw ended()
  }
  const showSelf = () => {
    els.local.srcObject = tracks.video ? new MediaStream([tracks.video]) : null
  }
  showSelf()

  const pc = new RTCPeerConnection({ iceServers: ICE })
  const offerer = sig.me < partnerId
  const remoteVideo = new MediaStream()
  const remoteAudio = new MediaStream()
  els.remote.muted = true // its sound plays through the <audio>
  els.remote.srcObject = remoteVideo
  els.audio.srcObject = remoteAudio
  pc.ontrack = ({ track }) => (track.kind === 'video' ? remoteVideo : remoteAudio).addTrack(track)
  const play = () =>
    els.audio.play().then(() => ev.blocked(false), (e: Error) => e.name === 'NotAllowedError' && ev.blocked(true))

  // One audio and one video slot from the start, so a camera turned on later needs no new offer.
  const senders: Record<'audio' | 'video', RTCRtpSender | null> = { audio: null, video: null }
  const fill = async () => {
    for (const tr of pc.getTransceivers()) {
      const kind = tr.receiver.track.kind as 'audio' | 'video'
      if (senders[kind]) continue
      tr.direction = 'sendrecv'
      senders[kind] = tr.sender
      await tr.sender.replaceTrack(tracks[kind])
    }
  }
  if (offerer) {
    pc.addTransceiver('audio', { direction: 'sendrecv' })
    pc.addTransceiver('video', { direction: 'sendrecv' })
    await fill()
  }

  let closed = false
  let connected = false
  let partnerCam = video
  const timers: ReturnType<typeof setTimeout>[] = []
  let stats: ReturnType<typeof setInterval> | undefined
  const close = (relay: boolean) => {
    if (closed) return
    closed = true
    timers.forEach(clearTimeout)
    clearInterval(stats)
    sig.send(relay ? { t: 'relay' } : { t: 'bye' })
    sig.close()
    pc.close()
    tracks.audio?.stop()
    tracks.video?.stop()
    els.remote.srcObject = null
    els.audio.srcObject = null
    els.local.srcObject = null
  }

  let ready: () => void = () => undefined
  let fail: (e: Error) => void = () => undefined
  const connectedOnce = new Promise<void>((ok, no) => ((ready = ok), (fail = no)))
  /** Before media flows: the join fails (the call screen goes to LiveKit). After: the call is lost (same). */
  const giveUp = () => {
    close(true)
    if (connected) ev.state('lost')
    else fail(new Error('p2p_failed'))
  }
  signal.addEventListener('abort', () => {
    close(false)
    fail(ended())
  })
  timers.push(setTimeout(() => !connected && giveUp(), CONNECT_MS))

  let recover: ReturnType<typeof setTimeout> | undefined
  pc.onconnectionstatechange = () => {
    const s = pc.connectionState
    if (s === 'connected') {
      clearTimeout(recover)
      if (!connected) {
        connected = true
        sig.send({ t: 'cam', on: !!tracks.video?.enabled })
        ready()
      } else ev.state('connected')
      void play()
    } else if (s === 'disconnected' && connected) {
      ev.state('reconnecting')
      clearTimeout(recover)
      recover = setTimeout(() => pc.connectionState !== 'connected' && giveUp(), RECOVER_MS)
    } else if (s === 'failed') giveUp()
  }

  // Their candidates can come before their offer is in: kept until then.
  const early: RTCIceCandidateInit[] = []
  let seen = false
  let offered = false
  const offer = async () => {
    if (offered) return
    offered = true
    await pc.setLocalDescription()
    sig.send({ t: 'sdp', sdp: pc.localDescription!.toJSON() })
  }
  pc.onicecandidate = ({ candidate }) => candidate && sig.send({ t: 'ice', c: candidate.toJSON() })
  const handle = async (m: CallMsg) => {
    if (closed) return
    try {
      if (m.t === 'hi') {
        if (seen) return
        seen = true
        sig.send({ t: 'hi' }) // so the side that came first knows I'm here too
        if (offerer) await offer()
      } else if (m.t === 'sdp') {
        if (offerer ? m.sdp.type !== 'answer' : m.sdp.type !== 'offer' || pc.remoteDescription) return
        await pc.setRemoteDescription(m.sdp)
        for (const c of early.splice(0)) await pc.addIceCandidate(c)
        if (!offerer) {
          await fill()
          await pc.setLocalDescription()
          sig.send({ t: 'sdp', sdp: pc.localDescription!.toJSON() })
        }
      } else if (m.t === 'ice') {
        if (pc.remoteDescription) await pc.addIceCandidate(m.c)
        else early.push(m.c)
      } else if (m.t === 'cam') {
        partnerCam = m.on
        if (connected) ev.partnerCamera(m.on)
      } else if (m.t === 'relay') giveUp()
      else if (m.t === 'bye') ev.partner('lost')
    } catch {
      giveUp()
    }
  }
  let queue = Promise.resolve()
  onMsg = (m) => void (queue = queue.then(() => handle(m)))
  inbox.splice(0).forEach(onMsg)
  ev.state('connecting')
  sig.send({ t: 'hi' })

  await connectedOnce
  ev.state('connected')
  ev.partnerCamera(partnerCam)

  // My bars from the path itself: round trip and the share of their packets lost, every 2 s.
  let last = { lost: 0, got: 0 }
  stats = setInterval(async () => {
    let rtt = 0
    const now = { lost: 0, got: 0 }
    ;(await pc.getStats()).forEach((r) => {
      if (r.type === 'candidate-pair' && r.nominated && r.state === 'succeeded') rtt = r.currentRoundTripTime ?? 0
      if (r.type === 'inbound-rtp') {
        now.lost += r.packetsLost ?? 0
        now.got += r.packetsReceived ?? 0
      }
    })
    const lost = now.lost - last.lost
    const total = lost + now.got - last.got
    last = now
    ev.quality(pathQuality(rtt, total > 0 ? lost / total : 0))
  }, 2000)

  const setTrack = async (kind: 'audio' | 'video', t: MediaStreamTrack | null) => {
    tracks[kind]?.stop()
    tracks[kind] = t
    await senders[kind]?.replaceTrack(t)
  }
  const device = async (c: MediaStreamConstraints) => {
    try {
      return (await get(c))[0]
    } catch (e) {
      throw new Error(deviceProblem(e))
    }
  }
  return {
    camera: cameraProblem,
    mic: micProblem,
    setMic: async (on) => {
      if (tracks.audio) tracks.audio.enabled = on
      else if (on) await setTrack('audio', await device({ audio: AUDIO }))
    },
    setCamera: async (on) => {
      // Off stops the camera (its light goes off), as LiveKit does.
      await setTrack('video', on ? await device({ video: camera(facing) }) : null)
      showSelf()
      sig.send({ t: 'cam', on })
    },
    flipCamera: async () => {
      if (!tracks.video) return facing
      const next: typeof facing = facing === 'user' ? 'environment' : 'user'
      tracks.video.stop() // some phones open only one camera at a time
      // That camera won't open: the one I had comes back.
      let to = next
      const t = await device({ video: camera(next) }).catch(() => ((to = facing), device({ video: camera(facing) })))
      await setTrack('video', t)
      facing = to
      showSelf()
      return facing
    },
    startAudio: async () => void (await play()),
    disconnect: async () => close(false),
  }
}
