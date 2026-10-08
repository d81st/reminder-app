package com.h4isenb.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // Палитра: индиго, бирюзовый и нейтральные
    private static final int INDIGO = 0xFF4F46E5;
    private static final int INDIGO_DARK = 0xFF3730A3;
    private static final int TEAL = 0xFF14B8A6;
    private static final int INK = 0xFF1B1F3B;
    private static final int MUTED = 0xFF6B7094;
    private static final int BG = 0xFFF4F5FB;
    private static final int LINE = 0xFFE3E5F2;
    private static final int TINT = 0xFFE9E8FC;

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    static class RV {
        Rule r;
        LinearLayout card;
        EditText eName, eInterval, eLead, eText;
        Switch sw;
        Button bStart, bEnd;
        CheckBox cSkip;
        FlowLayout chips;
        TextView leadNote;
    }

    private final List<RV> views = new ArrayList<>();
    private List<Rule> rules;
    private LinearLayout cards;
    private ScrollView scroll;
    private TextView status, emptyHint;
    private Button bPerm;
    private boolean ready = false;
    private boolean dirty = false;

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
        rules = Store.load(this);

        LinearLayout page = vbox();
        page.setBackgroundColor(BG);
        setContentView(page);

        // Шапка с градиентом и статусом
        LinearLayout header = vbox();
        GradientDrawable hbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{INDIGO_DARK, INDIGO});
        float r = dp(24);
        hbg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        header.setBackground(hbg);
        header.setPadding(dp(20), dp(24), dp(20), dp(22));
        TextView title = text("Напоминалка", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title);
        status = text("", 14, 0xDDFFFFFF);
        status.setPadding(0, dp(6), 0, 0);
        header.addView(status);
        page.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LinearLayout body = vbox();
        body.setPadding(dp(16), dp(6), dp(16), dp(32));
        scroll.addView(body);

        // Карточка настройки телефона
        LinearLayout setup = cardView();
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(MATCH, WRAP);
        slp.topMargin = dp(14);
        body.addView(setup, slp);

        bPerm = button("Разрешить уведомления", true);
        bPerm.setOnClickListener(x -> requestNotifications());
        setup.addView(bPerm, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout setupRow = hbox();
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        srlp.topMargin = dp(8);
        setup.addView(setupRow, srlp);
        Button bBattery = button("Работа в фоне", false);
        bBattery.setOnClickListener(x -> openBatterySettings());
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        half1.rightMargin = dp(4);
        setupRow.addView(bBattery, half1);
        Button bTest = button("Проверить", false);
        bTest.setOnClickListener(x -> sendTest());
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        half2.leftMargin = dp(4);
        setupRow.addView(bTest, half2);

        TextView note = text("«Работа в фоне»: разрешите приложению не ограничивать батарею, "
                + "тогда уведомления приходят вовремя, даже когда экран выключен.", 12, MUTED);
        note.setPadding(0, dp(8), 0, 0);
        setup.addView(note);

        emptyHint = text("Пока нет напоминаний. Нажмите «Новое напоминание».", 15, MUTED);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, dp(24), 0, dp(8));
        body.addView(emptyHint);

        cards = vbox();
        body.addView(cards, new LinearLayout.LayoutParams(MATCH, WRAP));
        for (Rule rule : rules) addCard(rule);

        Button bAdd = button("+  Новое напоминание", false);
        bAdd.setOnClickListener(x -> addNew());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(MATCH, WRAP);
        alp.topMargin = dp(16);
        body.addView(bAdd, alp);

        Button bSave = button("Сохранить и включить", true);
        bSave.setOnClickListener(x -> {
            persist(true);
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams save = new LinearLayout.LayoutParams(MATCH, WRAP);
        save.topMargin = dp(8);
        body.addView(bSave, save);

        ready = true;
        redraw();
        dirty = false;
        Scheduler.ensureChannel(this);
        Scheduler.scheduleNext(this);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ready) {
            redraw();
            updateStatus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ready && dirty) persist(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }

    // ---------- действия ----------

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

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть настройки батареи", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendTest() {
        collect();
        Scheduler.ensureChannel(this);
        String t = rules.isEmpty() || rules.get(0).text.trim().isEmpty() ? "Тестовое уведомление" : rules.get(0).text;
        Scheduler.post(this, 1, "Проверка", t);
    }

    private void addNew() {
        Rule r = new Rule("Новое напоминание", 9 * 60, 17 * 60, 60, false, "Текст напоминания");
        rules.add(r);
        addCard(r);
        refreshPreview();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void confirmDelete(final RV v) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить напоминание?")
                .setMessage(v.eName.getText().toString())
                .setPositiveButton("Удалить", (dlg, which) -> {
                    cards.removeView(v.card);
                    views.remove(v);
                    rules.remove(v.r);
                    persist(true);
                    redraw();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /** Сохраняет в базу и пересчитывает будильник. explicit = нажали «Сохранить» или удалили карточку. */
    private void persist(boolean explicit) {
        collect();
        Store.save(this, rules);
        if (explicit) Store.setLastFired(this, System.currentTimeMillis());
        Scheduler.ensureChannel(this);
        Scheduler.scheduleNext(this);
        dirty = false;
        updateStatus();
    }

    // ---------- карточка напоминания ----------

    private void addCard(Rule r) {
        final RV v = new RV();
        v.r = r;

        LinearLayout card = cardView();
        v.card = card;
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
        clp.topMargin = dp(14);
        cards.addView(card, clp);

        // Название и переключатель
        LinearLayout head = hbox();
        v.eName = field(r.name, "Название");
        v.eName.setBackground(null);
        v.eName.setPadding(0, 0, dp(8), 0);
        v.eName.setTextSize(17);
        v.eName.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(v.eName, new LinearLayout.LayoutParams(0, WRAP, 1f));
        v.sw = new Switch(this);
        v.sw.setChecked(r.enabled);
        v.sw.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        head.addView(v.sw);
        card.addView(head);

        // Начало и конец
        LinearLayout timeRow = hbox();
        timeRow.setPadding(0, dp(10), 0, 0);
        timeRow.addView(text("С", 14, MUTED));
        v.bStart = timeButton(v, true);
        LinearLayout.LayoutParams tb1 = new LinearLayout.LayoutParams(WRAP, WRAP);
        tb1.leftMargin = dp(8);
        timeRow.addView(v.bStart, tb1);
        TextView to = text("до", 14, MUTED);
        to.setPadding(dp(12), 0, 0, 0);
        timeRow.addView(to);
        v.bEnd = timeButton(v, false);
        LinearLayout.LayoutParams tb2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        tb2.leftMargin = dp(8);
        timeRow.addView(v.bEnd, tb2);
        card.addView(timeRow);

        // Интервал и «за сколько минут»
        LinearLayout numRow = hbox();
        numRow.setPadding(0, dp(10), 0, 0);
        numRow.setGravity(Gravity.TOP);
        LinearLayout colA = vbox();
        colA.addView(text("Каждые, мин", 12, MUTED));
        v.eInterval = field(String.valueOf(r.interval), "60");
        v.eInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
        colA.addView(v.eInterval, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams ca = new LinearLayout.LayoutParams(0, WRAP, 1f);
        ca.rightMargin = dp(6);
        numRow.addView(colA, ca);
        LinearLayout colB = vbox();
        colB.addView(text("Предупредить за, мин", 12, MUTED));
        v.eLead = field(String.valueOf(r.lead), "0");
        v.eLead.setInputType(InputType.TYPE_CLASS_NUMBER);
        colB.addView(v.eLead, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(0, WRAP, 1f);
        cb.leftMargin = dp(6);
        numRow.addView(colB, cb);
        card.addView(numRow);

        v.cSkip = new CheckBox(this);
        v.cSkip.setText("Пропускать время, когда срабатывает другое напоминание");
        v.cSkip.setTextSize(13);
        v.cSkip.setTextColor(MUTED);
        v.cSkip.setChecked(r.skipOverlap);
        v.cSkip.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        LinearLayout.LayoutParams sk = new LinearLayout.LayoutParams(MATCH, WRAP);
        sk.topMargin = dp(6);
        card.addView(v.cSkip, sk);

        // Текст
        v.eText = field(r.text, "Текст уведомления");
        v.eText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        v.eText.setMinLines(2);
        v.eText.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams tx = new LinearLayout.LayoutParams(MATCH, WRAP);
        tx.topMargin = dp(8);
        card.addView(v.eText, tx);

        // Времена чипами
        v.chips = new FlowLayout(this, dp(6), dp(6));
        LinearLayout.LayoutParams ch = new LinearLayout.LayoutParams(MATCH, WRAP);
        ch.topMargin = dp(12);
        card.addView(v.chips, ch);

        // Подпись и удаление
        LinearLayout foot = hbox();
        foot.setPadding(0, dp(8), 0, 0);
        v.leadNote = text("", 12, MUTED);
        foot.addView(v.leadNote, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView del = text("Удалить", 14, MUTED);
        del.setTypeface(Typeface.DEFAULT_BOLD);
        del.setPadding(dp(12), dp(8), 0, dp(8));
        del.setOnClickListener(x -> confirmDelete(v));
        foot.addView(del);
        card.addView(foot);

        views.add(v);
    }

    private Button timeButton(final RV v, final boolean isStart) {
        final Button b = button(Scheduler.fmt(isStart ? v.r.start : v.r.end), false);
        b.setMinWidth(dp(84));
        b.setMinimumWidth(dp(84));
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

    // ---------- данные и отрисовка ----------

    /** Переносит значения из полей в объекты напоминаний. */
    private void collect() {
        for (RV v : views) {
            Rule r = v.r;
            r.name = v.eName.getText().toString().trim();
            if (r.name.isEmpty()) r.name = "Напоминание";
            r.enabled = v.sw.isChecked();
            r.skipOverlap = v.cSkip.isChecked();
            r.interval = clamp(parse(v.eInterval, r.interval), 1, 720);
            r.lead = clamp(parse(v.eLead, r.lead), 0, 180);
            r.text = v.eText.getText().toString();
        }
    }

    /** Изменение пользователем: помечаем «нужно сохранить» и перерисовываем. */
    private void refreshPreview() {
        if (!ready) return;
        dirty = true;
        redraw();
    }

    private void redraw() {
        collect();
        emptyHint.setVisibility(views.isEmpty() ? View.VISIBLE : View.GONE);

        Calendar cal = Calendar.getInstance();
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        for (int i = 0; i < views.size(); i++) {
            RV v = views.get(i);
            List<Integer> t = Scheduler.times(rules, i);
            v.chips.removeAllViews();
            v.card.setAlpha(v.r.enabled ? 1f : 0.55f);

            if (t.isEmpty()) {
                v.chips.addView(chip("Нет времён: проверьте начало и конец", TINT, MUTED));
            } else {
                int next = -1;
                for (int m : t) {
                    if (m - v.r.lead > nowMin) {
                        next = m;
                        break;
                    }
                }
                for (int m : t) {
                    boolean past = m - v.r.lead <= nowMin;
                    TextView c;
                    if (v.r.enabled && m == next) c = chip(Scheduler.fmt(m), TEAL, Color.WHITE);
                    else c = chip(Scheduler.fmt(m), TINT, INDIGO);
                    if (past) c.setAlpha(0.45f);
                    v.chips.addView(c);
                }
            }
            v.leadNote.setText(v.r.lead > 0
                    ? "Придёт за " + v.r.lead + " мин до каждого времени"
                    : "");
        }
    }

    private void updateStatus() {
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        bPerm.setVisibility(notif ? View.GONE : View.VISIBLE);

        long now = System.currentTimeMillis();
        long best = -1;
        for (Scheduler.Event e : Scheduler.events(Store.load(this), now, now + 36L * 60L * Scheduler.MIN)) {
            if (best < 0 || e.at < best) best = e.at;
        }
        StringBuilder s = new StringBuilder();
        if (best > 0) {
            s.append("Следующее: ")
                    .append(new SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(new Date(best)));
        } else {
            s.append("Запланированных уведомлений нет");
        }
        s.append(notif ? "\nУведомления разрешены" : "\nУведомления не разрешены");
        status.setText(s.toString());
    }

    // ---------- вспомогательные ----------

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

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView chip(String s, int bg, int fg) {
        TextView t = text(s, 13, fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(rounded(bg, 16, 0));
        t.setPadding(dp(11), dp(5), dp(11), dp(5));
        return t;
    }

    private EditText field(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setBackground(rounded(BG, 12, LINE));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.addTextChangedListener(watcher);
        return e;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : INDIGO);
        b.setBackground(rounded(primary ? INDIGO : TINT, 12, 0));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(44));
        b.setMinimumHeight(dp(44));
        return b;
    }

    private LinearLayout cardView() {
        LinearLayout l = vbox();
        l.setBackground(rounded(Color.WHITE, 18, LINE));
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        return l;
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
}
