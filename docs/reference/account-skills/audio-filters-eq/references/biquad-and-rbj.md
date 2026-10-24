# Biquads & the RBJ Cookbook

When the SVF in `SKILL.md` is the default, this file covers the biquad alternative:
the Direct-Form structures, the full Robert Bristow-Johnson "Audio EQ Cookbook"
coefficient set (freely published; the formulas below are standard math facts), and
the cases where a biquad is genuinely the right tool.

## Contents
1. When to choose a biquad over the SVF
2. Transposed Direct Form II (the structure to use)
3. RBJ cookbook — intermediate variables
4. RBJ cookbook — coefficients for every shape
5. Numerical notes

## 1. When to choose a biquad over the SVF

Default to the SVF. Reach for a biquad only when one of these is true:

- You are porting **fixed, externally-provided coefficients** (e.g., a measured or
  offline-designed response) and want the exact same `b`/`a` topology.
- You need a specific **Direct-Form** numerical behavior to match a legacy result.
- Coefficients are **static** (never automated) and you want the marginally cheaper
  static cost with no need to morph between shapes.

If cutoff/Q/gain are automated or modulated, prefer the SVF — biquads zipper and can
transiently destabilize while their coefficients sweep.

## 2. Transposed Direct Form II

Use TDF2: only two state variables, good floating-point behavior, and the state has a
clear meaning that makes it safe to reset. Coefficients are normalized so `a0 = 1`
(divide every coefficient by `a0` once, at build time). State is **per channel**;
coefficients are shared across channels of the same band.

```cpp
#include <cmath>
constexpr double kPi = 3.14159265358979323846;

struct BiquadCoeffs { double b0=1, b1=0, b2=0, a1=0, a2=0; };  // a0 normalized to 1
struct BiquadState  { double s1=0, s2=0; void reset(){ s1=0; s2=0; } };

inline double biquadTick(const BiquadCoeffs& c, BiquadState& s, double x) {
    const double y = c.b0 * x + s.s1;
    s.s1 = c.b1 * x - c.a1 * y + s.s2;
    s.s2 = c.b2 * x - c.a2 * y;
    return y;
}

// Normalize raw (b0..b2, a0..a2) into BiquadCoeffs.
inline BiquadCoeffs normalize(double b0,double b1,double b2,double a0,double a1,double a2){
    BiquadCoeffs c;
    c.b0 = b0/a0; c.b1 = b1/a0; c.b2 = b2/a0; c.a1 = a1/a0; c.a2 = a2/a0;
    return c;
}
```

## 3. RBJ cookbook — intermediate variables

`f0` = center/cutoff (Hz), `fs` = sample rate, `Q` = quality, `gainDb` for peaking and
shelves only. `A = 10^(gainDb/40)` is amplitude (so the peaking gain at `f0` is
`gainDb`). Clamp `f0` to `(0, 0.49*fs]`.

```cpp
const double w0    = 2.0 * kPi * f0 / fs;
const double cw    = std::cos(w0);
const double sw    = std::sin(w0);
const double alpha = sw / (2.0 * Q);
const double A     = std::pow(10.0, gainDb / 40.0);   // peaking/shelf only
const double tsA   = 2.0 * std::sqrt(A) * alpha;      // shelf term: 2*sqrt(A)*alpha
```

## 4. RBJ cookbook — coefficients for every shape

Each block lists raw `b0,b1,b2,a0,a1,a2`; pass them through `normalize(...)`.

**Low-pass**
```
b0 = (1 - cw) / 2;  b1 = 1 - cw;        b2 = (1 - cw) / 2;
a0 = 1 + alpha;     a1 = -2 * cw;       a2 = 1 - alpha;
```
**High-pass**
```
b0 = (1 + cw) / 2;  b1 = -(1 + cw);     b2 = (1 + cw) / 2;
a0 = 1 + alpha;     a1 = -2 * cw;       a2 = 1 - alpha;
```
**Band-pass** (constant 0 dB peak gain)
```
b0 = alpha;         b1 = 0;             b2 = -alpha;
a0 = 1 + alpha;     a1 = -2 * cw;       a2 = 1 - alpha;
```
**Notch**
```
b0 = 1;             b1 = -2 * cw;       b2 = 1;
a0 = 1 + alpha;     a1 = -2 * cw;       a2 = 1 - alpha;
```
**All-pass**
```
b0 = 1 - alpha;     b1 = -2 * cw;       b2 = 1 + alpha;
a0 = 1 + alpha;     a1 = -2 * cw;       a2 = 1 - alpha;
```
**Peaking EQ**
```
b0 = 1 + alpha * A; b1 = -2 * cw;       b2 = 1 - alpha * A;
a0 = 1 + alpha / A; a1 = -2 * cw;       a2 = 1 - alpha / A;
```
**Low shelf**
```
b0 =      A * ((A + 1) - (A - 1) * cw + tsA);
b1 =  2 * A * ((A - 1) - (A + 1) * cw);
b2 =      A * ((A + 1) - (A - 1) * cw - tsA);
a0 =          (A + 1) + (A - 1) * cw + tsA;
a1 =     -2 * ((A - 1) + (A + 1) * cw);
a2 =          (A + 1) + (A - 1) * cw - tsA;
```
**High shelf**
```
b0 =      A * ((A + 1) + (A - 1) * cw + tsA);
b1 = -2 * A * ((A - 1) + (A + 1) * cw);
b2 =      A * ((A + 1) + (A - 1) * cw - tsA);
a0 =          (A + 1) - (A - 1) * cw + tsA;
a1 =      2 * ((A - 1) - (A + 1) * cw);
a2 =          (A + 1) - (A - 1) * cw - tsA;
```

## 5. Numerical notes

- Keep state in `double`. Direct-Form biquads lose precision at very low `f0/fs`
  (poles cluster near z = 1); the SVF does not have this weakness, which is one more
  reason it is the default.
- A biquad is exactly second order (12 dB/oct). For steeper responses cascade
  sections with the Butterworth Q values given in `SKILL.md`.
- Recompute coefficients at control rate, not per sample. If you must sweep a biquad,
  smooth the *parameters* and rebuild per block; do not interpolate `a`/`b`
  coefficients directly (intermediate coefficient sets can be unstable).
