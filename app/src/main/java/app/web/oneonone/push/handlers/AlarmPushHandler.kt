package app.web.oneonone.push.handlers

/** Frozen A3 seam. Claude replaces the no-op binding in A4. */
interface AlarmPushHandler { fun onAlarmPush(data: Map<String, String>) }
