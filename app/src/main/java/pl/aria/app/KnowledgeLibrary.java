package pl.aria.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Html;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

/** Źródłowa biblioteka dokumentów, niezależna od pamięci rozmów. */
final class KnowledgeLibrary {
    private KnowledgeLibrary() {}
    static final int MAX_BYTES=12*1024*1024, MAX_CHARS=350000;
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS library_docs (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,kind TEXT NOT NULL,added_at INTEGER NOT NULL,chars INTEGER NOT NULL,parts INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS library_parts (doc_id INTEGER NOT NULL,part_no INTEGER NOT NULL,body TEXT NOT NULL,PRIMARY KEY(doc_id,part_no))");
        db.execSQL("CREATE INDEX IF NOT EXISTS library_parts_doc ON library_parts(doc_id)");
    }
    static String name(Context ctx,Uri uri) {
        try(Cursor c=ctx.getContentResolver().query(uri,null,null,null,null)) {
            if(c!=null && c.moveToFirst()) { int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(i>=0)return c.getString(i); }
        } catch(Exception ignored) {}
        return "Dokument";
    }
    static byte[] read(InputStream stream,int max)throws IOException {
        if(stream==null)throw new IOException("Nie można otworzyć dokumentu");
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("Dokument przekracza limit rozmiaru");out.write(buf,0,n);}
            return out.toByteArray();
        }
    }
    static String extract(Context ctx,Uri uri,String filename)throws Exception {
        String name=filename.toLowerCase(Locale.ROOT);
        if(name.endsWith(".pdf")) {
            PDFBoxResourceLoader.init(ctx.getApplicationContext());
            byte[] data=read(ctx.getContentResolver().openInputStream(uri),MAX_BYTES);
            try(PDDocument pdf=PDDocument.load(data)) {
                if(pdf.isEncrypted())throw new IOException("PDF zabezpieczony hasłem");
                if(pdf.getNumberOfPages()>300)throw new IOException("Limit 300 stron PDF");
                String text=new PDFTextStripper().getText(pdf);
                if(text.trim().length()<40)throw new IOException("PDF jest skanem bez tekstu; OCR nie jest jeszcze dostępne");
                return text;
            }
        }
        if(name.endsWith(".epub")) {
            StringBuilder all=new StringBuilder();int count=0;
            try(ZipInputStream zin=new ZipInputStream(ctx.getContentResolver().openInputStream(uri))) {
                ZipEntry entry;
                while((entry=zin.getNextEntry())!=null && count++<600) {
                    String path=entry.getName().toLowerCase(Locale.ROOT);
                    if(path.endsWith(".xhtml")||path.endsWith(".html")||path.endsWith(".htm")) {
                        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
                        while((n=zin.read(buf))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Zbyt duży rozdział");out.write(buf,0,n);}
                        all.append('\n').append(html(new String(out.toByteArray(),StandardCharsets.UTF_8)));
                        if(all.length()>MAX_CHARS)throw new IOException("Limit 350 000 znaków tekstu");
                    }
                    zin.closeEntry();
                }
            }
            return all.toString();
        }
        if(name.endsWith(".txt")||name.endsWith(".md")||name.endsWith(".html")||name.endsWith(".htm")) {
            String text=new String(read(ctx.getContentResolver().openInputStream(uri),MAX_BYTES),StandardCharsets.UTF_8);
            return name.endsWith(".html")||name.endsWith(".htm")?html(text):text;
        }
        throw new IOException("Obsługiwane formaty: TXT, MD, HTML, EPUB, PDF tekstowy");
    }
    private static String html(String raw) {
        String clean=raw.replaceAll("(?is)<script\\b[^>]*>.*?</script>"," ").replaceAll("(?is)<style\\b[^>]*>.*?</style>"," ");
        return Html.fromHtml(clean,Html.FROM_HTML_MODE_LEGACY).toString();
    }
    static int add(SQLiteDatabase db,String title,String kind,String raw)throws Exception {
        String text=raw.replace('\u0000',' ').replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\n\n").trim();
        if(text.length()<40)throw new IOException("Za mało tekstu do analizy");
        if(text.length()>MAX_CHARS)throw new IOException("Limit: 350 000 znaków");
        ArrayList<String> parts=new ArrayList<>();int pos=0;
        while(pos<text.length()) {int end=Math.min(pos+850,text.length());if(end<text.length()){int space=text.lastIndexOf(' ',end);if(space>pos+400)end=space;}parts.add(text.substring(pos,end).trim());pos=end;}
        db.beginTransaction();
        try {
            ContentValues doc=new ContentValues();doc.put("title",title);doc.put("kind",kind);doc.put("added_at",System.currentTimeMillis());doc.put("chars",text.length());doc.put("parts",parts.size());
            long id=db.insertOrThrow("library_docs",null,doc);
            for(int i=0;i<parts.size();i++){ContentValues row=new ContentValues();row.put("doc_id",id);row.put("part_no",i+1);row.put("body",parts.get(i));db.insertOrThrow("library_parts",null,row);}
            db.setTransactionSuccessful();return parts.size();
        }finally{db.endTransaction();}
    }
    static int count(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM library_parts",null)){return c.moveToFirst()?c.getInt(0):0;}
    }
    static String overview(SQLiteDatabase db) {
        StringBuilder b=new StringBuilder("Biblioteka ARII\n\n");
        try(Cursor c=db.rawQuery("SELECT id,title,chars,parts FROM library_docs ORDER BY id DESC",null)){
            while(c.moveToNext())b.append('#').append(c.getLong(0)).append("  ").append(c.getString(1)).append("\n   ").append(c.getInt(2)).append(" znaków, ").append(c.getInt(3)).append(" fragmentów\n");
        }
        if(b.length()=="Biblioteka ARII\n\n".length())b.append("Brak materiałów. Dodaj artykuł lub książkę.");
        return b.toString();
    }
    static String titles(SQLiteDatabase db) {
        StringBuilder b=new StringBuilder();
        try(Cursor c=db.rawQuery("SELECT title FROM library_docs ORDER BY id DESC LIMIT 12",null)){while(c.moveToNext())b.append("• ").append(c.getString(0)).append('\n');}
        return b.toString();
    }
    static void remove(SQLiteDatabase db,long id) {
        db.beginTransaction();try{db.delete("library_parts","doc_id=?",new String[]{""+id});db.delete("library_docs","id=?",new String[]{""+id});db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    static String retrieve(SQLiteDatabase db,String question) {
        String q=MemoryContext.norm(question);
        HashSet<String> terms=new HashSet<>();
        for(String w:q.split("[^a-z0-9]+"))if(w.length()>=4&&!STOP.contains(w))terms.add(w);
        if(terms.isEmpty())return "";
        ArrayList<Hit> matches=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT p.part_no,p.body,d.title FROM library_parts p JOIN library_docs d ON d.id=p.doc_id",null)) {
            while(c.moveToNext()) {
                String body=c.getString(1),norm=MemoryContext.norm(body);int score=0;
                for(String t:terms){if(norm.contains(t))score+=2;else if(t.length()>5&&norm.contains(t.substring(0,t.length()-2)))score++;}
                if(score>0)matches.add(new Hit(c.getInt(0),body,c.getString(2),score));
            }
        }
        Collections.sort(matches,(a,b)->Integer.compare(b.score,a.score));
        StringBuilder b=new StringBuilder();
        for(int i=0;i<Math.min(3,matches.size());i++){Hit h=matches.get(i);b.append("Źródło: ").append(h.title).append(", fragment ").append(h.part).append("\n").append(h.body).append("\n");}
        return b.toString();
    }
    private static final HashSet<String> STOP=new HashSet<>(Arrays.asList("jestem","jestes","jakie","ktory","ktora","ktore","powiedz","prosze","aria","mozesz","chcialbym","wiesz","temat","tego","tych","mnie","twoim","swoim","dlaczego","czyli","oraz","jaki","jakim","gdzie","ksiazka","artykul"));
    private static final class Hit {final int part,score;final String body,title;Hit(int p,String b,String t,int s){part=p;body=b;title=t;score=s;}}
}
