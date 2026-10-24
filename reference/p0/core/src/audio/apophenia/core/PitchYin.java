package audio.apophenia.core;

/**
 * Reference YIN-style difference + cumulative mean normalized difference pitch estimate.
 * Deliberately simple scalar offline prototype. No Android dependencies, no neural network.
 * Input should be mono, DC-removed, and appropriately low-pass filtered before resampling.
 * Not a production tuner yet: requires device/noise/octave benchmark.
 */
public final class PitchYin {
    public static final class Result {
        public final double hz, confidence;
        public final boolean voiced;
        Result(double hz, double confidence, boolean voiced) {
            this.hz=hz; this.confidence=confidence; this.voiced=voiced;
        }
        public int nearestMidi() {
            return voiced ? (int)Math.round(69+12*Math.log(hz/440.0)/Math.log(2)) : -1;
        }
    }
    private static final Result SILENCE = new Result(0,0,false);
    /** Uses a fixed comparison span so the difference function does not shrink as lag rises. */
    public static Result estimate(float[] audio, int sampleRate, double minHz, double maxHz) {
        if(audio==null || sampleRate<2000 || minHz<20 || maxHz<=minHz || maxHz>sampleRate/2)
            throw new IllegalArgumentException("invalid audio, rate or pitch bounds");
        int minLag=Math.max(2,(int)Math.floor(sampleRate/maxHz));
        int maxLag=(int)Math.ceil(sampleRate/minHz);
        if(maxLag>=audio.length/2) return SILENCE;  // insufficient frame length for the low note
        double energy=0, mean=0;
        for(float v:audio){ energy += v*v; mean+=v; }
        mean/=audio.length;
        energy=energy/audio.length-mean*mean;
        if(energy<0.000006) return SILENCE;
        int span=audio.length-maxLag;
        double[] cmndf=new double[maxLag+1];
        double cumulative=0;
        for(int lag=1;lag<=maxLag;lag++) {
            double diff=0;
            for(int i=0;i<span;i++) {
                double d=audio[i]-audio[i+lag];diff+=d*d;
            }
            cumulative+=diff;
            cmndf[lag]=cumulative==0?1:(diff*lag/cumulative);
        }
        // Look for first local minimum below threshold, rather than global minimum at octave/subharmonic.
        int chosen=-1;final double threshold=0.15;
        for(int lag=minLag;lag<maxLag;lag++) if(cmndf[lag]<threshold) {
            while(lag+1<=maxLag && cmndf[lag+1]<cmndf[lag])lag++;
            chosen=lag;break;
        }
        if(chosen<0) {
            int best=minLag;
            for(int lag=minLag+1;lag<=maxLag;lag++)if(cmndf[lag]<cmndf[best])best=lag;
            if(cmndf[best]>=0.25) return SILENCE;
            chosen=best;
        }
        double refined=chosen;
        if(chosen>1&&chosen<maxLag) {
            double l=cmndf[chosen-1],c=cmndf[chosen],r=cmndf[chosen+1];
            double curvature=l-2*c+r;
            if(Math.abs(curvature)>1e-9)refined+=Math.max(-0.5,Math.min(0.5,0.5*(l-r)/curvature));
        }
        double conf=Math.max(0,Math.min(1,1-cmndf[chosen]));
        double hz=sampleRate/refined;
        return (hz>=minHz*0.97&&hz<=maxHz*1.03) ? new Result(hz,conf,true) : SILENCE;
    }
    private PitchYin() {}
}
