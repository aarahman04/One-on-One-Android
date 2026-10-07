package app.web.oneonone.call

enum class CallKind { Audio, Video }

/** Frozen A2 seam. Claude replaces the binding in A5; chat never starts WebRTC directly. */
interface CallLauncher { fun start(kind: CallKind) }
