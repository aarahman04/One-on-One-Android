// Run from ../One-on-One/backend: npx tsx ../../One-on-One-Android/scripts/pong-sync-trace.ts
// Two simulated players rally against the real backend PongSim over lossy, jittery links. Each player sees the
// ball only through the frames its link delivered (50 ms behind, like the app) and sends timed racket samples the
// server lag-compensates. Writes, per profile and seat, what that phone received and touched, plus the server's
// truth, for the Kotlin replay test (PongSyncReplayTest) to drive the real PongClient with.
import { writeFileSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
import { PongSim, flightPosition, type PongFrame } from '../../One-on-One/backend/src/games/pong.js'
import { landingWeights } from '../../One-on-One/backend/src/games/pongPhysics.js'

type Profile = { name: string; rtt: number; jitter: number; frameLoss: number }
const PROFILES: Profile[] = [
  { name: 'rtt50', rtt: 50, jitter: 5, frameLoss: 0 },
  { name: 'rtt100', rtt: 100, jitter: 10, frameLoss: .02 },
  { name: 'rtt200', rtt: 200, jitter: 20, frameLoss: .03 },
  { name: 'rtt300', rtt: 300, jitter: 30, frameLoss: .05 },
]
const TICK = 1000 / 60
const clamp = (x: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, x))
const r = (x: number, d = 10_000) => Math.round(x * d) / d
function random(seed: number) {
  return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296 }
}

type Frame = PongFrame & { id: string }
type Pending = { at: number; run: () => void }

class Player {
  frames: { at: number; f: Frame }[] = [] // arrivals in this phone's clock
  inputs: { at: number; x: number; y: number }[] = []
  private seen: Frame[] = []
  private offset = Infinity
  private n = 0
  private x = .5
  private y: number
  private swing: { vx: number; vy: number; miss: boolean } | null = null
  private rally = -1
  // Mirror of PongClient's coherent timeline: ball delay D moves between 50 ms (ball coming here) and the
  // opponent's measured lag F (ball going there), so this player swings at the ball the app would draw.
  private lags: [number, number][] = []
  private lastOppV = -Infinity
  private far = 50
  private delay = 50
  private ballTime = 0
  private last = 0
  private drawn: Frame | null = null
  private swung = false // stands in for the app's local prediction: the bat went through the ball
  constructor(readonly seat: 1 | 2, readonly skew: number, private rng: () => number, private missRate: number) {
    this.y = seat === 1 ? 1.88 : -.08 // where the server and app start the racket
  }
  private get forward() { return this.seat === 1 ? -1 : 1 }
  receive(f: Frame, local: number) {
    this.frames.push({ at: r(local, 1000), f })
    this.offset = Math.min(this.offset, local - f.t)
    if (!this.seen.length || f.t > this.seen[this.seen.length - 1].t) this.seen.push(f)
    const oppV = f.pv[2 - this.seat]
    if (oppV > this.lastOppV) { this.lastOppV = oppV; this.lags.push([f.t, f.t - oppV]); if (this.lags.length > 64) this.lags.shift() }
    if (this.seen.length > 40) this.seen.shift()
  }
  private toward(f: Frame | null) {
    if (!f) return false
    if (f.a) return (f.a[10] - f.a[1]) * this.forward < 0
    return f.serve ? f.serve === this.seat : false
  }
  /** The ball this phone draws, from received frames only. */
  private view(local: number) {
    if (!this.seen.length) return null
    const newest = local - this.offset, near = newest - 50, dt = this.last ? local - this.last : 0
    this.last = local
    const latest = this.lags.at(-1)?.[0] ?? 0, lag = Math.max(-1, ...this.lags.filter(l => l[0] >= latest - 3000).map(l => l[1]))
    if (lag >= 0) this.far += clamp(clamp(lag + 48, 50, 500) - this.far, -.2 * dt, .2 * dt)
    const d = this.drawn, change = (this.toward(d) && !this.swung ? 50 : this.far) - this.delay
    const landing = d?.a && !d.l ? d.t - d.a[12] * 1000 + landingWeights(d.a[2], d.a[5] / (6 / 12.16))[4] * d.a[13] * 1000 : NaN
    const remaining = landing - this.ballTime + change
    const rate = remaining > 1 ? clamp(change / remaining, -.75, .5) : change > 0 ? .5 : -.75
    this.delay = Math.abs(change) <= Math.abs(rate * dt) ? this.delay + change : this.delay + rate * dt
    this.ballTime = clamp(Math.max(this.ballTime, newest - this.delay), this.seen[0].t, Math.max(near, this.seen[0].t))
    const v = this.ballTime
    let f: Frame = this.seen[0]
    for (const candidate of this.seen) if (candidate.t <= v) f = candidate
    let start = f.a ? f.t - f.a[12] * 1000 : -Infinity
    for (const c of this.seen) if (c.a && c.t > f.t && (c.h !== f.h || c.j !== f.j) && c.t - c.a[12] * 1000 <= v && c.t - c.a[12] * 1000 >= start) {
      f = c; start = c.t - c.a[12] * 1000
    }
    this.drawn = f
    const age = (v - f.t) / 1000
    let at: number[]
    if (f.a) {
      const points = [0, 1, 2, 3].map(i => f!.a!.slice(i * 3, i * 3 + 3)) as [number, number, number][]
      points[3] = [points[3][0] + f.drift * age, points[3][1], points[3][2]]
      at = flightPosition(points, (f.a[12] + age) / f.a[13])
    } else at = [f.b[0], f.b[1], f.z[0]]
    return { v, w: Math.max(v, near), x: at[0], y: at[1], f, toward: this.toward(f) && !!f.a }
  }
  /** One 16 ms input step: track an incoming ball and swipe through it, sometimes deliberately miss. */
  input(local: number, send: (sample: { x: number; y: number; ct: number; v: number; w: number; n: number }) => void) {
    const ball = this.view(local)
    if (!ball || ball.f.s[0] + ball.f.s[1] !== this.rally && ball.f.serve) this.swing = null
    if (!ball) return
    this.rally = ball.f.s[0] + ball.f.s[1]
    const dt = .016, back = -this.forward
    const near = this.seat === 1 ? [1.3, 2.5] : [-.7, .5]
    const myServe = ball.f.serve === this.seat && ball.f.c === 0
    const ready = myServe || ball.toward && ball.f.l
    if (ready && !this.swing) this.swing = { vx: (this.rng() * 2 - 1) * 3, vy: 2 + this.rng() * 7, miss: !myServe && this.rng() < this.missRate }
    if (!ready && !ball.toward) this.swing = null
    let tx = .5, ty = this.seat === 1 ? 2.1 : -.3, speed = 2.5
    if (this.swing?.miss) { tx = ball.x < .5 ? 1.2 : -.2; speed = 4 }
    else if (this.swing) {
      // Behind the ball, then through it toward the net with this rally's stroke.
      const gap = (this.y - ball.y) * back
      if (gap > .3) { tx = ball.x; ty = ball.y + back * .26; speed = 6 }
      else { tx = this.x + this.swing.vx * dt; ty = this.y + this.forward * this.swing.vy * dt; speed = 99 }
    } else if (ball.toward) { tx = ball.x; ty = clamp(ball.y + back * .5, near[0], near[1]) }
    const dx = tx - this.x, dy = ty - this.y, d = Math.hypot(dx, dy), step = Math.min(1, speed * dt / (d || 1))
    this.x = clamp(this.x + dx * step, -.26, 1.26); this.y = clamp(this.y + dy * step, near[0], near[1])
    this.swung = !!this.swing && !this.swing.miss && (this.y - ball.y) * back < 0 && ball.toward
    this.inputs.push({ at: r(local, 1000), x: r(this.x), y: r(this.y) })
    send({ x: this.x, y: this.y, ct: Math.round(local), v: ball.v, w: ball.w, n: ++this.n })
  }
}

function run(profile: Profile, seed: number, ms: number) {
  const rng = random(seed)
  const start = 100_000
  const sim = new PongSim(() => .3, 7, start)
  const players = [new Player(1, 7_000, random(seed + 1), .3), new Player(2, -3_000, random(seed + 2), .3)]
  const queue: Pending[] = []
  const lastDown = [0, 0], lastUp = [0, 0]
  const oneWay = () => Math.max(1, profile.rtt / 2 + (rng() * 2 - 1) * profile.jitter)
  const truth: { hits: { t: number; seat: number; x: number; y: number }[]; points: { t: number; s: number[] }[]; score?: number[] } = { hits: [], points: [] }
  let hits = 0, score = '0,0', last = start
  for (let tick = 0; tick * TICK < ms; tick++) {
    const now = Math.floor(start + tick * TICK) // production clock is whole milliseconds
    queue.sort((a, b) => a.at - b.at)
    while (queue.length && queue[0].at <= now) queue.shift()!.run()
    sim.step(now - last, now); last = now // like matches.ts: the real elapsed time to `now`
    const f = sim.frame()
    if (sim.hits > hits) { hits = sim.hits; truth.hits.push({ t: r(f.t, 10), seat: 0, x: f.b[0], y: f.b[1] }) }
    if (f.s.join() !== score) { score = f.s.join(); truth.points.push({ t: r(f.t, 10), s: [...f.s] }) }
    if (sim.outcome) break
    if (tick % 2 === 0) for (const [i, p] of players.entries()) {
      if (rng() < profile.frameLoss) continue
      const frame = { id: 'm', ...sim.frame() }
      const at = lastDown[i] = Math.max(lastDown[i], now + oneWay())
      queue.push({ at, run: () => p.receive(frame, at + p.skew) })
    }
    // Each phone samples its touch every 16 ms of its own clock.
    for (const [i, p] of players.entries()) {
      p.input(now + p.skew, sample => {
        const at = lastUp[i] = Math.max(lastUp[i], now + oneWay())
        queue.push({ at, run: () => sim.paddle(p.seat, sample.x, sample.y, false, [sample.x, sample.y],
          { ct: sample.ct, v: sample.v, w: sample.w, n: sample.n, source: 's', rtt: profile.rtt + profile.jitter * 2 }, at) })
      })
    }
  }
  truth.score = sim.score
  return { profile: profile.name, rtt: profile.rtt, truth,
    seats: players.map(p => ({ seat: p.seat, frames: p.frames, inputs: p.inputs })) }
}

const traces = PROFILES.map((p, i) => run(p, 1000 + i, 40_000))
for (const t of traces) console.log(t.profile, 'server hits', t.truth.hits.length, 'points', t.truth.points.length, 'score', t.truth.score)
const out = new URL('../app/src/test/resources/pong-sync-traces.json.gz', import.meta.url)
writeFileSync(out, gzipSync(JSON.stringify(traces)))
console.log('wrote', out.pathname)
