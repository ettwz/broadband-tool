package dev.broadband.toolbox.privileged;
oneway interface IRootEvents {
    void onEvent(String requestId, String state, String payload);
}
