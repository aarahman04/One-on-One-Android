# Backend prompt: instant presence (One-on-One web repo)

Status: original proposal preserved from the 2026-10-19 handoff. Backend/web and
Android implementations now exist on their respective `feat/live-presence`
branches; deployment/release is tracked in each repo's progress document.
The web repo's `docs/API-CONTRACT.md` remains authoritative. The sketch below is
historical; use the implemented handler rather than copying it verbatim.

No database or Supabase change is needed. Presence lives in server memory and is never stored.

## Prompt (give this to the agent working in aarahman04/One-on-One)

Add real-time presence to the Socket.IO server so "Online" / "Last seen" changes the moment the other member opens or leaves the chat. Right now clients guess presence from `last_read_at`, which is bumped by a `POST /read` heartbeat every 10 s and treated as online for 15 s. That makes "Online" stay on for up to 20 s after someone leaves, and it depends on the phone's clock matching the server's.

Requirements:

1. New client → server event `presence` with body `{ active: boolean }`, and no ack.
   - `active: true` means "this socket's chat screen is in the foreground". Clients send it on resume or focus and again after every reconnect.
   - `active: false` means it is in the background or hidden. Clients send it on pause or blur.
   - Rate limit it to 20 per socket per 10 s, the same as `typing`.
   - Store it on `socket.data.active`. A new socket starts with `active = false`.
2. Server → client event `presence` with body `{ userId, online: boolean, lastSeenAt: string | null }`.
   - `online` is true when ANY of that user's sockets in the connection room has `active === true`.
   - `lastSeenAt` is the ISO time when the user last went from online to offline in this process. It is null while they are online, and null if this process never saw them.
   - Emit it to the OTHER member's sockets only, and only when that user's `online` value actually changes.
3. Recompute the user's state when any of these happen: a `presence` event arrives, the socket disconnects (add `socket.on('disconnect')`), or the socket joins the room in `io.on('connection')`.
4. When a socket connects or sends `presence`, also send the partner's current state to that socket alone (`socket.emit('presence', ...)`). This shows the right state on open without waiting for a change.
5. Keep a `Map<userId, lastSeenAt>` in the process. Use `io.in(room).fetchSockets()` to compute `online`, as `syncDelivery` and the receipt emitter already do. Re-resolve the live connection per event with `getLiveConnectionForUser`, the same way `typing` does. Never trust a connection id from the client.
6. Leave the `last_read_at` heartbeat and `receipt:update` exactly as they are. Older clients keep working, and `lastReadAt` stays the fallback "last seen" after a server restart.
7. Document both events in `docs/API-CONTRACT.md`: add them to the socket event tables and add a short "Presence" section.
8. Add tests: two sockets for one user, where one going inactive keeps the user online; disconnect sends offline; a change is relayed only to the other member; a socket with no live connection is ignored.
9. Web client (`ChatPage.ts`): emit `presence` from `visibilitychange`, `focus` / `blur` and after each socket connect. Show `online` from the event, and fall back to the `lastReadAt` window when no event has arrived.

## Reference server sketch (socketServer.ts)

```ts
const lastSeen = new Map<string, string>()          // userId -> ISO, process memory only
const lastOnline = new Map<string, boolean>()        // userId -> last broadcast value

async function presenceOf(io: Server, connectionId: string, userId: string) {
  const sockets = await io.in(room(connectionId)).fetchSockets()
  const online = sockets.some((s) => (s.data as SocketData).userId === userId && (s.data as SocketData).active === true)
  return { userId, online, lastSeenAt: online ? null : lastSeen.get(userId) ?? null }
}

async function publishPresence(io: Server, connectionId: string, userId: string) {
  const state = await presenceOf(io, connectionId, userId)
  if (lastOnline.get(userId) === state.online) return
  if (lastOnline.get(userId) === true && !state.online) state.lastSeenAt = new Date().toISOString(), lastSeen.set(userId, state.lastSeenAt)
  lastOnline.set(userId, state.online)
  for (const s of await io.in(room(connectionId)).fetchSockets()) {
    if ((s.data as SocketData).userId !== userId) s.emit('presence', state)
  }
}

// inside io.on('connection'):
socket.on('presence', async (msg: { active?: unknown }) => {
  // rate limit like typing ...
  const { userId } = socket.data as SocketData
  const connection = await getLiveConnectionForUser(userId)
  if (!connection) return
  ;(socket.data as SocketData).active = msg?.active === true
  socket.join(room(connection.id))
  await publishPresence(io, connection.id, userId)
  socket.emit('presence', await presenceOf(io, connection.id, otherMemberId(connection, userId)))
})
socket.on('disconnect', async () => {
  const { userId } = socket.data as SocketData
  const connection = await getLiveConnectionForUser(userId).catch(() => null)
  if (connection) await publishPresence(io, connection.id, userId)
})
```

(`SocketData` gains `active?: boolean`.)

## After it ships

Tell the Android agent that the contract now has `presence`. The Android change is small: InternetTransport emits `presence` when `MessageService.visible()` changes and after each reconnect, and exposes a `presence` flow. ChatPresence prefers that flow and falls back to the existing `lastReadAt` window.
