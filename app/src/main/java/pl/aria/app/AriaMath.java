package pl.aria.app;

import java.util.regex.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Arytmetyka dat jest deterministyczna i nie przypisuje wyniku dokumentowi. */
final class AriaMath {
    private AriaMath(){}
    private static final Pattern FULL=Pattern.compile("(?iu)(\\d{1,2})\\s+(stycznia|lutego|marca|kwietnia|maja|czerwca|lipca|sierpnia|września|wrzesnia|października|pazdziernika|listopada|grudnia)\\s+(\\d{4})");
    private static int month(String s){
        String[] names={"stycznia","lutego","marca","kwietnia","maja","czerwca","lipca","sierpnia","wrzesnia","pazdziernika","listopada","grudnia"};
        String v=MemoryContext.norm(s);
        for(int i=0;i<names.length;i++)if(names[i].equals(v))return i<=8?i+1:i==9?10:i==10?11:12;
        return 0;
    }
    static String answer(String q){
        String n=MemoryContext.norm(q);
        if(!(n.contains("ile lat")||n.contains("lat minelo")||n.contains("roznic")||n.contains("ile czasu")))return null;
        Matcher m=FULL.matcher(q);LocalDate[] dates=new LocalDate[2];int i=0;
        while(m.find()&&i<2){
            try{dates[i++]=LocalDate.of(Integer.parseInt(m.group(3)),month(m.group(2)),Integer.parseInt(m.group(1)));}
            catch(Exception e){return null;}
        }
        if(i<2)return null;
        long years=ChronoUnit.YEARS.between(dates[0],dates[1]);
        return "Od "+dates[0]+" do "+dates[1]+" upłynęło "+years+" pełnych lat. To wynik obliczenia kalendarzowego, a nie cytat z dokumentu.";
    }
}
