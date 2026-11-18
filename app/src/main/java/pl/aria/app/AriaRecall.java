package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Deterministyczne wyszukiwanie w wypowiedziach użytkownika; nigdy w odpowiedziach AI. */
final class AriaRecall {
    private AriaRecall() {}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(
        "jestem","jestes","jest","oraz","ktory","ktora","ktore","mozesz","powiedz","prosze",
        "chcialbym","chce","mam","masz","tego","temat","temacie","jakie","kiedy","gdzie",
        "dlaczego","czy","sie","dla","ten","tam","tym","tutaj","moje","twoje","aria"));
    private static Set<String> words(String s){
        Set<String> result=new HashSet<>();
        for(String t:DialogueEngine.normalize(s).split("[^a-z0-9]+"))
            if(t.length()>=4&&!STOP.contains(t))result.add(t);
        return result;
    }
    static String evidence(SQLiteDatabase db,String question,long currentEvent){
        Set<String> q=words(question);
        if(q.isEmpty())return "";
        ArrayList<Hit> hits=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,content FROM events WHERE type='user' AND id<? ORDER BY id DESC LIMIT 350",new String[]{String.valueOf(currentEvent)})){
            while(c.moveToNext()){
                String text=c.getString(1);
                if(text==null||text.length()<8||text.length()>2000)continue;
                Set<String> w=words(text);int common=0;
                for(String t:q)if(w.contains(t))common++;
                if(common==0)continue;
                double score=common/(double)Math.sqrt(Math.max(1,q.size())*Math.max(1,w.size()));
                hits.add(new Hit(c.getLong(0),text,score));
            }
        }catch(Exception ignored){return "";}
        hits.sort((a,b)->Double.compare(b.score,a.score));
        StringBuilder out=new StringBuilder();int count=0;
        for(Hit h:hits){
            if(count>=3||out.length()>750)break;
            out.append("[Wypowiedź użytkownika #").append(h.id).append("] ")
               .append(h.content.substring(0,Math.min(230,h.content.length())).replace('\n',' ')).append('\n');
            count++;
        }
        return out.toString();
    }
    private static final class Hit {
        final long id;final String content;final double score;
        Hit(long id,String content,double score){this.id=id;this.content=content;this.score=score;}
    }
}
