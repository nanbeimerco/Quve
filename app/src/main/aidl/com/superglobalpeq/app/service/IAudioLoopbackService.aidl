package com.superglobalpeq.app.service;

interface IAudioLoopbackService {
    boolean startLoopback(int clientAppUid, in int[] excludedUids, String dspConfigJson);
    void updateDspConfig(String dspConfigJson);
    void stopLoopback();
    String getStatusJson();
    void destroy();
}
