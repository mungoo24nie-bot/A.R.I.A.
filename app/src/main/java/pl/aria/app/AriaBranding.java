package pl.aria.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

/** Akronim: Autonomiczna Rozwijająca się Inteligencja Adaptacyjna. */
final class AriaBranding {
    private AriaBranding() {}
    static SpannableString styledAcronym() {
        String text="Autonomiczna Rozwijająca się Inteligencja Adaptacyjna";
        SpannableString s=new SpannableString(text);
        // Pozycje obliczane po granicach słów, by uniknąć błędów diakrytycznych.
        String[] words={"Autonomiczna","Rozwijająca","Inteligencja","Adaptacyjna"};
        for(String word:words) {
            int start=text.indexOf(word);
            if(start>=0) {
                s.setSpan(new ForegroundColorSpan(Color.rgb(77,230,255)),
                    start,start+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new StyleSpan(Typeface.BOLD),
                    start,start+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return s;
    }
}
