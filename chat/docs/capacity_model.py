"""Yaari load + cost model (2026-11-02): CCU x voice share -> control plane, media plane, monthly cost.
Every rate is read in the code (cited) or an assumption (A*); prices are the official pages listed in COST_MODEL_100K.md.
Run: python3 chat/docs/capacity_model.py"""
CCU = [1_000, 5_000, 10_000, 50_000, 100_000]
P = [0.01, 0.05, 0.10, 0.25, 0.50, 1.00]

# Non-voice users (A1, assumption): who does what at the peak minute.
SEARCH, RCHAT, DM, BROWSE = 0.10, 0.30, 0.20, 0.40
ROOM_SHARE = 0.5          # A2: of voice users, half in rooms, half in 1:1 calls
ROOM_SIZE, SPEAKERS = 15, 4  # A3: average room, unmuted mics at a time (dtx off -> they send all the time)
VISIBLE = 0.7             # A4: room members with the room on screen (3 s beat), rest 20 s
MSG_EVERY = 15            # A5: a chatting user sends one message every 15 s
RELAY_FRIENDS = 0.20      # A6: friend P2P calls that fall back to the server (not measured; industry-quoted ~80% direct)
SILENT_KBPS = 35          # A9: an unmuted mic in silence with dtx off: ~5-12 kbps Opus VBR x RED + ~20 kbps headers (SCALE_UP estimate, not measured)
STREAM_KBPS = 106         # 86 kbps measured payload (Opus 48k + RED, HANDOFF sdp-full) + ~20 kbps IP/UDP/SRTP headers at 50 pps
AVG_OVER_PEAK = 0.5       # A7: monthly average concurrency / peak
MONTH_S = 30 * 86400

def control(ccu, p, target=False):
    v = ccu * p; nv = ccu - v
    room, call = v * ROOM_SHARE, v * (1 - ROOM_SHARE)
    visible_app = ccu * 0.8   # A8: tabs/app in foreground
    if not target:  # the code today (random-screen POLL_MS 2000, voice-lost 3 s/20 s, app-shell 30 s, right-panel 30/60 s, online 15 s, resync 60 s)
        rps = (nv * SEARCH / 2 + room * (VISIBLE / 3 + (1 - VISIBLE) / 20)
               + visible_app * (1 / 30 + 1 / 30 + 1 / 60) + nv * BROWSE * 1 / 15
               + (nv * (RCHAT + DM)) * (1 / MSG_EVERY + 1 / 60) + room * 0.3 / MSG_EVERY)
    else:  # target: presence/matching/badges in memory on the gateway; DB only for writes + 1/60 s room seen + page loads
        rps = (nv * SEARCH / 30 + room / 60 + visible_app / 120
               + (nv * (RCHAT + DM)) / MSG_EVERY + room * 0.3 / MSG_EVERY)
    msgs = (nv * (RCHAT + DM)) / MSG_EVERY + room * 0.3 / MSG_EVERY
    deliveries = (nv * (RCHAT + DM)) / MSG_EVERY * 2 + room * 0.3 / MSG_EVERY * ROOM_SIZE
    return dict(ws=ccu, db_rps=rps, msgs=msgs, fanout=deliveries, match_rps=nv * SEARCH / 2)

def media(ccu, p, strangers=False):
    v = ccu * p; room = v * ROOM_SHARE; call = v * (1 - ROOM_SHARE)
    rooms = room / ROOM_SIZE
    relay = 1.0 if strangers else RELAY_FRIENDS
    sfu_participants = room + call * relay
    room_kbps = STREAM_KBPS + (SPEAKERS - 1) * SILENT_KBPS        # one talker at a time, the other open mics silent
    egress_kbps = rooms * (ROOM_SIZE - 1) * room_kbps + call * relay * STREAM_KBPS   # every member hears the stage
    ingress_kbps = rooms * room_kbps + call * relay * STREAM_KBPS
    egress_streams = rooms * (ROOM_SIZE - 1) * SPEAKERS + call * relay
    gbps = (egress_kbps + ingress_kbps) / 1e6
    tb_month = egress_kbps / 8 / 1e9 * MONTH_S * AVG_OVER_PEAK  # TB/month (egress only, as clouds bill)
    pmin_month = sfu_participants * MONTH_S / 60 * AVG_OVER_PEAK
    return dict(webrtc=v, sfu=sfu_participants, egress_streams=egress_streams, gbps=gbps, tb=tb_month, pmin=pmin_month)

if __name__ == '__main__':
    print('CONTROL (current code vs target), voice share 10%')
    for c in CCU:
        a, b = control(c, .10), control(c, .10, True)
        print(f'{c:>7} ws={c:>7} db_rps now={a["db_rps"]:>8.0f} target={b["db_rps"]:>7.0f} match_rps={a["match_rps"]:>6.0f} msgs/s={a["msgs"]:>6.0f} fanout/s={a["fanout"]:>7.0f}')
    print('\nCURRENT CODE db_rps by voice share')
    for c in CCU:
        print(f'{c:>7}', ' '.join(f'{control(c, p)["db_rps"]:>8.0f}' for p in P))
    for strangers in (False, True):
        print(f'\nMEDIA strangers={strangers}: SFU participants / Gbps (in+out) / egress TB per month / participant-minutes per month (M)')
        for c in CCU:
            print(f'{c:>7}', ' | '.join(f'{m["sfu"]:>6.0f} {m["gbps"]:>6.2f}G {m["tb"]:>7.1f}TB {m["pmin"]/1e6:>7.1f}M' for m in (media(c, p, strangers) for p in P)))

# ---------------- COST (USD/month). Prices: official pages read 2026-11-02 (COST_MODEL_100K.md lists URLs). ----------------
import math
MS_PER_CALL = 4.0          # A10: DB CPU ms per RPC on a dedicated core (CAPACITY 10-23: 4-10 ms; Micro measures more, shared CPU)
CPU_UTIL = 0.6             # plan to 60%
MAU_PER_CCU = 20           # A11: monthly actives per peak concurrent (not measured)
RESP_KB = 1.0              # A12: average RPC response size
MSG_KB = 0.5               # A13: average realtime message size
TYPING_EXTRA = 0.5         # A14: presence/typing/room events on top of chat deliveries
VCPU_MONTH = 20.0          # A15: dedicated vCPU-month incl. RAM: Hetzner CCX33 EU 139 EUR/8, AWS c7g.xlarge Mumbai 71.7 USD/4, E2E SDC3 72.4 USD/4
STREAMS_PER_NODE = 8_000   # A16: subscribed audio tracks per 16-vCPU SFU node at 70% CPU (LiveKit benchmark is an upper bound)
WS_PER_NODE = 25_000       # A17: realtime sockets per 8-vCPU Realtime node (benchmark: 250k on 2-6 nodes of unstated size)
SUPA_COMPUTE = [(2, 110), (4, 210), (8, 410), (16, 960), (32, 1870), (48, 2800), (64, 3730)]  # dedicated vCPU, $/mo

def db_vcpu(rps): return rps * MS_PER_CALL / 1000 / CPU_UTIL

def supabase_cloud(ccu, p, target):
    c = control(ccu, p, target)
    need = db_vcpu(c['db_rps'])
    size = next(((v, usd) for v, usd in SUPA_COMPUTE if v >= need), None)
    compute = size[1] if size else None                      # None = beyond 16XL -> Enterprise / sharding
    rt_conn = max(0, ccu - 500) / 1000 * 10
    msgs_m = (c['msgs'] + c['fanout'] * (1 + TYPING_EXTRA)) * MONTH_S * AVG_OVER_PEAK / 1e6
    rt_msgs = max(0, math.ceil(msgs_m - 5)) * 2.5
    egress_gb = (c['db_rps'] * RESP_KB + c['fanout'] * (1 + TYPING_EXTRA) * MSG_KB) * MONTH_S * AVG_OVER_PEAK / 1e6
    egress = max(0, egress_gb - 250) * 0.09
    mau = max(0, ccu * MAU_PER_CCU - 100_000) * 0.00325
    plan = 25 if ccu <= 10_000 else 599   # >10k realtime connections needs Enterprise: Team price used as a FLOOR
    total = None if compute is None else plan + compute - 10 + rt_conn + rt_msgs + egress + mau
    return dict(need_vcpu=need, compute=compute, rt_conn=rt_conn, rt_msgs=rt_msgs, egress=egress, mau=mau, total=total,
                enterprise=ccu > 10_000 or compute is None)

def selfhost_control(ccu, p, target, bw):
    c = control(ccu, p, target)
    db = max(8, math.ceil(db_vcpu(c['db_rps'])))            # primary
    vcpu = db * 2 + max(8, db // 2)                          # + synchronous standby (same size) + read replica
    vcpu += max(2, math.ceil(ccu / WS_PER_NODE)) * 8         # Realtime cluster (>=2 nodes)
    vcpu += 2 * 4 + 2 * 4                                    # PostgREST+GoTrue x2, Valkey primary+replica
    egress_gb = (c['db_rps'] * RESP_KB + c['fanout'] * (1 + TYPING_EXTRA) * MSG_KB) * MONTH_S * AVG_OVER_PEAK / 1e6
    fixed = 60 + 50 + 25                                     # backups/object storage, monitoring (Sentry Team 29 + Grafana Pro 19), Cloudflare Pro
    return dict(vcpu=vcpu, servers=vcpu * VCPU_MONTH, egress=egress_gb * bw, fixed=fixed, total=vcpu * VCPU_MONTH + egress_gb * bw + fixed)

def livekit_cloud(ccu, p, strangers=False):
    m = media(ccu, p, strangers)
    gb = m['tb'] * 1000
    if m['sfu'] <= 1000: base, inc_min, rate, inc_gb, gbrate = 50, 150_000, 0.0005, 250, 0.12
    else: base, inc_min, rate, inc_gb, gbrate = 500, 1_500_000, 0.0004, 3000, 0.10   # >5,000 concurrent: Enterprise; Scale rates as reference
    return dict(total=base + max(0, m['pmin'] - inc_min) * rate + max(0, gb - inc_gb) * gbrate, enterprise=m['sfu'] > 5000,
                minutes=max(0, m['pmin'] - inc_min) * rate, data=max(0, gb - inc_gb) * gbrate)

def selfhost_media(ccu, p, bw, strangers=False):
    m = media(ccu, p, strangers)
    nodes = max(2, math.ceil(m['egress_streams'] / STREAMS_PER_NODE * 1.3))   # 30% headroom, N+1
    gb = m['tb'] * 1000
    return dict(nodes=nodes, servers=nodes * 16 * VCPU_MONTH, data=gb * bw, total=nodes * 16 * VCPU_MONTH + gb * bw + 30)

def cf_realtime(ccu, p, strangers=False):
    gb = media(ccu, p, strangers)['tb'] * 1000
    return max(0, gb - 1000) * 0.05

if __name__ == '__main__':
    CC = [1_000, 10_000, 50_000, 100_000]
    print('\nCONTROL PLANE $/mo (voice 10%)  [supabase now | supabase target | selfhost target bw$0.01 | selfhost target bw$0.08]')
    for c in CC:
        a, b = supabase_cloud(c, .1, False), supabase_cloud(c, .1, True)
        s1, s2 = selfhost_control(c, .1, True, 0.01), selfhost_control(c, .1, True, 0.08)
        f = lambda d: 'n/a(>16XL)' if d['total'] is None else f"{d['total']:>9.0f}{'*' if d['enterprise'] else ''}"
        print(f'{c:>7} {f(a)} (vcpu {a["need_vcpu"]:.0f}, msgs ${a["rt_msgs"]:.0f}, mau ${a["mau"]:.0f}) | {f(b)} (vcpu {b["need_vcpu"]:.0f}, msgs ${b["rt_msgs"]:.0f}) | {s1["total"]:>7.0f} ({s1["vcpu"]} vcpu) | {s2["total"]:>7.0f}')
    print('\nMEDIA $/mo friends+rooms [LiveKit Cloud | self LiveKit bw$0.01 | self bw$0.08 | Cloudflare Realtime]')
    for c in CC:
        print(f'{c:>7}', ' || '.join(f"{livekit_cloud(c,p)['total']:>8.0f}{'*' if livekit_cloud(c,p)['enterprise'] else ' '} {selfhost_media(c,p,0.01)['total']:>7.0f}({selfhost_media(c,p,0.01)['nodes']}n) {selfhost_media(c,p,0.08)['total']:>7.0f} {cf_realtime(c,p):>7.0f}" for p in P))

def scenario(ccu, p, name, bw=0.01):
    if name == 'A':   # managed, today's code
        s = supabase_cloud(ccu, p, False)
        if s['total'] is None: return None
        return s['total'] + livekit_cloud(ccu, p)['total'] + 20
    if name == 'B':   # Supabase (DB/API/Auth) + Vercel kept; realtime+matching self-run; LiveKit Cloud kept
        s = supabase_cloud(ccu, p, True)
        if s['total'] is None: return None
        own_rt = (max(2, math.ceil(ccu / WS_PER_NODE)) * 8 + 8 + 8) * VCPU_MONTH
        return s['total'] - s['rt_conn'] - s['rt_msgs'] + own_rt + livekit_cloud(ccu, p)['total'] + 20
    if name == 'C':   # everything self-run, static web on Cloudflare
        return selfhost_control(ccu, p, True, bw)['total'] + selfhost_media(ccu, p, bw)['total']
    if name == 'D':   # hybrid path: cheaper of Supabase(target code)/self-run control, cheaper of LiveKit/self-run media
        sc = supabase_cloud(ccu, p, True)['total']
        ctrl = min(x for x in (sc, selfhost_control(ccu, p, True, bw)['total']) if x is not None)
        return ctrl + min(livekit_cloud(ccu, p)['total'], selfhost_media(ccu, p, bw)['total'])

if __name__ == '__main__':
    print('\nSCENARIOS infra $/mo, voice 10% (bw $0.01 | $0.08), ops people NOT included')
    for c in [1_000, 10_000, 50_000, 100_000]:
        print(f'{c:>7}', ' '.join(f"{n}={(scenario(c,.1,n) or float('nan')):>8.0f}" for n in 'ABCD'), ' | C@0.08=', round(scenario(c,.1,'C',0.08)))
