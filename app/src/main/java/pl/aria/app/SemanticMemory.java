package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.Normalizer;
import java.util.*;

/** Wyszukiwanie oparte na zgodności pojęć i kontynuacjach rozmowy.
 * To heurystyka semantyczna, nie model embeddingowy.
 */
final class SemanticMemory {
    private SemanticMemory() {}
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "aria","pamietasz","pamiet","pamietam","wiesz","wiem","moim","moj","moja",
        "moje","twoje","twoim","jak","jaki","jakie","czy","kiedy","gdzie","co",
        "powiedz","przypomnij","informacje","temat","mnie","sie","jest","byl",
        "byla","mam","ktory","ktora","ktore","oraz","tego","tym","wczesniej"));
    private static String normalize(String s) {
        return Normalizer.normalize(s.toLowerCase(new Locale("pl","PL")),
            Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace("ł","l");
    }
    private static String stem(String word) {
        if(word.startsWith("samochod")) return "samochod";
        if(word.startsWith("pierwsz")) return "pierwsz";
        if(word.startsWith("skuter")) return "skuter";
        if(word.startsWith("praw") && word.contains("jazd")) return "prawo_jazdy";
        if(word.startsWith("rozmow")) return "rozmow";
        if(word.startsWith("slow")) return "slow";
        if(word.length()>=8) return word.substring(0,word.length()-2);
        if(word.length()>=6) return word.substring(0,word.length()-1);
        return word;
    }
    private static Set<String> terms(String s) {
        Set<String> out=new HashSet<>();
        for(String w:normalize(s).split("[^a-z0-9]+")) {
            if(w.length()>=4 && !STOP.contains(w)) out.add(stem(w));
        }
        return out;
    }
    private static class Item {
        final long id; final String text; final Set<String> words; final boolean question;
        Item(long id,String text) {
            this.id=id;this.text=text;this.words=terms(text);
            String n=normalize(text.trim());
            question=text.contains("?") || n.startsWith("co ") || n.startsWith("jak ")
                || n.startsWith("czy ") || n.startsWith("aria,");
        }
    }
    static String evidence(SQLiteDatabase db,String question,long excludeId) {
        List<Item> all=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,content FROM events WHERE type='user' AND id<? ORDER BY id",
                new String[]{String.valueOf(excludeId>0?excludeId:Long.MAX_VALUE)})) {
            while(c.moveToNext()) all.add(new Item(c.getLong(0),c.getString(1)));
        }
        Set<String> query=terms(question);
        if(query.isEmpty()) return "";
        // Nie dopuszczamy dopasowania tylko do słowa "pierwszy" przy pytaniu o samochód.
        List<Item> anchors=new ArrayList<>();
        for(Item item:all) {
            if(item.question) continue;
            int common=0;
            for(String w:query) if(item.words.contains(w)) common++;
            if(common>=Math.min(2,query.size())) anchors.add(item);
        }
        anchors.sort((a,b)->Long.compare(b.id,a.id));
        List<Item> selected=new ArrayList<>();
        for(Item anchor:anchors) {
            if(selected.size()>=5) break;
            selected.add(anchor);
            int index=all.indexOf(anchor);
            // Kontynuacje: "to był...", "był to...", "w kolorze...", "razem z..."
            for(int j=index+1;j<all.size() && j<=index+2;j++) {
                Item next=all.get(j);
                if(next.id-anchor.id>5 || next.question) break;
                String n=normalize(next.text.trim());
                if(n.startsWith("to byl") || n.startsWith("byl to") ||
                   n.startsWith("to jest") || n.startsWith("w kolorze") ||
                   n.startsWith("razem z") || n.startsWith("w domu") ||
                   n.startsWith("mial ") || n.startsWith("byla to")) {
                    if(!selected.contains(next)) selected.add(next);
                } else break;
            }
        }
        selected.sort(Comparator.comparingLong(x->x.id));
        StringBuilder out=new StringBuilder();
        for(Item item:selected) {
            if(out.length()>950) break;
            String t=item.text.replace('\n',' ').trim();
            if(t.length()>220)t=t.substring(0,220);
            out.append("#").append(item.id).append(": ").append(t).append("\n");
        }
        return out.toString();
    }
}
