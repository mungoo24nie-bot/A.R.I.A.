package pl.aria.app;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pierwszy autonomiczny, deterministyczny moduł poznawczy ARII.
 * Nie udaje modelu językowego: obsługuje tylko jednoznaczne proste działania.
 * Gdy nie rozpoznaje zadania, zwraca null i przekazuje je modelowi.
 */
final class AriaCognitiveCore {
    private AriaCognitiveCore() {}
    private static final Pattern EXPRESSION=Pattern.compile(
        "^\\s*(?:ile\\s+(?:wynosi|to)|oblicz|policz|jaki\\s+jest\\s+wynik\\s+(?:dzialania|działania))?\\s*"+
        "(-?\\d{1,12}(?:[,.]\\d{1,8})?)\\s*([+*/×÷−-])\\s*"+
        "(-?\\d{1,12}(?:[,.]\\d{1,8})?)\\s*[?!.]?\\s*$",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    static String answer(String question) {
        if(question==null)return null;
        Matcher m=EXPRESSION.matcher(question.trim());
        if(!m.matches())return null;
        try {
            BigDecimal left=new BigDecimal(m.group(1).replace(',','.'));
            BigDecimal right=new BigDecimal(m.group(3).replace(',','.'));
            BigDecimal result;
            switch(m.group(2)){
                case "+":result=left.add(right);break;
                case "-":case "−":result=left.subtract(right);break;
                case "*":case "×":result=left.multiply(right);break;
                case "/":case "÷":
                    if(right.signum()==0)return "Nie można dzielić przez zero.";
                    result=left.divide(right,MathContext.DECIMAL64);break;
                default:return null;
            }
            return result.stripTrailingZeros().toPlainString().replace('.',',');
        }catch(ArithmeticException|NumberFormatException e){
            return null;
        }
    }
}
