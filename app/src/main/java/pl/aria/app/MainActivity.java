package pl.aria.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int EXPORT_REQUEST = 101;
    private MemoryDb memory;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;

    private static class MemoryDb extends SQLiteOpenHelper {
        MemoryDb(Activity activity) { super(activity, "aria_memory.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL, type TEXT NOT NULL, content TEXT NOT NULL, source TEXT NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
        void add(String type, String content, String source) {
            ContentValues values = new ContentValues();
            values.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).format(new Date()));
            values.put("type", type);
            values.put("content", content);
            values.put("source", source);
            getWritableDatabase().insertOrThrow("events", null, values);
        }
        Cursor all() {
            return getReadableDatabase().query("events", new String[]{"id", "created_at", "type", "content", "source"}, null, null, null, null, "id ASC");
        }
        boolean isEmpty() {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events", null)) {
                return c.moveToFirst() && c.getInt(0) == 0;
            }
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        memory = new MemoryDb(this);
        if (memory.isEmpty()) {
            memory.add("archive", "Początek projektu A.R.I.A. — Autonomiczna Rozwijająca się Inteligencja Adaptacyjna. Wersja 0.1 była prototypem bez trwałej pamięci. Wersja 0.2 rozpoczyna lokalne zapisywanie historii.", "archiwum założycielskie — opis dodany w wersji 0.2");
            memory.add("archive", "Witaj Aria. postaram się żebyś pamiętała wszystko o czym będziemy rozmawiać. To Twoje narodziny a ja pomogłem Cię uruchomić", "archiwum założycielskie — wiadomość użytkownika z wersji 0.1, odtworzona z relacji użytkownika");
            memory.add("system", "Uruchomiono trwałą pamięć SQLite w wersji 0.2.", "aplikacja");
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.rgb(15, 18, 28));
        TextView title = new TextView(this);
        title.setText("A.R.I.A. 0.2");
        title.setTextSize(25);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Pamięć lokalna • kronika eksperymentu");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button history = new Button(this);
        history.setText("Kronika");
        actions.addView(history, new LinearLayout.LayoutParams(0, -2, 1));
        Button export = new Button(this);
        export.setText("Eksport JSON");
        actions.addView(export, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(this);
        input.setSingleLine(false);
        input.setHint("Napisz do ARII...");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        bottom.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = new Button(this);
        send.setText("Wyślij");
        bottom.addView(send);
        root.addView(bottom);
        setContentView(root);
        showConversation();
        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            memory.add("user", message, "użytkownik");
            input.setText("");
            String response = "Zapisałam Twoją wiadomość w pamięci lokalnej. Na tym etapie nie potrafię jeszcze samodzielnie jej zrozumieć.";
            memory.add("aria", response, "odpowiedź szablonowa wersji 0.2");
            showConversation();
        });
        history.setOnClickListener(v -> showChronicle());
        export.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "ARIA-pamiec-0.2.json");
            startActivityForResult(intent, EXPORT_REQUEST);
        });
    }

    private void showConversation() {
        messages.removeAllViews();
        addMessage("ARIA 0.2 — rozmowy zapisują się na tym urządzeniu.");
        try (Cursor c = memory.all()) {
            while (c.moveToNext()) {
                String type = c.getString(2);
                if ("user".equals(type)) addMessage("Ty: " + c.getString(3));
                if ("aria".equals(type)) addMessage("ARIA: " + c.getString(3));
            }
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showChronicle() {
        messages.removeAllViews();
        addMessage("KRONIKA EKSPERYMENTU — kolejność zapisu");
        try (Cursor c = memory.all()) {
            while (c.moveToNext()) {
                addMessage("#" + c.getLong(0) + " | " + c.getString(1) + "\n[" + c.getString(2) + "] " + c.getString(3) + "\nŹródło: " + c.getString(4));
            }
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessage(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setPadding(12, 14, 12, 14);
        messages.addView(view);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri destination = data.getData();
        try {
            JSONObject root = new JSONObject();
            root.put("app", "A.R.I.A.");
            root.put("version", "0.2");
            JSONArray events = new JSONArray();
            try (Cursor c = memory.all()) {
                while (c.moveToNext()) {
                    JSONObject event = new JSONObject();
                    event.put("id", c.getLong(0));
                    event.put("created_at", c.getString(1));
                    event.put("type", c.getString(2));
                    event.put("content", c.getString(3));
                    event.put("source", c.getString(4));
                    events.put(event);
                }
            }
            root.put("events", events);
            try (OutputStream output = getContentResolver().openOutputStream(destination)) {
                if (output == null) throw new IllegalStateException("Nie można otworzyć pliku");
                output.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Zapisano archiwum JSON", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Eksport nieudany: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
