package dev.broadband.toolbox.privileged

import org.json.JSONObject

interface EthernetBackend {
    val qualified: Boolean
    val reason: String
    fun resolve(id: String): RootInterface
    fun snapshot(ref: RootInterface): JSONObject
    fun validate(ref: RootInterface, plan: JSONObject, original: JSONObject) {}
    fun target(original: String, plan: JSONObject): String
    fun apply(ref: RootInterface, target: String)
    fun waitFor(ref: RootInterface, expectedParcel: String, plan: JSONObject?, timeoutMs: Long = 8000): JSONObject
    fun setMtu(ref: RootInterface, mtu: Int)
    fun route(ref: RootInterface, netId: Int, destination: String, gateway: String, add: Boolean)
    fun reconnect(ref: RootInterface)
}
