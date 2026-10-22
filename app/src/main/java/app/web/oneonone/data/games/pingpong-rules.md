# Ping Pong physics rules

Table widths, seconds; `BAT_R=.137`, `BALL_R=.033`. Keep the rendered circles inside those physical radii (inset outlines). The handle is decorative, on the player's baseline side.

- Run 240 fixed steps/second, at most eight per frame/tick; discard excess stalled-frame time. Interpolate rendering between the last two fixed states.
- Bat motion is a timestamped polyline, capped at `1/.06` widths/second. Keep every historical/coalesced sample. Measure collision velocity from the last 50 ms of actual simulated positions. A stopped/clamped bat has zero velocity; lift/cancel clears queued local movement immediately. Only the local input owns the local bat.
- Relative swept circles find the first impact, advance to it, and continue the remaining time. An initial penetration is always a push. A ball can never impede a bat.
- In order: front, aligned, bat-driven contact above `.15` speed is STRIKE; front ball-driven contact is BLOCK; everything else is PUSH. Use the Gather decision table, including its `front > -.2` and 60-degree alignment rules.
- STRIKE direction is the normalized midpoint of contact normal and bat direction, with forward component at least `.15`. Speed is `clamp(max(2 + 1.25*closingSpeed, escapeSpeed), 2, 25)`. The one-time escape floor is `1.05*actualSweepNormal/dot(direction,normal)`: the 50 ms average otherwise understates a hard start and allows the bat to overtake its shot. The maximum (1.5 times the bat cap) accommodates even a glancing escape.
- BLOCK uses moving-wall restitution `.6`, capped by the same maximum. It clears spin. PUSH changes position only and remains a push until separation; the ball stays solid during follow-through.
- STRIKE/BLOCK lock that bat out until separation exceeds the sum of radii plus `.01`; then another approaching sweep may contact it. A locked bat cannot push or steer the flight.
- Only a glancing STRIKE sets spin: `clamp(cross(normal,batVelocity)/BAT_SPEED_MAX,-1,1)`. Decay time is `.6 s`; angular rate is `-.1*spin*(vy/speed)^2` rad/s. The heading factor bounds lateral drift at the far end even for shallow shots. Speed stays constant. Left-to-right front-face slices curve left. A 180-degree seat rotation preserves scalar spin.
- Flying balls have OPEN edges and no XY wall or net bounce. A PUSH that would leave the table slides along its edge to the nearest valid bat-rim intersection. Keep the existing bat limits (`x=-.26..1.26`, own-view `y=1.3..2.5`) and table landing/floor scoring. The surface crossing is swept, so a fast shot cannot skip its landing. Height is separate from planar velocity.
- Draw table, ball shadow, white tapered history, ball, bat shadow, bat. Keep 12 trail samples over at most 180 ms. Debug builds warn once per contiguous overlap outside the strike/block lock.

The backend remains authoritative for score, serves, hits, landings and outcomes. The web client shares `pongPhysics.ts`; Android and the demo mirror it in `PongPhysics.kt`, pinned by matching tests/fixture.

Backward-compatible additions: paddle `path` is up to 128 ordered `[monotonicSeconds,x,y]` samples (up to one second per batch). Existing `x/y/released/touch` stay valid. The server maps sample time to its fixed clock with one packet of interpolation delay; release is a timed stop boundary. Frames add `rules:1`, `locks` (`0=armed,1=impulse lock,2=push`), and `lastHit`. Existing fields/14-value height arc remain. Older servers still use the legacy cubic render decoder; older one-axis clients retain automatic serves.
