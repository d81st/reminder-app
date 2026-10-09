package com.h4isenb.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

    private static final int INDIGO = 0xFF4F46E5;
    private static final int INDIGO_DARK = 0xFF3730A3;
    private static final int GREEN = 0xFF22C55E;
    private static final int ROSE = 0xFFF43F5E;
    /** Цвета карточек: индиго, бирюзовый, розовый, янтарный, зелёный, голубой. */
    private static final int[] ACCENTS = {INDIGO, 0xFF14B8A6, ROSE, 0xFFF59E0B, GREEN, 0xFF0EA5E9};

    private static final int REQ_NOTIF = 1;
    private static final int REQ_NOTIF_SET = 2;
    private static final int REQ_EXACT = 3;
    private static final int REQ_BATT = 4;
    private static final int REQ_SOUND = 5;
    private static final int MAX_RULES = 30;
    private static final int MAX_CHIPS = 24;

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    // палитра выбранной темы
    private boolean dark;
    private int cBg, cCard, cInk, cMuted, cLine, cTint, cTintText;

    static class RV {
        Rule r;
        LinearLayout card, detail, colors;
        View dot;
        TextView title, sub, chevron, leadNote, repNote;
        Switch sw;
        EditText eName, eInterval, eLead, eText, eRepEvery, eRepCount;
        Button bStart, bEnd;
        CheckBox cSkip;
        FlowLayout chips;
        boolean expanded;
    }

    static class PR {
        LinearLayout row;
        TextView st;
        Button btn;
    }

    private final List<RV> views = new ArrayList<>();
    private List<Rule> rules;

    private LinearLayout page, bottom, cards, logBox;
    private ScrollView scrollRem, scrollLog, scrollSet;
    private TextView status, warn, emptyHint, themeBtn, soundLabel;
    private ImageView fab;
    private boolean keyboardOpen = false;
    private Ringtone preview;
    private TextView[] tabs = new TextView[3];
    private PR pNotif, pBatt, pExact;
    private int currentTab = 0;

    private boolean ready = false;
    private boolean dirty = false;
    private int permStep = 0;
    private boolean chainActive = false;
    private boolean fromUser = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveTask = () -> {
        if (dirty) persist();
    };

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

    // =====================================================================
    // жизненный цикл
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        dark = Store.isDark(this);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        handleDoneIntent(getIntent());
        initPalette();
        rules = Store.load(this);

        Window w = getWindow();
        w.setNavigationBarColor(cCard);
        if (!dark && Build.VERSION.SDK_INT >= 26) {
            View dv = w.getDecorView();
            dv.setSystemUiVisibility(dv.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        page = vbox();
        page.setBackgroundColor(cBg);
        setContentView(page);

        buildHeader();

        FrameLayout frame = new FrameLayout(this);
        page.addView(frame, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        scrollRem = newScroll();
        scrollLog = newScroll();
        scrollSet = newScroll();
        frame.addView(scrollRem, new FrameLayout.LayoutParams(MATCH, MATCH));
        frame.addView(scrollLog, new FrameLayout.LayoutParams(MATCH, MATCH));
        frame.addView(scrollSet, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Плавающая кнопка «+»: лежит поверх списка, плюс нарисован вектором и стоит ровно по центру
        fab = new ImageView(this);
        fab.setImageResource(R.drawable.ic_add);
        fab.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fab.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(INDIGO);
        fab.setBackground(fabBg);
        fab.setElevation(dp(8));
        fab.setContentDescription("Добавить напоминание");
        fab.setOnClickListener(x -> addNew());
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END);
        flp.setMarginEnd(dp(18));
        flp.bottomMargin = dp(18);
        frame.addView(fab, flp);

        buildRemindersPage();
        buildLogPage();
        buildSettingsPage();
        buildTabBar();

        // Когда открыта клавиатура, нижнюю панель прячем, чтобы не съедала место
        page.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            page.getWindowVisibleDisplayFrame(r);
            int h = page.getRootView().getHeight();
            boolean open = h > 0 && (h - r.bottom) > h * 0.15f;
            keyboardOpen = open;
            bottom.setVisibility(open ? View.GONE : View.VISIBLE);
            updateFab();
        });

        ready = true;
        redraw();
        dirty = false;
        selectTab(0);
        Scheduler.ensureChannel(this);
        Scheduler.scheduleNext(this);
        updateStatus();
        refreshPermRows();

        // При первом открытии (и при каждом новом запуске) сразу просим недостающие разрешения
        if (savedInstanceState == null) page.post(this::startPermissionChain);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDoneIntent(intent);
    }

    /** Тап по уведомлению с повторами означает «увидел»: остальные повторы отменяются. */
    private void handleDoneIntent(Intent intent) {
        if (intent == null) return;
        long rule = intent.getLongExtra("done_rule", 0L);
        long base = intent.getLongExtra("done_base", 0L);
        if (rule > 0 && base > 0) {
            Store.markDone(this, rule, base);
            Scheduler.scheduleNext(this);
            intent.removeExtra("done_rule");
            intent.removeExtra("done_base");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ready) {
            redraw();
            updateStatus();
            refreshPermRows();
            if (currentTab == 1) renderLog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
        handler.removeCallbacks(saveTask);
        if (ready && dirty) persist();
    }

    @Override
    public void onBackPressed() {
        if (currentTab != 0) selectTab(0);
        else super.onBackPressed();
    }

    // =====================================================================
    // разрешения
    // =====================================================================

    private void startPermissionChain() {
        permStep = 0;
        advancePermissions();
    }

    /** По очереди просит: уведомления, точные будильники, работу в фоне. Каждый шаг делается один раз за запуск. */
    private void advancePermissions() {
        while (permStep < 3) {
            int s = permStep++;
            if (s == 0 && !Perms.notificationsOk(this)
                    && (Build.VERSION.SDK_INT >= 33 || Perms.canNag(this, "nagNotifOld", 3))) {
                chainActive = true;
                fromUser = false;
                askNotifications();
                return;
            }
            if (s == 1 && !Perms.exactAlarmOk(this)) {
                chainActive = true;
                askExactAlarm();
                return;
            }
            if (s == 2 && !Perms.batteryOk(this) && Perms.canNag(this, "nagBattery", 3)) {
                chainActive = true;
                askBattery();
                return;
            }
        }
        chainActive = false;
        refreshPermRows();
        updateStatus();
        Scheduler.scheduleNext(this);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            openNotificationSettings();
        }
    }

    private void openNotificationSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 26) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivityForResult(i, REQ_NOTIF_SET);
        } catch (Exception e) {
            afterAsk();
        }
    }

    private void askExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) {
            afterAsk();
            return;
        }
        try {
            startActivityForResult(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())), REQ_EXACT);
        } catch (Exception e) {
            afterAsk();
        }
    }

    private void askBattery() {
        try {
            startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())), REQ_BATT);
        } catch (Exception e) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), REQ_BATT);
            } catch (Exception e2) {
                afterAsk();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF && Build.VERSION.SDK_INT >= 33
                && !Perms.notificationsOk(this)
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                && (fromUser || Perms.canNag(this, "nagNotif", 2))) {
            // Система больше не показывает диалог (отказано насовсем): ведём в настройки уведомлений
            openNotificationSettings();
            return;
        }
        afterAsk();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SOUND) {
            handleSound(resultCode, data);
            return;
        }
        afterAsk();
    }

    private void afterAsk() {
        if (!ready) return;
        refreshPermRows();
        updateStatus();
        Scheduler.scheduleNext(this);
        if (chainActive) {
            chainActive = false;
            advancePermissions();
        }
    }

    // =====================================================================
    // каркас: шапка, вкладки
    // =====================================================================

    private void initPalette() {
        cBg = dark ? 0xFF0F1220 : 0xFFF4F5FB;
        cCard = dark ? 0xFF1A1E33 : 0xFFFFFFFF;
        cInk = dark ? 0xFFECEEFB : 0xFF1B1F3B;
        cMuted = dark ? 0xFF9AA0C4 : 0xFF6B7094;
        cLine = dark ? 0xFF2A2F4A : 0xFFE3E5F2;
        cTint = dark ? 0xFF2A2D5C : 0xFFE9E8FC;
        cTintText = dark ? 0xFFB4AFFF : INDIGO;
    }

    private void buildHeader() {
        LinearLayout header = vbox();
        GradientDrawable hbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{dark ? 0xFF1E1B4B : INDIGO_DARK, dark ? INDIGO_DARK : INDIGO});
        float r = dp(24);
        hbg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        header.setBackground(hbg);
        header.setPadding(dp(20), dp(18), dp(14), dp(18));

        LinearLayout row = hbox();
        TextView title = text("Напоминалка", 24, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        themeBtn = text(dark ? "☀" : "☾", 24, Color.WHITE);
        themeBtn.setPadding(dp(14), dp(4), dp(8), dp(4));
        themeBtn.setOnClickListener(x -> toggleTheme());
        row.addView(themeBtn);
        header.addView(row);

        status = text("", 14, 0xDDFFFFFF);
        status.setPadding(0, dp(4), dp(6), 0);
        header.addView(status);

        warn = text("", 13, 0xFFFFD5DC);
        warn.setPadding(0, dp(2), dp(6), 0);
        warn.setVisibility(View.GONE);
        header.addView(warn);

        page.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private ScrollView newScroll() {
        ScrollView sv = new ScrollView(this);
        LinearLayout body = vbox();
        body.setPadding(dp(16), dp(6), dp(16), dp(24));
        sv.addView(body, new FrameLayout.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private LinearLayout bodyOf(ScrollView sv) {
        return (LinearLayout) sv.getChildAt(0);
    }

    private void buildTabBar() {
        bottom = vbox();
        View line = new View(this);
        line.setBackgroundColor(cLine);
        bottom.addView(line, new LinearLayout.LayoutParams(MATCH, dp(1)));

        LinearLayout bar = hbox();
        bar.setBackgroundColor(cCard);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bottom.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));

        tabs[0] = tabView("Напоминания", 0);
        bar.addView(tabs[0], new LinearLayout.LayoutParams(0, WRAP, 1f));

        tabs[1] = tabView("Журнал", 1);
        bar.addView(tabs[1], new LinearLayout.LayoutParams(0, WRAP, 1f));
        tabs[2] = tabView("Настройки", 2);
        bar.addView(tabs[2], new LinearLayout.LayoutParams(0, WRAP, 1f));

        page.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private TextView tabView(String label, final int index) {
        TextView t = text(label, 12, cMuted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        t.setSingleLine(true);
        t.setOnClickListener(x -> selectTab(index));
        return t;
    }

    private void selectTab(int i) {
        currentTab = i;
        scrollRem.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        scrollLog.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        scrollSet.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        for (int j = 0; j < tabs.length; j++) {
            boolean on = j == i;
            tabs[j].setTextColor(on ? cTintText : cMuted);
            tabs[j].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabs[j].setBackground(on ? rounded(cTint, 14, 0) : null);
        }
        if (i == 1) renderLog();
        if (i == 2) refreshPermRows();
        updateFab();
    }

    /** Кнопка «+» видна только на странице напоминаний и пока не открыта клавиатура. */
    private void updateFab() {
        if (fab != null) fab.setVisibility(currentTab == 0 && !keyboardOpen ? View.VISIBLE : View.GONE);
    }

    private void toggleTheme() {
        handler.removeCallbacks(saveTask);
        if (dirty) persist();
        Store.setDark(this, !dark);
        recreate();
    }

    // =====================================================================
    // страница «Напоминания»
    // =====================================================================

    private void buildRemindersPage() {
        LinearLayout b = bodyOf(scrollRem);
        b.setPadding(dp(16), dp(6), dp(16), dp(96)); // запас снизу, чтобы кнопка «+» не закрывала последнюю карточку
        emptyHint = text("Пока нет напоминаний. Нажмите «+», чтобы создать.", 15, cMuted);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, dp(40), 0, dp(8));
        b.addView(emptyHint);
        cards = vbox();
        b.addView(cards, new LinearLayout.LayoutParams(MATCH, WRAP));
        for (Rule r : rules) addCard(r);
    }

    private void addNew() {
        selectTab(0);
        if (rules.size() >= MAX_RULES) {
            Toast.makeText(this, "Достигнут предел: " + MAX_RULES + " напоминаний", Toast.LENGTH_SHORT).show();
            return;
        }
        Rule r = new Rule("Новое напоминание", 9 * 60, 17 * 60, 60, false, "Текст напоминания");
        r.color = rules.size() % ACCENTS.length;
        rules.add(r);
        RV v = addCard(r);
        setExpanded(v, true);
        refreshPreview();
        scrollRem.post(() -> scrollRem.fullScroll(View.FOCUS_DOWN));
    }

    private RV addCard(Rule r) {
        final RV v = new RV();
        v.r = r;

        LinearLayout card = vbox();
        v.card = card;
        card.setBackground(rounded(cCard, 16, cLine));
        card.setPadding(dp(14), dp(8), dp(14), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
        clp.topMargin = dp(10);
        cards.addView(card, clp);

        // Свёрнутая часть: цвет, название, краткая сводка, переключатель
        LinearLayout top = hbox();
        top.setMinimumHeight(dp(52));
        top.setOnClickListener(x -> setExpanded(v, !v.expanded));
        v.dot = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dlp.rightMargin = dp(10);
        top.addView(v.dot, dlp);

        LinearLayout titles = vbox();
        v.title = text("", 16, cInk);
        v.title.setTypeface(Typeface.DEFAULT_BOLD);
        v.title.setSingleLine(true);
        v.title.setEllipsize(TextUtils.TruncateAt.END);
        v.sub = text("", 12, cMuted);
        v.sub.setSingleLine(true);
        v.sub.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(v.title);
        titles.addView(v.sub);
        top.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));

        v.sw = new Switch(this);
        v.sw.setChecked(r.enabled);
        v.sw.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(WRAP, WRAP);
        swp.leftMargin = dp(8);
        top.addView(v.sw, swp);

        v.chevron = text("▾", 16, cMuted);
        v.chevron.setPadding(dp(10), 0, 0, 0);
        top.addView(v.chevron);
        card.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Раскрывающаяся часть с настройками
        v.detail = vbox();
        v.detail.setVisibility(View.GONE);
        card.addView(v.detail, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout d = v.detail;

        d.addView(label("Название"), labelLp(10));
        v.eName = field(r.name, "Название");
        v.eName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        v.eName.setSingleLine(true);
        d.addView(v.eName, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout timeRow = hbox();
        timeRow.setPadding(0, dp(12), 0, 0);
        timeRow.addView(text("С", 14, cMuted));
        v.bStart = timeButton(v, true);
        LinearLayout.LayoutParams tb1 = new LinearLayout.LayoutParams(WRAP, WRAP);
        tb1.leftMargin = dp(8);
        timeRow.addView(v.bStart, tb1);
        TextView to = text("до", 14, cMuted);
        to.setPadding(dp(14), 0, 0, 0);
        timeRow.addView(to);
        v.bEnd = timeButton(v, false);
        LinearLayout.LayoutParams tb2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        tb2.leftMargin = dp(8);
        timeRow.addView(v.bEnd, tb2);
        d.addView(timeRow);

        LinearLayout numRow = hbox();
        numRow.setGravity(Gravity.TOP);
        numRow.setPadding(0, dp(12), 0, 0);
        LinearLayout colA = vbox();
        colA.addView(label("Каждые, мин"));
        v.eInterval = numberField(String.valueOf(r.interval), "60", 1, 720);
        colA.addView(v.eInterval, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams ca = new LinearLayout.LayoutParams(0, WRAP, 1f);
        ca.rightMargin = dp(6);
        numRow.addView(colA, ca);
        LinearLayout colB = vbox();
        colB.addView(label("Предупредить за, мин"));
        v.eLead = numberField(String.valueOf(r.lead), "0", 0, 180);
        colB.addView(v.eLead, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(0, WRAP, 1f);
        cb.leftMargin = dp(6);
        numRow.addView(colB, cb);
        d.addView(numRow);

        // Повторы: «каждые M минут» и «сколько раз»
        LinearLayout repRow = hbox();
        repRow.setGravity(Gravity.TOP);
        repRow.setPadding(0, dp(12), 0, 0);
        LinearLayout colC = vbox();
        colC.addView(label("Повторять каждые, мин"));
        v.eRepEvery = numberField(String.valueOf(r.repeatEvery), "5", 1, 120);
        colC.addView(v.eRepEvery, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams cc = new LinearLayout.LayoutParams(0, WRAP, 1f);
        cc.rightMargin = dp(6);
        repRow.addView(colC, cc);
        LinearLayout colD = vbox();
        colD.addView(label("Сколько раз (0 = выкл.)"));
        v.eRepCount = numberField(String.valueOf(r.repeatCount), "0", 0, 20);
        colD.addView(v.eRepCount, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams cd = new LinearLayout.LayoutParams(0, WRAP, 1f);
        cd.leftMargin = dp(6);
        repRow.addView(colD, cd);
        d.addView(repRow);

        v.repNote = text("", 12, cMuted);
        v.repNote.setPadding(0, dp(6), 0, 0);
        d.addView(v.repNote);

        v.cSkip = new CheckBox(this);
        v.cSkip.setText("Пропускать время, когда срабатывает другое напоминание");
        v.cSkip.setTextSize(13);
        v.cSkip.setTextColor(cMuted);
        v.cSkip.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{INDIGO, cMuted}));
        v.cSkip.setChecked(r.skipOverlap);
        v.cSkip.setOnCheckedChangeListener((btn, checked) -> refreshPreview());
        LinearLayout.LayoutParams sk = new LinearLayout.LayoutParams(MATCH, WRAP);
        sk.topMargin = dp(6);
        d.addView(v.cSkip, sk);

        d.addView(label("Текст уведомления"), labelLp(8));
        v.eText = field(r.text, "Текст уведомления");
        v.eText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        v.eText.setMinLines(2);
        v.eText.setGravity(Gravity.TOP);
        d.addView(v.eText, new LinearLayout.LayoutParams(MATCH, WRAP));

        d.addView(label("Цвет"), labelLp(12));
        v.colors = hbox();
        for (int i = 0; i < ACCENTS.length; i++) {
            final int idx = i;
            View c = new View(this);
            c.setOnClickListener(x -> {
                v.r.color = idx;
                refreshPreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(12);
            v.colors.addView(c, lp);
        }
        LinearLayout.LayoutParams colorsLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        colorsLp.topMargin = dp(4);
        d.addView(v.colors, colorsLp);

        d.addView(label("Времена"), labelLp(12));
        v.chips = new FlowLayout(this, dp(6), dp(6));
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(MATCH, WRAP);
        chp.topMargin = dp(6);
        d.addView(v.chips, chp);

        LinearLayout foot = hbox();
        foot.setPadding(0, dp(8), 0, 0);
        v.leadNote = text("", 12, cMuted);
        foot.addView(v.leadNote, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView del = text("Удалить", 14, ROSE);
        del.setTypeface(Typeface.DEFAULT_BOLD);
        del.setPadding(dp(12), dp(8), 0, dp(8));
        del.setOnClickListener(x -> confirmDelete(v));
        foot.addView(del);
        d.addView(foot);

        views.add(v);
        return v;
    }

    private void setExpanded(RV v, boolean e) {
        v.expanded = e;
        v.detail.setVisibility(e ? View.VISIBLE : View.GONE);
        v.chevron.setText(e ? "▴" : "▾");
    }

    private void confirmDelete(final RV v) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить напоминание?")
                .setMessage(v.r.name)
                .setPositiveButton("Удалить", (dlg, which) -> {
                    cards.removeView(v.card);
                    views.remove(v);
                    rules.remove(v.r);
                    persist();
                    redraw();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private Button timeButton(final RV v, final boolean isStart) {
        final Button b = button(TimeMath.fmt(isStart ? v.r.start : v.r.end), false);
        b.setMinWidth(dp(84));
        b.setMinimumWidth(dp(84));
        b.setOnClickListener(x -> {
            int cur = isStart ? v.r.start : v.r.end;
            new TimePickerDialog(this, (tp, h, m) -> {
                if (isStart) v.r.start = h * 60 + m;
                else v.r.end = h * 60 + m;
                b.setText(TimeMath.fmt(h * 60 + m));
                refreshPreview();
            }, cur / 60, cur % 60, true).show();
        });
        return b;
    }

    /** Числовое поле: при уходе фокуса значение приводится к допустимому диапазону. */
    private EditText numberField(String value, String hint, final int lo, final int hi) {
        final EditText e = field(value, hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                int n = clamp(parse(e, lo), lo, hi);
                String s = String.valueOf(n);
                if (!s.equals(e.getText().toString())) e.setText(s);
            }
        });
        return e;
    }

    // =====================================================================
    // данные и отрисовка
    // =====================================================================

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
            r.repeatEvery = clamp(parse(v.eRepEvery, r.repeatEvery), 1, 120);
            r.repeatCount = clamp(parse(v.eRepCount, r.repeatCount), 0, 20);
            r.text = v.eText.getText().toString();
            r.color = clamp(r.color, 0, ACCENTS.length - 1);
        }
    }

    /** Изменение пользователем: перерисовать и сохранить через паузу (чтобы не писать в базу на каждую букву). */
    private void refreshPreview() {
        if (!ready) return;
        dirty = true;
        redraw();
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, 700);
    }

    private void persist() {
        handler.removeCallbacks(saveTask);
        try {
            collect();
            Store.save(this, rules);
            // правка расписания не должна «догонять» уже прошедшие времена
            Store.setLastFired(this, Math.max(Store.getLastFired(this), System.currentTimeMillis()));
            Scheduler.ensureChannel(this);
            Scheduler.scheduleNext(this);
            dirty = false;
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось сохранить: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void redraw() {
        collect();
        emptyHint.setVisibility(views.isEmpty() ? View.VISIBLE : View.GONE);

        Calendar cal = Calendar.getInstance();
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        for (RV v : views) {
            Rule r = v.r;
            int idx = rules.indexOf(r);
            if (idx < 0) continue;
            int acc = ACCENTS[r.color];
            List<Integer> t = TimeMath.times(rules, idx);

            v.card.setAlpha(r.enabled ? 1f : 0.6f);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(acc);
            v.dot.setBackground(dot);
            v.title.setText(r.name);
            v.sub.setText(subtitle(r, t, nowMin));
            paintColors(v);

            v.chips.removeAllViews();
            if (t.isEmpty()) {
                v.chips.addView(chip("Нет времён: проверьте начало и конец", cTint, cMuted));
            } else {
                int next = nextTime(t, r.lead, nowMin);
                int shown = 0;
                for (int m : t) {
                    if (shown >= MAX_CHIPS) break;
                    boolean past = m - r.lead <= nowMin;
                    TextView c;
                    if (r.enabled && m == next) c = accentChip(TimeMath.fmt(m), acc, true);
                    else c = accentChip(TimeMath.fmt(m), acc, false);
                    if (past) c.setAlpha(0.45f);
                    v.chips.addView(c);
                    shown++;
                }
                if (t.size() > MAX_CHIPS) v.chips.addView(chip("ещё " + (t.size() - MAX_CHIPS), cTint, cMuted));
            }
            v.leadNote.setText(r.lead > 0 ? "Придёт за " + r.lead + " мин до каждого времени" : "");
            v.repNote.setText(repeatNote(r));
        }
        updateStatus();
    }

    private static int nextTime(List<Integer> t, int lead, int nowMin) {
        for (int m : t) if (m - lead > nowMin) return m;
        return -1;
    }

    /** Пояснение под полями повторов: сколько повторов будет и как их остановить. */
    private static String repeatNote(Rule r) {
        if (r.repeatCount <= 0) return "Повторы выключены. Поставьте число больше 0, чтобы уведомление повторялось.";
        int eff = r.effectiveRepeats();
        if (eff == 0) {
            return "Повторы не помещаются: они должны закончиться раньше следующего времени (интервал "
                    + r.interval + " мин). Уменьшите шаг повтора.";
        }
        String s = "После каждого уведомления ещё " + eff + " " + timesWord(eff) + ", каждые " + r.repeatEvery
                + " мин; последнее через " + (eff * r.repeatEvery) + " мин.";
        if (eff < r.repeatCount) s += " Остальные не помещаются до следующего времени.";
        return s + "\nЧтобы остановить повторы, нажмите «Готово» в уведомлении или смахните его.";
    }

    private static String timesWord(int n) {
        int a = n % 100;
        int b = n % 10;
        if (a >= 11 && a <= 14) return "раз";
        if (b == 1) return "раз";
        if (b >= 2 && b <= 4) return "раза";
        return "раз";
    }

    private String subtitle(Rule r, List<Integer> t, int nowMin) {
        if (t.isEmpty()) return "Нет времён: проверьте начало и конец";
        String range = TimeMath.fmt(r.start) + "–" + TimeMath.fmt(r.end) + " · каждые " + r.interval + " мин"
                + (r.effectiveRepeats() > 0 ? " · ↻" + r.effectiveRepeats() : "");
        if (!r.enabled) return range + " · выключено";
        int next = nextTime(t, r.lead, nowMin);
        return range + (next >= 0 ? " · след. " + TimeMath.fmt(next) : " · завтра " + TimeMath.fmt(t.get(0)));
    }

    private void paintColors(RV v) {
        for (int j = 0; j < v.colors.getChildCount(); j++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(ACCENTS[j]);
            if (j == v.r.color) g.setStroke(dp(3), cInk);
            v.colors.getChildAt(j).setBackground(g);
        }
    }

    private void updateStatus() {
        if (status == null) return;
        long now = System.currentTimeMillis();
        long best = -1;
        for (TimeMath.Event e : TimeMath.events(rules, now, now + 36L * 60L * TimeMath.MIN)) {
            if (e.repeat == 0 && (best < 0 || e.at < best)) best = e.at; // в «Следующем» только основные
        }
        if (best > 0) {
            status.setText("Следующее: "
                    + new SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(new Date(best)));
        } else {
            status.setText("Запланированных уведомлений нет");
        }
        if (!Perms.notificationsOk(this)) {
            warn.setText("Уведомления выключены. Разрешите их во вкладке «Настройки».");
            warn.setVisibility(View.VISIBLE);
        } else {
            warn.setVisibility(View.GONE);
        }
    }

    // =====================================================================
    // страница «Журнал»
    // =====================================================================

    private void buildLogPage() {
        LinearLayout b = bodyOf(scrollLog);
        LinearLayout head = hbox();
        head.setPadding(0, dp(10), 0, 0);
        TextView t = text("Последние уведомления", 17, cInk);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Button clear = button("Очистить", false);
        clear.setOnClickListener(x -> {
            Store.clearLog(this);
            renderLog();
        });
        head.addView(clear);
        b.addView(head);
        logBox = vbox();
        b.addView(logBox, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void renderLog() {
        logBox.removeAllViews();
        List<Store.LogItem> items = Store.loadLog(this, 100);
        if (items.isEmpty()) {
            TextView e = text("Пока пусто. Здесь появятся сработавшие уведомления.", 15, cMuted);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            logBox.addView(e);
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("d MMM, HH:mm", new Locale("ru"));
        for (Store.LogItem it : items) {
            LinearLayout c = vbox();
            c.setBackground(rounded(cCard, 14, cLine));
            c.setPadding(dp(14), dp(10), dp(14), dp(10));
            c.addView(text(f.format(new Date(it.at)), 12, cMuted));
            TextView title = text(it.title, 14, cInk);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(title);
            if (it.text != null && !it.text.isEmpty()) c.addView(text(it.text, 13, cMuted));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.topMargin = dp(8);
            logBox.addView(c, lp);
        }
    }

    // =====================================================================
    // страница «Настройки»
    // =====================================================================

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout c = vbox();
        c.setBackground(rounded(cCard, 16, cLine));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        parent.addView(c, lp);
        TextView t = text(title, 17, cInk);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);
        return c;
    }

    private void buildSettingsPage() {
        LinearLayout b = bodyOf(scrollSet);

        LinearLayout look = section(b, "Оформление");
        LinearLayout themeRow = hbox();
        themeRow.setPadding(0, dp(8), 0, 0);
        themeRow.addView(text("Тёмная тема", 15, cInk), new LinearLayout.LayoutParams(0, WRAP, 1f));
        Switch themeSw = new Switch(this);
        themeSw.setChecked(dark);
        themeSw.setOnCheckedChangeListener((btn, checked) -> {
            if (checked != dark) toggleTheme();
        });
        themeRow.addView(themeSw);
        look.addView(themeRow);

        LinearLayout snd = section(b, "Звук");
        LinearLayout sndRow = hbox();
        sndRow.setPadding(0, dp(8), 0, 0);
        LinearLayout sndLeft = vbox();
        sndLeft.addView(text("Звук уведомлений", 15, cInk));
        soundLabel = text(soundTitle(), 13, cMuted);
        sndLeft.addView(soundLabel);
        sndRow.addView(sndLeft, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Button pick = button("Выбрать", false);
        pick.setOnClickListener(x -> pickSound());
        sndRow.addView(pick);
        snd.addView(sndRow);
        TextView sndHint = text("Список мелодий вашего телефона. После выбора звук один раз проигрывается.", 12, cMuted);
        sndHint.setPadding(0, dp(6), 0, 0);
        snd.addView(sndHint);

        LinearLayout perms = section(b, "Разрешения");
        pNotif = permRow(perms, "Уведомления", "Без этого напоминания не появятся.", x -> {
            fromUser = true;
            chainActive = false;
            askNotifications();
        });
        pBatt = permRow(perms, "Работа в фоне", "Не ограничивать батарею, чтобы уведомления приходили вовремя при выключенном экране.", x -> {
            fromUser = true;
            chainActive = false;
            askBattery();
        });
        pExact = permRow(perms, "Точные будильники", "Нужны, чтобы уведомление пришло минута в минуту.", x -> {
            fromUser = true;
            chainActive = false;
            askExactAlarm();
        });

        LinearLayout check = section(b, "Проверка");
        TextView hint = text("Если на телефоне есть «Автозапуск» (Xiaomi, Huawei, Oppo и др.), "
                + "включите его для этого приложения в настройках телефона.", 13, cMuted);
        hint.setPadding(0, dp(6), 0, dp(8));
        check.addView(hint);
        Button test = button("Отправить тестовое уведомление", true);
        test.setOnClickListener(x -> sendTest());
        check.addView(test, new LinearLayout.LayoutParams(MATCH, WRAP));
        Button appSettings = button("Открыть настройки приложения", false);
        appSettings.setOnClickListener(x -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(MATCH, WRAP);
        alp.topMargin = dp(8);
        check.addView(appSettings, alp);

        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView v = text("Напоминалка " + ver, 12, cMuted);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(16), 0, 0);
        b.addView(v);
    }

    private PR permRow(LinearLayout card, String label, String hint, View.OnClickListener l) {
        PR p = new PR();
        p.row = vbox();
        p.row.setPadding(0, dp(12), 0, 0);
        LinearLayout line = hbox();
        LinearLayout left = vbox();
        TextView lt = text(label, 15, cInk);
        lt.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(lt);
        p.st = text("", 13, cMuted);
        left.addView(p.st);
        line.addView(left, new LinearLayout.LayoutParams(0, WRAP, 1f));
        p.btn = button("Разрешить", true);
        p.btn.setOnClickListener(l);
        line.addView(p.btn);
        p.row.addView(line);
        TextView h = text(hint, 12, cMuted);
        h.setPadding(0, dp(2), 0, 0);
        p.row.addView(h);
        card.addView(p.row);
        return p;
    }

    private void setPerm(PR p, boolean ok) {
        p.st.setText(ok ? "Разрешено" : "Нужно разрешить");
        p.st.setTextColor(ok ? GREEN : ROSE);
        p.btn.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private void refreshPermRows() {
        if (pNotif == null) return;
        setPerm(pNotif, Perms.notificationsOk(this));
        setPerm(pBatt, Perms.batteryOk(this));
        pExact.row.setVisibility(Build.VERSION.SDK_INT >= 31 ? View.VISIBLE : View.GONE);
        setPerm(pExact, Perms.exactAlarmOk(this));
    }

    // ---------- звук ----------

    private String soundTitle() {
        String s = Store.getSound(this);
        if ("silent".equals(s)) return "Без звука";
        if (s == null || s.isEmpty() || "default".equals(s)) return "По умолчанию";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(s));
            if (r != null) return r.getTitle(this);
        } catch (Exception ignored) {
        }
        return "Выбранный звук";
    }

    private void pickSound() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук уведомлений");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Scheduler.soundUri(this));
        try {
            startActivityForResult(i, REQ_SOUND);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть выбор звука", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSound(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) return;
        Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        String value;
        if (picked == null) value = "silent";
        else if (picked.equals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))) value = "default";
        else value = picked.toString();
        Store.setSound(this, value);
        Scheduler.ensureChannel(this);
        soundLabel.setText(soundTitle());
        playPreview(Scheduler.soundUri(this));
    }

    private void playPreview(Uri u) {
        stopPreview();
        if (u == null) return;
        try {
            preview = RingtoneManager.getRingtone(this, u);
            if (preview != null) {
                preview.play();
                handler.postDelayed(this::stopPreview, 3000);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopPreview() {
        if (preview != null) {
            try {
                preview.stop();
            } catch (Exception ignored) {
            }
            preview = null;
        }
    }

    private void sendTest() {
        if (!Perms.notificationsOk(this)) {
            Toast.makeText(this, "Сначала разрешите уведомления", Toast.LENGTH_SHORT).show();
            return;
        }
        collect();
        Scheduler.ensureChannel(this);
        String body = rules.isEmpty() || rules.get(0).text.trim().isEmpty()
                ? "Тестовое уведомление" : rules.get(0).text;
        Scheduler.post(this, 1, "Проверка", body);
        Store.addLog(this, System.currentTimeMillis(), "Проверка", body);
        if (currentTab == 1) renderLog();
    }

    // =====================================================================
    // вспомогательные
    // =====================================================================

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

    private static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int withAlpha(int c, float f) {
        return Color.argb((int) (255 * f), Color.red(c), Color.green(c), Color.blue(c));
    }

    private static boolean isLight(int c) {
        return 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c) > 170f;
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

    private TextView label(String s) {
        return text(s, 12, cMuted);
    }

    private LinearLayout.LayoutParams labelLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(3);
        return lp;
    }

    private TextView chip(String s, int bg, int fg) {
        TextView t = text(s, 13, fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(rounded(bg, 16, 0));
        t.setPadding(dp(11), dp(5), dp(11), dp(5));
        return t;
    }

    private TextView accentChip(String s, int acc, boolean strong) {
        if (strong) return chip(s, acc, isLight(acc) ? 0xFF1B1F3B : Color.WHITE);
        int fg = dark ? mix(acc, Color.WHITE, 0.3f) : mix(acc, Color.BLACK, 0.35f);
        return chip(s, withAlpha(acc, 0.16f), fg);
    }

    private EditText field(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(cInk);
        e.setHintTextColor(cMuted);
        e.setTextSize(15);
        e.setBackground(rounded(cBg, 12, cLine));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.addTextChangedListener(watcher);
        return e;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : cTintText);
        b.setBackground(rounded(primary ? INDIGO : cTint, 12, 0));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(44));
        b.setMinimumHeight(dp(44));
        return b;
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
