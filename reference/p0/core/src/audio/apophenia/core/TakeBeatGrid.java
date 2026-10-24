package audio.apophenia.core;
import java.util.Arrays;
/**
 * Explicit sample positions of clicks DURING capture, not an inferred tempo or a claim
 * that performed notes were on-grid. Keeps tempo changes and irregular meters possible.
 */
public final class TakeBeatGrid {
    public final int sampleRate;
    public final long[] clickFrames;
    public final boolean[] downbeats;
    public TakeBeatGrid(int sampleRate,long[] clickFrames,boolean[] downbeats) {
        if(sampleRate<8000 || clickFrames==null || downbeats==null || clickFrames.length!=downbeats.length)
            throw new IllegalArgumentException("bad metronome grid");
        this.sampleRate=sampleRate;
        this.clickFrames=clickFrames.clone();this.downbeats=downbeats.clone();
        for(int i=1;i<clickFrames.length;i++)if(clickFrames[i]<=clickFrames[i-1])throw new IllegalArgumentException("clicks must be ordered");
    }
    public double[] clickSeconds() {
        double[] seconds=new double[clickFrames.length];
        for(int i=0;i<seconds.length;i++)seconds[i]=clickFrames[i]/(double)sampleRate;
        return seconds;
    }
    /** Returns NaN for tempo-changing or too-short grids. Tempo is the actual click interval. */
    public double constantClickBpm() {
        if(clickFrames.length<3)return Double.NaN;
        long delta=clickFrames[1]-clickFrames[0];
        for(int i=2;i<clickFrames.length;i++)if(Math.abs((clickFrames[i]-clickFrames[i-1])-delta)>1)return Double.NaN;
        return 60.0*sampleRate/delta;
    }
}
