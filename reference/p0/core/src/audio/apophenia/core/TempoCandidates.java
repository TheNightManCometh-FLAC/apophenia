package audio.apophenia.core;

import java.util.*;
/** Deterministic prototype for post-onset tempo proposals. NOT calibrated for real audio. */
public final class TempoCandidates {
    public static final class Candidate {
        public final double bpm, fit;
        public final int hits;
        Candidate(double bpm, double fit, int hits) { this.bpm=bpm; this.fit=fit; this.hits=hits; }
        public String toString() { return String.format(Locale.US,"%.1f BPM | %.2f lattice fit | %d onsets",bpm,fit,hits); }
    }
    /** onsets in seconds, monotonic increasing; returns ranked half/double alternatives, or empty when weak. */
    public static List<Candidate> estimate(double[] onsets) {
        if(onsets==null || onsets.length<5) return Collections.emptyList();
        double span=onsets[onsets.length-1]-onsets[0];
        if(span<2.8) return Collections.emptyList();
        for(int i=1;i<onsets.length;i++) if(!(onsets[i]>onsets[i-1])) throw new IllegalArgumentException("onsets must increase");
        ArrayList<Candidate> results=new ArrayList<>();
        for(double bpm=40;bpm<=240;bpm+=0.5) {
            double beat=60.0/bpm, sum=0, occupancy=0;
            for(int i=0;i<onsets.length;i++) {
                // Allow attacks on a half-beat: riffs often land on subdivisions.
                double subdivision=beat/2.0;
                double rel=(onsets[i]-onsets[0])/subdivision;
                double delta=Math.abs(rel-Math.rint(rel))*subdivision;
                double tolerance=Math.max(0.025, Math.min(0.070,subdivision*0.23));
                double local=Math.max(0,1-delta/tolerance);
                sum+=local; if(local>0.60) occupancy++;
            }
            // Minimal complexity preference; precise mode inference needs onset accents/pulse.
            double coverage=occupancy/onsets.length;
            double fit=0.65*(sum/onsets.length)+0.35*coverage;
            results.add(new Candidate(bpm,fit,onsets.length));
        }
        results.sort((a,b)->Double.compare(b.fit,a.fit));
        ArrayList<Candidate> unique=new ArrayList<>();
        for(Candidate c:results) {
            if(c.fit<0.52) break;
            boolean tooClose=false;
            for(Candidate old: unique) if(Math.abs(c.bpm-old.bpm)<4) { tooClose=true; break; }
            if(!tooClose) unique.add(c);
            if(unique.size()==6) break;
        }
        return unique;
    }
    private TempoCandidates() {}
}
