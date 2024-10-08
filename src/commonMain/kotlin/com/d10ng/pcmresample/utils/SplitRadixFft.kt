/*
 * Copyright Takuya OOURA, 1996-2001
 *
 * You may use, copy, modify and distribute this code
 * for any purpose (include commercial use) and without fee.
 * Please refer to this package when you modify this code.
 */
package com.d10ng.pcmresample.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


/**
 * Fast Fourier/Cosine/Sine Transform.
 * <pre>
 * dimension   :one
 * data length :power of 2
 * decimation  :frequency
 * radix       :**split-radix**
 * data        :inplace
 * table       :use
</pre> *
 * <h4>Appendix:</h4>
 *
 *
 * The cos/sin table is recalculated when the larger table required.
 * w[] and ip[] are compatible with all routines.
 *
 * @author [Takuya OOURA](mailto:ooura@mmm.t.u-tokyo.ac.jp)
 * @author [Naohide Sano](mailto:vavivavi@yahoo.co.jp) (nsano)
 * @version 0.00 060127 nsano port to java version <br></br>
 */
class SplitRadixFft {
    /**
     * Complex Discrete Fourier Transform.
     * <pre>
     * [definition]
     * &lt;case1&gt;
     * X[k] = sum_j=0&amp;circ;n-1 x[j]*exp(2*pi*i*j*k/n), 0&lt;=k&lt;n
     * &lt;case2&gt;
     * X[k] = sum_j=0&amp;circ;n-1 x[j]*exp(-2*pi*i*j*k/n), 0&lt;=k&lt;n
     * (notes: sum_j=0&amp;circ;n-1 is a summation from j=0 to n-1)
     * [usage]
     * &lt;case1&gt;
     * ip[0] = 0; // first time only
     * cdft(2*n, 1, a, ip, w);
     * &lt;case2&gt;
     * ip[0] = 0; // first time only
     * cdft(2*n, -1, a, ip, w);
     * [remark]
     * Inverse of
     * cdft(2*n, -1, a, ip, w);
     * is
     * cdft(2*n, 1, a, ip, w);
     * for (j = 0; j &lt;= 2 * n - 1; j++) {
     * a[j] *= 1.0 / n;
     * }
     * .
    </pre> *
     * @param n 2*n data length (int)
     * n &gt;= 1, n = power of 2
     * @param isgn
     * @param a a[0...2*n-1] input/output data (REAL *)
     * input data
     * a[2*j] = Re(x[j]),
     * a[2*j+1] = Im(x[j]), 0&lt;=j&lt;n
     * output data
     * a[2*k] = Re(X[k]),
     * a[2*k+1] = Im(X[k]), 0&lt;=k&lt;n
     * @param ip ip[0...*] work area for bit reversal (int *)
     * length of ip &gt;= 2+sqrt(n)
     * strictly,
     * length of ip &gt;=
     * 2+(1&lt;&lt;(int)(log(n+0.5)/log(2))/2).
     * ip[0],ip[1] are pointers of the cos/sin table.
     * @param w w[0...n/2-1] cos/sin table (REAL *)
     * w[],ip[] are initialized if ip[0] == 0.
     */
    fun cdft(n: Int, isgn: Int, a: DoubleArray, ip: IntArray, w: DoubleArray) {
        var nw: Int

        nw = ip[0]
        if (n > (nw shl 2)) {
            nw = n shr 2
            makewt(nw, ip, w)
        }
        if (isgn >= 0) {
            cftfsub(n, a, ip, 2, nw, w)
        } else {
            cftbsub(n, a, ip, 2, nw, w)
        }
    }

    /**
     * Real Discrete Fourier Transform.
     * <pre>
     * [definition]
     * &lt;case1&gt; RDFT
     * R[k] = sum_j = 0 &amp;  (n - 1) a[j] * cos(2 * pi * j * k / n), 0 &lt;= k &lt;= n / 2
     * I[k] = sum_j = 0 &amp;  (n - 1) a[j] * sin(2 * pi * j * k / n), 0 &lt; k &lt; n / 2
     * &lt;case2&gt; IRDFT (excluding scale)
     * a[k] = (R[0] + R[n / 2] * cos(pi * k)) / 2 +
     * sum_j = 1 &amp;  (n / 2 - 1) R[j] * cos(2 * pi * j * k / n) +
     * sum_j = 1 &amp;  (n / 2 - 1) I[j] * sin(2 * pi * j * k / n), 0 &lt;= k &lt; n
     * [usage]
     * &lt;case1&gt;
     * ip[0] = 0; // first time only
     * rdft(n, 1, a, ip, w);
     * &lt;case2&gt;
     * ip[0] = 0; // first time only
     * rdft(n, -1, a, ip, w);
     * [remark]
     * Inverse of
     * rdft(n, 1, a, ip, w);
     * is
     * rdft(n, -1, a, ip, w);
     * for (j = 0; j &lt;= n - 1; j++) {
     * a[j] *= 2.0 / n;
     * }
     * .
    </pre> *
     * @param n data length <br></br>
     * n &gt;= 2, n = power of 2
     * @param isgn
     * @param a [0...n-1] input/output data
     * <pre>
     * &lt;case1&gt;
     * output data
     * a[2 * k] = R[k], 0 &lt;= k &lt; n / 2
     * a[2 * k + 1] = I[k], 0 &lt; k &lt; n / 2
     * a[1] = R[n/2]
     * &lt;case2&gt;
     * input data
     * a[2 * j] = R[j], 0 &lt;= j &lt; n / 2
     * a[2 * j + 1] = I[j], 0 &lt; j &lt; n / 2
     * a[1] = R[n / 2]
    </pre> *
     * @param ip [0...*] work area for bit reversal
     * <pre>
     * length of ip &gt;= 2 + sqrt(n / 2)
     * strictly,
     * length of ip &gt;=
     * 2 + (1 &lt;&lt; (int) (log(n / 2 + 0.5) / log(2)) / 2).
    </pre> *
     * ip[0],ip[1] are pointers of the cos/sin table.
     * @param w [0...n/2-1] cos/sin table <br></br>
     * w[],ip[] are initialized if ip[0] == 0.
     */
    fun rdft(n: Int, isgn: Int, a: DoubleArray, ip: IntArray, w: DoubleArray) {
        var nw: Int
        var nc: Int
        val xi: Double

        nw = ip[0]
        if (n > (nw shl 2)) {
            nw = n shr 2
            makewt(nw, ip, w)
        }
        nc = ip[1]
        if (n > (nc shl 2)) {
            nc = n shr 2
            makect(nc, ip, w, nw)
        }
        if (isgn >= 0) {
            if (n > 4) {
                cftfsub(n, a, ip, 2, nw, w)
                rftfsub(n, a, nc, w, nw)
            } else if (n == 4) {
                cftfsub(n, a, ip, 2, nw, w)
            }
            xi = a[0] - a[1]
            a[0] += a[1]
            a[1] = xi
        } else {
            a[1] = 0.5 * (a[0] - a[1])
            a[0] -= a[1]
            if (n > 4) {
                rftbsub(n, a, nc, w, nw)
                cftbsub(n, a, ip, 2, nw, w)
            } else if (n == 4) {
                cftbsub(n, a, ip, 2, nw, w)
            }
        }
    }

    /**
     * Discrete Cosine Transform.
     * <pre>
     * [definition]
     * &lt;case1&gt; IDCT (excluding scale)
     * C[k] = sum_j=0&amp;circ;n-1 a[j]*cos(pi*j*(k+1/2)/n), 0&lt;=k&lt;n
     * &lt;case2&gt; DCT
     * C[k] = sum_j=0&amp;circ;n-1 a[j]*cos(pi*(j+1/2)*k/n), 0&lt;=k&lt;n
     * [usage]
     * &lt;case1&gt;
     * ip[0] = 0; // first time only
     * ddct(n, 1, a, ip, w);
     * &lt;case2&gt;
     * ip[0] = 0; // first time only
     * ddct(n, -1, a, ip, w);
     * [remark]
     * Inverse of
     * ddct(n, -1, a, ip, w);
     * is
     * a[0] *= 0.5;
     * ddct(n, 1, a, ip, w);
     * for (j = 0; j &lt;= n - 1; j++) {
     * a[j] *= 2.0 / n;
     * }
     * .
    </pre> *
     * @param n data length (int)
     * <pre>
     * n &gt;= 2, n = power of 2
    </pre> *
     * @param isgn
     * @param a [0...n-1] input/output data (REAL *)
     * <pre>
     * output data
     * a[k] = C[k], 0&lt;=k&lt;n
    </pre> *
     * @param ip [0...*] work area for bit reversal (int *)
     * <pre>
     * length of ip &gt;= 2+sqrt(n/2)
     * strictly,
     * length of ip &gt;=
     * 2+(1&lt;&lt;(int)(log(n/2+0.5)/log(2))/2).
     * ip[0],ip[1] are pointers of the cos/sin table.
    </pre> *
     * @param w [0...n*5/4-1] cos/sin table (REAL *)
     * <pre>
     * w[],ip[] are initialized if ip[0] == 0.
    </pre> *
     */
    fun ddct(n: Int, isgn: Int, a: DoubleArray, ip: IntArray, w: DoubleArray) {
        var j: Int
        var nw: Int
        var nc: Int
        var xr: Double

        nw = ip[0]
        if (n > (nw shl 2)) {
            nw = n shr 2
            makewt(nw, ip, w)
        }
        nc = ip[1]
        if (n > nc) {
            nc = n
            makect(nc, ip, w, nw)
        }
        if (isgn < 0) {
            xr = a[n - 1]
            j = n - 2
            while (j >= 2) {
                a[j + 1] = a[j] - a[j - 1]
                a[j] += a[j - 1]
                j -= 2
            }
            a[1] = a[0] - xr
            a[0] += xr
            if (n > 4) {
                rftbsub(n, a, nc, w, nw)
                cftbsub(n, a, ip, 2, nw, w)
            } else if (n == 4) {
                cftbsub(n, a, ip, 2, nw, w)
            }
        }
        dctsub(n, a, nc, w, nw)
        if (isgn >= 0) {
            if (n > 4) {
                cftfsub(n, a, ip, 2, nw, w)
                rftfsub(n, a, nc, w, nw)
            } else if (n == 4) {
                cftfsub(n, a, ip, 2, nw, w)
            }
            xr = a[0] - a[1]
            a[0] += a[1]
            j = 2
            while (j < n) {
                a[j - 1] = a[j] - a[j + 1]
                a[j] += a[j + 1]
                j += 2
            }
            a[n - 1] = xr
        }
    }

    /**
     * Discrete Sine Transform.
     * <pre>
     * [definition]
     * &lt;case1&gt; IDST (excluding scale)
     * S[k] = sum_j=1n A[j]*sin(pi*j*(k+1/2)/n), 0&lt;=k&lt;n
     * &lt;case2&gt; DST
     * S[k] = sum_j=0n-1 a[j]*sin(pi*(j+1/2)*k/n), 0&lt;k&lt;=n
     * [usage]
     * &lt;case1&gt;
     * ip[0] = 0; // first time only
     * ddst(n, 1, a, ip, w);
     * &lt;case2&gt;
     * ip[0] = 0; // first time only
     * ddst(n, -1, a, ip, w);
     * [remark]
     * Inverse of
     * ddst(n, -1, a, ip, w);
     * is
     * a[0] *= 0.5;
     * ddst(n, 1, a, ip, w);
     * for (j = 0; j &lt;= n - 1; j++) {
     * a[j] *= 2.0 / n;
     * }
     * .
    </pre> *
     * @param n data length (int)
     * n &gt;= 2, n = power of 2
     * @param isgn
     * @param a [0...n-1] input/output data (REAL *)
     * &lt;case1&gt;
     * input data
     * a[j] = A[j], 0&lt;j&lt;n
     * a[0] = A[n]
     * output data
     * a[k] = S[k], 0&lt;=k&lt;n
     * &lt;case2&gt;
     * output data
     * a[k] = S[k], 0&lt;k&lt;n
     * a[0] = S[n]
     * @param ip [0...*] work area for bit reversal (int *)
     * length of ip &gt;= 2+sqrt(n/2)
     * strictly,
     * length of ip &gt;=
     * 2+(1&lt;&lt;(int)(log(n/2+0.5)/log(2))/2).
     * ip[0],ip[1] are pointers of the cos/sin table.
     * @param w [0...n*5/4-1] cos/sin table (REAL *)
     * w[],ip[] are initialized if ip[0] == 0.
     */
    fun ddst(n: Int, isgn: Int, a: DoubleArray, ip: IntArray, w: DoubleArray) {
        var j: Int
        var nw: Int
        var nc: Int
        var xr: Double

        nw = ip[0]
        if (n > (nw shl 2)) {
            nw = n shr 2
            makewt(nw, ip, w)
        }
        nc = ip[1]
        if (n > nc) {
            nc = n
            makect(nc, ip, w, nw)
        }
        if (isgn < 0) {
            xr = a[n - 1]
            j = n - 2
            while (j >= 2) {
                a[j + 1] = -a[j] - a[j - 1]
                a[j] -= a[j - 1]
                j -= 2
            }
            a[1] = a[0] + xr
            a[0] -= xr
            if (n > 4) {
                rftbsub(n, a, nc, w, nw)
                cftbsub(n, a, ip, 2, nw, w)
            } else if (n == 4) {
                cftbsub(n, a, ip, 2, nw, w)
            }
        }
        dstsub(n, a, nc, w, nw)
        if (isgn >= 0) {
            if (n > 4) {
                cftfsub(n, a, ip, 2, nw, w)
                rftfsub(n, a, nc, w, nw)
            } else if (n == 4) {
                cftfsub(n, a, ip, 2, nw, w)
            }
            xr = a[0] - a[1]
            a[0] += a[1]
            j = 2
            while (j < n) {
                a[j - 1] = -a[j] - a[j + 1]
                a[j] -= a[j + 1]
                j += 2
            }
            a[n - 1] = -xr
        }
    }

    /**
     * Cosine Transform of RDFT (Real Symmetric DFT).
     * <pre>
     * [definition]
     * C[k] = sum_j=0n a[j]*cos(pi*j*k/n), 0&lt;=k&lt;=n
     * [usage]
     * ip[0] = 0; // first time only
     * dfct(n, a, t, ip, w);
     * [parameters]
     * [remark]
     * Inverse of
     * a[0] *= 0.5;
     * a[n] *= 0.5;
     * dfct(n, a, t, ip, w);
     * is
     * a[0] *= 0.5;
     * a[n] *= 0.5;
     * dfct(n, a, t, ip, w);
     * for (j = 0; j &lt;= n; j++) {
     * a[j] *= 2.0 / n;
     * }
     * .
    </pre> *
     * @param n data length - 1 (int)
     * <pre>
     * n &gt;= 2, n = power of 2
    </pre> *
     * @param a [0...n] input/output data (REAL *)
     * <pre>
     * output data
     * a[k] = C[k], 0&lt;=k&lt;=n
    </pre> *
     * @param t [0...n/2] work area (REAL *)
     * @param ip [0...*] work area for bit reversal (int *)
     * <pre>
     * length of ip &gt;= 2+sqrt(n/4)
     * strictly,
     * length of ip &gt;=
     * 2+(1&lt;&lt;(int)(log(n/4+0.5)/log(2))/2).
     * ip[0],ip[1] are pointers of the cos/sin table.
    </pre> *
     * @param w [0...n*5/8-1] cos/sin table (REAL *)
     * <pre>
     * w[],ip[] are initialized if ip[0] == 0.
    </pre> *
     */
    fun dfct(n: Int, a: DoubleArray, t: DoubleArray, ip: IntArray, w: DoubleArray) {
        var j: Int
        var k: Int
        var l: Int
        var m: Int
        var mh: Int
        var nw: Int
        var nc: Int
        var xr: Double
        var xi: Double
        var yr: Double
        var yi: Double

        nw = ip[0]
        if (n > (nw shl 3)) {
            nw = n shr 3
            makewt(nw, ip, w)
        }
        nc = ip[1]
        if (n > (nc shl 1)) {
            nc = n shr 1
            makect(nc, ip, w, nw)
        }
        m = n shr 1
        yi = a[m]
        xi = a[0] + a[n]
        a[0] -= a[n]
        t[0] = xi - yi
        t[m] = xi + yi
        if (n > 2) {
            mh = m shr 1
            j = 1
            while (j < mh) {
                k = m - j
                xr = a[j] - a[n - j]
                xi = a[j] + a[n - j]
                yr = a[k] - a[n - k]
                yi = a[k] + a[n - k]
                a[j] = xr
                a[k] = yr
                t[j] = xi - yi
                t[k] = xi + yi
                j++
            }
            t[mh] = a[mh] + a[n - mh]
            a[mh] -= a[n - mh]
            dctsub(m, a, nc, w, nw)
            if (m > 4) {
                cftfsub(m, a, ip, 2, nw, w)
                rftfsub(m, a, nc, w, nw)
            } else if (m == 4) {
                cftfsub(m, a, ip, 2, nw, w)
            }
            a[n - 1] = a[0] - a[1]
            a[1] = a[0] + a[1]
            j = m - 2
            while (j >= 2) {
                a[2 * j + 1] = a[j] + a[j + 1]
                a[2 * j - 1] = a[j] - a[j + 1]
                j -= 2
            }
            l = 2
            m = mh
            while (m >= 2) {
                dctsub(m, t, nc, w, nw)
                if (m > 4) {
                    cftfsub(m, t, ip, 2, nw, w)
                    rftfsub(m, t, nc, w, nw)
                } else if (m == 4) {
                    cftfsub(m, t, ip, 2, nw, w)
                }
                a[n - l] = t[0] - t[1]
                a[l] = t[0] + t[1]
                k = 0
                j = 2
                while (j < m) {
                    k += l shl 2
                    a[k - l] = t[j] - t[j + 1]
                    a[k + l] = t[j] + t[j + 1]
                    j += 2
                }
                l = l shl 1
                mh = m shr 1
                j = 0
                while (j < mh) {
                    k = m - j
                    t[j] = t[m + k] - t[m + j]
                    t[k] = t[m + k] + t[m + j]
                    j++
                }
                t[mh] = t[m + mh]
                m = mh
            }
            a[l] = t[0]
            a[n] = t[2] - t[1]
            a[0] = t[2] + t[1]
        } else {
            a[1] = a[0]
            a[2] = t[0]
            a[0] = t[1]
        }
    }

    /**
     * Sine Transform of RDFT (Real Anti-symmetric DFT).
     * <pre>
     * [definition]
     * S[k] = sum_j=1&amp;circ;n-1 a[j]*sin(pi*j*k/n), 0&lt;k&lt;n
     * [usage]
     * ip[0] = 0; // first time only
     * dfst(n, a, t, ip, w);
     * [remark]
     * Inverse of
     * dfst(n, a, t, ip, w);
     * is
     * dfst(n, a, t, ip, w);
     * for (j = 1; j &lt;= n - 1; j++) {
     * a[j] *= 2.0 / n;
     * }
     * .
    </pre> *
     * @param n data length + 1 (int)
     * <pre>
     * n &gt;= 2, n = power of 2
    </pre> *
     * @param a [0...n-1] input/output data (REAL *)
     * <pre>
     * output data
     * a[k] = S[k], 0&lt;k&lt;n
     * (a[0] is used for work area)
    </pre> *
     * @param t [0...n/2-1] work area (REAL *)
     * @param ip [0...*] work area for bit reversal (int *)
     * <pre>
     * length of ip &gt;= 2+sqrt(n/4)
     * strictly,
     * length of ip &gt;=
     * 2+(1&lt;&lt;(int)(log(n/4+0.5)/log(2))/2).
     * ip[0],ip[1] are pointers of the cos/sin table.
    </pre> *
     * @param w [0...n*5/8-1] cos/sin table (REAL *)
     * <pre>
     * w[],ip[] are initialized if ip[0] == 0.
    </pre> *
     */
    fun dfst(n: Int, a: DoubleArray, t: DoubleArray, ip: IntArray, w: DoubleArray) {
        var j: Int
        var k: Int
        var l: Int
        var m: Int
        var mh: Int
        var nw: Int
        var nc: Int
        var xr: Double
        var xi: Double
        var yr: Double
        var yi: Double

        nw = ip[0]
        if (n > (nw shl 3)) {
            nw = n shr 3
            makewt(nw, ip, w)
        }
        nc = ip[1]
        if (n > (nc shl 1)) {
            nc = n shr 1
            makect(nc, ip, w, nw)
        }
        if (n > 2) {
            m = n shr 1
            mh = m shr 1
            j = 1
            while (j < mh) {
                k = m - j
                xr = a[j] + a[n - j]
                xi = a[j] - a[n - j]
                yr = a[k] + a[n - k]
                yi = a[k] - a[n - k]
                a[j] = xr
                a[k] = yr
                t[j] = xi + yi
                t[k] = xi - yi
                j++
            }
            t[0] = a[mh] - a[n - mh]
            a[mh] += a[n - mh]
            a[0] = a[m]
            dstsub(m, a, nc, w, nw)
            if (m > 4) {
                cftfsub(m, a, ip, 2, nw, w)
                rftfsub(m, a, nc, w, nw)
            } else if (m == 4) {
                cftfsub(m, a, ip, 2, nw, w)
            }
            a[n - 1] = a[1] - a[0]
            a[1] = a[0] + a[1]
            j = m - 2
            while (j >= 2) {
                a[2 * j + 1] = a[j] - a[j + 1]
                a[2 * j - 1] = -a[j] - a[j + 1]
                j -= 2
            }
            l = 2
            m = mh
            while (m >= 2) {
                dstsub(m, t, nc, w, nw)
                if (m > 4) {
                    cftfsub(m, t, ip, 2, nw, w)
                    rftfsub(m, t, nc, w, nw)
                } else if (m == 4) {
                    cftfsub(m, t, ip, 2, nw, w)
                }
                a[n - l] = t[1] - t[0]
                a[l] = t[0] + t[1]
                k = 0
                j = 2
                while (j < m) {
                    k += l shl 2
                    a[k - l] = -t[j] - t[j + 1]
                    a[k + l] = t[j] - t[j + 1]
                    j += 2
                }
                l = l shl 1
                mh = m shr 1
                j = 1
                while (j < mh) {
                    k = m - j
                    t[j] = t[m + k] + t[m + j]
                    t[k] = t[m + k] - t[m + j]
                    j++
                }
                t[0] = t[m + mh]
                m = mh
            }
            a[l] = t[0]
        }
        a[0] = 0.0
    }

    // -------- initializing routines --------
    /**  */
    private fun makewt(nw: Int, ip: IntArray, w: DoubleArray) {
        var j: Int
        var nwh: Int
        var nw0: Int
        var nw1: Int
        val delta: Double
        val wn4r: Double
        var wk1r: Double
        var wk1i: Double
        var wk3r: Double
        var wk3i: Double

        ip[0] = nw
        ip[1] = 1
        if (nw > 2) {
            nwh = nw shr 1
            //          delta = Math.atan(1.0) / nwh;
            delta = PI / 4 / nwh
            wn4r = cos(delta * nwh)
            w[0] = 1.0
            w[1] = wn4r
            if (nwh >= 4) {
                w[2] = 0.5 / cos(delta * 2)
                w[3] = 0.5 / cos(delta * 6)
            }
            j = 4
            while (j < nwh) {
                w[j] = cos(delta * j)
                w[j + 1] = sin(delta * j)
                w[j + 2] = cos(3 * delta * j)
                w[j + 3] = sin(3 * delta * j)
                j += 4
            }
            nw0 = 0
            while (nwh > 2) {
                nw1 = nw0 + nwh
                nwh = nwh shr 1
                w[nw1] = 1.0
                w[nw1 + 1] = wn4r
                if (nwh >= 4) {
                    wk1r = w[nw0 + 4]
                    wk3r = w[nw0 + 6]
                    w[nw1 + 2] = 0.5 / wk1r
                    w[nw1 + 3] = 0.5 / wk3r
                }
                j = 4
                while (j < nwh) {
                    wk1r = w[nw0 + 2 * j]
                    wk1i = w[nw0 + 2 * j + 1]
                    wk3r = w[nw0 + 2 * j + 2]
                    wk3i = w[nw0 + 2 * j + 3]
                    w[nw1 + j] = wk1r
                    w[nw1 + j + 1] = wk1i
                    w[nw1 + j + 2] = wk3r
                    w[nw1 + j + 3] = wk3i
                    j += 4
                }
                nw0 = nw1
            }
        }
    }

    /**  */
    private fun makect(nc: Int, ip: IntArray, c: DoubleArray, cP: Int) {
        var j: Int
        val nch: Int
        val delta: Double

        ip[1] = nc
        if (nc > 1) {
            nch = nc shr 1
            //          delta = Math.atan(1.0) / nch;
            delta = PI / 4 / nch
            c[cP + 0] = cos(delta * nch)
            c[cP + nch] = 0.5 * c[cP + 0]
            j = 1
            while (j < nch) {
                c[cP + j] = 0.5 * cos(delta * j)
                c[cP + nc - j] = 0.5 * sin(delta * j)
                j++
            }
        }
    }

    // -------- child routines --------
    /**
     * 2nd
     * @see .rdft
     * @see .ddct
     * @see .cdft
     * @see .ddst
     * @see .dfst
     * @see .dfct
     */
    private fun cftfsub(n: Int, a: DoubleArray, ip: IntArray, ipP: Int, nw: Int, w: DoubleArray) {
        val m: Int

        if (n > 32) {
            m = n shr 2
            cftf1st(n, a, w, nw - m)
            if (n > CDFT_RECURSIVE_N) {
                cftrec1(m, a, 0, nw, w)
                cftrec2(m, a, m, nw, w)
                cftrec1(m, a, 2 * m, nw, w)
                cftrec1(m, a, 3 * m, nw, w)
            } else if (m > 32) {
                cftexp1(n, a, 0, nw, w)
            } else {
                cftfx41(n, a, 0, nw, w)
            }
            bitrv2(n, ip, ipP, a)
        } else if (n > 8) {
            if (n == 32) {
                cftf161(a, 0, w, nw - 8)
                bitrv216(a)
            } else {
                cftf081(a, 0, w, 0)
                bitrv208(a)
            }
        } else if (n == 8) {
            cftf040(a)
        } else if (n == 4) {
            cftx020(a)
        }
    }

    /**
     * 2nd
     * @see .rdft
     * @see .ddct
     * @see .cdft
     * @see .ddst
     */
    private fun cftbsub(n: Int, a: DoubleArray, ip: IntArray, ipP: Int, nw: Int, w: DoubleArray) {
        val m: Int

        if (n > 32) {
            m = n shr 2
            cftb1st(n, a, w, nw - m)
            if (n > CDFT_RECURSIVE_N) {
                cftrec1(m, a, 0, nw, w)
                cftrec2(m, a, m, nw, w)
                cftrec1(m, a, 2 * m, nw, w)
                cftrec1(m, a, 3 * m, nw, w)
            } else if (m > 32) {
                cftexp1(n, a, 0, nw, w)
            } else {
                cftfx41(n, a, 0, nw, w)
            }
            bitrv2conj(n, ip, ipP, a)
        } else if (n > 8) {
            if (n == 32) {
                cftf161(a, 0, w, nw - 8)
                bitrv216neg(a)
            } else {
                cftf081(a, 0, w, 0)
                bitrv208neg(a)
            }
        } else if (n == 8) {
            cftb040(a)
        } else if (n == 4) {
            cftx020(a)
        }
    }

    /**
     * 3rd
     * @see .cftfsub
     */
    private fun bitrv2(n: Int, ip: IntArray, ipP: Int, a: DoubleArray) {
        var j: Int
        var j1: Int
        var k: Int
        var k1: Int
        var l: Int
        var m: Int
        var xr: Double
        var xi: Double
        var yr: Double
        var yi: Double

        ip[ipP + 0] = 0
        l = n
        m = 1
        while ((m shl 3) < l) {
            l = l shr 1
            j = 0
            while (j < m) {
                ip[ipP + m + j] = ip[ipP + j] + l
                j++
            }
            m = m shl 1
        }
        val m2 = 2 * m
        if ((m shl 3) == l) {
            k = 0
            while (k < m) {
                j = 0
                while (j < k) {
                    j1 = 2 * j + ip[ipP + k]
                    k1 = 2 * k + ip[ipP + j]
                    xr = a[j1]
                    xi = a[j1 + 1]
                    yr = a[k1]
                    yi = a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 += 2 * m2
                    xr = a[j1]
                    xi = a[j1 + 1]
                    yr = a[k1]
                    yi = a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 -= m2
                    xr = a[j1]
                    xi = a[j1 + 1]
                    yr = a[k1]
                    yi = a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 += 2 * m2
                    xr = a[j1]
                    xi = a[j1 + 1]
                    yr = a[k1]
                    yi = a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j++
                }
                j1 = 2 * k + m2 + ip[ipP + k]
                k1 = j1 + m2
                xr = a[j1]
                xi = a[j1 + 1]
                yr = a[k1]
                yi = a[k1 + 1]
                a[j1] = yr
                a[j1 + 1] = yi
                a[k1] = xr
                a[k1 + 1] = xi
                k++
            }
        } else {
            k = 1
            while (k < m) {
                j = 0
                while (j < k) {
                    j1 = 2 * j + ip[ipP + k]
                    k1 = 2 * k + ip[ipP + j]
                    xr = a[j1]
                    xi = a[j1 + 1]
                    yr = a[k1]
                    yi = a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 += m2
                    xr = a[j1]
                    xi = a[j1 + 1]
                    yr = a[k1]
                    yi = a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j++
                }
                k++
            }
        }
    }

    /**
     * 3rd
     * @see .cftbsub
     */
    private fun bitrv2conj(n: Int, ip: IntArray, ipP: Int, a: DoubleArray) {
        var j: Int
        var j1: Int
        var k: Int
        var k1: Int
        var l: Int
        var m: Int
        var xr: Double
        var xi: Double
        var yr: Double
        var yi: Double

        ip[ipP + 0] = 0
        l = n
        m = 1
        while ((m shl 3) < l) {
            l = l shr 1
            j = 0
            while (j < m) {
                ip[ipP + m + j] = ip[ipP + j] + l
                j++
            }
            m = m shl 1
        }
        val m2 = 2 * m
        if ((m shl 3) == l) {
            k = 0
            while (k < m) {
                j = 0
                while (j < k) {
                    j1 = 2 * j + ip[ipP + k]
                    k1 = 2 * k + ip[ipP + j]
                    xr = a[j1]
                    xi = -a[j1 + 1]
                    yr = a[k1]
                    yi = -a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 += 2 * m2
                    xr = a[j1]
                    xi = -a[j1 + 1]
                    yr = a[k1]
                    yi = -a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 -= m2
                    xr = a[j1]
                    xi = -a[j1 + 1]
                    yr = a[k1]
                    yi = -a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 += 2 * m2
                    xr = a[j1]
                    xi = -a[j1 + 1]
                    yr = a[k1]
                    yi = -a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j++
                }
                k1 = 2 * k + ip[ipP + k]
                a[k1 + 1] = -a[k1 + 1]
                j1 = k1 + m2
                k1 = j1 + m2
                xr = a[j1]
                xi = -a[j1 + 1]
                yr = a[k1]
                yi = -a[k1 + 1]
                a[j1] = yr
                a[j1 + 1] = yi
                a[k1] = xr
                a[k1 + 1] = xi
                k1 += m2
                a[k1 + 1] = -a[k1 + 1]
                k++
            }
        } else {
            a[1] = -a[1]
            a[m2 + 1] = -a[m2 + 1]
            k = 1
            while (k < m) {
                j = 0
                while (j < k) {
                    j1 = 2 * j + ip[ipP + k]
                    k1 = 2 * k + ip[ipP + j]
                    xr = a[j1]
                    xi = -a[j1 + 1]
                    yr = a[k1]
                    yi = -a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j1 += m2
                    k1 += m2
                    xr = a[j1]
                    xi = -a[j1 + 1]
                    yr = a[k1]
                    yi = -a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                    j++
                }
                k1 = 2 * k + ip[ipP + k]
                a[k1 + 1] = -a[k1 + 1]
                a[k1 + m2 + 1] = -a[k1 + m2 + 1]
                k++
            }
        }
    }

    /**
     * 3rd
     * @see .cftfsub
     */
    private fun bitrv216(a: DoubleArray) {
        val x1r = a[2]
        val x1i = a[3]
        val x2r = a[4]
        val x2i = a[5]
        val x3r = a[6]
        val x3i = a[7]
        val x4r = a[8]
        val x4i = a[9]
        val x5r = a[10]
        val x5i = a[11]
        val x7r = a[14]
        val x7i = a[15]
        val x8r = a[16]
        val x8i = a[17]
        val x10r = a[20]
        val x10i = a[21]
        val x11r = a[22]
        val x11i = a[23]
        val x12r = a[24]
        val x12i = a[25]
        val x13r = a[26]
        val x13i = a[27]
        val x14r = a[28]
        val x14i = a[29]
        a[2] = x8r
        a[3] = x8i
        a[4] = x4r
        a[5] = x4i
        a[6] = x12r
        a[7] = x12i
        a[8] = x2r
        a[9] = x2i
        a[10] = x10r
        a[11] = x10i
        a[14] = x14r
        a[15] = x14i
        a[16] = x1r
        a[17] = x1i
        a[20] = x5r
        a[21] = x5i
        a[22] = x13r
        a[23] = x13i
        a[24] = x3r
        a[25] = x3i
        a[26] = x11r
        a[27] = x11i
        a[28] = x7r
        a[29] = x7i
    }

    /**
     * 3rd
     * @see .cftbsub
     */
    private fun bitrv216neg(a: DoubleArray) {
        val x1r = a[2]
        val x1i = a[3]
        val x2r = a[4]
        val x2i = a[5]
        val x3r = a[6]
        val x3i = a[7]
        val x4r = a[8]
        val x4i = a[9]
        val x5r = a[10]
        val x5i = a[11]
        val x6r = a[12]
        val x6i = a[13]
        val x7r = a[14]
        val x7i = a[15]
        val x8r = a[16]
        val x8i = a[17]
        val x9r = a[18]
        val x9i = a[19]
        val x10r = a[20]
        val x10i = a[21]
        val x11r = a[22]
        val x11i = a[23]
        val x12r = a[24]
        val x12i = a[25]
        val x13r = a[26]
        val x13i = a[27]
        val x14r = a[28]
        val x14i = a[29]
        val x15r = a[30]
        val x15i = a[31]
        a[2] = x15r
        a[3] = x15i
        a[4] = x7r
        a[5] = x7i
        a[6] = x11r
        a[7] = x11i
        a[8] = x3r
        a[9] = x3i
        a[10] = x13r
        a[11] = x13i
        a[12] = x5r
        a[13] = x5i
        a[14] = x9r
        a[15] = x9i
        a[16] = x1r
        a[17] = x1i
        a[18] = x14r
        a[19] = x14i
        a[20] = x6r
        a[21] = x6i
        a[22] = x10r
        a[23] = x10i
        a[24] = x2r
        a[25] = x2i
        a[26] = x12r
        a[27] = x12i
        a[28] = x4r
        a[29] = x4i
        a[30] = x8r
        a[31] = x8i
    }

    /**
     * 3rd
     * @see .cftfsub
     */
    private fun bitrv208(a: DoubleArray) {
        val x1r = a[2]
        val x1i = a[3]
        val x3r = a[6]
        val x3i = a[7]
        val x4r = a[8]
        val x4i = a[9]
        val x6r = a[12]
        val x6i = a[13]
        a[2] = x4r
        a[3] = x4i
        a[6] = x6r
        a[7] = x6i
        a[8] = x1r
        a[9] = x1i
        a[12] = x3r
        a[13] = x3i
    }

    /**
     * 3rd
     * @see .cftbsub
     */
    private fun bitrv208neg(a: DoubleArray) {
        val x1r = a[2]
        val x1i = a[3]
        val x2r = a[4]
        val x2i = a[5]
        val x3r = a[6]
        val x3i = a[7]
        val x4r = a[8]
        val x4i = a[9]
        val x5r = a[10]
        val x5i = a[11]
        val x6r = a[12]
        val x6i = a[13]
        val x7r = a[14]
        val x7i = a[15]
        a[2] = x7r
        a[3] = x7i
        a[4] = x3r
        a[5] = x3i
        a[6] = x5r
        a[7] = x5i
        a[8] = x1r
        a[9] = x1i
        a[10] = x6r
        a[11] = x6i
        a[12] = x2r
        a[13] = x2i
        a[14] = x4r
        a[15] = x4i
    }

    /**
     * 3rd
     * @see .cftfsub
     */
    private fun cftf1st(n: Int, a: DoubleArray, w: DoubleArray, wP: Int) {
        var j0: Int
        var j1: Int
        var j2: Int
        var j3: Int
        val m: Int
        var wk1r: Double
        var wk1i: Double
        var wk3r: Double
        var wk3i: Double
        var wd1r: Double
        var wd1i: Double
        var wd3r: Double
        var wd3i: Double
        var x0r: Double
        var x0i: Double
        var x1r: Double
        var x1i: Double
        var x2r: Double
        var x2i: Double
        var x3r: Double
        var x3i: Double
        var y0r: Double
        var y0i: Double
        var y1r: Double
        var y1i: Double
        var y2r: Double
        var y2i: Double
        var y3r: Double
        var y3i: Double

        val mh = n shr 3
        m = 2 * mh
        j1 = m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[0] + a[j2]
        x0i = a[1] + a[j2 + 1]
        x1r = a[0] - a[j2]
        x1i = a[1] - a[j2 + 1]
        x2r = a[j1] + a[j3]
        x2i = a[j1 + 1] + a[j3 + 1]
        x3r = a[j1] - a[j3]
        x3i = a[j1 + 1] - a[j3 + 1]
        a[0] = x0r + x2r
        a[1] = x0i + x2i
        a[j1] = x0r - x2r
        a[j1 + 1] = x0i - x2i
        a[j2] = x1r - x3i
        a[j2 + 1] = x1i + x3r
        a[j3] = x1r + x3i
        a[j3 + 1] = x1i - x3r
        val wn4r = w[wP + 1]
        val csc1 = w[wP + 2]
        val csc3 = w[wP + 3]
        wd1r = 1.0
        wd1i = 0.0
        wd3r = 1.0
        wd3i = 0.0
        var k = 0
        var j = 2
        while (j < mh - 2) {
            k += 4
            wk1r = csc1 * (wd1r + w[wP + k])
            wk1i = csc1 * (wd1i + w[wP + k + 1])
            wk3r = csc3 * (wd3r + w[wP + k + 2])
            wk3i = csc3 * (wd3i - w[wP + k + 3])
            wd1r = w[wP + k]
            wd1i = w[wP + k + 1]
            wd3r = w[wP + k + 2]
            wd3i = -w[wP + k + 3]
            j1 = j + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[j] + a[j2]
            x0i = a[j + 1] + a[j2 + 1]
            x1r = a[j] - a[j2]
            x1i = a[j + 1] - a[j2 + 1]
            y0r = a[j + 2] + a[j2 + 2]
            y0i = a[j + 3] + a[j2 + 3]
            y1r = a[j + 2] - a[j2 + 2]
            y1i = a[j + 3] - a[j2 + 3]
            x2r = a[j1] + a[j3]
            x2i = a[j1 + 1] + a[j3 + 1]
            x3r = a[j1] - a[j3]
            x3i = a[j1 + 1] - a[j3 + 1]
            y2r = a[j1 + 2] + a[j3 + 2]
            y2i = a[j1 + 3] + a[j3 + 3]
            y3r = a[j1 + 2] - a[j3 + 2]
            y3i = a[j1 + 3] - a[j3 + 3]
            a[j] = x0r + x2r
            a[j + 1] = x0i + x2i
            a[j + 2] = y0r + y2r
            a[j + 3] = y0i + y2i
            a[j1] = x0r - x2r
            a[j1 + 1] = x0i - x2i
            a[j1 + 2] = y0r - y2r
            a[j1 + 3] = y0i - y2i
            x0r = x1r - x3i
            x0i = x1i + x3r
            a[j2] = wk1r * x0r - wk1i * x0i
            a[j2 + 1] = wk1r * x0i + wk1i * x0r
            x0r = y1r - y3i
            x0i = y1i + y3r
            a[j2 + 2] = wd1r * x0r - wd1i * x0i
            a[j2 + 3] = wd1r * x0i + wd1i * x0r
            x0r = x1r + x3i
            x0i = x1i - x3r
            a[j3] = wk3r * x0r + wk3i * x0i
            a[j3 + 1] = wk3r * x0i - wk3i * x0r
            x0r = y1r + y3i
            x0i = y1i - y3r
            a[j3 + 2] = wd3r * x0r + wd3i * x0i
            a[j3 + 3] = wd3r * x0i - wd3i * x0r
            j0 = m - j
            j1 = j0 + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[j0] + a[j2]
            x0i = a[j0 + 1] + a[j2 + 1]
            x1r = a[j0] - a[j2]
            x1i = a[j0 + 1] - a[j2 + 1]
            y0r = a[j0 - 2] + a[j2 - 2]
            y0i = a[j0 - 1] + a[j2 - 1]
            y1r = a[j0 - 2] - a[j2 - 2]
            y1i = a[j0 - 1] - a[j2 - 1]
            x2r = a[j1] + a[j3]
            x2i = a[j1 + 1] + a[j3 + 1]
            x3r = a[j1] - a[j3]
            x3i = a[j1 + 1] - a[j3 + 1]
            y2r = a[j1 - 2] + a[j3 - 2]
            y2i = a[j1 - 1] + a[j3 - 1]
            y3r = a[j1 - 2] - a[j3 - 2]
            y3i = a[j1 - 1] - a[j3 - 1]
            a[j0] = x0r + x2r
            a[j0 + 1] = x0i + x2i
            a[j0 - 2] = y0r + y2r
            a[j0 - 1] = y0i + y2i
            a[j1] = x0r - x2r
            a[j1 + 1] = x0i - x2i
            a[j1 - 2] = y0r - y2r
            a[j1 - 1] = y0i - y2i
            x0r = x1r - x3i
            x0i = x1i + x3r
            a[j2] = wk1i * x0r - wk1r * x0i
            a[j2 + 1] = wk1i * x0i + wk1r * x0r
            x0r = y1r - y3i
            x0i = y1i + y3r
            a[j2 - 2] = wd1i * x0r - wd1r * x0i
            a[j2 - 1] = wd1i * x0i + wd1r * x0r
            x0r = x1r + x3i
            x0i = x1i - x3r
            a[j3] = wk3i * x0r + wk3r * x0i
            a[j3 + 1] = wk3i * x0i - wk3r * x0r
            x0r = y1r + y3i
            x0i = y1i - y3r
            a[j3 - 2] = wd3i * x0r + wd3r * x0i
            a[j3 - 1] = wd3i * x0i - wd3r * x0r
            j += 4
        }
        wk1r = csc1 * (wd1r + wn4r)
        wk1i = csc1 * (wd1i + wn4r)
        wk3r = csc3 * (wd3r - wn4r)
        wk3i = csc3 * (wd3i - wn4r)
        j0 = mh
        j1 = j0 + m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[j0 - 2] + a[j2 - 2]
        x0i = a[j0 - 1] + a[j2 - 1]
        x1r = a[j0 - 2] - a[j2 - 2]
        x1i = a[j0 - 1] - a[j2 - 1]
        x2r = a[j1 - 2] + a[j3 - 2]
        x2i = a[j1 - 1] + a[j3 - 1]
        x3r = a[j1 - 2] - a[j3 - 2]
        x3i = a[j1 - 1] - a[j3 - 1]
        a[j0 - 2] = x0r + x2r
        a[j0 - 1] = x0i + x2i
        a[j1 - 2] = x0r - x2r
        a[j1 - 1] = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        a[j2 - 2] = wk1r * x0r - wk1i * x0i
        a[j2 - 1] = wk1r * x0i + wk1i * x0r
        x0r = x1r + x3i
        x0i = x1i - x3r
        a[j3 - 2] = wk3r * x0r + wk3i * x0i
        a[j3 - 1] = wk3r * x0i - wk3i * x0r
        x0r = a[j0] + a[j2]
        x0i = a[j0 + 1] + a[j2 + 1]
        x1r = a[j0] - a[j2]
        x1i = a[j0 + 1] - a[j2 + 1]
        x2r = a[j1] + a[j3]
        x2i = a[j1 + 1] + a[j3 + 1]
        x3r = a[j1] - a[j3]
        x3i = a[j1 + 1] - a[j3 + 1]
        a[j0] = x0r + x2r
        a[j0 + 1] = x0i + x2i
        a[j1] = x0r - x2r
        a[j1 + 1] = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        a[j2] = wn4r * (x0r - x0i)
        a[j2 + 1] = wn4r * (x0i + x0r)
        x0r = x1r + x3i
        x0i = x1i - x3r
        a[j3] = -wn4r * (x0r + x0i)
        a[j3 + 1] = -wn4r * (x0i - x0r)
        x0r = a[j0 + 2] + a[j2 + 2]
        x0i = a[j0 + 3] + a[j2 + 3]
        x1r = a[j0 + 2] - a[j2 + 2]
        x1i = a[j0 + 3] - a[j2 + 3]
        x2r = a[j1 + 2] + a[j3 + 2]
        x2i = a[j1 + 3] + a[j3 + 3]
        x3r = a[j1 + 2] - a[j3 + 2]
        x3i = a[j1 + 3] - a[j3 + 3]
        a[j0 + 2] = x0r + x2r
        a[j0 + 3] = x0i + x2i
        a[j1 + 2] = x0r - x2r
        a[j1 + 3] = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        a[j2 + 2] = wk1i * x0r - wk1r * x0i
        a[j2 + 3] = wk1i * x0i + wk1r * x0r
        x0r = x1r + x3i
        x0i = x1i - x3r
        a[j3 + 2] = wk3i * x0r + wk3r * x0i
        a[j3 + 3] = wk3i * x0i - wk3r * x0r
    }

    /**
     * 3rd
     * @see .cftbsub
     */
    private fun cftb1st(n: Int, a: DoubleArray, w: DoubleArray, wP: Int) {
        var j0: Int
        var j1: Int
        var j2: Int
        var j3: Int
        val m: Int
        var wk1r: Double
        var wk1i: Double
        var wk3r: Double
        var wk3i: Double
        var wd1r: Double
        var wd1i: Double
        var wd3r: Double
        var wd3i: Double
        var x0r: Double
        var x0i: Double
        var x1r: Double
        var x1i: Double
        var x2r: Double
        var x2i: Double
        var x3r: Double
        var x3i: Double
        var y0r: Double
        var y0i: Double
        var y1r: Double
        var y1i: Double
        var y2r: Double
        var y2i: Double
        var y3r: Double
        var y3i: Double

        val mh = n shr 3
        m = 2 * mh
        j1 = m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[0] + a[j2]
        x0i = -a[1] - a[j2 + 1]
        x1r = a[0] - a[j2]
        x1i = -a[1] + a[j2 + 1]
        x2r = a[j1] + a[j3]
        x2i = a[j1 + 1] + a[j3 + 1]
        x3r = a[j1] - a[j3]
        x3i = a[j1 + 1] - a[j3 + 1]
        a[0] = x0r + x2r
        a[1] = x0i - x2i
        a[j1] = x0r - x2r
        a[j1 + 1] = x0i + x2i
        a[j2] = x1r + x3i
        a[j2 + 1] = x1i + x3r
        a[j3] = x1r - x3i
        a[j3 + 1] = x1i - x3r
        val wn4r = w[wP + 1]
        val csc1 = w[wP + 2]
        val csc3 = w[wP + 3]
        wd1r = 1.0
        wd1i = 0.0
        wd3r = 1.0
        wd3i = 0.0
        var k = 0
        var j = 2
        while (j < mh - 2) {
            k += 4
            wk1r = csc1 * (wd1r + w[wP + k])
            wk1i = csc1 * (wd1i + w[wP + k + 1])
            wk3r = csc3 * (wd3r + w[wP + k + 2])
            wk3i = csc3 * (wd3i - w[wP + k + 3])
            wd1r = w[wP + k]
            wd1i = w[wP + k + 1]
            wd3r = w[wP + k + 2]
            wd3i = -w[wP + k + 3]
            j1 = j + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[j] + a[j2]
            x0i = -a[j + 1] - a[j2 + 1]
            x1r = a[j] - a[j2]
            x1i = -a[j + 1] + a[j2 + 1]
            y0r = a[j + 2] + a[j2 + 2]
            y0i = -a[j + 3] - a[j2 + 3]
            y1r = a[j + 2] - a[j2 + 2]
            y1i = -a[j + 3] + a[j2 + 3]
            x2r = a[j1] + a[j3]
            x2i = a[j1 + 1] + a[j3 + 1]
            x3r = a[j1] - a[j3]
            x3i = a[j1 + 1] - a[j3 + 1]
            y2r = a[j1 + 2] + a[j3 + 2]
            y2i = a[j1 + 3] + a[j3 + 3]
            y3r = a[j1 + 2] - a[j3 + 2]
            y3i = a[j1 + 3] - a[j3 + 3]
            a[j] = x0r + x2r
            a[j + 1] = x0i - x2i
            a[j + 2] = y0r + y2r
            a[j + 3] = y0i - y2i
            a[j1] = x0r - x2r
            a[j1 + 1] = x0i + x2i
            a[j1 + 2] = y0r - y2r
            a[j1 + 3] = y0i + y2i
            x0r = x1r + x3i
            x0i = x1i + x3r
            a[j2] = wk1r * x0r - wk1i * x0i
            a[j2 + 1] = wk1r * x0i + wk1i * x0r
            x0r = y1r + y3i
            x0i = y1i + y3r
            a[j2 + 2] = wd1r * x0r - wd1i * x0i
            a[j2 + 3] = wd1r * x0i + wd1i * x0r
            x0r = x1r - x3i
            x0i = x1i - x3r
            a[j3] = wk3r * x0r + wk3i * x0i
            a[j3 + 1] = wk3r * x0i - wk3i * x0r
            x0r = y1r - y3i
            x0i = y1i - y3r
            a[j3 + 2] = wd3r * x0r + wd3i * x0i
            a[j3 + 3] = wd3r * x0i - wd3i * x0r
            j0 = m - j
            j1 = j0 + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[j0] + a[j2]
            x0i = -a[j0 + 1] - a[j2 + 1]
            x1r = a[j0] - a[j2]
            x1i = -a[j0 + 1] + a[j2 + 1]
            y0r = a[j0 - 2] + a[j2 - 2]
            y0i = -a[j0 - 1] - a[j2 - 1]
            y1r = a[j0 - 2] - a[j2 - 2]
            y1i = -a[j0 - 1] + a[j2 - 1]
            x2r = a[j1] + a[j3]
            x2i = a[j1 + 1] + a[j3 + 1]
            x3r = a[j1] - a[j3]
            x3i = a[j1 + 1] - a[j3 + 1]
            y2r = a[j1 - 2] + a[j3 - 2]
            y2i = a[j1 - 1] + a[j3 - 1]
            y3r = a[j1 - 2] - a[j3 - 2]
            y3i = a[j1 - 1] - a[j3 - 1]
            a[j0] = x0r + x2r
            a[j0 + 1] = x0i - x2i
            a[j0 - 2] = y0r + y2r
            a[j0 - 1] = y0i - y2i
            a[j1] = x0r - x2r
            a[j1 + 1] = x0i + x2i
            a[j1 - 2] = y0r - y2r
            a[j1 - 1] = y0i + y2i
            x0r = x1r + x3i
            x0i = x1i + x3r
            a[j2] = wk1i * x0r - wk1r * x0i
            a[j2 + 1] = wk1i * x0i + wk1r * x0r
            x0r = y1r + y3i
            x0i = y1i + y3r
            a[j2 - 2] = wd1i * x0r - wd1r * x0i
            a[j2 - 1] = wd1i * x0i + wd1r * x0r
            x0r = x1r - x3i
            x0i = x1i - x3r
            a[j3] = wk3i * x0r + wk3r * x0i
            a[j3 + 1] = wk3i * x0i - wk3r * x0r
            x0r = y1r - y3i
            x0i = y1i - y3r
            a[j3 - 2] = wd3i * x0r + wd3r * x0i
            a[j3 - 1] = wd3i * x0i - wd3r * x0r
            j += 4
        }
        wk1r = csc1 * (wd1r + wn4r)
        wk1i = csc1 * (wd1i + wn4r)
        wk3r = csc3 * (wd3r - wn4r)
        wk3i = csc3 * (wd3i - wn4r)
        j0 = mh
        j1 = j0 + m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[j0 - 2] + a[j2 - 2]
        x0i = -a[j0 - 1] - a[j2 - 1]
        x1r = a[j0 - 2] - a[j2 - 2]
        x1i = -a[j0 - 1] + a[j2 - 1]
        x2r = a[j1 - 2] + a[j3 - 2]
        x2i = a[j1 - 1] + a[j3 - 1]
        x3r = a[j1 - 2] - a[j3 - 2]
        x3i = a[j1 - 1] - a[j3 - 1]
        a[j0 - 2] = x0r + x2r
        a[j0 - 1] = x0i - x2i
        a[j1 - 2] = x0r - x2r
        a[j1 - 1] = x0i + x2i
        x0r = x1r + x3i
        x0i = x1i + x3r
        a[j2 - 2] = wk1r * x0r - wk1i * x0i
        a[j2 - 1] = wk1r * x0i + wk1i * x0r
        x0r = x1r - x3i
        x0i = x1i - x3r
        a[j3 - 2] = wk3r * x0r + wk3i * x0i
        a[j3 - 1] = wk3r * x0i - wk3i * x0r
        x0r = a[j0] + a[j2]
        x0i = -a[j0 + 1] - a[j2 + 1]
        x1r = a[j0] - a[j2]
        x1i = -a[j0 + 1] + a[j2 + 1]
        x2r = a[j1] + a[j3]
        x2i = a[j1 + 1] + a[j3 + 1]
        x3r = a[j1] - a[j3]
        x3i = a[j1 + 1] - a[j3 + 1]
        a[j0] = x0r + x2r
        a[j0 + 1] = x0i - x2i
        a[j1] = x0r - x2r
        a[j1 + 1] = x0i + x2i
        x0r = x1r + x3i
        x0i = x1i + x3r
        a[j2] = wn4r * (x0r - x0i)
        a[j2 + 1] = wn4r * (x0i + x0r)
        x0r = x1r - x3i
        x0i = x1i - x3r
        a[j3] = -wn4r * (x0r + x0i)
        a[j3 + 1] = -wn4r * (x0i - x0r)
        x0r = a[j0 + 2] + a[j2 + 2]
        x0i = -a[j0 + 3] - a[j2 + 3]
        x1r = a[j0 + 2] - a[j2 + 2]
        x1i = -a[j0 + 3] + a[j2 + 3]
        x2r = a[j1 + 2] + a[j3 + 2]
        x2i = a[j1 + 3] + a[j3 + 3]
        x3r = a[j1 + 2] - a[j3 + 2]
        x3i = a[j1 + 3] - a[j3 + 3]
        a[j0 + 2] = x0r + x2r
        a[j0 + 3] = x0i - x2i
        a[j1 + 2] = x0r - x2r
        a[j1 + 3] = x0i + x2i
        x0r = x1r + x3i
        x0i = x1i + x3r
        a[j2 + 2] = wk1i * x0r - wk1r * x0i
        a[j2 + 3] = wk1i * x0i + wk1r * x0r
        x0r = x1r - x3i
        x0i = x1i - x3r
        a[j3 + 2] = wk3i * x0r + wk3r * x0i
        a[j3 + 3] = wk3i * x0i - wk3r * x0r
    }

    /**  */
    private fun cftrec1(n: Int, a: DoubleArray, aP: Int, nw: Int, w: DoubleArray) {
        val m = n shr 2
        cftmdl1(n, a, aP, w, nw - 2 * m)
        if (n > CDFT_RECURSIVE_N) {
            cftrec1(m, a, aP, nw, w)
            cftrec2(m, a, aP + m, nw, w)
            cftrec1(m, a, aP + 2 * m, nw, w)
            cftrec1(m, a, aP + 3 * m, nw, w)
        } else {
            cftexp1(n, a, aP, nw, w)
        }
    }

    /**  */
    private fun cftrec2(n: Int, a: DoubleArray, aP: Int, nw: Int, w: DoubleArray) {
        val m = n shr 2
        cftmdl2(n, a, aP, w, nw - n)
        if (n > CDFT_RECURSIVE_N) {
            cftrec1(m, a, aP, nw, w)
            cftrec2(m, a, aP + m, nw, w)
            cftrec1(m, a, aP + 2 * m, nw, w)
            cftrec2(m, a, aP + 3 * m, nw, w)
        } else {
            cftexp2(n, a, aP, nw, w)
        }
    }

    /**  */
    private fun cftexp1(n: Int, a: DoubleArray, aP: Int, nw: Int, w: DoubleArray) {
        var j: Int
        var k: Int
        var l: Int

        l = n shr 2
        while (l > 128) {
            k = l
            while (k < n) {
                j = k - l
                while (j < n) {
                    cftmdl1(l, a, aP + j, w, nw - (l shr 1))
                    cftmdl2(l, a, aP + k + j, w, nw - l)
                    cftmdl1(l, a, aP + 2 * k + j, w, nw - (l shr 1))
                    j += 4 * k
                }
                k = k shl 2
            }
            cftmdl1(l, a, aP + n - l, w, nw - (l shr 1))
            l = l shr 2
        }
        k = l
        while (k < n) {
            j = k - l
            while (j < n) {
                cftmdl1(l, a, aP + j, w, nw - (l shr 1))
                cftfx41(l, a, aP + j, nw, w)
                cftmdl2(l, a, aP + k + j, w, nw - l)
                cftfx42(l, a, aP + k + j, nw, w)
                cftmdl1(l, a, aP + 2 * k + j, w, nw - (l shr 1))
                cftfx41(l, a, aP + 2 * k + j, nw, w)
                j += 4 * k
            }
            k = k shl 2
        }
        cftmdl1(l, a, aP + n - l, w, nw - (l shr 1))
        cftfx41(l, a, aP + n - l, nw, w)
    }

    /**  */
    private fun cftexp2(n: Int, a: DoubleArray, aP: Int, nw: Int, w: DoubleArray) {
        var j: Int
        var k: Int
        var l: Int

        val m = n shr 1
        l = n shr 2
        while (l > 128) {
            k = l
            while (k < m) {
                j = k - l
                while (j < m) {
                    cftmdl1(l, a, aP + j, w, nw - (l shr 1))
                    cftmdl1(l, a, aP + m + j, w, nw - (l shr 1))
                    j += 2 * k
                }
                j = 2 * k - l
                while (j < m) {
                    cftmdl2(l, a, aP + j, w, nw - l)
                    cftmdl2(l, a, aP + m + j, w, nw - l)
                    j += 4 * k
                }
                k = k shl 2
            }
            l = l shr 2
        }
        k = l
        while (k < m) {
            j = k - l
            while (j < m) {
                cftmdl1(l, a, aP + j, w, nw - (l shr 1))
                cftfx41(l, a, aP + j, nw, w)
                cftmdl1(l, a, aP + m + j, w, nw - (l shr 1))
                cftfx41(l, a, aP + m + j, nw, w)
                j += 2 * k
            }
            j = 2 * k - l
            while (j < m) {
                cftmdl2(l, a, aP + j, w, nw - l)
                cftfx42(l, a, aP + j, nw, w)
                cftmdl2(l, a, aP + m + j, w, nw - l)
                cftfx42(l, a, aP + m + j, nw, w)
                j += 4 * k
            }
            k = k shl 2
        }
    }

    /**  */
    private fun cftmdl1(n: Int, a: DoubleArray, aP: Int, w: DoubleArray, wP: Int) {
        var j0: Int
        var j1: Int
        var j2: Int
        var j3: Int
        val m: Int
        var wk1r: Double
        var wk1i: Double
        var wk3r: Double
        var wk3i: Double
        var x0r: Double
        var x0i: Double
        var x1r: Double
        var x1i: Double
        var x2r: Double
        var x2i: Double
        var x3r: Double
        var x3i: Double

        val mh = n shr 3
        m = 2 * mh
        j1 = m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[aP + 0] + a[aP + j2]
        x0i = a[aP + 1] + a[aP + j2 + 1]
        x1r = a[aP + 0] - a[aP + j2]
        x1i = a[aP + 1] - a[aP + j2 + 1]
        x2r = a[aP + j1] + a[aP + j3]
        x2i = a[aP + j1 + 1] + a[aP + j3 + 1]
        x3r = a[aP + j1] - a[aP + j3]
        x3i = a[aP + j1 + 1] - a[aP + j3 + 1]
        a[aP + 0] = x0r + x2r
        a[aP + 1] = x0i + x2i
        a[aP + j1] = x0r - x2r
        a[aP + j1 + 1] = x0i - x2i
        a[aP + j2] = x1r - x3i
        a[aP + j2 + 1] = x1i + x3r
        a[aP + j3] = x1r + x3i
        a[aP + j3 + 1] = x1i - x3r
        val wn4r = w[wP + 1]
        var k = 0
        var j = 2
        while (j < mh) {
            k += 4
            wk1r = w[wP + k]
            wk1i = w[wP + k + 1]
            wk3r = w[wP + k + 2]
            wk3i = -w[wP + k + 3]
            j1 = j + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[aP + j] + a[aP + j2]
            x0i = a[aP + j + 1] + a[aP + j2 + 1]
            x1r = a[aP + j] - a[aP + j2]
            x1i = a[aP + j + 1] - a[aP + j2 + 1]
            x2r = a[aP + j1] + a[aP + j3]
            x2i = a[aP + j1 + 1] + a[aP + j3 + 1]
            x3r = a[aP + j1] - a[aP + j3]
            x3i = a[aP + j1 + 1] - a[aP + j3 + 1]
            a[aP + j] = x0r + x2r
            a[aP + j + 1] = x0i + x2i
            a[aP + j1] = x0r - x2r
            a[aP + j1 + 1] = x0i - x2i
            x0r = x1r - x3i
            x0i = x1i + x3r
            a[aP + j2] = wk1r * x0r - wk1i * x0i
            a[aP + j2 + 1] = wk1r * x0i + wk1i * x0r
            x0r = x1r + x3i
            x0i = x1i - x3r
            a[aP + j3] = wk3r * x0r + wk3i * x0i
            a[aP + j3 + 1] = wk3r * x0i - wk3i * x0r
            j0 = m - j
            j1 = j0 + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[aP + j0] + a[aP + j2]
            x0i = a[aP + j0 + 1] + a[aP + j2 + 1]
            x1r = a[aP + j0] - a[aP + j2]
            x1i = a[aP + j0 + 1] - a[aP + j2 + 1]
            x2r = a[aP + j1] + a[aP + j3]
            x2i = a[aP + j1 + 1] + a[aP + j3 + 1]
            x3r = a[aP + j1] - a[aP + j3]
            x3i = a[aP + j1 + 1] - a[aP + j3 + 1]
            a[aP + j0] = x0r + x2r
            a[aP + j0 + 1] = x0i + x2i
            a[aP + j1] = x0r - x2r
            a[aP + j1 + 1] = x0i - x2i
            x0r = x1r - x3i
            x0i = x1i + x3r
            a[aP + j2] = wk1i * x0r - wk1r * x0i
            a[aP + j2 + 1] = wk1i * x0i + wk1r * x0r
            x0r = x1r + x3i
            x0i = x1i - x3r
            a[aP + j3] = wk3i * x0r + wk3r * x0i
            a[aP + j3 + 1] = wk3i * x0i - wk3r * x0r
            j += 2
        }
        j0 = mh
        j1 = j0 + m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[aP + j0] + a[aP + j2]
        x0i = a[aP + j0 + 1] + a[aP + j2 + 1]
        x1r = a[aP + j0] - a[aP + j2]
        x1i = a[aP + j0 + 1] - a[aP + j2 + 1]
        x2r = a[aP + j1] + a[aP + j3]
        x2i = a[aP + j1 + 1] + a[aP + j3 + 1]
        x3r = a[aP + j1] - a[aP + j3]
        x3i = a[aP + j1 + 1] - a[aP + j3 + 1]
        a[aP + j0] = x0r + x2r
        a[aP + j0 + 1] = x0i + x2i
        a[aP + j1] = x0r - x2r
        a[aP + j1 + 1] = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        a[aP + j2] = wn4r * (x0r - x0i)
        a[aP + j2 + 1] = wn4r * (x0i + x0r)
        x0r = x1r + x3i
        x0i = x1i - x3r
        a[aP + j3] = -wn4r * (x0r + x0i)
        a[aP + j3 + 1] = -wn4r * (x0i - x0r)
    }

    /**  */
    private fun cftmdl2(n: Int, a: DoubleArray, aP: Int, w: DoubleArray, wP: Int) {
        var j0: Int
        var j1: Int
        var j2: Int
        var j3: Int
        var kr: Int
        val m: Int
        var wk1r: Double
        var wk1i: Double
        var wk3r: Double
        var wk3i: Double
        var wd1r: Double
        var wd1i: Double
        var wd3r: Double
        var wd3i: Double
        var x0r: Double
        var x0i: Double
        var x1r: Double
        var x1i: Double
        var x2r: Double
        var x2i: Double
        var x3r: Double
        var x3i: Double
        var y0r: Double
        var y0i: Double
        var y2r: Double
        var y2i: Double

        val mh = n shr 3
        m = 2 * mh
        val wn4r = w[wP + 1]
        j1 = m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[aP + 0] - a[aP + j2 + 1]
        x0i = a[aP + 1] + a[aP + j2]
        x1r = a[aP + 0] + a[aP + j2 + 1]
        x1i = a[aP + 1] - a[aP + j2]
        x2r = a[aP + j1] - a[aP + j3 + 1]
        x2i = a[aP + j1 + 1] + a[aP + j3]
        x3r = a[aP + j1] + a[aP + j3 + 1]
        x3i = a[aP + j1 + 1] - a[aP + j3]
        y0r = wn4r * (x2r - x2i)
        y0i = wn4r * (x2i + x2r)
        a[aP + 0] = x0r + y0r
        a[aP + 1] = x0i + y0i
        a[aP + j1] = x0r - y0r
        a[aP + j1 + 1] = x0i - y0i
        y0r = wn4r * (x3r - x3i)
        y0i = wn4r * (x3i + x3r)
        a[aP + j2] = x1r - y0i
        a[aP + j2 + 1] = x1i + y0r
        a[aP + j3] = x1r + y0i
        a[aP + j3 + 1] = x1i - y0r
        var k = 0
        kr = 2 * m
        var j = 2
        while (j < mh) {
            k += 4
            wk1r = w[wP + k]
            wk1i = w[wP + k + 1]
            wk3r = w[wP + k + 2]
            wk3i = -w[wP + k + 3]
            kr -= 4
            wd1i = w[wP + kr]
            wd1r = w[wP + kr + 1]
            wd3i = w[wP + kr + 2]
            wd3r = -w[wP + kr + 3]
            j1 = j + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[aP + j] - a[aP + j2 + 1]
            x0i = a[aP + j + 1] + a[aP + j2]
            x1r = a[aP + j] + a[aP + j2 + 1]
            x1i = a[aP + j + 1] - a[aP + j2]
            x2r = a[aP + j1] - a[aP + j3 + 1]
            x2i = a[aP + j1 + 1] + a[aP + j3]
            x3r = a[aP + j1] + a[aP + j3 + 1]
            x3i = a[aP + j1 + 1] - a[aP + j3]
            y0r = wk1r * x0r - wk1i * x0i
            y0i = wk1r * x0i + wk1i * x0r
            y2r = wd1r * x2r - wd1i * x2i
            y2i = wd1r * x2i + wd1i * x2r
            a[aP + j] = y0r + y2r
            a[aP + j + 1] = y0i + y2i
            a[aP + j1] = y0r - y2r
            a[aP + j1 + 1] = y0i - y2i
            y0r = wk3r * x1r + wk3i * x1i
            y0i = wk3r * x1i - wk3i * x1r
            y2r = wd3r * x3r + wd3i * x3i
            y2i = wd3r * x3i - wd3i * x3r
            a[aP + j2] = y0r + y2r
            a[aP + j2 + 1] = y0i + y2i
            a[aP + j3] = y0r - y2r
            a[aP + j3 + 1] = y0i - y2i
            j0 = m - j
            j1 = j0 + m
            j2 = j1 + m
            j3 = j2 + m
            x0r = a[aP + j0] - a[aP + j2 + 1]
            x0i = a[aP + j0 + 1] + a[aP + j2]
            x1r = a[aP + j0] + a[aP + j2 + 1]
            x1i = a[aP + j0 + 1] - a[aP + j2]
            x2r = a[aP + j1] - a[aP + j3 + 1]
            x2i = a[aP + j1 + 1] + a[aP + j3]
            x3r = a[aP + j1] + a[aP + j3 + 1]
            x3i = a[aP + j1 + 1] - a[aP + j3]
            y0r = wd1i * x0r - wd1r * x0i
            y0i = wd1i * x0i + wd1r * x0r
            y2r = wk1i * x2r - wk1r * x2i
            y2i = wk1i * x2i + wk1r * x2r
            a[aP + j0] = y0r + y2r
            a[aP + j0 + 1] = y0i + y2i
            a[aP + j1] = y0r - y2r
            a[aP + j1 + 1] = y0i - y2i
            y0r = wd3i * x1r + wd3r * x1i
            y0i = wd3i * x1i - wd3r * x1r
            y2r = wk3i * x3r + wk3r * x3i
            y2i = wk3i * x3i - wk3r * x3r
            a[aP + j2] = y0r + y2r
            a[aP + j2 + 1] = y0i + y2i
            a[aP + j3] = y0r - y2r
            a[aP + j3 + 1] = y0i - y2i
            j += 2
        }
        wk1r = w[wP + m]
        wk1i = w[wP + m + 1]
        j0 = mh
        j1 = j0 + m
        j2 = j1 + m
        j3 = j2 + m
        x0r = a[aP + j0] - a[aP + j2 + 1]
        x0i = a[aP + j0 + 1] + a[aP + j2]
        x1r = a[aP + j0] + a[aP + j2 + 1]
        x1i = a[aP + j0 + 1] - a[aP + j2]
        x2r = a[aP + j1] - a[aP + j3 + 1]
        x2i = a[aP + j1 + 1] + a[aP + j3]
        x3r = a[aP + j1] + a[aP + j3 + 1]
        x3i = a[aP + j1 + 1] - a[aP + j3]
        y0r = wk1r * x0r - wk1i * x0i
        y0i = wk1r * x0i + wk1i * x0r
        y2r = wk1i * x2r - wk1r * x2i
        y2i = wk1i * x2i + wk1r * x2r
        a[aP + j0] = y0r + y2r
        a[aP + j0 + 1] = y0i + y2i
        a[aP + j1] = y0r - y2r
        a[aP + j1 + 1] = y0i - y2i
        y0r = wk1i * x1r - wk1r * x1i
        y0i = wk1i * x1i + wk1r * x1r
        y2r = wk1r * x3r - wk1i * x3i
        y2i = wk1r * x3i + wk1i * x3r
        a[aP + j2] = y0r - y2r
        a[aP + j2 + 1] = y0i - y2i
        a[aP + j3] = y0r + y2r
        a[aP + j3 + 1] = y0i + y2i
    }

    /**  */
    private fun cftfx41(n: Int, a: DoubleArray, aP: Int, nw: Int, w: DoubleArray) {
        if (n == 128) {
            cftf161(a, aP, w, nw - 8)
            cftf162(a, aP + 32, w, nw - 32)
            cftf161(a, aP + 64, w, nw - 8)
            cftf161(a, aP + 96, w, nw - 8)
        } else {
            cftf081(a, aP, w, nw - 16)
            cftf082(a, aP + 16, w, nw - 16)
            cftf081(a, aP + 32, w, nw - 16)
            cftf081(a, aP + 48, w, nw - 16)
        }
    }

    /**  */
    private fun cftfx42(n: Int, a: DoubleArray, aP: Int, nw: Int, w: DoubleArray) {
        if (n == 128) {
            cftf161(a, aP, w, nw - 8)
            cftf162(a, aP + 32, w, nw - 32)
            cftf161(a, aP + 64, w, nw - 8)
            cftf162(a, aP + 96, w, nw - 32)
        } else {
            cftf081(a, aP, w, nw - 16)
            cftf082(a, aP + 16, w, nw - 16)
            cftf081(a, aP + 32, w, nw - 16)
            cftf082(a, aP + 48, w, nw - 16)
        }
    }

    /**  */
    private fun cftf161(a: DoubleArray, aP: Int, w: DoubleArray, wP: Int) {
        val wk1r: Double
        val wk1i: Double
        val y0r: Double
        val y0i: Double
        val y1r: Double
        val y1i: Double
        val y2r: Double
        val y2i: Double
        val y3r: Double
        val y3i: Double
        val y4r: Double
        val y4i: Double
        val y5r: Double
        val y5i: Double
        val y6r: Double
        val y6i: Double
        val y7r: Double
        val y7i: Double
        val y8r: Double
        val y8i: Double
        val y9r: Double
        val y9i: Double
        val y10r: Double
        val y10i: Double
        val y11r: Double
        val y11i: Double
        val y12r: Double
        val y12i: Double
        val y13r: Double
        val y13i: Double
        val y14r: Double
        val y14i: Double
        val y15r: Double
        val y15i: Double

        val wn4r = w[wP + 1]
        wk1i = wn4r * w[wP + 2]
        wk1r = wk1i + w[wP + 2]
        var x0r = a[aP + 0] + a[aP + 16]
        var x0i = a[aP + 1] + a[aP + 17]
        var x1r = a[aP + 0] - a[aP + 16]
        var x1i = a[aP + 1] - a[aP + 17]
        var x2r = a[aP + 8] + a[aP + 24]
        var x2i = a[aP + 9] + a[aP + 25]
        var x3r = a[aP + 8] - a[aP + 24]
        var x3i = a[aP + 9] - a[aP + 25]
        y0r = x0r + x2r
        y0i = x0i + x2i
        y4r = x0r - x2r
        y4i = x0i - x2i
        y8r = x1r - x3i
        y8i = x1i + x3r
        y12r = x1r + x3i
        y12i = x1i - x3r
        x0r = a[aP + 2] + a[aP + 18]
        x0i = a[aP + 3] + a[aP + 19]
        x1r = a[aP + 2] - a[aP + 18]
        x1i = a[aP + 3] - a[aP + 19]
        x2r = a[aP + 10] + a[aP + 26]
        x2i = a[aP + 11] + a[aP + 27]
        x3r = a[aP + 10] - a[aP + 26]
        x3i = a[aP + 11] - a[aP + 27]
        y1r = x0r + x2r
        y1i = x0i + x2i
        y5r = x0r - x2r
        y5i = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        y9r = wk1r * x0r - wk1i * x0i
        y9i = wk1r * x0i + wk1i * x0r
        x0r = x1r + x3i
        x0i = x1i - x3r
        y13r = wk1i * x0r - wk1r * x0i
        y13i = wk1i * x0i + wk1r * x0r
        x0r = a[aP + 4] + a[aP + 20]
        x0i = a[aP + 5] + a[aP + 21]
        x1r = a[aP + 4] - a[aP + 20]
        x1i = a[aP + 5] - a[aP + 21]
        x2r = a[aP + 12] + a[aP + 28]
        x2i = a[aP + 13] + a[aP + 29]
        x3r = a[aP + 12] - a[aP + 28]
        x3i = a[aP + 13] - a[aP + 29]
        y2r = x0r + x2r
        y2i = x0i + x2i
        y6r = x0r - x2r
        y6i = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        y10r = wn4r * (x0r - x0i)
        y10i = wn4r * (x0i + x0r)
        x0r = x1r + x3i
        x0i = x1i - x3r
        y14r = wn4r * (x0r + x0i)
        y14i = wn4r * (x0i - x0r)
        x0r = a[aP + 6] + a[aP + 22]
        x0i = a[aP + 7] + a[aP + 23]
        x1r = a[aP + 6] - a[aP + 22]
        x1i = a[aP + 7] - a[aP + 23]
        x2r = a[aP + 14] + a[aP + 30]
        x2i = a[aP + 15] + a[aP + 31]
        x3r = a[aP + 14] - a[aP + 30]
        x3i = a[aP + 15] - a[aP + 31]
        y3r = x0r + x2r
        y3i = x0i + x2i
        y7r = x0r - x2r
        y7i = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        y11r = wk1i * x0r - wk1r * x0i
        y11i = wk1i * x0i + wk1r * x0r
        x0r = x1r + x3i
        x0i = x1i - x3r
        y15r = wk1r * x0r - wk1i * x0i
        y15i = wk1r * x0i + wk1i * x0r
        x0r = y12r - y14r
        x0i = y12i - y14i
        x1r = y12r + y14r
        x1i = y12i + y14i
        x2r = y13r - y15r
        x2i = y13i - y15i
        x3r = y13r + y15r
        x3i = y13i + y15i
        a[aP + 24] = x0r + x2r
        a[aP + 25] = x0i + x2i
        a[aP + 26] = x0r - x2r
        a[aP + 27] = x0i - x2i
        a[aP + 28] = x1r - x3i
        a[aP + 29] = x1i + x3r
        a[aP + 30] = x1r + x3i
        a[aP + 31] = x1i - x3r
        x0r = y8r + y10r
        x0i = y8i + y10i
        x1r = y8r - y10r
        x1i = y8i - y10i
        x2r = y9r + y11r
        x2i = y9i + y11i
        x3r = y9r - y11r
        x3i = y9i - y11i
        a[aP + 16] = x0r + x2r
        a[aP + 17] = x0i + x2i
        a[aP + 18] = x0r - x2r
        a[aP + 19] = x0i - x2i
        a[aP + 20] = x1r - x3i
        a[aP + 21] = x1i + x3r
        a[aP + 22] = x1r + x3i
        a[aP + 23] = x1i - x3r
        x0r = y5r - y7i
        x0i = y5i + y7r
        x2r = wn4r * (x0r - x0i)
        x2i = wn4r * (x0i + x0r)
        x0r = y5r + y7i
        x0i = y5i - y7r
        x3r = wn4r * (x0r - x0i)
        x3i = wn4r * (x0i + x0r)
        x0r = y4r - y6i
        x0i = y4i + y6r
        x1r = y4r + y6i
        x1i = y4i - y6r
        a[aP + 8] = x0r + x2r
        a[aP + 9] = x0i + x2i
        a[aP + 10] = x0r - x2r
        a[aP + 11] = x0i - x2i
        a[aP + 12] = x1r - x3i
        a[aP + 13] = x1i + x3r
        a[aP + 14] = x1r + x3i
        a[aP + 15] = x1i - x3r
        x0r = y0r + y2r
        x0i = y0i + y2i
        x1r = y0r - y2r
        x1i = y0i - y2i
        x2r = y1r + y3r
        x2i = y1i + y3i
        x3r = y1r - y3r
        x3i = y1i - y3i
        a[aP + 0] = x0r + x2r
        a[aP + 1] = x0i + x2i
        a[aP + 2] = x0r - x2r
        a[aP + 3] = x0i - x2i
        a[aP + 4] = x1r - x3i
        a[aP + 5] = x1i + x3r
        a[aP + 6] = x1r + x3i
        a[aP + 7] = x1i - x3r
    }

    /**  */
    private fun cftf162(a: DoubleArray, aP: Int, w: DoubleArray, wP: Int) {
        var x2r: Double
        var x2i: Double
        val y0r: Double
        val y0i: Double
        val y1r: Double
        val y1i: Double
        val y2r: Double
        val y2i: Double
        val y3r: Double
        val y3i: Double
        val y4r: Double
        val y4i: Double
        val y5r: Double
        val y5i: Double
        val y6r: Double
        val y6i: Double
        val y7r: Double
        val y7i: Double
        val y8r: Double
        val y8i: Double
        val y9r: Double
        val y9i: Double
        val y10r: Double
        val y10i: Double
        val y11r: Double
        val y11i: Double
        val y12r: Double
        val y12i: Double
        val y13r: Double
        val y13i: Double
        val y14r: Double
        val y14i: Double
        val y15r: Double
        val y15i: Double

        val wn4r = w[wP + 1]
        val wk1r = w[wP + 4]
        val wk1i = w[wP + 5]
        val wk3r = w[wP + 6]
        val wk3i = w[wP + 7]
        val wk2r = w[wP + 8]
        val wk2i = w[wP + 9]
        var x1r = a[aP + 0] - a[aP + 17]
        var x1i = a[aP + 1] + a[aP + 16]
        var x0r = a[aP + 8] - a[aP + 25]
        var x0i = a[aP + 9] + a[aP + 24]
        x2r = wn4r * (x0r - x0i)
        x2i = wn4r * (x0i + x0r)
        y0r = x1r + x2r
        y0i = x1i + x2i
        y4r = x1r - x2r
        y4i = x1i - x2i
        x1r = a[aP + 0] + a[aP + 17]
        x1i = a[aP + 1] - a[aP + 16]
        x0r = a[aP + 8] + a[aP + 25]
        x0i = a[aP + 9] - a[aP + 24]
        x2r = wn4r * (x0r - x0i)
        x2i = wn4r * (x0i + x0r)
        y8r = x1r - x2i
        y8i = x1i + x2r
        y12r = x1r + x2i
        y12i = x1i - x2r
        x0r = a[aP + 2] - a[aP + 19]
        x0i = a[aP + 3] + a[aP + 18]
        x1r = wk1r * x0r - wk1i * x0i
        x1i = wk1r * x0i + wk1i * x0r
        x0r = a[aP + 10] - a[aP + 27]
        x0i = a[aP + 11] + a[aP + 26]
        x2r = wk3i * x0r - wk3r * x0i
        x2i = wk3i * x0i + wk3r * x0r
        y1r = x1r + x2r
        y1i = x1i + x2i
        y5r = x1r - x2r
        y5i = x1i - x2i
        x0r = a[aP + 2] + a[aP + 19]
        x0i = a[aP + 3] - a[aP + 18]
        x1r = wk3r * x0r - wk3i * x0i
        x1i = wk3r * x0i + wk3i * x0r
        x0r = a[aP + 10] + a[aP + 27]
        x0i = a[aP + 11] - a[aP + 26]
        x2r = wk1r * x0r + wk1i * x0i
        x2i = wk1r * x0i - wk1i * x0r
        y9r = x1r - x2r
        y9i = x1i - x2i
        y13r = x1r + x2r
        y13i = x1i + x2i
        x0r = a[aP + 4] - a[aP + 21]
        x0i = a[aP + 5] + a[aP + 20]
        x1r = wk2r * x0r - wk2i * x0i
        x1i = wk2r * x0i + wk2i * x0r
        x0r = a[aP + 12] - a[aP + 29]
        x0i = a[aP + 13] + a[aP + 28]
        x2r = wk2i * x0r - wk2r * x0i
        x2i = wk2i * x0i + wk2r * x0r
        y2r = x1r + x2r
        y2i = x1i + x2i
        y6r = x1r - x2r
        y6i = x1i - x2i
        x0r = a[aP + 4] + a[aP + 21]
        x0i = a[aP + 5] - a[aP + 20]
        x1r = wk2i * x0r - wk2r * x0i
        x1i = wk2i * x0i + wk2r * x0r
        x0r = a[aP + 12] + a[aP + 29]
        x0i = a[aP + 13] - a[aP + 28]
        x2r = wk2r * x0r - wk2i * x0i
        x2i = wk2r * x0i + wk2i * x0r
        y10r = x1r - x2r
        y10i = x1i - x2i
        y14r = x1r + x2r
        y14i = x1i + x2i
        x0r = a[aP + 6] - a[aP + 23]
        x0i = a[aP + 7] + a[aP + 22]
        x1r = wk3r * x0r - wk3i * x0i
        x1i = wk3r * x0i + wk3i * x0r
        x0r = a[aP + 14] - a[aP + 31]
        x0i = a[aP + 15] + a[aP + 30]
        x2r = wk1i * x0r - wk1r * x0i
        x2i = wk1i * x0i + wk1r * x0r
        y3r = x1r + x2r
        y3i = x1i + x2i
        y7r = x1r - x2r
        y7i = x1i - x2i
        x0r = a[aP + 6] + a[aP + 23]
        x0i = a[aP + 7] - a[aP + 22]
        x1r = wk1i * x0r + wk1r * x0i
        x1i = wk1i * x0i - wk1r * x0r
        x0r = a[aP + 14] + a[aP + 31]
        x0i = a[aP + 15] - a[aP + 30]
        x2r = wk3i * x0r - wk3r * x0i
        x2i = wk3i * x0i + wk3r * x0r
        y11r = x1r + x2r
        y11i = x1i + x2i
        y15r = x1r - x2r
        y15i = x1i - x2i
        x1r = y0r + y2r
        x1i = y0i + y2i
        x2r = y1r + y3r
        x2i = y1i + y3i
        a[aP + 0] = x1r + x2r
        a[aP + 1] = x1i + x2i
        a[aP + 2] = x1r - x2r
        a[aP + 3] = x1i - x2i
        x1r = y0r - y2r
        x1i = y0i - y2i
        x2r = y1r - y3r
        x2i = y1i - y3i
        a[aP + 4] = x1r - x2i
        a[aP + 5] = x1i + x2r
        a[aP + 6] = x1r + x2i
        a[aP + 7] = x1i - x2r
        x1r = y4r - y6i
        x1i = y4i + y6r
        x0r = y5r - y7i
        x0i = y5i + y7r
        x2r = wn4r * (x0r - x0i)
        x2i = wn4r * (x0i + x0r)
        a[aP + 8] = x1r + x2r
        a[aP + 9] = x1i + x2i
        a[aP + 10] = x1r - x2r
        a[aP + 11] = x1i - x2i
        x1r = y4r + y6i
        x1i = y4i - y6r
        x0r = y5r + y7i
        x0i = y5i - y7r
        x2r = wn4r * (x0r - x0i)
        x2i = wn4r * (x0i + x0r)
        a[aP + 12] = x1r - x2i
        a[aP + 13] = x1i + x2r
        a[aP + 14] = x1r + x2i
        a[aP + 15] = x1i - x2r
        x1r = y8r + y10r
        x1i = y8i + y10i
        x2r = y9r - y11r
        x2i = y9i - y11i
        a[aP + 16] = x1r + x2r
        a[aP + 17] = x1i + x2i
        a[aP + 18] = x1r - x2r
        a[aP + 19] = x1i - x2i
        x1r = y8r - y10r
        x1i = y8i - y10i
        x2r = y9r + y11r
        x2i = y9i + y11i
        a[aP + 20] = x1r - x2i
        a[aP + 21] = x1i + x2r
        a[aP + 22] = x1r + x2i
        a[aP + 23] = x1i - x2r
        x1r = y12r - y14i
        x1i = y12i + y14r
        x0r = y13r + y15i
        x0i = y13i - y15r
        x2r = wn4r * (x0r - x0i)
        x2i = wn4r * (x0i + x0r)
        a[aP + 24] = x1r + x2r
        a[aP + 25] = x1i + x2i
        a[aP + 26] = x1r - x2r
        a[aP + 27] = x1i - x2i
        x1r = y12r + y14i
        x1i = y12i - y14r
        x0r = y13r - y15i
        x0i = y13i + y15r
        x2r = wn4r * (x0r - x0i)
        x2i = wn4r * (x0i + x0r)
        a[aP + 28] = x1r - x2i
        a[aP + 29] = x1i + x2r
        a[aP + 30] = x1r + x2i
        a[aP + 31] = x1i - x2r
    }

    /**  */
    private fun cftf081(a: DoubleArray, aP: Int, w: DoubleArray, wP: Int) {
        val y0r: Double
        val y0i: Double
        val y1r: Double
        val y1i: Double
        val y2r: Double
        val y2i: Double
        val y3r: Double
        val y3i: Double
        val y4r: Double
        val y4i: Double
        val y5r: Double
        val y5i: Double
        val y6r: Double
        val y6i: Double
        val y7r: Double
        val y7i: Double

        val wn4r = w[wP + 1]
        var x0r = a[aP + 0] + a[aP + 8]
        var x0i = a[aP + 1] + a[aP + 9]
        var x1r = a[aP + 0] - a[aP + 8]
        var x1i = a[aP + 1] - a[aP + 9]
        var x2r = a[aP + 4] + a[aP + 12]
        var x2i = a[aP + 5] + a[aP + 13]
        var x3r = a[aP + 4] - a[aP + 12]
        var x3i = a[aP + 5] - a[aP + 13]
        y0r = x0r + x2r
        y0i = x0i + x2i
        y2r = x0r - x2r
        y2i = x0i - x2i
        y1r = x1r - x3i
        y1i = x1i + x3r
        y3r = x1r + x3i
        y3i = x1i - x3r
        x0r = a[aP + 2] + a[aP + 10]
        x0i = a[aP + 3] + a[aP + 11]
        x1r = a[aP + 2] - a[aP + 10]
        x1i = a[aP + 3] - a[aP + 11]
        x2r = a[aP + 6] + a[aP + 14]
        x2i = a[aP + 7] + a[aP + 15]
        x3r = a[aP + 6] - a[aP + 14]
        x3i = a[aP + 7] - a[aP + 15]
        y4r = x0r + x2r
        y4i = x0i + x2i
        y6r = x0r - x2r
        y6i = x0i - x2i
        x0r = x1r - x3i
        x0i = x1i + x3r
        x2r = x1r + x3i
        x2i = x1i - x3r
        y5r = wn4r * (x0r - x0i)
        y5i = wn4r * (x0r + x0i)
        y7r = wn4r * (x2r - x2i)
        y7i = wn4r * (x2r + x2i)
        a[aP + 8] = y1r + y5r
        a[aP + 9] = y1i + y5i
        a[aP + 10] = y1r - y5r
        a[aP + 11] = y1i - y5i
        a[aP + 12] = y3r - y7i
        a[aP + 13] = y3i + y7r
        a[aP + 14] = y3r + y7i
        a[aP + 15] = y3i - y7r
        a[aP + 0] = y0r + y4r
        a[aP + 1] = y0i + y4i
        a[aP + 2] = y0r - y4r
        a[aP + 3] = y0i - y4i
        a[aP + 4] = y2r - y6i
        a[aP + 5] = y2i + y6r
        a[aP + 6] = y2r + y6i
        a[aP + 7] = y2i - y6r
    }

    /**  */
    private fun cftf082(a: DoubleArray, aP: Int, w: DoubleArray, wP: Int) {
        var x1r: Double
        var x1i: Double
        val y2r: Double
        val y2i: Double
        val y3r: Double
        val y3i: Double
        val y4r: Double
        val y4i: Double
        val y5r: Double
        val y5i: Double
        val y6r: Double
        val y6i: Double
        val y7r: Double
        val y7i: Double

        val wn4r = w[wP + 1]
        val wk1r = w[wP + 4]
        val wk1i = w[wP + 5]
        val y0r = a[aP + 0] - a[aP + 9]
        val y0i = a[aP + 1] + a[aP + 8]
        val y1r = a[aP + 0] + a[aP + 9]
        val y1i = a[aP + 1] - a[aP + 8]
        var x0r = a[aP + 4] - a[aP + 13]
        var x0i = a[aP + 5] + a[aP + 12]
        y2r = wn4r * (x0r - x0i)
        y2i = wn4r * (x0i + x0r)
        x0r = a[aP + 4] + a[aP + 13]
        x0i = a[aP + 5] - a[aP + 12]
        y3r = wn4r * (x0r - x0i)
        y3i = wn4r * (x0i + x0r)
        x0r = a[aP + 2] - a[aP + 11]
        x0i = a[aP + 3] + a[aP + 10]
        y4r = wk1r * x0r - wk1i * x0i
        y4i = wk1r * x0i + wk1i * x0r
        x0r = a[aP + 2] + a[aP + 11]
        x0i = a[aP + 3] - a[aP + 10]
        y5r = wk1i * x0r - wk1r * x0i
        y5i = wk1i * x0i + wk1r * x0r
        x0r = a[aP + 6] - a[aP + 15]
        x0i = a[aP + 7] + a[aP + 14]
        y6r = wk1i * x0r - wk1r * x0i
        y6i = wk1i * x0i + wk1r * x0r
        x0r = a[aP + 6] + a[aP + 15]
        x0i = a[aP + 7] - a[aP + 14]
        y7r = wk1r * x0r - wk1i * x0i
        y7i = wk1r * x0i + wk1i * x0r
        x0r = y0r + y2r
        x0i = y0i + y2i
        x1r = y4r + y6r
        x1i = y4i + y6i
        a[aP + 0] = x0r + x1r
        a[aP + 1] = x0i + x1i
        a[aP + 2] = x0r - x1r
        a[aP + 3] = x0i - x1i
        x0r = y0r - y2r
        x0i = y0i - y2i
        x1r = y4r - y6r
        x1i = y4i - y6i
        a[aP + 4] = x0r - x1i
        a[aP + 5] = x0i + x1r
        a[aP + 6] = x0r + x1i
        a[aP + 7] = x0i - x1r
        x0r = y1r - y3i
        x0i = y1i + y3r
        x1r = y5r - y7r
        x1i = y5i - y7i
        a[aP + 8] = x0r + x1r
        a[aP + 9] = x0i + x1i
        a[aP + 10] = x0r - x1r
        a[aP + 11] = x0i - x1i
        x0r = y1r + y3i
        x0i = y1i - y3r
        x1r = y5r + y7r
        x1i = y5i + y7i
        a[aP + 12] = x0r - x1i
        a[aP + 13] = x0i + x1r
        a[aP + 14] = x0r + x1i
        a[aP + 15] = x0i - x1r
    }

    /**
     * 3rd
     * when n = 8.
     * @see .cftfsub
     */
    private fun cftf040(a: DoubleArray) {
        val x0r = a[0] + a[4]
        val x0i = a[1] + a[5]
        val x1r = a[0] - a[4]
        val x1i = a[1] - a[5]
        val x2r = a[2] + a[6]
        val x2i = a[3] + a[7]
        val x3r = a[2] - a[6]
        val x3i = a[3] - a[7]
        a[0] = x0r + x2r
        a[1] = x0i + x2i
        a[4] = x0r - x2r
        a[5] = x0i - x2i
        a[2] = x1r - x3i
        a[3] = x1i + x3r
        a[6] = x1r + x3i
        a[7] = x1i - x3r
    }

    /**
     * 3rd
     * when n = 8.
     * @see .cftbsub
     */
    private fun cftb040(a: DoubleArray) {
        val x0r = a[0] + a[4]
        val x0i = a[1] + a[5]
        val x1r = a[0] - a[4]
        val x1i = a[1] - a[5]
        val x2r = a[2] + a[6]
        val x2i = a[3] + a[7]
        val x3r = a[2] - a[6]
        val x3i = a[3] - a[7]
        a[0] = x0r + x2r
        a[1] = x0i + x2i
        a[4] = x0r - x2r
        a[5] = x0i - x2i
        a[2] = x1r + x3i
        a[3] = x1i - x3r
        a[6] = x1r - x3i
        a[7] = x1i + x3r
    }

    /**
     * 3rd
     * when n = 4.
     * @see .cftbsub
     * @see .cftfsub
     */
    private fun cftx020(a: DoubleArray) {
        val x0r = a[0] - a[2]
        val x0i = a[1] - a[3]
        a[0] += a[2]
        a[1] += a[3]
        a[2] = x0r
        a[3] = x0i
    }

    /**
     * 2nd
     * @see .rdft
     * @see .ddct
     * @see .ddst
     * @see .dfst
     * @see .dfct
     */
    private fun rftfsub(n: Int, a: DoubleArray, nc: Int, c: DoubleArray, cP: Int) {
        var k: Int
        val ks: Int
        var wkr: Double
        var wki: Double
        var xr: Double
        var xi: Double
        var yr: Double
        var yi: Double

        val m = n shr 1
        ks = 2 * nc / m
        var kk = 0
        var j = 2
        while (j < m) {
            k = n - j
            kk += ks
            wkr = 0.5 - c[cP + nc - kk]
            wki = c[cP + kk]
            xr = a[j] - a[k]
            xi = a[j + 1] + a[k + 1]
            yr = wkr * xr - wki * xi
            yi = wkr * xi + wki * xr
            a[j] -= yr
            a[j + 1] -= yi
            a[k] += yr
            a[k + 1] -= yi
            j += 2
        }
    }

    /**
     * 2nd
     * @see .rdft
     * @see .ddct
     * @see .ddst
     */
    private fun rftbsub(n: Int, a: DoubleArray, nc: Int, c: DoubleArray, cP: Int) {
        var k: Int
        val ks: Int
        var wkr: Double
        var wki: Double
        var xr: Double
        var xi: Double
        var yr: Double
        var yi: Double

        val m = n shr 1
        ks = 2 * nc / m
        var kk = 0
        var j = 2
        while (j < m) {
            k = n - j
            kk += ks
            wkr = 0.5 - c[cP + nc - kk]
            wki = c[cP + kk]
            xr = a[j] - a[k]
            xi = a[j + 1] + a[k + 1]
            yr = wkr * xr + wki * xi
            yi = wkr * xi - wki * xr
            a[j] -= yr
            a[j + 1] -= yi
            a[k] += yr
            a[k + 1] -= yi
            j += 2
        }
    }

    /**
     * 2nd
     * @see .ddct
     * @see .dfct
     */
    private fun dctsub(n: Int, a: DoubleArray, nc: Int, c: DoubleArray, cP: Int) {
        var k: Int
        var wkr: Double
        var wki: Double
        var xr: Double

        val m = n shr 1
        val ks = nc / n
        var kk = 0
        var j = 1
        while (j < m) {
            k = n - j
            kk += ks
            wkr = c[cP + kk] - c[cP + nc - kk]
            wki = c[cP + kk] + c[cP + nc - kk]
            xr = wki * a[j] - wkr * a[k]
            a[j] = wkr * a[j] + wki * a[k]
            a[k] = xr
            j++
        }
        a[m] *= c[cP + 0]
    }

    /**
     * 2nd
     * @see .ddst
     * @see .dfst
     */
    private fun dstsub(n: Int, a: DoubleArray, nc: Int, c: DoubleArray, cP: Int) {
        var k: Int
        var wkr: Double
        var wki: Double
        var xr: Double

        val m = n shr 1
        val ks = nc / n
        var kk = 0
        var j = 1
        while (j < m) {
            k = n - j
            kk += ks
            wkr = c[cP + kk] - c[cP + nc - kk]
            wki = c[cP + kk] + c[cP + nc - kk]
            xr = wki * a[k] - wkr * a[j]
            a[k] = wkr * a[k] + wki * a[j]
            a[j] = xr
            j++
        }
        a[m] *= c[cP + 0]
    }

    companion object {
        /**  */
        private const val CDFT_RECURSIVE_N = 512
    }
} /* */

