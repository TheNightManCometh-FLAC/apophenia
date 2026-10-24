package audio.apophenia.core;
import java.util.*;
/** Tiny core that consumes CMU ARPABET pronunciations; dictionary itself not embedded in this P0. */
public final class LyricProsody {
    public static int[] stress(String pronunciation) {
        List<Integer> out=new ArrayList<>();
        for(String token:pronunciation.split("\\s+")) {
            if(token.length()>1) {char c=token.charAt(token.length()-1);if(c>='0'&&c<='2')out.add(c-'0');}
        }
        int[] result=new int[out.size()];for(int i=0;i<result.length;i++)result[i]=out.get(i);
        return result;
    }
    /** Compares primary accents of stressed syllables with melody metric weights (0=weak,1=strong). */
    public static double fit(int[] syllableStress,double[] accents) {
        if(syllableStress.length==0||accents.length==0)return 0;
        // DP supports melisma: one syllable held across multiple notes, penalized mildly.
        double[][] dp=new double[syllableStress.length+1][accents.length+1];
        for(double[] row:dp)Arrays.fill(row,-1e9);
        dp[0][0]=0;
        for(int i=0;i<syllableStress.length;i++)for(int j=0;j<accents.length;j++) {
            if(dp[i][j]<-1e8)continue;
            double want=syllableStress[i]==1?1.0:(syllableStress[i]==2?0.65:0.0);
            double value=1.0-Math.abs(want-accents[j]);
            dp[i+1][j+1]=Math.max(dp[i+1][j+1],dp[i][j]+value);
            // Single syllable can span two notes; useful for sustained vowels.
            if(j+2<=accents.length)dp[i+1][j+2]=Math.max(dp[i+1][j+2],dp[i][j]+value+0.2);
        }
        double best=dp[syllableStress.length][accents.length];
        return best<0?0:Math.min(1,best/syllableStress.length);
    }
    /** Last stressed vowel through end; approximate perfect-rhyme key using ARPABET. */
    public static String rhymeTail(String pronunciation) {
        String[] p=pronunciation.trim().split("\\s+"); int start=-1;
        for(int i=0;i<p.length;i++) if(p[i].endsWith("1"))start=i;
        if(start<0) for(int i=0;i<p.length;i++) if(p[i].matches(".*[012]"))start=i;
        if(start<0)return "";
        StringBuilder sb=new StringBuilder();
        for(int i=start;i<p.length;i++) {
            String token=p[i].replaceAll("[012]$","");
            if(sb.length()>0)sb.append(' ');sb.append(token);
        }
        return sb.toString();
    }
    private LyricProsody(){}
}
