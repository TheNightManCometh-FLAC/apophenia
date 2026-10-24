import audio.apophenia.core.*;
import java.util.*;
public class CoreSmoke {
    static int count=0;
    static void ok(boolean pass,String label){count++;if(!pass)throw new AssertionError(label);}
    static void near(double expected,double actual,double err,String label){ok(Math.abs(expected-actual)<=err,label+" expected="+expected+" got="+actual);}
    public static void main(String[] args) {
        // Beat intervals at 92 BPM with syncopated eighths.
        double[] onsets={0,60d/92,120d/92,180d/92,240d/92,300d/92,360d/92,420d/92};
        List<TempoCandidates.Candidate> t=TempoCandidates.estimate(onsets);
        ok(!t.isEmpty(),"regular onsets must produce candidates");
        ok(t.stream().anyMatch(x->Math.abs(x.bpm-92)<1),"should include ~92 BPM candidate, even with half/double uncertainty");
        ok(TempoCandidates.estimate(new double[]{0,0.2,0.6}).isEmpty(),"short onsets cannot establish tempo");
        double[] d=new double[12];for(int pc:new int[]{0,2,4,5,7,9,11})d[pc]=1;
        List<ModeEvidence.Candidate> m=ModeEvidence.rank(d,null);
        ok(m.size()==84,"7 modes x 12 roots, no unique answer forced");
        ok(ModeEvidence.tonicallyAmbiguous(m),"C Ionian and D Dorian share notes; with no tonic evidence ambiguous");
        double[] hints=new double[12];hints[2]=1;
        List<ModeEvidence.Candidate> preferred=ModeEvidence.rank(d,hints);
        ok(preferred.get(0).name.equals("D Dorian"),"D tonic evidence favors D Dorian on C major collection");
        ok(Arrays.equals(new int[]{1,0},LyricProsody.stress("M EH1 L AH0")),"phonetic stresses extracted");
        ok(LyricProsody.rhymeTail("K AE1 T").equals(LyricProsody.rhymeTail("B AE1 T")),"cat/bat rhyme");
        ok(LyricProsody.fit(new int[]{1,0},new double[]{1,0})>LyricProsody.fit(new int[]{1,0},new double[]{0,1}),"prosody favors strong lyric on strong beat");
        System.out.println("CoreSmoke PASS "+count+" assertions | synthetic inputs only, not real audio accuracy tests");
    }
}
