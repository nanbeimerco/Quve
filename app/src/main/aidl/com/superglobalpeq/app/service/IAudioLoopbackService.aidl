package com.superglobalpeq.app.service;

interface IAudioLoopbackService {
    boolean startLoopback(int clientAppUid, in int[] excludedUids, String dspConfigJson) = 0;
    void updateDspConfig(String dspConfigJson) = 1;
    void stopLoopback() = 2;
    String getStatusJson() = 3;
    void registerClientToken(IBinder clientToken) = 4;
    void destroy() = 16777114;
}
