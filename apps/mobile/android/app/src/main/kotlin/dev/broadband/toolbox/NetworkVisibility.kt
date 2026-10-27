package dev.broadband.toolbox

/** Carrier service networks (IMS, MMS, emergency calls) are not user data networks. */
internal fun showInNetworkOverview(transport: String, internet: Boolean): Boolean =
    transport != "cellular" || internet
