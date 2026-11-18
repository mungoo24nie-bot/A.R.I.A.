package pl.aria.app;

import java.text.Normalizer;
import java.util.Locale;

/** Minimalne, deterministyczne odpowiedzi bez uruchamiania GGUF.
 * Nie zastępuje modelu w zwykłej rozmowie i nie udaje wspomnień.
 */
final class AriaConversationBasics {
    private AriaConversationBasics() {}

    static String answer(String input) {
        if(input==null)return null;
        String n=Normalizer.normalize(input.toLowerCase(Locale.forLanguageTag("pl-PL")),
            Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace("ł","l")
            .replaceAll("[!?.…,:;]+"," ").trim().replaceAll("\\s+"," ");
        switch(n) {
            case "czesc": case "hej": case "hej aria": case "czesc aria":
            case "witaj": case "witaj aria": case "dzien dobry":
            case "dobry wieczor": case "siema":
                return "Cześć! Jestem ARIA. O czym chcesz porozmawiać?";
            case "dziekuje": case "dzieki": case "dziekuje aria":
            case "dzieki aria":
                return "Nie ma za co! W czym jeszcze mogę pomóc?";
            case "dobranoc": case "dobranoc aria":
                return "Dobranoc! Do następnej rozmowy.";
            case "do widzenia": case "na razie": case "pa": case "pa aria":
                return "Do zobaczenia!";
            default: return null;
        }
    }
}
