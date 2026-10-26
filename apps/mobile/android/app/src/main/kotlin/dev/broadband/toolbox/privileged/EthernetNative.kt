package dev.broadband.toolbox.privileged

class EthernetNative {
    external fun snapshot(index: Int): String
    companion object { init { System.loadLibrary("broadband_native") } }
}
