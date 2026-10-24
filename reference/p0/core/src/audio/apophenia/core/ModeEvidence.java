package audio.apophenia.core;
import java.util.*;
/** Supports seven diatonic modes; more exotic families should be added with tested catalog data. */
public final class ModeEvidence {
    private static final String[] NAME = {"Ionian","Dorian","Phrygian","Lydian","Mixolydian","Aeolian","Locrian"};
    private static final String[] NOTE={"C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B"};
    private static final int[][] SCALES={{0,2,4,5,7,9,11},{0,2,3,5,7,9,10},{0,1,3,5,7,8,10},{0,2,4,6,7,9,11},{0,2,4,5,7,9,10},{0,2,3,5,7,8,10},{0,1,3,5,6,8,10}};
    public static final class Candidate {
        public final String name;
        public final double score;
        public final int tonic, mode;
        Candidate(int t,int m,double s) { tonic=t;mode=m;score=s;name=NOTE[t]+" "+NAME[m]; }
        public String toString(){return name+" score="+String.format(Locale.US,"%.3f",score);}
    }
    /** durations[0..11]: observed pitch-class duration weights; tonicHints[0..11] optional independent evidence. */
    public static List<Candidate> rank(double[] durations, double[] tonicHints) {
        if(durations==null || durations.length!=12) throw new IllegalArgumentException("pitch classes 0..11 required");
        if(tonicHints!=null&&tonicHints.length!=12) throw new IllegalArgumentException("12 tonic hints required");
        double total=0; for(double x:durations){if(x<0) throw new IllegalArgumentException("negative duration");total+=x;}
        if(total==0)return Collections.emptyList();
        List<Candidate> candidates=new ArrayList<>();
        for(int t=0;t<12;t++) for(int m=0;m<7;m++) {
            boolean[] in=new boolean[12];for(int interval:SCALES[m])in[(t+interval)%12]=true;
            double agree=0;for(int pc=0;pc<12;pc++)agree+=durations[pc]*(in[pc]?1:-1.3);
            double scaleFit=agree/total;
            double tonicWeight=tonicHints==null?0:Math.max(0,tonicHints[t]);
            candidates.add(new Candidate(t,m,scaleFit+0.45*tonicWeight));
        }
        candidates.sort((a,b)->Double.compare(b.score,a.score));
        return candidates;
    }
    /** Same pitch classes alone cannot distinguish relative modes. */
    public static boolean tonicallyAmbiguous(List<Candidate> ranks) {
        if(ranks.size()<2)return true;
        Candidate first=ranks.get(0);
        for(int i=1;i<ranks.size();i++) if(ranks.get(i).score>first.score-0.05 && ranks.get(i).tonic!=first.tonic)return true;
        return false;
    }
    private ModeEvidence(){}
}
