package com.blez.dualnav.core.data.datasource.wifi

internal object WiFiConstants {
    const val SERVICE_TYPE = "_dualnav._tcp."
    const val PORT = 8988
    const val DISCOVERY_WINDOW_MS = 4000L

    /** NSD TXT record key carrying this device's persistent identity, so a peer can be
     * re-identified after its `host:port` goes stale (e.g. a DHCP-assigned IP change). */
    const val STABLE_ID_ATTRIBUTE = "stableId"
}
