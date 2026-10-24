import audio.apophenia.core.*;
import java.util.*;
import java.io.*;
/** Fully synthetic tests only; do NOT infer microphone detection accuracy from PASS. */
public class ResearchPrototypesTest {
    static int checks=0;
    static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static void near(double expected,double got,double tol,String why){ok(Math.abs(expected-got)<tol,why+": expected="+expected+", got="+got);}
    static float[] tone(double hz,int sampleRate,int size,double overtone) {
        float[] x=new float[size];
        for(int i=0;i<size;i++)x[i]=(float)(0.65*Math.sin(2*Math.PI*hz*i/sampleRate)+overtone*Math.sin(4*Math.PI*hz*i/sampleRate));
        return x;
    }
    static double[] chord(int... pitchClasses) {
        double[] out=new double[12];for(int pc:pitchClasses)out[pc]=1;return out;
    }
    public static void main(String[] args) throws Exception {
        PitchYin.Result a=PitchYin.estimate(tone(440,12000,2048,.1),12000,40,800);
        ok(a.voiced,"A4 voiced");near(440,a.hz,3,"A4 accuracy on ideal sine");
        PitchYin.Result low=PitchYin.estimate(tone(41.203,12000,2048,.27),12000,27,200);
        ok(low.voiced,"bass E1 voiced");near(41.203,low.hz,1.2,"E1 accuracy on ideal harmonic tone");
        ok(!PitchYin.estimate(new float[2048],12000,30,1000).voiced,"silence must be unvoiced");
        ok(PitchYin.estimate(tone(440,12000,100,0),12000,27,1000).nearestMidi()==-1,"too short frame must not invent pitch");
        ChordTemplates.Guess c=ChordTemplates.guess(chord(0,4,7));
        ok(c.options.get(0).label.equals("C"),"C major synthetic chroma");
        ok(!c.ambiguous,"full triad should outrank missing-tone extensions");
        ChordTemplates.Guess g=ChordTemplates.guess(chord(7,11,2,5));
        ok(g.options.get(0).label.equals("G7"),"G7 synthetic chroma");
        ChordTemplates.Guess ambiguous=ChordTemplates.guess(chord(0,7));
        ok(ambiguous.ambiguous,"root and fifth cannot define triad quality");
        TakeBeatGrid grid=new TakeBeatGrid(48000,new long[]{0,24000,48000,72000},new boolean[]{true,false,false,false});
        near(120,grid.constantClickBpm(),1e-9,"click BPM from captured sample frames");
        ok(Double.isNaN(new TakeBeatGrid(48000,new long[]{0,24000,50000},new boolean[]{true,false,false}).constantClickBpm()),"tempo changes stay explicit");
        PronouncingDictionary dict=PronouncingDictionary.parse(new StringReader(
          ";;; demo only\nLIGHT  L AY1 T\nLIGHT(1)  L AY1 T\nRECORD  R EH1 K ER0 D\nRECORD(1)  R IH0 K AO1 R D\n"));
        ok(dict.size()==2,"alternate pronunciations are not counted as separate words");
        ok(dict.pronunciations("record").size()==2,"alternate pronunciations are preserved");
        ok(dict.pronunciations("unlisted").isEmpty(),"OOV must not invent syllables");
        System.out.println("ResearchPrototypesTest PASS "+checks+" assertions (synthetic signals only)");
    }
}
