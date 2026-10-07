package app.web.oneonone.push.handlers

/** Frozen A3 seam. Claude replaces the no-op binding in A5. */
interface CallPushHandler {
    fun onCallPush(data: Map<String, String>)
    fun onCallEndPush(data: Map<String, String>)
}
