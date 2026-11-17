package org.tworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

/** Tile World 1.3.2 (MS-Regeln) fuer Android: Anzeige, Touch-Steuerung, Sound, Level-Auswahl. */
public class MainActivity extends Activity {
    /** Sound-Dateien je SND_*-Bit (null = im MS-Regelwerk unbenutzt) */
    static final String[] SND = {"death", "tada", "ding", "tick", null, "oof", "chack", "ting", "thief",
            "teleport", "door", "socket", "click", null, null, null, "bomb", "splash"};
    static final String[] DEATH = {"", "Chip ist ertrunken!", "Chip ist verbrannt!", "Chip wurde gesprengt!",
            "Die Zeit ist abgelaufen!", "Chip wurde erwischt!", "Chip ist tot!"};
    static final String[] MENU = {"Neustart", "Vorheriges Level", "N\u00e4chstes Level", "Level w\u00e4hlen", "Level-Set w\u00e4hlen"};

    /** Wie im Original: Tastenwiederholung nach 500 ms (SDL_EnableKeyRepeat(500, 75)), Tipp bleibt 3 Ticks gueltig. */
    static final int REPEAT_MS = 500, PULSE_TICKS = 3, UNDO_MAX = 1000;

    V v;

    @Override public void onCreate(Bundle b) { super.onCreate(b); v = new V(this); setContentView(v); }
    @Override protected void onResume() { super.onResume(); v.go(true); }
    @Override protected void onPause() { super.onPause(); v.go(false); }
    @Override public boolean onCreateOptionsMenu(Menu m) {
        for (int i = 0; i < MENU.length; i++) m.add(0, i, i, MENU[i]);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem it) { v.menu(it.getItemId()); return true; }

    class V extends View implements Runnable {
        final AssetManager am = getAssets();
        final SharedPreferences pr = getPreferences(0);
        final Paint p = new Paint();
        final Rect src = new Rect(), pad = new Rect();
        final RectF dst = new RectF();
        final int[] sid = new int[18];
        final SoundPool sp = new SoundPool(8, AudioManager.STREAM_MUSIC, 0);
        Bitmap atlas;
        String set;
        byte[][] ds;
        String[] names;
        Game g;
        final Rect undoBtn = new Rect();
        final ArrayList<int[]> hist = new ArrayList<int[]>();  // Undo: Zustand vor jedem Chip-Schritt
        int cur, mode, tick, dir, pulse, pulseTicks;  // mode: 0 bereit/pausiert, 1 laeuft, 2 beendet
        boolean win, ignore;
        long next, overAt, since;

        V(Context c) {
            super(c);
            try {
                atlas = BitmapFactory.decodeStream(am.open("tiles.png"));
                for (int i = 0; i < 18; i++) {
                    if (SND[i] == null) continue;
                    AssetFileDescriptor a = am.openFd("sound/" + SND[i] + ".wav");
                    sid[i] = sp.load(a, 1);
                    a.close();
                }
            } catch (IOException e) { throw new RuntimeException(e); }
            p.setAntiAlias(true);
            setKeepScreenOn(true);
            loadSet(pr.getString("pack", "levels/CCLP1.dat"));
            start(pr.getInt("lvl", 0));
        }

        /** Liest eine .dat-Datei; Level mit ungueltigem Passwort werden wie im Original uebersprungen. */
        void loadSet(String f) {
            ArrayList<byte[]> d = new ArrayList<byte[]>();
            ArrayList<String> n = new ArrayList<String>();
            try {
                InputStream in = am.open(f);
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int k; (k = in.read(b)) > 0; ) o.write(b, 0, k);
                in.close();
                byte[] a = o.toByteArray();
                int cnt = Game.w(a, 4), q = 6;
                for (int i = 0; i < cnt && q + 2 <= a.length; i++) {
                    int sz = Game.w(a, q);
                    q += 2;
                    byte[] l = Arrays.copyOfRange(a, q, q + sz);
                    q += sz;
                    try {
                        Game x = new Game(l);
                        if (x.pass.length() == 4) { d.add(l); n.add(x.number + ". " + x.name); }
                    } catch (RuntimeException e) { }
                }
            } catch (IOException e) { throw new RuntimeException(e); }
            set = f;
            ds = d.toArray(new byte[0][]);
            names = n.toArray(new String[0]);
        }

        void start(int i) {
            cur = Math.max(0, Math.min(i, ds.length - 1));
            g = new Game(ds[cur]);
            g.rnd = System.nanoTime() & 0x7FFFFFFFL;
            mode = tick = dir = pulse = 0;
            hist.clear();
            pr.edit().putString("pack", set).putInt("lvl", cur).commit();
            invalidate();
        }

        void go(boolean on) {
            removeCallbacks(this);
            if (on) { next = SystemClock.uptimeMillis(); post(this); }
        }

        /** Spieltakt: 55 ms pro Tick (MS-Regeln: 1100 ms / 20 Ticks). */
        public void run() {
            long now = SystemClock.uptimeMillis();
            if (now - next > 500) next = now;
            boolean d = false;
            while (mode == 1 && now >= next) {
                int cmd = dir != 0 && now - since >= REPEAT_MS ? dir : pulse;
                int[] pre = g.save();
                int p0 = g.creatures.get(0).pos;
                int r = g.turn(tick++, cmd);
                Game.Cr chip = g.creatures.get(0);
                if (chip.tdir != 0 && chip.pos != p0) {  // Chip hat sich selbst bewegt -> Undo-Schritt
                    hist.add(pre);
                    if (hist.size() > UNDO_MAX) hist.remove(0);
                }
                if (pulse != 0 && (g.input == 0 || --pulseTicks <= 0)) pulse = 0;
                for (int i = 0; i < 18; i++) if ((g.sfx >> i & 1) != 0 && sid[i] != 0) sp.play(sid[i], 1, 1, 0, 0, 1);
                if (r != 0) { mode = 2; win = r > 0; overAt = now; }
                next += 55;
                d = true;
            }
            if (d) invalidate();
            postDelayed(this, 10);
        }

        void menu(int id) {
            if (id == 0) start(cur);
            else if (id == 1) start(cur - 1);
            else if (id == 2) start(cur + 1);
            else if (id == 3) pick("Level", names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int i) { start(i); }
            });
            else try {
                // erst die mitgelieferten CCLP-Sets, darunter die zusaetzlichen Packs
                final ArrayList<String> p = new ArrayList<String>();
                for (String d : new String[] {"levels", "more"}) {
                    String[] f = am.list(d);
                    Arrays.sort(f, String.CASE_INSENSITIVE_ORDER);
                    for (String x : f) p.add(d + "/" + x);
                }
                String[] n = new String[p.size()];
                for (int i = 0; i < n.length; i++)
                    n[i] = p.get(i).substring(p.get(i).indexOf('/') + 1).replace(".dat", "").replace('_', ' ');
                pick("Level-Set", n, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int i) { loadSet(p.get(i)); start(0); }
                });
            } catch (IOException e) { }
        }

        /** Springt zum Zustand vor dem letzten Chip-Schritt (Zeit inklusive); Spiel pausiert bis zur naechsten Eingabe. */
        void undo() {
            if (hist.isEmpty()) return;
            g.load(hist.remove(hist.size() - 1));
            tick = g.currenttime + 1;
            mode = dir = pulse = 0;
            invalidate();
        }

        void pick(String t, String[] items, DialogInterface.OnClickListener l) {
            new AlertDialog.Builder(MainActivity.this).setTitle(t).setItems(items, l).show();
        }

        /** Touch im Steuerfeld: Tipp = ein Schritt, gedrueckt halten = Wiederholung nach REPEAT_MS. */
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getAction() & MotionEvent.ACTION_MASK;
            boolean up = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
            if (a == MotionEvent.ACTION_DOWN && undoBtn.contains((int) e.getX(), (int) e.getY())) { ignore = true; undo(); }
            if (ignore) { if (up) ignore = false; return true; }
            if (up) { dir = 0; return true; }
            if (mode == 2) {
                if (a == MotionEvent.ACTION_DOWN && SystemClock.uptimeMillis() - overAt > 500) start(win ? cur + 1 : cur);
                return true;
            }
            int d = 0;
            float dx = e.getX() - pad.centerX(), dy = e.getY() - pad.centerY();
            if (pad.contains((int) e.getX(), (int) e.getY()) && dx * dx + dy * dy > pad.width() * pad.width() / 100f)
                d = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? Game.W : Game.E) : (dy < 0 ? Game.N : Game.S);
            if (d != dir) {
                dir = d;
                since = SystemClock.uptimeMillis();
                if (d != 0) { pulse = d; pulseTicks = PULSE_TICKS; }
            }
            if (mode == 0 && d != 0) { mode = 1; next = SystemClock.uptimeMillis(); }
            return true;
        }

        /** Zeichnet Tile id in dst: Atlas-Layout 16 Spalten, Index = Tile-ID. */
        void tile(Canvas c, int id) {
            if (id == 0) return;
            int x = (id & 15) * 48, y = (id >> 4) * 48;
            src.set(x, y, x + 48, y + 48);
            c.drawBitmap(atlas, src, dst, null);
        }

        void text(Canvas c, String s, float x, float y, float wmax) {
            String line = "";
            for (String wd : s.split(" ")) {
                if (p.measureText(line + wd) > wmax && line.length() > 0) {
                    c.drawText(line, x, y, p);
                    y += p.getTextSize() * 1.2f;
                    line = "";
                }
                line += wd + " ";
            }
            c.drawText(line, x, y, p);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight(), m = Math.min(w, h), ts = m / 9;
            int px = w < h ? 0 : m, py = w < h ? m : 0, pw = w - px;
            c.drawColor(Color.BLACK);
            // Kartenausschnitt 9x9 um Chip (wie displaymapview)
            int cp = g.creatures.get(0).pos;
            int vx = Math.max(0, Math.min(cp % 32 - 4, 23)), vy = Math.max(0, Math.min(cp / 32 - 4, 23));
            for (int y = 0; y < 9; y++)
                for (int x = 0; x < 9; x++) {
                    Game.Cell k = g.map[(vy + y) * 32 + vx + x];
                    dst.set(x * ts, y * ts, x * ts + ts, y * ts + ts);
                    tile(c, Game.Empty);
                    tile(c, k.bot.id);
                    tile(c, k.top.id);
                }
            // Info-Anzeige (displayinfo)
            pad.set(px, py + 3 * ts, px + pw, h);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(ts * .5f);
            c.drawText(g.name, px + pw / 2, py + ts * .6f, p);
            p.setFakeBoldText(false);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(ts * .4f);
            String t = g.time == 0 ? "---" : "" + Math.max(0, g.time - (g.currenttime + g.timeoffset) / 20);
            c.drawText("Level " + g.number + "  Passwort " + g.pass, px + ts * .2f, py + ts * 1.2f, p);
            c.drawText("Chips " + g.chipsneeded + "   Zeit " + t, px + ts * .2f, py + ts * 1.8f, p);
            float is = ts * .7f;
            for (int n = 0; n < 4; n++) {
                float x = px + pw - (4 - n) * is - ts * .2f;
                dst.set(x, py + ts, x + is, py + ts + is);
                tile(c, Game.Empty);
                if (g.inv[n] != 0) tile(c, Game.Key_Red + n);
                dst.offset(0, is + ts * .05f);
                tile(c, Game.Empty);
                if (g.inv[4 + n] != 0) tile(c, Game.Boots_Ice + n);
            }
            // Steuerfeld
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0x55FFFFFF);
            p.setTextSize(ts * 1.5f);
            float s = p.getTextSize() / 3, cx = pad.centerX(), cy = pad.centerY();
            c.drawText("\u25B2", cx, pad.top + pad.height() * .2f, p);
            c.drawText("\u25BC", cx, pad.bottom - pad.height() * .1f, p);
            c.drawText("\u25C0", pad.left + pad.width() * .15f, cy + s, p);
            c.drawText("\u25B6", pad.right - pad.width() * .15f, cy + s, p);
            p.setTextSize(ts * .4f);
            if (mode == 0) c.drawText(tick == 0 ? "Pfeil antippen = Start" : "Pfeil antippen = weiter", cx, cy, p);
            p.setTextAlign(Paint.Align.LEFT);
            if (g.showhint) {
                p.setColor(0xDD000000);
                c.drawRect(pad, p);
                p.setColor(Color.YELLOW);
                p.setTextSize(ts * .38f);
                text(c, g.hint, pad.left + ts * .2f, pad.top + ts * .5f, pad.width() - ts * .4f);
            }
            // Undo-Button (links unten im Steuerfeld)
            undoBtn.set(pad.left, pad.bottom - (int) (ts * 1.1f), pad.left + (int) (ts * 2.4f), pad.bottom);
            p.setColor(0x55FFFFFF);
            c.drawRect(undoBtn, p);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(ts * .4f);
            c.drawText("Undo " + hist.size(), undoBtn.centerX(), undoBtn.centerY() + ts * .15f, p);
            if (mode == 2) {
                p.setColor(0xCC000000);
                c.drawRect(0, ts * 3, ts * 9, ts * 6, p);
                p.setColor(win ? Color.GREEN : Color.RED);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(ts * .6f);
                c.drawText(win ? "Level geschafft!" : DEATH[g.chipstatus], ts * 4.5f, ts * 4.4f, p);
                p.setColor(Color.WHITE);
                p.setTextSize(ts * .4f);
                c.drawText("Tippen = " + (win ? "n\u00e4chstes Level" : "nochmal"), ts * 4.5f, ts * 5.2f, p);
            }
        }
    }
}
