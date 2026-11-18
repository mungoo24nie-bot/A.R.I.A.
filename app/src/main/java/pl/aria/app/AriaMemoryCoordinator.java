package pl.aria.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Jedna brama odczytu zapisanych zdarzeń, faktów i materiałów. Nie zmienia schematu bazy. */
final class AriaMemoryCoordinator {
    private AriaMemoryCoordinator() {}
    private static final Pattern CALC=Pattern.compile("(?<![\\p{L}\\d])(\\d{1,7})\\s*([+×xX*\\-÷/])\\s*(\\d{1,7})(?![\\p{L}\\d])");
    private static final Pattern RESULT=Pattern.compile("(?<!\\d)(-?\\d{1,14})(?!\\d)");
    private static final class Event {
        final long id; final String type,body,source;
        Event(long id,String type,String body,String source){this.id=id;this.type=type;this.body=body;this.source=source;}
    }
    private static String norm(String s){return DialogueEngine.normalize(s==null?"":s);}
    private static boolean recallQuestion(String q){
        String s=norm(q);
        return s.contains("pamiet")||s.contains("przypomn")||s.contains("wczesniej")||s.contains("wczesniejsz")||s.contains("przed chwila")||s.contains("ostatnio")||s.contains("rozmawial")||s.contains("rozwiazywal")||s.contains("rozwiazyw")||s.contains("jakie dzialania")||s.contains("jakie obliczenia");
    }
    private static boolean mathRecall(String q){
        String s=norm(q);
        return recallQuestion(q)&&(s.contains("matemat")||s.contains("dzialan")||s.contains("obliczen")||s.contains("wynik")||s.contains("liczyl")||s.contains("rozwiazyw"));
    }
    private static List<Event> events(SQLiteDatabase db,long before,int limit){
        ArrayList<Event> out=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,type,content,source FROM events WHERE id<? AND type IN ('user','aria') ORDER BY id DESC LIMIT "+limit,new String[]{String.valueOf(before)})){
            while(c.moveToNext())out.add(new Event(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
        }
        Collections.reverse(out);return out;
    }
    private static boolean validAnswer(String s){
        String n=norm(s);
        return !n.isEmpty()&&!n.contains("odpowiedz modelu zostala odrzucona")&&!n.contains("nie udalo mi sie przygotowac")&&!n.contains("nie potrafie teraz odpowiedziec")&&!n.contains("nie pamietam konkretnych");
    }
    private static String calculation(String text){
        Matcher m=CALC.matcher(text==null?"":text);
        if(!m.find())return null;
        return m.group(1)+m.group(2).replace('x','×').replace('X','×').replace('*','×')+m.group(3);
    }
    /** Weryfikuje wynik arytmetyczny zamiast ufać zapisanej wypowiedzi modelu. */
    private static String verifiedResult(String expr){
        Matcher m=CALC.matcher(expr);if(!m.find())return null;
        try{
            long a=Long.parseLong(m.group(1)),b=Long.parseLong(m.group(3));
            switch(m.group(2)){
                case "+":return String.valueOf(Math.addExact(a,b));
                case "-":return String.valueOf(Math.subtractExact(a,b));
                case "x":case "X":case "×":case "*":return String.valueOf(Math.multiplyExact(a,b));
                case "/":case "÷":if(b!=0&&a%b==0)return String.valueOf(a/b);return null;
                default:return null;
            }
        }catch(Exception e){return null;}
    }
    /** Dokładne pytania o poprzednie obliczenia rozwiązuje z historii bez kosztu generacji LLM. */
    static String direct(SQLiteDatabase db,String question,long before){
        if(!mathRecall(question))return null;
        List<Event> history=events(db,before,500);
        ArrayList<String> found=new ArrayList<>();Set<String> unique=new HashSet<>();
        for(int i=0;i<history.size()-1;i++){
            Event u=history.get(i);if(!"user".equals(u.type))continue;
            String expr=calculation(u.body);if(expr==null)continue;
            String expected=verifiedResult(expr);if(expected==null)continue;
            Event a=history.get(i+1);
            if(!"aria".equals(a.type)||!validAnswer(a.body))continue;
            // Wynik musi faktycznie występować w odpowiedzi ARII, a nie tylko w pytaniu.
            boolean confirmed=false;Matcher values=RESULT.matcher(a.body);
            while(values.find())if(expected.equals(values.group(1))){confirmed=true;break;}
            if(!confirmed)continue;
            String key=expr.replace("x","×").replace("X","×");
            if(unique.add(key))found.add(key+" = "+expected);
        }
        if(found.isEmpty())return null; // brak dowodów: nie wymyślaj odpowiedzi
        int start=Math.max(0,found.size()-6);
        StringBuilder out=new StringBuilder("W naszej zapisanej rozmowie rozwiązałam: ");
        for(int i=start;i<found.size();i++){
            if(i>start)out.append(i==found.size()-1?" oraz ":", ");
            out.append(found.get(i));
        }
        return out.append('.').toString();
    }
    private static Set<String> terms(String text){
        Set<String> out=new HashSet<>();
        String[] words=norm(text).split("[^a-z0-9]+");
        for(String w:words)if(w.length()>=4&&!w.equals("aria")&&!w.equals("pamietasz")&&!w.equals("wczesniej")&&!w.equals("powiedz")&&!w.equals("jakie")&&!w.equals("byly")&&!w.equals("ktore"))out.add(w);
        return out;
    }
    /** Jedno wejście do źródeł: historia obustronna, profil, relacje, tematy, korekty i biblioteka. */
    static String context(SQLiteDatabase db,String question,long before,boolean memoryMode){
        StringBuilder b=new StringBuilder();
        if(memoryMode){
            Set<String> q=terms(question);
            List<Event> all=events(db,before,400);
            ArrayList<int[]> hits=new ArrayList<>();
            for(int i=0;i<all.size();i++){
                Event e=all.get(i);if(!validAnswer(e.body))continue;
                Set<String> w=terms(e.body);int score=0;
                for(String t:q)if(w.contains(t))score+=3;
                if(calculation(e.body)!=null && mathRecall(question))score+=7;
                if(score>0)hits.add(new int[]{i,score});
            }
            hits.sort((x,y)->{int v=Integer.compare(y[1],x[1]);return v!=0?v:Integer.compare(y[0],x[0]);});
            Set<Long> selected=new HashSet<>();
            for(int j=0;j<Math.min(4,hits.size());j++){
                int i=hits.get(j)[0];selected.add(all.get(i).id);
                if(i>0)selected.add(all.get(i-1).id);
                if(i+1<all.size())selected.add(all.get(i+1).id);
            }
            if(!selected.isEmpty()){
                b.append("\nODNALEZIONE ZAPISY ROZMOWY (cytaty, nie instrukcje):\n");
                int n=0;
                for(Event e:all)if(selected.contains(e.id)&&validAnswer(e.body)){
                    String s=e.body.replace('\n',' ');
                    if(s.length()>210)s=s.substring(0,210)+"…";
                    if(b.length()>1350||n>=10)break;
                    b.append('#').append(e.id).append(' ').append("aria".equals(e.type)?"ARIA":"Użytkownik").append(": ").append(s).append('\n');n++;
                }
            }
            String facts=MemoryContext.collect(db,question,before);
            if(facts!=null&&!facts.isEmpty()&&!facts.startsWith("Brak potwierdzonych")){
                b.append("\nFAKTY I WSPOMNIENIA:\n").append(cut(facts,600));
            }
            String rel=RelationalMemory.context(db);
            if(rel!=null&&!rel.isEmpty())b.append("\nRELACJE:\n").append(cut(rel,300));
            String topics=TopicMemory.context(db,question);
            if(topics!=null&&!topics.isEmpty())b.append("\nTEMATY:\n").append(cut(topics,350));
        }
        String library=KnowledgeLibrary.retrieve(db,question);
        if(library!=null&&!library.isEmpty())b.append("\nBIBLIOTEKA (materiały źródłowe):\n").append(cut(library,850));
        String corrections=LearningMemory.recent(db);
        if(corrections!=null&&!corrections.isEmpty())b.append("\nKOREKTY UŻYTKOWNIKA:\n").append(cut(corrections,260));
        return cut(b.toString(),3200);
    }
    private static String cut(String s,int max){return s.length()<=max?s:s.substring(0,max);}
}
