package com.h4isenb.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    static class RV {
        Rule r;
        Switch sw;
        Button bStart, bEnd;
        EditText eInterval, eLead, eText;
        CheckBox cSkip;
        TextView preview;
    }

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private final List<RV> views = new ArrayList<>();
    private List<Rule> rules;
    private LinearLayout root;
    private TextView status;
    private boolean ready = false;

    private final TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            refreshPreview();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Напоминалка");
        rules = Store.load(this);

        ScrollView sv = new ScrollView(this);
        root = vbox();
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        sv.addView(root);
        setContentView(sv);

        status = text("", 14);
        root.addView(status);

        addButton("Разрешить уведомления", x -> requestNotifications());
        addButton("Не ограничивать батарею (чтобы работало в фоне)", x -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть настройки батареи", Toast.LENGTH_SHORT).show();
            }
        });
        addButton("Проверить уведомление", x -> {
            collect();
            Scheduler.ensureChannel(this);
            Scheduler.post(this, 1, "Проверка", rules.get(0).text);
        });

        for (Rule r : rules) addCard(r);

        addButton("Сохранить и включить", x -> save());

        ready = true;
        refreshPreview();
        Scheduler.ensureChannel(this);
        Scheduler.scheduleNext(this);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ready) updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else if (Build.VERSION.SDK_INT >= 26) {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } else {
            Toast.makeText(this, "Уведомления включены по умолчанию", Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        collect();
        Store.save(this, rules);
        Store.setLastFired(this, System.currentTimeMillis());
        Scheduler.ensureChannel(this);
        Scheduler.scheduleNext(this);
        updateStatus();
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
    }

    private void addCard(Rule r) {
        final RV v = new RV();
        v.r = r;

        LinearLayout card = vbox();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF1F3F9);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
        clp.topMargin = dp(16);
        root.addView(card, clp);

        LinearLayout head = hbox();
        TextView title = text(r.name, 17);
        title.setTypeface(null, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        v.sw = new Switch(this);
        v.sw.setChecked(r.enabled);
        v.sw.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        head.addView(v.sw);
        card.addView(head);

        LinearLayout row1 = hbox();
        row1.setPadding(0, dp(8), 0, 0);
        row1.addView(text("Начало ", 14));
        v.bStart = timeButton(v, true);
        row1.addView(v.bStart);
        row1.addView(text("   Конец ", 14));
        v.bEnd = timeButton(v, false);
        row1.addView(v.bEnd);
        card.addView(row1);

        LinearLayout row2 = hbox();
        row2.addView(text("Каждые ", 14));
        v.eInterval = number(r.interval);
        row2.addView(v.eInterval, new LinearLayout.LayoutParams(dp(72), WRAP));
        row2.addView(text(" мин, за ", 14));
        v.eLead = number(r.lead);
        row2.addView(v.eLead, new LinearLayout.LayoutParams(dp(72), WRAP));
        row2.addView(text(" мин до", 14));
        card.addView(row2);

        v.cSkip = new CheckBox(this);
        v.cSkip.setText("Не повторять время, когда срабатывает другое расписание (целые часы)");
        v.cSkip.setChecked(r.skipOverlap);
        v.cSkip.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        card.addView(v.cSkip);

        v.eText = new EditText(this);
        v.eText.setHint("Текст уведомления");
        v.eText.setText(r.text);
        v.eText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        v.eText.setMinLines(2);
        card.addView(v.eText, new LinearLayout.LayoutParams(MATCH, WRAP));

        v.preview = text("", 13);
        v.preview.setPadding(0, dp(8), 0, 0);
        card.addView(v.preview);

        views.add(v);
    }

    private Button timeButton(final RV v, final boolean isStart) {
        final Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(Scheduler.fmt(isStart ? v.r.start : v.r.end));
        b.setOnClickListener(x -> {
            int cur = isStart ? v.r.start : v.r.end;
            new TimePickerDialog(this, (tp, h, m) -> {
                if (isStart) v.r.start = h * 60 + m;
                else v.r.end = h * 60 + m;
                b.setText(Scheduler.fmt(h * 60 + m));
                refreshPreview();
            }, cur / 60, cur % 60, true).show();
        });
        return b;
    }

    private EditText number(int value) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setGravity(Gravity.CENTER);
        e.setText(String.valueOf(value));
        e.addTextChangedListener(watcher);
        return e;
    }

    /** Переносит значения из полей в объекты расписаний. */
    private void collect() {
        for (RV v : views) {
            Rule r = v.r;
            r.enabled = v.sw.isChecked();
            r.skipOverlap = v.cSkip.isChecked();
            r.interval = clamp(parse(v.eInterval, r.interval), 1, 720);
            r.lead = clamp(parse(v.eLead, r.lead), 0, 180);
            r.text = v.eText.getText().toString();
        }
    }

    private void refreshPreview() {
        if (!ready) return;
        collect();
        for (int i = 0; i < views.size(); i++) {
            RV v = views.get(i);
            List<Integer> t = Scheduler.times(rules, i);
            if (t.isEmpty()) {
                v.preview.setText("Нет времён: проверьте начало и конец");
                continue;
            }
            StringBuilder sb = new StringBuilder("Времена: ");
            for (int m : t) sb.append(Scheduler.fmt(m)).append("  ");
            if (v.r.lead > 0) sb.append("\nУведомление придёт за ").append(v.r.lead).append(" мин до каждого времени.");
            v.preview.setText(sb.toString().trim());
        }
    }

    private void updateStatus() {
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        StringBuilder s = new StringBuilder(notif
                ? "Уведомления: разрешены"
                : "Уведомления: НЕ разрешены — нажмите кнопку ниже");
        long now = System.currentTimeMillis();
        long best = -1;
        for (Scheduler.Event e : Scheduler.events(rules, now, now + 36L * 60L * Scheduler.MIN)) {
            if (best < 0 || e.at < best) best = e.at;
        }
        if (best > 0) {
            s.append("\nСледующее уведомление: ")
                    .append(new SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(new Date(best)));
        } else {
            s.append("\nЗапланированных уведомлений нет");
        }
        status.setText(s.toString());
    }

    private static int parse(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        return t;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void addButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(8);
        root.addView(b, lp);
    }
}
