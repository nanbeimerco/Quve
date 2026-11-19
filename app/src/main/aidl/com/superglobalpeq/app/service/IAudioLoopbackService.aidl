package com.superglobalpeq.app.service;

interface IAudioLoopbackService {
    boolean startLoopback(int clientAppUid, in int[] excludedUids, boolean globalLowLatency, in int[] lowLatencyUids, String dspConfigJson) = 0;
    void updateDspConfig(String dspConfigJson) = 1;
    void stopLoopback() = 2;
    String getStatusJson() = 3;
    void registerClientToken(IBinder clientToken) = 4;
    void updateLatencyPolicy(boolean globalLowLatency, in int[] lowLatencyUids) = 5;
    void updateAutoDevicePolicy(String policyJson) = 6;
    boolean startShootoutRound(IBinder clientToken, String roundConfigJson) = 7;
    void selectShootoutSide(int side) = 8;
    void stopShootout() = 9;
    boolean allowOverlayForClient() = 10;
    void destroy() = 16777114;
}
