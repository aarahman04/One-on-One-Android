// Run from ../One-on-One/backend: node --import tsx ../../One-on-One-Android/scripts/pong-contact-harness.ts
import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs'
import { createRequire } from 'node:module'
import { execFileSync } from 'node:child_process'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { PongSim, contactRadius, flightPosition } from '../../One-on-One/backend/src/games/pong.js'
import { sweepContact, rimPosition } from '../../One-on-One/backend/src/games/pongPhysics.js'
import { CompensatedDisplay, type Input } from './pong-display-model.js'

// Extract the original simulation, never a copy of its physics, for comparable BEFORE runs.
const modern = process.argv.includes('--modern') || process.argv.includes('--trace')
let baselineDirectory: string | undefined
let Simulation = PongSim
if (!modern && !process.argv.includes('--golden')) {
  baselineDirectory = mkdtempSync(join(tmpdir(), 'pong-contact-'))
  const cwd = fileURLToPath(new URL('../../One-on-One/', import.meta.url))
  for (const name of ['pong', 'pongPhysics']) {
    const source = execFileSync('git', ['show', `bf35268:backend/src/games/${name}.ts`], { cwd, encoding: 'utf8' })
    writeFileSync(join(baselineDirectory, `${name}.ts`), source)
  }
  Simulation = createRequire(import.meta.url)(join(baselineDirectory, 'pong.ts')).PongSim
}

type Frame = ReturnType<PongSim['frame']> & { t: number }
type Profile = { latency: number; jitter: number; hz: number; frameLoss: number; paddleLoss: number }
type Packet = { x: number; y: number; ct: number; v: number; n: number; released: boolean }
const clamp = (x: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, x))
function random(seed: number) {
  return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296 }
}

/** Baseline of PongClient.receive/advance before the contact change; golden-tested in Kotlin. */
export class Display {
  frame: Frame | null = null
  offset = Infinity
  x = .5; y = .9; z = 0; v = 0
  px = .5; py = 2.5
  private freeze: [number, number] | null = null
  private freezeAt = 0
  private used = false
  private bx = .5; private by = .9
  receive(f: Frame, now: number) {
    if (this.frame && f.t < this.frame.t) return
    if (!this.frame || f.h !== this.frame.h) { this.freeze = null; this.used = false; this.bx = f.b[0]; this.by = f.b[1]; this.x = this.bx; this.y = this.by }
    this.frame = f; this.offset = Math.min(this.offset, now - f.t)
  }
  paddle(x: number, y: number, now: number) {
    const f = this.frame
    if (f?.b[3] && f.b[3] > 0 && f.l && !this.used && this.z >= -.033) {
      const r = contactRadius(this.z)
      const c = sweepContact([this.bx, this.by], [this.bx, this.by], [this.px, this.py], [x, y], r, -1)
      if (c && c.ny <= 1e-6 && (x - this.px) * c.nx + (y - this.py) * c.ny > 1e-6) {
        this.freeze = rimPosition(c, [x, y], r); this.freezeAt = now; this.used = true
      }
    }
    this.px = x; this.py = y
  }
  advance(now: number) {
    const f = this.frame
    if (!f) return
    const age = clamp(now - f.t - this.offset, 0, 150) / 1000
    const a = f.a
    const target = a ? flightPosition(Array.from({ length: 4 }, (_, i) => a.slice(i * 3, i * 3 + 3) as [number, number, number])
      .map((p, i) => i === 3 ? [p[0] + f.drift * age, p[1], p[2]] : p), (a[12] + age) / a[13])
      : [f.b[0] + f.b[2] * age, f.b[1] + f.b[3] * age, f.z[0]]
    if (f.b[3] > 0 && f.l && target[2] >= -.033 && this.by <= this.py) {
      const c = sweepContact([this.bx, this.by], target as [number, number], [this.px, this.py], [this.px, this.py], contactRadius(Math.max(this.z, target[2])), -1)
      if (c && this.by + (target[1] - this.by) * c.time <= this.py) {
        target[0] = this.bx + (target[0] - this.bx) * c.time; target[1] = this.by + (target[1] - this.by) * c.time
      }
    }
    this.bx = target[0]; this.by = target[1]; this.z = target[2]; this.v = f.t + age * 1000
    if (now - this.freezeAt > 150) this.freeze = null
    this.x = this.freeze?.[0] ?? this.bx; this.y = this.freeze?.[1] ?? this.by
  }
}

function trial(profile: Profile, seed: number, style: number, modern = false, trace?: unknown[]) {
  const rng = random(seed), sim = new Simulation(() => .75), display = modern ? new CompensatedDisplay() : new Display()
  // Eight server-observed handshake/heartbeat probes on the same simulated link.
  const probeRng = random(seed ^ 0x123456)
  const measuredRtt = Math.max(...Array.from({ length: 8 }, () =>
    Math.max(0, profile.latency + (probeRng() * 2 - 1) * profile.jitter) +
    Math.max(0, profile.latency + (probeRng() * 2 - 1) * profile.jitter)))
  // A real neutral serve and table bounce. The receiver starts safely outside its path.
  sim.paddle(1, .5, 2.5, true); sim.paddle(2, .5, -.4); sim.step(3000)
  sim.paddle(2, .5, .44, true); sim.step(8)
  assert.equal(sim.hits, 1)
  let actualTarget: number[] | null = null
  const probe = sim as unknown as { strike: (seat: number) => void; flight: { points: number[][]; duration: number } }
  const strike = probe.strike.bind(sim)
  probe.strike = (seat) => {
    strike(seat)
    if (seat === 1 && actualTarget === null) actualTarget = probe.flight.points[3].concat(probe.flight.duration)
  }
  const epoch = modern ? sim.frame().t : 3008
  const queue: { at: number; run: () => void }[] = []
  let lastDown = 0, lastUp = 0, nextTick = 0, tickAt = 0, nextFrame = 0, nextRender = 0, nextSend = 0, sequence = 0
  let started = -1, overlap = -1, overlapFrames = 0, contactFrames = 0, launch = -1, confirmed = -1, snap = 0, ghost = false, startY = 2.5, startX = .5
  let launchGap = 0
  let lastHits = sim.hits
  let input: Input | null = null
  let releasedStroke = false
  const recent: Input[] = []
  const move = (x: number, y: number, now: number, released = false) => {
    const result = display instanceof CompensatedDisplay ? display.paddle(x, y, now, released) : display.paddle(x, y, now)
    if (result) { input = result; recent.push(result); if (recent.length > 32) recent.shift() }
    trace?.push({ type: 'paddle', at: now, x, y, released, expected: [display.x, display.y, display.z, display.v] })
  }
  const packet = (released: boolean, now: number) => {
    if (modern && released) move(display.px, display.py, now, true)
    const p: Packet = modern && input ? { ...input, released } : { x: display.px, y: display.py, ct: now, v: display.v, n: ++sequence, released }
    const samples = recent.filter(sample => sample.n < p.n).concat(p).map(sample => ({ ...sample, touch: [sample.x, sample.y] as [number, number], rtt: measuredRtt }))
    if (rng() < profile.paddleLoss) return
    const at = lastUp = Math.ceil(Math.max(lastUp, now + Math.max(0, profile.latency + (rng() * 2 - 1) * profile.jitter)))
    queue.push({ at, run: () => {
      if (modern) sim.paddleBatch(1, samples, epoch + at)
      else sim.paddle(1, p.x, p.y, p.released, undefined)
    } })
  }
  for (let now = 0; now <= 2400; now++) {
    if (launch >= 0 && now > launch + 180) break
    queue.sort((a, b) => a.at - b.at)
    while (queue[0]?.at <= now) queue.shift()!.run()
    if (now >= nextTick) {
      sim.step(now - tickAt || 1, epoch + now); tickAt = now; nextTick = now + 12 + Math.floor(rng() * 10)
      if (sim.hits > lastHits && confirmed < 0) {
        lastHits = sim.hits
        confirmed = now
      }
    }
    if (now >= nextFrame) {
      nextFrame += 1000 / 30
      if (rng() >= profile.frameLoss) {
        const f = { ...sim.frame(), t: modern ? sim.frame().t : epoch + now }
        const at = lastDown = Math.ceil(Math.max(lastDown, now + Math.max(0, profile.latency + (rng() * 2 - 1) * profile.jitter)))
        queue.push({ at, run: () => {
          const x = display.x, y = display.y
          display.receive(f, at)
          trace?.push({ type: 'receive', at, frame: f, expected: [display.x, display.y, display.z, display.v] })
          if (f.h === 2 && launch < 0) {
            snap = Math.hypot(display.x - x, display.y - y); launch = at
            launchGap = Math.max(0, Math.hypot(x - display.px, y - display.py) - contactRadius(display.z))
            ghost = launchGap > .033
          }
        } })
      }
    }
    if (now >= nextRender) {
      nextRender += 1000 / profile.hz
      display.advance(now)
      trace?.push({ type: 'advance', at: now, expected: [display.x, display.y, display.z, display.v] })
      if (started < 0 && display.frame?.l && display.y > 1.35 && display.z >= -.033) {
        started = now; startX = display.x + (style === 3 ? -.35 : style === 4 ? .35 : 0)
        startY = style === 5 ? display.y - .25 : Math.max(1.3, display.y + .35)
        move(clamp(startX, -.26, 1.26), clamp(startY, 1.3, 2.5), now)
      }
      if (started >= 0 && !releasedStroke) {
        const duration = [500, 240, 60, 100, 100, 120][style]
        const t = clamp((now - started) / duration, 0, 1)
        if (style === 0) { move(display.x, 1.8, now) }
        else {
          move(clamp(startX + (display.x - startX) * t, -.26, 1.26),
            clamp(startY + (style === 5 ? .5 : -.6) * t, 1.3, 2.5), now)
        }
        const gap = Math.hypot(display.x - display.px, display.y - display.py) - contactRadius(display.z)
        if (gap <= 0 && display.z >= -.033 && display.frame?.l) {
          if (overlap < 0) overlap = now
          overlapFrames++; contactFrames++
        } else contactFrames = 0
        if (now >= nextSend) { packet(false, now); nextSend = now + (display instanceof CompensatedDisplay && display.possible ? 16 : 33) }
        if (now - started >= duration) { packet(true, now); releasedStroke = true }
      }
    }
  }
  const d = display instanceof CompensatedDisplay ? display : null
  const visibleLaunch = d && d.firstPredictedAt >= 0 ? d.firstPredictedAt : launch
  const visibleContact = d && d.firstPredictedAt >= 0 ? Math.min(overlap < 0 ? Infinity : overlap, d.firstPredictedAt) : overlap
  const mismatch = actualTarget && d?.firstTarget ? Math.max(...(actualTarget as number[]).map((x, i) => Math.abs(x - d.firstTarget![i]))) : null
  return { overlapMiss: overlapFrames >= 2 && (confirmed < 0 || visibleLaunch < 0 || visibleLaunch - visibleContact > 150), ghost: d ? d.firstLaunchGap > .033 : ghost,
    overlap: visibleContact >= 0, snap: d ? Math.max(d.confirmationSnap, ...d.blendSteps) : snap,
    delay: visibleContact >= 0 && visibleLaunch >= 0 ? visibleLaunch - visibleContact : null,
    early: visibleContact >= 0 && visibleLaunch >= 0 && visibleLaunch < visibleContact,
    launchGap: d ? Math.max(0, d.firstLaunchGap) : launchGap,
    predictions: display instanceof CompensatedDisplay ? display.predictionCount : 0,
    rejections: d?.rejections ?? 0, mismatch, unconfirmed: confirmed < 0 }
}

const profiles: Profile[] = []
for (const latency of [0, 40, 80, 150]) for (const jitter of [0, 20, 40]) for (const hz of [60, 90, 120])
  for (const loss of [false, true]) profiles.push({ latency, jitter, hz, frameLoss: loss ? .1 : 0, paddleLoss: loss ? .05 : 0 })
const percentile = (a: number[], q: number) => a.sort((a, b) => a - b)[Math.max(0, Math.ceil(a.length * q) - 1)] ?? 0
const goldenPath = new URL('../app/src/test/resources/pong-display-baseline.json', import.meta.url)
if (process.argv.includes('--trace')) {
  const traces: unknown[] = []
  for (const latency of [0, 40, 150]) for (const hz of [60, 90, 120]) for (const style of [0, 2, 3]) {
    const actions: unknown[] = []
    trial({ latency, jitter: 20, hz, frameLoss: .1, paddleLoss: .05 }, 5000 + latency + style, style, true, actions)
    traces.push({ latency, hz, style, actions })
  }
  const actions: unknown[] = []
  trial({ latency: 150, jitter: 0, hz: 90, frameLoss: .1, paddleLoss: .05 }, 67050, 2, true, actions)
  traces.push({ latency: 150, hz: 90, style: 2, seed: 67050, actions })
  writeFileSync(new URL('../app/src/test/resources/pong-display-traces.json', import.meta.url), JSON.stringify(traces) + '\n')
  process.exit(0)
}
if (process.argv.includes('--golden')) {
  const sim = new PongSim(() => .75), d = new Display()
  sim.paddle(1, .5, 2.5, true); sim.step(3000)
  const frames: { at: number; frame: Frame; draws: { at: number; xyz: number[] }[] }[] = []
  for (const [t, at] of [[1000, 1040], [1050, 1110], [1100, 1140], [1150, 1220]]) {
    sim.step(50)
    const frame = { ...sim.frame(), t }
    d.receive(frame, at)
    const draws = [at, at + 8, at + 16].map(at => { d.advance(at); return { at, xyz: [d.x, d.y, d.z] } })
    frames.push({ at, frame, draws })
  }
  writeFileSync(goldenPath, JSON.stringify(frames, null, 2) + '\n')
  process.exit(0)
}
const golden = JSON.parse(readFileSync(goldenPath, 'utf8'))
const check = new Display()
for (const item of golden) {
  check.receive(item.frame, item.at)
  for (const draw of item.draws) {
    check.advance(draw.at)
    ;[check.x, check.y, check.z].forEach((x, i) => assert.ok(Math.abs(x - draw.xyz[i]) < 1e-5, 'baseline display trace'))
  }
}
const trialsPerProfile = 240
const rows = profiles.map((p, index) => {
  const results = Array.from({ length: trialsPerProfile }, (_, i) => trial(p, 10000 + index * 1000 + i, i % 6, modern))
  return { ...p, trials: results.length, visibleOverlaps: results.filter(x => x.overlap).length,
    visibleOverlapMissRate: results.filter(x => x.overlapMiss).length / Math.max(1, results.filter(x => x.overlap).length),
    ghostHitRate: results.filter(x => x.ghost).length / results.length,
    launchSnapP99: percentile(results.map(x => x.snap), .99), launchSnapMax: Math.max(...results.map(x => x.snap)),
    contactToLaunchP95: percentile(results.flatMap(x => x.delay === null ? [] : [x.delay]), .95),
    drawnGapMax: Math.max(...results.map(x => x.launchGap)),
    earlyLaunches: results.filter(x => x.early).length,
    predictions: results.reduce((n, x) => n + x.predictions, 0), rejectedPredictions: results.reduce((n, x) => n + x.rejections, 0),
      strokeMismatch: Math.max(...results.flatMap(x => x.mismatch === null ? [] : [x.mismatch])),
      unconfirmed: results.filter(x => x.unconfirmed).length }
})
const report = { revision: modern ? 'paired-contact-candidate' : 'bf35268-original-simulation-and-display', trials: rows.length * trialsPerProfile, profiles: rows }
const output = process.argv[2]
if (output) writeFileSync(output, JSON.stringify(report, null, 2) + '\n')
console.table([0, 40, 80, 150].map(latency => {
  const group = rows.filter(x => x.latency === latency)
  return { latency, trials: group.length * trialsPerProfile,
    missPct: (100 * group.reduce((s, x) => s + x.visibleOverlapMissRate, 0) / group.length).toFixed(2),
    ghostPct: (100 * group.reduce((s, x) => s + x.ghostHitRate, 0) / group.length).toFixed(2),
    snapMax: Math.max(...group.map(x => x.launchSnapMax)).toFixed(4), launchP95: Math.max(...group.map(x => x.contactToLaunchP95)).toFixed(1) }
}))

if (baselineDirectory) rmSync(baselineDirectory, { recursive: true })
if (modern) for (const row of rows) {
  const name = JSON.stringify(row)
  assert.ok(row.visibleOverlaps >= row.trials * .95, `coverage: ${name}`)
  assert.ok(row.visibleOverlapMissRate <= (row.latency === 150 && row.paddleLoss ? .02 : .005), `miss: ${name}`)
  assert.ok(row.ghostHitRate <= .005 && row.drawnGapMax <= 1.5 * .033, `ghost: ${name}`)
  assert.ok(row.launchSnapP99 <= .02 && row.launchSnapMax <= .05, `correction: ${name}`)
  assert.equal(row.earlyLaunches, 0, `early launch: ${name}`)
  if (row.latency <= 80) assert.ok(row.contactToLaunchP95 <= 1000 / row.hz, `launch delay: ${name}`)
  assert.ok(row.strokeMismatch <= .0001, `stroke: ${name}`)
  assert.equal(row.unconfirmed, 0, `unconfirmed: ${name}`)
}
