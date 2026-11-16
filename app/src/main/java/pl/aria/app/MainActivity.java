
package pl.aria.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private LinearLayout messages;
    private EditText input;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.rgb(15, 18, 28));

        TextView title = new TextView(this);
        title.setText("A.R.I.A.");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Autonomiczna Rozwijająca się Inteligencja Adaptacyjna");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages);

        root.addView(scroll, new LinearLayout.LayoutParams(
            -1, 0, 1
        ));

        TextView welcome = new TextView(this);
        welcome.setText("ARIA 0.1\nPierwsze uruchomienie.");
        welcome.setTextColor(Color.WHITE);
        welcome.setTextSize(17);
        welcome.setPadding(10, 20, 10, 20);
        messages.addView(welcome);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);

        input = new EditText(this);
        input.setHint("Napisz do ARII...");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        bottom.addView(input, new LinearLayout.LayoutParams(
            0, -2, 1
        ));

        Button send = new Button(this);
        send.setText("Wyślij");
        bottom.addView(send);

        root.addView(bottom);
        setContentView(root);

        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();

            if (!message.isEmpty()) {
                addMessage("Ty: " + message);
                input.setText("");
                addMessage("ARIA: Odebrałam wiadomość.");
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void addMessage(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setPadding(12, 14, 12, 14);
        messages.addView(view);
    }
}
