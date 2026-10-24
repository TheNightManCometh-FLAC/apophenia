package audio.apophenia.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Input format: CMUdict `WORD(2)  F OW1 N IY0 M` and comment lines beginning with `;;;`.
 * This is a small reference parser for P0 tests, NOT an optimized phone dictionary index.
 * Keep alternate pronunciations; incorrect stress is worse than an honest unknown result.
 */
public final class PronouncingDictionary {
    private final Map<String,List<String>> byWord;
    private PronouncingDictionary(Map<String,List<String>> byWord) { this.byWord=byWord; }
    public static PronouncingDictionary parse(Reader source) throws IOException {
        Map<String,List<String>> entries=new LinkedHashMap<>();
        BufferedReader reader=new BufferedReader(source);
        String line;
        while((line=reader.readLine())!=null) {
            line=line.trim();
            if(line.isEmpty() || line.startsWith(";;;"))continue;
            int space=line.indexOf(' ');
            if(space<1)space=line.indexOf('\t');
            if(space<1)continue;
            String word=line.substring(0,space).toUpperCase(Locale.ROOT);
            word=word.replaceFirst("\\([0-9]+\\)$","");
            String pron=line.substring(space).trim().toUpperCase(Locale.ROOT);
            if(pron.length()==0)continue;
            List<String> variants=entries.get(word);
            if(variants==null){variants=new ArrayList<>();entries.put(word,variants);}
            if(!variants.contains(pron))variants.add(pron);
        }
        Map<String,List<String>> immutable=new LinkedHashMap<>();
        for(Map.Entry<String,List<String>> e:entries.entrySet())
            immutable.put(e.getKey(),Collections.unmodifiableList(e.getValue()));
        return new PronouncingDictionary(Collections.unmodifiableMap(immutable));
    }
    public List<String> pronunciations(String word) {
        if(word==null)return Collections.emptyList();
        List<String> found=byWord.get(word.toUpperCase(Locale.ROOT).replaceAll("^[^A-Z']+|[^A-Z']+$",""));
        return found==null?Collections.<String>emptyList():found;
    }
    public int size(){return byWord.size();}
}
