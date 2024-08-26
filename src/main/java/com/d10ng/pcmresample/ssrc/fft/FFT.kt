package com.d10ng.pcmresample.ssrc.fft;

/**
 * Created by Wayne on 5/19/2015.
 * FFT
 */
public interface FFT {
    void init(int n);
    void reset();
    void realDFT(double[] a);
    void realInverseDFT(double[] a);
}
