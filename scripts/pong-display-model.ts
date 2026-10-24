// Deterministic test model of PongClient; its action traces are executed by the Kotlin suite.
import { flightPosition, type PongFrame } from '../../One-on-One/backend/src/games/pong.js'
import { contactRadius, launch, rimPosition, StrokeWindow, sweepContact } from '../../One-on-One/backend/src/games/pongPhysics.js'

export type TimedFrame = PongFrame
const clamp = (x: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, x))
type Prediction = { arc: number[]; at: number; viewAt: number; driftAt: number; hits: number; n: number }
export type Input = { x: number; y: number; ct: number; v: number; n: number; released: boolean }
export class CompensatedDisplay {
  frame: TimedFrame | null = null
  frames: TimedFrame[] = []
  x = .5; y = .9; z = 0; px = .5; py = 2.5; v = 0
  ct = 0; n = 0
  predictedAt = -1
  firstPredictedAt = -1
  firstLaunchGap = 0
  firstTarget: number[] | null = null
  predictionCount = 0
  rejections = 0
  confirmations = 0
  confirmationSnap = 0
  launchGap = 0
  blendSteps: number[] = []
  private confirmationAccepted = false
  private confirmedPrediction: Prediction | null = null
  private confirmedSide = 0
  private confirmedIdleAt = 0
  private predictedV = 0
  private offsets: number[] = []
  private offset: number | null = null
  private prediction: Prediction | null = null
  private used = false
  private selected: TimedFrame | null = null
  private stroke = new StrokeWindow()
  private inputAt = 0
  private released = true
  private lastAdvance = 0
  private previous = [.5, .9, 0]
  private previousTime = 0
  private reconcile = false
  private blend: { at: number; delta: number[]; remaining: number } | null = null
  get eligible() {
    const f = this.selected ?? this.frame
    return !!f && this.z >= -.033 && (f.l && f.b[3] > 0 || f.serve === 1 && f.c === 0) && !this.prediction && !this.blend && !this.reconcile
  }
  get possible() { return !!this.prediction || this.eligible && Math.hypot(this.x - this.px, this.y - this.py) <= 2 * contactRadius(this.z) }
  get hasPrediction() { return !!this.prediction }

  receive(f: TimedFrame, now: number) {
    const old = this.frame
    if (old && f.t < old.t) return
    const reset = !old || f.s.some((s, i) => s !== old.s[i]) || f.serve !== 0 && (!old.serve || f.c > old.c + 100)
    const hit = !!old && f.h !== old.h
    const before = [this.x, this.y]
    if (reset || hit || this.prediction && f.ack[0] >= this.prediction.n && f.h <= this.prediction.hits) {
      if (this.prediction && !reset) {
        this.confirmedPrediction = this.prediction
        this.confirmedSide = this.stroke.stroke.side; this.confirmedIdleAt = this.inputAt + 120
        this.reconcile = true; this.confirmationAccepted = hit
        if (hit) this.confirmations++
        else this.rejections++
      }
      this.prediction = null; this.used = false
      if (reset) { this.frames.length = 0; this.stroke.clear(); this.blend = null; this.reconcile = false; this.confirmedPrediction = null; this.selected = f; this.px = f.p[0]; this.py = f.r[0] }
    }
    this.frame = f; this.n = Math.max(this.n, f.ack[0])
    this.offsets.push(now - f.t)
    if (this.offsets.length > 32) this.offsets.shift()
    const sorted = [...this.offsets].sort((a, b) => a - b), estimate = sorted[Math.floor((sorted.length - 1) / 8)]
    this.offset = this.offset === null ? estimate : this.offset + clamp(estimate - this.offset, -1, 1)
    this.frames.push(f)
    while (this.frames.length > 32 || this.frames.length > 1 && f.t - this.frames[0].t > 800) this.frames.shift()
    if (reset || !f.a && f.b[2] === 0 && f.b[3] === 0 && !this.prediction) {
      this.x = f.b[0]; this.y = f.b[1]; this.z = f.z[0]; this.previous = [this.x, this.y, this.z]
    }
    if (hit && !reset) this.confirmationSnap = Math.max(this.confirmationSnap, Math.hypot(this.x - before[0], this.y - before[1]))
  }

  private position(f: TimedFrame, at: number) {
    const age = (at - f.t) / 1000, a = f.a
    if (!a) return [f.b[0] + f.b[2] * age, f.b[1] + f.b[3] * age, f.z[0]]
    const points = Array.from({ length: 4 }, (_, i) => a.slice(i * 3, i * 3 + 3) as [number, number, number])
    points[3][0] += f.drift * age
    return flightPosition(points, (a[12] + age) / a[13])
  }

  private drift(now: number) {
    const p = this.prediction
    if (!p) return
    const end = Math.min(now, this.inputAt + 120), seconds = Math.max(0, end - p.driftAt) / 1000
    p.arc[9] = Math.fround(p.arc[9] + this.stroke.stroke.side * (.3 / 12.16 / .02) * seconds)
    p.driftAt = Math.max(p.driftAt, now)
    if (now - this.inputAt > 120) this.stroke.clear()
  }

  advance(now: number) {
    if (!this.frame) return
    this.lastAdvance = now
    const priorViewTime = this.v
    let selected = this.frames.at(-1)!
    this.v = clamp(Math.max(this.v, now - this.offset! - 50), this.frames[0].t, this.prediction ? Infinity : selected.t + 50)
    for (const f of this.frames) { if (f.t <= this.v) selected = f; else break }
    const start = selected.a ? selected.t - Math.round(selected.a[12] * 10000) / 10 : -Infinity
    for (const f of this.frames) {
      if (!f.a) continue
      const at = f.t - Math.round(f.a[12] * 10000) / 10
      if (f.t > selected.t && at > start + 1 && at <= this.v) { selected = f; break }
    }
    this.selected = selected
    let target = this.position(selected, this.v)
    this.previous = [this.x, this.y, this.z]
    this.previousTime = priorViewTime
    if (!this.prediction && this.eligible && (this.released || now - this.inputAt > 120))
      this.predict(this.previous, target, [this.px, this.py], [this.px, this.py], now, true)
    if (this.prediction) {
      this.drift(now)
      const p = this.prediction
      target = this.position({ ...selected, a: p.arc, t: p.viewAt, drift: 0 }, this.v)
    }
    if (this.reconcile) {
      const source = this.confirmedPrediction
      let anchor = [this.x, this.y, this.z]
      if (source) {
        source.arc[9] = Math.fround(source.arc[9] + this.confirmedSide * (.3 / 12.16 / .02) * Math.max(0, Math.min(now, this.confirmedIdleAt) - source.driftAt) / 1000)
        source.driftAt = now
        anchor = this.position({ ...selected, a: source.arc, t: source.viewAt, drift: 0 }, this.v)
      }
      if (this.confirmationAccepted && selected.h < this.frame.h) target = anchor
      else {
        this.blend = { at: now, delta: anchor.map((x, i) => x - target[i]), remaining: 1 }
        this.reconcile = false
      }
    }
    if (this.blend) {
      const remaining = clamp(1 - (now - this.blend.at) / 60, 0, 1)
      this.blendSteps.push(Math.hypot(this.blend.delta[0], this.blend.delta[1]) * (this.blend.remaining - remaining))
      this.blend.remaining = remaining
      target = target.map((v, i) => v + this.blend!.delta[i] * remaining)
      if (remaining === 0) this.blend = null
    }
    ;[this.x, this.y, this.z] = target.map(Math.fround)
  }

  paddle(x: number, y: number, now = this.lastAdvance, released = false): Input {
    x = Math.fround(clamp(x, -.26, 1.26)); y = Math.fround(clamp(y, 1.3, 2.5))
    this.drift(now)
    this.ct = Math.max(this.ct + 1, Math.floor(now)); this.n++
    this.stroke.sample(this.ct, x, y); this.inputAt = now
    if (this.eligible) this.predict(this.previous, [this.x, this.y, this.z], [this.px, this.py], [x, y], now)
    if (released) this.stroke.clear()
    this.released = released
    this.px = x; this.py = y
    this.previous = [this.x, this.y, this.z]
    this.previousTime = this.v
    return { x, y, ct: this.ct, v: this.v, n: this.n, released }
  }

  private predict(a: number[], b: number[], p0: [number, number], p1: [number, number], now: number, nextSample = false) {
    if (this.used || !this.eligible) return
    const r = contactRadius(Math.max(a[2], b[2]))
    const c = sweepContact([a[0], a[1]], [b[0], b[1]], p0, p1, r, -1)
    if (!c) return
    const bat: [number, number] = [p0[0] + (p1[0] - p0[0]) * c.time, p0[1] + (p1[1] - p0[1]) * c.time]
    const rim = rimPosition(c, bat, r), h = a[2] + (b[2] - a[2]) * c.time
    const viewAt = this.previousTime > 0 ? this.previousTime + (this.v - this.previousTime) * c.time : this.v
    const at = now - Math.max(0, this.v - viewAt)
    this.prediction = { arc: launch(rim[0], rim[1], h, this.stroke.stroke).map(Math.fround), at, viewAt, driftAt: at,
      hits: this.frame!.h, n: Math.max(this.n + (nextSample ? 1 : 0), this.frame!.ack[0] + 1) }
    this.used = true; this.predictedAt = now; this.predictionCount++
    this.predictedV = this.v
    this.launchGap = Math.hypot(a[0] + (b[0] - a[0]) * c.time - bat[0], a[1] + (b[1] - a[1]) * c.time - bat[1]) - r
    if (this.firstPredictedAt < 0) {
      this.firstPredictedAt = now; this.firstLaunchGap = this.launchGap
      this.firstTarget = this.prediction.arc.slice(9, 12).concat(this.prediction.arc[13])
    }
    this.x = Math.fround(rim[0]); this.y = Math.fround(rim[1]); this.z = Math.fround(h)
  }
}
