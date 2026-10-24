package audio.apophenia.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Baseline beat-level chroma -> chord-name templates. No note separation or inversion claims. */
public final class ChordTemplates {
    private static final String[] ROOT = {"C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B"};
    private static final String[] QUALITY = {"", "m", "dim", "aug", "sus2", "sus4", "7", "maj7", "m7", "m7b5"};
    private static final int[][] INTERVALS = {
        {0,4,7},{0,3,7},{0,3,6},{0,4,8},{0,2,7},{0,5,7},
        {0,4,7,10},{0,4,7,11},{0,3,7,10},{0,3,6,10}
    };
    public static final class Candidate {
        public final String label;
        public final int root,quality;
        public final double score;
        Candidate(int root,int quality,double score) {
            this.root=root;this.quality=quality;this.score=score;
            this.label=ROOT[root]+QUALITY[quality];
        }
    }
    public static final class Guess {
        public final List<Candidate> options;
        public final boolean ambiguous;
        public final String explanation;
        Guess(List<Candidate> options, boolean ambiguous, String explanation) {
            this.options=options;this.ambiguous=ambiguous;this.explanation=explanation;
        }
    }
    public static Guess guess(double[] chroma) {
        if(chroma==null || chroma.length!=12)throw new IllegalArgumentException("12 chroma bins required");
        double total=0;
        for(double v:chroma){if(v<0 || !Double.isFinite(v))throw new IllegalArgumentException("invalid chroma");total+=v;}
        if(total<=0)return new Guess(Collections.<Candidate>emptyList(),true,"No reliable pitched content");
        ArrayList<Candidate> all = new ArrayList<>();
        for(int root=0;root<12;root++)for(int q=0;q<QUALITY.length;q++) {
            boolean[] included=new boolean[12];
            for(int interval:INTERVALS[q])included[(root+interval)%12]=true;
            double matched=0,missing=0;
            int absentChordTones=0;
            for(int pc=0;pc<12;pc++) {
                if(included[pc]) {matched+=chroma[pc];if(chroma[pc]<total*0.07)absentChordTones++;}
                else missing+=chroma[pc];
            }
            // Broadly captures energy purity. A smaller, less complete chord cannot explain extra notes.
            double score=(matched-0.75*missing)/total - 0.65*absentChordTones/(double)INTERVALS[q].length;
            all.add(new Candidate(root,q,score));
        }
        Collections.sort(all, new Comparator<Candidate>() {
            @Override public int compare(Candidate a,Candidate b){return Double.compare(b.score,a.score);}
        });
        List<Candidate> top=Collections.unmodifiableList(new ArrayList<Candidate>(all.subList(0,Math.min(5,all.size()))));
        double gap=top.get(0).score-top.get(1).score;
        boolean ambiguous=top.get(0).score<0.75 || gap<0.12;
        return new Guess(top,ambiguous,ambiguous?"Insufficient evidence to choose one chord; showing alternatives":"Chord template fits observed pitch classes");
    }
    private ChordTemplates() {}
}
