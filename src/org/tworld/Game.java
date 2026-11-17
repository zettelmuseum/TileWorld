package org.tworld;

import java.util.ArrayList;

/** Tile World 1.3.2, MS-Regeln: Port von mslogic.c, encoding.c (Level-Parser) und random.c. */
final class Game {
    static final int NIL = 0, N = 1, W = 2, S = 4, E = 8;
    static final int Empty = 1, Slide_North = 2, Slide_West = 3, Slide_South = 4, Slide_East = 5, Slide_Random = 6, Ice = 7,
            IceWall_Northwest = 8, IceWall_Northeast = 9, IceWall_Southwest = 10, IceWall_Southeast = 11,
            Gravel = 12, Dirt = 13, Water = 14, Fire = 15, Bomb = 16, Beartrap = 17, Burglar = 18, HintButton
            = 19, Button_Blue = 20, Button_Green = 21, Button_Red = 22, Button_Brown = 23, Teleport = 24, Wall
            = 25, Wall_North = 26, Wall_West = 27, Wall_South = 28, Wall_East = 29, Wall_Southeast = 30,
            HiddenWall_Perm = 31, HiddenWall_Temp = 32, BlueWall_Real = 33, BlueWall_Fake = 34,
            SwitchWall_Open = 35, SwitchWall_Closed = 36, PopupWall = 37, CloneMachine = 38, Door_Red = 39,
            Door_Blue = 40, Door_Yellow = 41, Door_Green = 42, Socket = 43, Exit = 44, ICChip = 45, Key_Red =
            46, Key_Blue = 47, Key_Yellow = 48, Key_Green = 49, Boots_Ice = 50, Boots_Slide = 51, Boots_Fire =
            52, Boots_Water = 53, Block_Static = 54, Drowned_Chip = 55, Burned_Chip = 56, Bombed_Chip = 57,
            Exited_Chip = 58, Exit_Extra_1 = 59, Exit_Extra_2 = 60, Overlay_Buffer = 61, Floor_Reserved2 = 62,
            Floor_Reserved1 = 63, Chip = 64, Block = 68, Tank = 72, Ball = 76, Glider = 80, Fireball = 84,
            Walker = 88, Blob = 92, Teeth = 96, Bug = 100, Paramecium = 104, Swimming_Chip = 108, Pushing_Chip
            = 112, Entity_Reserved2 = 116, Entity_Reserved1 = 120, Water_Splash = 124, Bomb_Explosion = 125,
            Entity_Explosion = 126, Animation_Reserved1 = 127;
    static final int CHIP_OKAY = 0, CHIP_DROWNED = 1, CHIP_BURNED = 2, CHIP_BOMBED = 3, CHIP_OUTOFTIME = 4,
            CHIP_COLLIDED = 5, CHIP_NOTOKAY = 6;
    static final int FS_BUTTONDOWN = 1, FS_CLONING = 2, FS_BROKEN = 4, FS_HASMUTANT = 8, FS_MARKER = 16;
    static final int CS_RELEASED = 1, CS_CLONING = 2, CS_HASMOVED = 4, CS_TURNING = 8, CS_SLIP = 16,
            CS_SLIDE = 32, CS_DEFERPUSH = 64, CS_MUTANT = 128;
    static final int CMM_NOLEAVECHECK = 1, CMM_NOEXPOSEWALLS = 2, CMM_CLONECANTBLOCK = 4, CMM_NOPUSHING = 8,
            CMM_TELEPORTPUSH = 16, CMM_NOFIRECHECK = 32, CMM_NODEFERBUTTONS = 64;
    /** Sound-Bits (SND_*) */
    static final int SND_CHIP_LOSES = 0, SND_CHIP_WINS = 1, SND_TIME_OUT = 2, SND_TIME_LOW = 3, SND_CANT_MOVE = 5,
            SND_IC_COLLECTED = 6, SND_ITEM_COLLECTED = 7, SND_BOOTS_STOLEN = 8, SND_TELEPORTING = 9,
            SND_DOOR_OPENED = 10, SND_SOCKET_OPENED = 11, SND_BUTTON_PUSHED = 12, SND_BOMB_EXPLODES = 16,
            SND_WATER_SPLASH = 17;

    static final int[] DELTA = {0, -32, -1, 0, 32, 0, 0, 0, 1};

    /** Level-Datei-Code -> Tile-ID */
    static final int[] F = {
            1, 25, 45, 14, 15, 31, 26, 27, 28, 29, 54, 13, 7, 4, 68, 69, 70, 71, 2, 5, 3, 44, 40, 39, 42, 41,
            11, 10, 8, 9, 34, 33, 61, 18, 43, 21, 22, 36, 35, 23, 20, 24, 16, 17, 32, 12, 37, 19, 30, 38, 6,
            55, 56, 57, 31, 31, 31, 58, 59, 60, 108, 109, 110, 111, 100, 101, 102, 103, 84, 85, 86, 87, 76,
            77, 78, 79, 72, 73, 74, 75, 80, 81, 82, 83, 96, 97, 98, 99, 88, 89, 90, 91, 92, 93, 94, 95, 104,
            105, 106, 107, 47, 46, 49, 48, 53, 52, 50, 51, 64, 65, 66, 67};
    /** Bewegungsregeln je Tile (3 Hex-Ziffern: Chip, Block, Monster; Bits NWSE) */
    static final String ML = "000fffffffffffffffff0fffccc666999333ff0f00fffffffffffff00ffffffffffffffffff000bbb777eeedddccc000f00f00f00fff000f00000f00f00f00f00f00ff0f00ffffffffffffff0ff0ff0ff0f00000000000000000000000000000";

    static final class Tile { int id, st; }
    static final class Cell { final Tile top = new Tile(), bot = new Tile(); }
    static final class Cr { int pos = -1, id, dir, tdir, state; boolean hidden; }
    static final class Slip { Cr cr; int dir; Slip(Cr c, int d) { cr = c; dir = d; } }

    // ---- Spielzustand (gamestate)
    final Cell[] map = new Cell[1024];
    final int[] inv = new int[8];                 // Schluessel rot,blau,gelb,gruen; Stiefel Eis,Sog,Feuer,Wasser
    int number, time, chipsneeded, currenttime = -1, timeoffset, input, sfx, stepping;
    String name = "", pass = "", hint = "";
    boolean showhint;
    int trapN, clN, crN;
    final int[] trapFrom = new int[256], trapTo = new int[256], clFrom = new int[256], clTo = new int[256],
            crlist = new int[256];
    long rnd = 12345;
    // ---- msstate
    int chipwait, chipstatus, controllerdir, lastslipdir;
    boolean completed;
    final ArrayList<Cr> creatures = new ArrayList<>(), blocks = new ArrayList<>();
    final ArrayList<Slip> slips = new ArrayList<>();

    // ================= Level-Parser (encoding.c / series.c) =================
    static int w(byte[] d, int p) { return (d[p] & 255) | ((d[p + 1] & 255) << 8); }
    static int rp(byte[] d, int px, int py) { int x = d[px] & 255; return x < 32 ? x + 32 * (d[py] & 255) : 1024; }

    Game(byte[] d) {
        for (int i = 0; i < 1024; i++) map[i] = new Cell();
        int len = d.length;
        if (len < 10 || w(d, 0) == 0 || w(d, 6) > 1) throw new IllegalArgumentException("level");
        number = w(d, 0); time = w(d, 2); chipsneeded = w(d, 4);
        int size = w(d, 8), p = 10;
        layer(d, p, size, true); p += size;
        size = w(d, p); p += 2;
        layer(d, p, size, false); p += size;
        p += 2;
        while (p + 2 < len) {
            int f = d[p] & 255; size = d[p + 1] & 255; p += 2;
            if (p + size > len) size = len - p;
            switch (f) {
                case 1: if (size > 1) time = w(d, p); break;
                case 2: if (size >= 2) chipsneeded = w(d, p); break;
                case 3: name = str(d, p, size); break;
                case 4:
                    trapN = size / 10;
                    for (int i = 0; i < trapN; i++) { trapFrom[i] = rp(d, p + i * 10, p + i * 10 + 2); trapTo[i] = rp(d, p + i * 10 + 4, p + i * 10 + 6); }
                    break;
                case 5:
                    clN = size / 8;
                    for (int i = 0; i < clN; i++) { clFrom[i] = rp(d, p + i * 8, p + i * 8 + 2); clTo[i] = rp(d, p + i * 8 + 4, p + i * 8 + 6); }
                    break;
                case 6:
                    for (int n = 0; n < size && n < 15 && d[p + n] != 0; n++) pass += (char) ((d[p + n] & 255) ^ 0x99);
                    break;
                case 7: hint = str(d, p, size); break;
                case 10:
                    crN = size / 2;
                    for (int i = 0; i < crN; i++) crlist[i] = rp(d, p + i * 2, p + i * 2 + 1);
                    break;
            }
            p += size;
        }
        init();
    }

    static String str(byte[] d, int p, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n && d[p + i] != 0; i++) b.append((char) (d[p + i] & 255));
        return b.toString();
    }

    void layer(byte[] d, int p, int size, boolean top) {
        int pos = 0;
        for (int n = 0; n < size && pos < 1024; n++) {
            int cnt = 1, id = d[p + n] & 255;
            if (id == 255) { cnt = d[p + ++n] & 255; id = d[p + ++n] & 255; }
            id = id < F.length ? F[id] : Wall;
            while (cnt-- > 0 && pos < 1024) { Tile t = top ? map[pos].top : map[pos].bot; pos++; t.id = id; }
        }
    }

    // ================= Zufall (random.c) =================
    int nextr() { rnd = (rnd * 1103515245L + 12345L) & 0x7FFFFFFFL; return (int) rnd; }
    int random4() { nextr(); return (int) (rnd >> 29); }
    void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
    void randomp3(int[] a, int o) {
        nextr();
        swap(a, o + (int) (rnd >> 30), o + 1);
        swap(a, o + (int) ((3.0 * (rnd & 0x3FFFFFFFL)) / (double) 0x40000000L), o + 2);
    }
    void randomp4(int[] a) {
        nextr();
        swap(a, (int) (rnd >> 30), 1);
        swap(a, (int) ((3.0 * (rnd & 0x0FFFFFFFL)) / (double) 0x10000000L), 2);
        swap(a, (int) ((rnd >> 28) & 3), 3);
    }

    // ================= Makros =================
    static boolean isslide(int f) { return f >= Slide_North && f <= Slide_Random; }
    static boolean isice(int f) { return f >= Ice && f <= IceWall_Southeast; }
    static boolean isdoor(int f) { return f >= Door_Red && f <= Door_Green; }
    static boolean iskey(int f) { return f >= Key_Red && f <= Key_Green; }
    static boolean isboots(int f) { return f >= Boots_Ice && f <= Boots_Water; }
    static boolean iscreature(int f) { return f >= Chip && f < Water_Splash; }
    static int creatureid(int id) { return id & ~3; }
    static int creaturedirid(int id) { return 1 << (id & 3); }
    static int crtile(int id, int dir) { return id | ((0x30210 >> (dir * 2)) & 3); }
    static int left(int d) { return ((d << 1) | (d >> 3)) & 15; }
    static int back(int d) { return ((d << 2) | (d >> 2)) & 15; }
    static int right(int d) { return ((d << 3) | (d >> 1)) & 15; }

    /** Index in inv[] (possession()) */
    static int pi(int o) {
        switch (o) {
            case Key_Red: case Door_Red: return 0;
            case Key_Blue: case Door_Blue: return 1;
            case Key_Yellow: case Door_Yellow: return 2;
            case Key_Green: case Door_Green: return 3;
            case Boots_Ice: case Ice: case IceWall_Northwest: case IceWall_Northeast: case IceWall_Southwest:
            case IceWall_Southeast: return 4;
            case Boots_Slide: case Slide_North: case Slide_West: case Slide_South: case Slide_East:
            case Slide_Random: return 5;
            case Boots_Fire: case Fire: return 6;
            default: return 7; // Boots_Water, Water
        }
    }
    int poss(int o) { return inv[pi(o)]; }

    int ml(int floor, int who) { return floor < 64 ? Character.digit(ML.charAt(floor * 3 + who), 16) : 0; }
    void sound(int s) { sfx |= 1 << s; }
    Cr chip() { return creatures.get(0); }

    // ================= Listen =================
    void appendslip(Cr cr, int dir) {
        for (Slip s : slips) if (s.cr == cr) { s.dir = dir; return; }
        slips.add(new Slip(cr, dir));
    }
    void prependslip(Cr cr, int dir) {
        if (!slips.isEmpty() && slips.get(0).cr == cr) { slips.get(0).dir = dir; return; }
        slips.add(0, new Slip(cr, dir));
    }
    int getslipdir(Cr cr) {
        for (Slip s : slips) if (s.cr == cr) return s.dir;
        return NIL;
    }
    void removeslip(Cr cr) {
        for (int n = 0; n < slips.size(); n++) if (slips.get(n).cr == cr) { slips.remove(n); return; }
    }

    int getslidedir(int floor) {
        switch (floor) {
            case Slide_North: return N;
            case Slide_West: return W;
            case Slide_South: return S;
            case Slide_East: return E;
            case Slide_Random: return 1 << random4();
        }
        return NIL;
    }
    static int icewallturn(int floor, int dir) {
        switch (floor) {
            case IceWall_Northeast: return dir == S ? E : dir == W ? N : dir;
            case IceWall_Southwest: return dir == N ? W : dir == E ? S : dir;
            case IceWall_Northwest: return dir == S ? W : dir == E ? N : dir;
            case IceWall_Southeast: return dir == N ? E : dir == W ? S : dir;
        }
        return dir;
    }
    int trapfrombutton(int pos) { for (int i = 0; i < trapN; i++) if (trapFrom[i] == pos) return trapTo[i]; return -1; }
    int clonerfrombutton(int pos) { for (int i = 0; i < clN; i++) if (clFrom[i] == pos) return clTo[i]; return -1; }

    int floorat(int pos) {
        Cell c = map[pos];
        if (!iskey(c.top.id) && !isboots(c.top.id) && !iscreature(c.top.id)) return c.top.id;
        if (!iskey(c.bot.id) && !isboots(c.bot.id) && !iscreature(c.bot.id)) return c.bot.id;
        return Empty;
    }
    Tile getfloorat(int pos) {
        Cell c = map[pos];
        if (!iskey(c.top.id) && !isboots(c.top.id) && !iscreature(c.top.id)) return c.top;
        return c.bot;
    }
    boolean istrapbuttondown(int pos) { return pos >= 0 && pos < 1024 && map[pos].top.id != Button_Brown; }
    void pushtile(int pos, int id, int st) {
        Cell c = map[pos];
        c.bot.id = c.top.id; c.bot.st = c.top.st;
        c.top.id = id; c.top.st = st;
    }
    void poptile(int pos) {
        Cell c = map[pos];
        c.top.id = c.bot.id; c.top.st = c.bot.st;
        c.bot.id = Empty; c.bot.st = 0;
    }
    boolean istrapopen(int pos, int skippos) {
        for (int i = 0; i < trapN; i++)
            if (trapTo[i] == pos && trapFrom[i] != skippos && istrapbuttondown(trapFrom[i])) return true;
        return false;
    }
    void togglewalls() {
        for (int pos = 0; pos < 1024; pos++) {
            Cell c = map[pos];
            if ((c.top.id == SwitchWall_Open || c.top.id == SwitchWall_Closed) && (c.top.st & FS_BROKEN) == 0)
                c.top.id ^= SwitchWall_Open ^ SwitchWall_Closed;
            if ((c.bot.id == SwitchWall_Open || c.bot.id == SwitchWall_Closed) && (c.bot.st & FS_BROKEN) == 0)
                c.bot.id ^= SwitchWall_Open ^ SwitchWall_Closed;
        }
    }

    // ================= Kreaturen =================
    Cr lookupcreature(int pos, boolean includechip) {
        for (Cr c : creatures) {
            if (c.hidden) continue;
            if (c.pos == pos && (c.id != Chip || includechip)) return c;
        }
        return null;
    }
    Cr lookupblock(int pos) {
        for (Cr b : blocks) if (b.pos == pos && !b.hidden) return b;
        Cr cr = new Cr();
        cr.id = Block; cr.pos = pos;
        int id = map[pos].top.id;
        if (id == Block_Static) cr.dir = NIL;
        else if (creatureid(id) == Block) cr.dir = creaturedirid(id);
        blocks.add(cr);
        return cr;
    }
    void updatecreature(Cr cr) {
        if (cr.hidden) return;
        Tile tile = map[cr.pos].top;
        int id = cr.id, dir;
        if (id == Block) {
            tile.id = Block_Static;
            if ((cr.state & CS_MUTANT) != 0) tile.id = crtile(Chip, N);
            return;
        } else if (id == Chip) {
            if (chipstatus != 0) {
                if (chipstatus == CHIP_BURNED) { tile.id = Burned_Chip; return; }
                if (chipstatus == CHIP_DROWNED) { tile.id = Drowned_Chip; return; }
            } else if (map[cr.pos].bot.id == Water) id = Swimming_Chip;
        }
        dir = cr.dir;
        if ((cr.state & CS_TURNING) != 0) dir = right(dir);
        tile.id = crtile(id, dir);
        tile.st = 0;
    }
    void addcreaturetomap(Cr cr) {
        if (cr.hidden) return;
        pushtile(cr.pos, Empty, 0);
        updatecreature(cr);
    }
    Cr awakencreature(int pos) {
        int tid = map[pos].top.id;
        if (!iscreature(tid) || creatureid(tid) == Chip) return null;
        Cr c = new Cr();
        c.id = creatureid(tid); c.dir = creaturedirid(tid); c.pos = pos;
        if (c.id == Block) blocks.add(c); else creatures.add(c);
        return c;
    }
    void removecreature(Cr cr) {
        cr.state &= ~(CS_SLIP | CS_SLIDE);
        if (cr.id == Chip) { if (chipstatus == CHIP_OKAY) chipstatus = CHIP_NOTOKAY; }
        else cr.hidden = true;
    }
    void turntanks(Cr mid) {
        for (int n = 0; n < creatures.size(); n++) {
            Cr c = creatures.get(n);
            if (c.hidden || c.id != Tank) continue;
            c.dir = back(c.dir);
            if ((c.state & CS_TURNING) == 0) c.state |= CS_TURNING | CS_HASMOVED;
            if (c != mid) {
                if (creatureid(map[c.pos].top.id) == Tank) updatecreature(c);
                else {
                    if ((c.state & CS_TURNING) != 0) { c.state &= ~CS_TURNING; updatecreature(c); c.state |= CS_TURNING; }
                    c.dir = back(c.dir);
                }
            }
        }
    }
    void startfloormovement(Cr cr, int floor) {
        int dir;
        cr.state &= ~(CS_SLIP | CS_SLIDE);
        if (isice(floor)) dir = icewallturn(floor, cr.dir);
        else if (isslide(floor)) dir = getslidedir(floor);
        else if (floor == Teleport) dir = cr.dir;
        else if (floor == Beartrap && cr.id == Block) dir = cr.dir;
        else return;
        if (cr.id == Chip) {
            cr.state |= isslide(floor) ? CS_SLIDE : CS_SLIP;
            prependslip(cr, dir);
            cr.dir = dir;
            updatecreature(cr);
        } else {
            cr.state |= CS_SLIP;
            appendslip(cr, dir);
        }
    }
    void endfloormovement(Cr cr) { cr.state &= ~(CS_SLIP | CS_SLIDE); removeslip(cr); }
    void updatesliplist() {
        for (int n = slips.size() - 1; n >= 0; n--)
            if ((slips.get(n).cr.state & (CS_SLIP | CS_SLIDE)) == 0) endfloormovement(slips.get(n).cr);
    }

    // ================= Bewegung =================
    boolean pushblock(int pos, int dir, int flags) {
        Cr cr = lookupblock(pos);
        if ((cr.state & (CS_SLIP | CS_SLIDE)) != 0) {
            int sd = getslipdir(cr);
            if (dir == sd || dir == back(sd)) if ((flags & CMM_TELEPORTPUSH) == 0) return false;
        }
        if ((flags & CMM_TELEPORTPUSH) == 0 && map[pos].bot.id == Block_Static) map[pos].bot.id = Empty;
        if ((flags & CMM_NODEFERBUTTONS) == 0) cr.state |= CS_DEFERPUSH;
        boolean r = advancecreature(cr, dir);
        if ((flags & CMM_NODEFERBUTTONS) == 0) cr.state &= ~CS_DEFERPUSH;
        if (!r) cr.state &= ~(CS_SLIP | CS_SLIDE);
        return r;
    }

    boolean canmakemove(Cr cr, int dir, int flags) {
        int y = cr.pos / 32, x = cr.pos % 32, floor, id;
        y += dir == N ? -1 : dir == S ? 1 : 0;
        x += dir == W ? -1 : dir == E ? 1 : 0;
        if (y < 0 || y >= 32 || x < 0 || x >= 32) return false;
        int to = y * 32 + x;
        if ((flags & CMM_NOLEAVECHECK) == 0) {
            switch (map[cr.pos].bot.id) {
                case Wall_North: if (dir == N) return false; break;
                case Wall_West: if (dir == W) return false; break;
                case Wall_South: if (dir == S) return false; break;
                case Wall_East: if (dir == E) return false; break;
                case Wall_Southeast: if ((dir & (S | E)) != 0) return false; break;
                case Beartrap: if ((cr.state & CS_RELEASED) == 0) return false; break;
            }
        }
        if (cr.id == Chip) {
            floor = floorat(to);
            if ((ml(floor, 0) & dir) == 0) return false;
            if (floor == Socket && chipsneeded > 0) return false;
            if (isdoor(floor) && poss(floor) == 0) return false;
            if (iscreature(map[to].top.id)) {
                id = creatureid(map[to].top.id);
                if (id == Chip || id == Swimming_Chip || id == Block) return false;
            }
            if (floor == HiddenWall_Temp || floor == BlueWall_Real) {
                if ((flags & CMM_NOEXPOSEWALLS) == 0) getfloorat(to).id = Wall;
                return false;
            }
            if (floor == Block_Static) {
                if (!pushblock(to, dir, flags)) return false;
                else if ((flags & CMM_NOPUSHING) != 0) return false;
                if ((flags & CMM_TELEPORTPUSH) != 0 && floorat(to) == Block_Static && map[to].bot.id == Empty) return true;
                return canmakemove(cr, dir, flags | CMM_NOPUSHING);
            }
        } else if (cr.id == Block) {
            floor = map[to].top.id;
            if (iscreature(floor)) {
                id = creatureid(floor);
                return id == Chip || id == Swimming_Chip;
            }
            if ((ml(floor, 1) & dir) == 0) return false;
        } else {
            floor = map[to].top.id;
            if (iscreature(floor)) {
                id = creatureid(floor);
                if (id == Chip || id == Swimming_Chip) {
                    floor = map[to].bot.id;
                    if (iscreature(floor)) {
                        id = creatureid(floor);
                        return id == Chip || id == Swimming_Chip;
                    }
                }
            }
            if (iscreature(floor)) {
                if ((flags & CMM_CLONECANTBLOCK) != 0 && floor == crtile(cr.id, cr.dir)) return true;
                return false;
            }
            if ((ml(floor, 2) & dir) == 0) return false;
            if (floor == Fire && (cr.id == Bug || cr.id == Walker))
                if ((flags & CMM_NOFIRECHECK) == 0) return false;
        }
        if (map[to].bot.id == CloneMachine) return false;
        return true;
    }

    void choosecreaturemove(Cr cr) {
        int[] ch = {NIL, NIL, NIL, NIL};
        int dir, pdir, floor, y, x, m, n;
        cr.tdir = NIL;
        if (cr.hidden) return;
        if (cr.id == Block) return;
        if ((currenttime & 2) != 0) return;
        if (cr.id == Teeth || cr.id == Blob) if (((currenttime + stepping) & 4) != 0) return;
        if ((cr.state & CS_TURNING) != 0) { cr.state &= ~(CS_TURNING | CS_HASMOVED); updatecreature(cr); }
        if ((cr.state & CS_HASMOVED) != 0) { controllerdir = NIL; return; }
        if ((cr.state & (CS_SLIP | CS_SLIDE)) != 0) return;
        floor = floorat(cr.pos);
        pdir = dir = cr.dir;
        if (floor == CloneMachine || floor == Beartrap) {
            switch (cr.id) {
                case Tank: case Ball: case Glider: case Fireball: case Walker:
                    ch[0] = dir;
                    break;
                case Blob:
                    ch[0] = dir; ch[1] = left(dir); ch[2] = back(dir); ch[3] = right(dir);
                    randomp4(ch);
                    break;
                case Bug: case Paramecium: case Teeth:
                    ch[0] = controllerdir;
                    cr.tdir = controllerdir;
                    return;
            }
        } else {
            switch (cr.id) {
                case Tank:
                    ch[0] = dir;
                    break;
                case Ball:
                    ch[0] = dir; ch[1] = back(dir);
                    break;
                case Glider:
                    ch[0] = dir; ch[1] = left(dir); ch[2] = right(dir); ch[3] = back(dir);
                    break;
                case Fireball:
                    ch[0] = dir; ch[1] = right(dir); ch[2] = left(dir); ch[3] = back(dir);
                    break;
                case Walker:
                    ch[0] = dir; ch[1] = left(dir); ch[2] = back(dir); ch[3] = right(dir);
                    randomp3(ch, 1);
                    break;
                case Blob:
                    ch[0] = dir; ch[1] = left(dir); ch[2] = back(dir); ch[3] = right(dir);
                    randomp4(ch);
                    break;
                case Bug:
                    ch[0] = left(dir); ch[1] = dir; ch[2] = right(dir); ch[3] = back(dir);
                    break;
                case Paramecium:
                    ch[0] = right(dir); ch[1] = dir; ch[2] = left(dir); ch[3] = back(dir);
                    break;
                case Teeth:
                    y = chip().pos / 32 - cr.pos / 32;
                    x = chip().pos % 32 - cr.pos % 32;
                    n = y < 0 ? N : y > 0 ? S : NIL;
                    if (y < 0) y = -y;
                    m = x < 0 ? W : x > 0 ? E : NIL;
                    if (x < 0) x = -x;
                    if (x > y) { ch[0] = m; ch[1] = n; } else { ch[0] = n; ch[1] = m; }
                    pdir = ch[2] = ch[0];
                    break;
            }
        }
        for (n = 0; n < 4 && ch[n] != NIL; ++n) {
            cr.tdir = ch[n];
            controllerdir = cr.tdir;
            if (canmakemove(cr, ch[n], 0)) return;
        }
        if (cr.id == Tank) {
            if ((cr.state & CS_RELEASED) != 0 || (floor != Beartrap && floor != CloneMachine)) cr.state |= CS_HASMOVED;
        }
        cr.tdir = pdir;
    }

    void choosechipmove(Cr cr, boolean discard) {
        cr.tdir = NIL;
        if (cr.hidden) return;
        if ((currenttime & 3) == 0) cr.state &= ~CS_HASMOVED;
        if ((cr.state & CS_HASMOVED) != 0) return;
        int dir = input;
        input = NIL;
        if (discard || ((cr.state & CS_SLIDE) != 0 && dir == cr.dir)) return;
        if ((dir & (N | S)) != 0 && (dir & (E | W)) != 0) dir &= N | S;
        cr.tdir = dir;
    }

    int teleportcreature(Cr cr, int start) {
        if (cr.dir == NIL) return NIL;
        int origpos = cr.pos, dest = start;
        for (;;) {
            --dest;
            if (dest < 0) dest += 1024;
            if (dest == start) break;
            Tile t = map[dest].top;
            if (t.id != Teleport || (t.st & FS_BROKEN) != 0) continue;
            cr.pos = dest;
            boolean f = canmakemove(cr, cr.dir, CMM_NOLEAVECHECK | CMM_NOEXPOSEWALLS | CMM_NODEFERBUTTONS
                    | CMM_NOFIRECHECK | CMM_TELEPORTPUSH);
            cr.pos = origpos;
            if (f) break;
        }
        return dest;
    }

    void choosemove(Cr cr) {
        if (cr.id == Chip) choosechipmove(cr, (cr.state & CS_SLIP) != 0);
        else if ((cr.state & CS_SLIP) != 0) cr.tdir = NIL;
        else choosecreaturemove(cr);
    }

    void activatecloner(int buttonpos) {
        int pos = clonerfrombutton(buttonpos);
        if (pos < 0 || pos >= 1024) return;
        int tid = map[pos].top.id;
        if (!iscreature(tid) || creatureid(tid) == Chip) return;
        Cr cr;
        if (creatureid(tid) == Block) {
            cr = lookupblock(pos);
            if (cr.dir != NIL) advancecreature(cr, cr.dir);
        } else {
            if ((map[pos].bot.st & FS_CLONING) != 0) return;
            Cr d = new Cr();
            d.id = creatureid(tid); d.dir = creaturedirid(tid); d.pos = pos;
            if (!canmakemove(d, d.dir, CMM_CLONECANTBLOCK)) return;
            cr = awakencreature(pos);
            if (cr == null) return;
            cr.state |= CS_CLONING;
            if (map[pos].bot.id == CloneMachine) map[pos].bot.st |= FS_CLONING;
        }
    }

    void springtrap(int buttonpos) {
        int pos = trapfrombutton(buttonpos);
        if (pos < 0 || pos >= 1024) return;
        int id = map[pos].top.id;
        Cr cr;
        if (id == Block_Static || (map[pos].bot.st & FS_HASMUTANT) != 0) {
            cr = lookupblock(pos);
            if (cr != null) cr.state |= CS_RELEASED;
        } else if (iscreature(id)) {
            cr = lookupcreature(pos, true);
            if (cr != null) cr.state |= CS_RELEASED;
        }
    }

    void resetbuttons() {
        for (int pos = 0; pos < 1024; pos++) { map[pos].top.st &= ~FS_BUTTONDOWN; map[pos].bot.st &= ~FS_BUTTONDOWN; }
    }

    void handlebuttons() {
        for (int pos = 0; pos < 1024; pos++) {
            int id;
            if ((map[pos].top.st & FS_BUTTONDOWN) != 0) { map[pos].top.st &= ~FS_BUTTONDOWN; id = map[pos].top.id; }
            else if ((map[pos].bot.st & FS_BUTTONDOWN) != 0) { map[pos].bot.st &= ~FS_BUTTONDOWN; id = map[pos].bot.id; }
            else continue;
            switch (id) {
                case Button_Blue: sound(SND_BUTTON_PUSHED); turntanks(null); break;
                case Button_Green: togglewalls(); break;
                case Button_Red: activatecloner(pos); sound(SND_BUTTON_PUSHED); break;
                case Button_Brown: springtrap(pos); sound(SND_BUTTON_PUSHED); break;
            }
        }
    }

    boolean startmovement(Cr cr, int dir) {
        int floor = map[cr.pos].bot.id;
        if (!canmakemove(cr, dir, 0)) {
            if (cr.id == Chip || (floor != Beartrap && floor != CloneMachine && (cr.state & CS_SLIP) == 0)) {
                cr.dir = dir;
                updatecreature(cr);
            }
            return false;
        }
        if (floor == Beartrap) {
            if ((cr.state & CS_MUTANT) != 0) map[cr.pos].bot.st &= ~FS_HASMUTANT;
        }
        cr.state &= ~CS_RELEASED;
        cr.dir = dir;
        return true;
    }

    void endmovement(Cr cr, int dir) {
        boolean dead = false;
        int oldpos = cr.pos, newpos = cr.pos + (dir < 9 ? DELTA[dir] : 0);
        Cell cell = map[newpos];
        Tile tile = cell.top;
        int floor = tile.id, id, i;
        if (cr.id == Chip) {
            switch (floor) {
                case Empty: poptile(newpos); break;
                case Water: if (poss(Boots_Water) == 0) chipstatus = CHIP_DROWNED; break;
                case Fire: if (poss(Boots_Fire) == 0) chipstatus = CHIP_BURNED; break;
                case Dirt: poptile(newpos); break;
                case BlueWall_Fake: poptile(newpos); break;
                case PopupWall: tile.id = Wall; break;
                case Door_Red: case Door_Blue: case Door_Yellow: case Door_Green:
                    if (floor != Door_Green) inv[pi(floor)]--;
                    poptile(newpos);
                    sound(SND_DOOR_OPENED);
                    break;
                case Boots_Ice: case Boots_Slide: case Boots_Fire: case Boots_Water:
                case Key_Red: case Key_Blue: case Key_Yellow: case Key_Green:
                    if (iscreature(cell.bot.id)) chipstatus = CHIP_COLLIDED;
                    inv[pi(floor)]++;
                    poptile(newpos);
                    sound(SND_ITEM_COLLECTED);
                    break;
                case Burglar:
                    inv[4] = inv[5] = inv[6] = inv[7] = 0;
                    sound(SND_BOOTS_STOLEN);
                    break;
                case ICChip:
                    if (chipsneeded != 0) --chipsneeded;
                    poptile(newpos);
                    sound(SND_IC_COLLECTED);
                    break;
                case Socket: poptile(newpos); sound(SND_SOCKET_OPENED); break;
                case Bomb: chipstatus = CHIP_BOMBED; sound(SND_BOMB_EXPLODES); break;
                default: if (iscreature(floor)) chipstatus = CHIP_COLLIDED; break;
            }
        } else if (cr.id == Block) {
            switch (floor) {
                case Empty: poptile(newpos); break;
                case Water: tile.id = Dirt; dead = true; sound(SND_WATER_SPLASH); break;
                case Bomb: tile.id = Empty; dead = true; sound(SND_BOMB_EXPLODES); break;
                case Teleport: if ((tile.st & FS_BROKEN) == 0) newpos = teleportcreature(cr, newpos); break;
            }
            id = map[oldpos].top.id;
            if (iscreature(id) && creatureid(id) == Chip) cr.state |= CS_MUTANT;
        } else {
            if (iscreature(cell.top.id)) { tile = cell.bot; floor = cell.bot.id; }
            switch (floor) {
                case Water: if (cr.id != Glider) dead = true; break;
                case Fire: if (cr.id != Fireball) dead = true; break;
                case Bomb: cell.top.id = Empty; dead = true; sound(SND_BOMB_EXPLODES); break;
                case Teleport: if ((tile.st & FS_BROKEN) == 0) newpos = teleportcreature(cr, newpos); break;
            }
        }
        if (map[oldpos].bot.id != CloneMachine || cr.id == Chip) poptile(oldpos);
        if (dead) {
            removecreature(cr);
            if (map[oldpos].bot.id == CloneMachine) map[oldpos].bot.st &= ~FS_CLONING;
            return;
        }
        if (cr.id == Chip && floor == Teleport && (tile.st & FS_BROKEN) == 0) {
            i = newpos;
            newpos = teleportcreature(cr, newpos);
            if (newpos != i) {
                sound(SND_TELEPORTING);
                if (floorat(newpos) == Block_Static) {
                    if (lastslipdir == NIL) {
                        cr.dir = N;
                        map[newpos].top.id = crtile(Chip, N);
                        floor = Empty;
                    } else cr.dir = lastslipdir;
                }
            }
        }
        cr.pos = newpos;
        addcreaturetomap(cr);
        cr.pos = oldpos;
        tile = cell.bot;
        switch (floor) {
            case Button_Blue:
                if ((cr.state & CS_DEFERPUSH) != 0) tile.st |= FS_BUTTONDOWN; else turntanks(cr);
                sound(SND_BUTTON_PUSHED);
                break;
            case Button_Green:
                if ((cr.state & CS_DEFERPUSH) != 0) tile.st |= FS_BUTTONDOWN; else togglewalls();
                break;
            case Button_Red:
                if ((cr.state & CS_DEFERPUSH) != 0) tile.st |= FS_BUTTONDOWN; else activatecloner(newpos);
                sound(SND_BUTTON_PUSHED);
                break;
            case Button_Brown:
                if ((cr.state & CS_DEFERPUSH) != 0) tile.st |= FS_BUTTONDOWN; else springtrap(newpos);
                sound(SND_BUTTON_PUSHED);
                break;
        }
        cr.pos = newpos;
        if (map[oldpos].bot.id == CloneMachine) map[oldpos].bot.st &= ~FS_CLONING;
        if (floor == Beartrap) {
            if (istrapopen(newpos, oldpos)) cr.state |= CS_RELEASED;
        } else if (map[newpos].bot.id == Beartrap) {
            for (i = 0; i < trapN; i++) if (trapTo[i] == newpos) { cr.state |= CS_RELEASED; break; }
        }
        if (cr.id == Chip) {
            if (chipstatus != CHIP_OKAY) return;
            if (cell.bot.id == Exit) { completed = true; return; }
        } else if (iscreature(cell.bot.id)) {
            if (creatureid(cell.bot.id) == Chip || creatureid(cell.bot.id) == Swimming_Chip) {
                chipstatus = CHIP_COLLIDED;
                return;
            }
        }
        int wasslipping = cr.state & (CS_SLIP | CS_SLIDE);
        if (floor == Teleport) startfloormovement(cr, floor);
        else if (isice(floor) && (cr.id != Chip || poss(Boots_Ice) == 0)) startfloormovement(cr, floor);
        else if (isslide(floor) && (cr.id != Chip || poss(Boots_Slide) == 0)) startfloormovement(cr, floor);
        else if (floor == Beartrap && cr.id == Block && wasslipping != 0) {
            startfloormovement(cr, floor);
            if ((cr.state & CS_MUTANT) != 0) cell.bot.st |= FS_HASMUTANT;
        } else cr.state &= ~(CS_SLIP | CS_SLIDE);
        if (wasslipping == 0 && (cr.state & (CS_SLIP | CS_SLIDE)) != 0 && cr.id != Chip) controllerdir = getslipdir(cr);
    }

    boolean advancecreature(Cr cr, int dir) {
        if (dir == NIL) return true;
        if (cr.id == Chip) chipwait = 0;
        if (!startmovement(cr, dir)) {
            if (cr.id == Chip) { sound(SND_CANT_MOVE); resetbuttons(); }
            return false;
        }
        endmovement(cr, dir);
        if (cr.id == Chip) handlebuttons();
        return true;
    }

    int checkforending() {
        if (chipstatus != CHIP_OKAY) { sound(SND_CHIP_LOSES); return -1; }
        if (completed) { sound(SND_CHIP_WINS); return 1; }
        return 0;
    }

    void floormovements() {
        for (int n = 0; n < slips.size(); n++) {
            int saved = slips.size();
            Cr cr = slips.get(n).cr;
            if ((cr.state & (CS_SLIP | CS_SLIDE)) == 0) continue;
            int slipdir = slips.get(n).dir;
            if (slipdir == NIL) continue;
            if (cr.id == Chip) lastslipdir = slipdir;
            if (advancecreature(cr, slipdir)) {
                if (cr.id == Chip) cr.state &= ~CS_HASMOVED;
            } else {
                int floor = map[cr.pos].bot.id;
                if (isslide(floor)) {
                    if (cr.id == Chip) cr.state &= ~CS_HASMOVED;
                } else if (isice(floor)) {
                    slipdir = icewallturn(floor, back(slipdir));
                    if (cr.id == Chip) lastslipdir = slipdir;
                    if (advancecreature(cr, slipdir)) if (cr.id == Chip) cr.state &= ~CS_HASMOVED;
                } else if (cr.id == Chip) {
                    if (floor == Teleport || floor == Block_Static) {
                        lastslipdir = slipdir = back(slipdir);
                        if (advancecreature(cr, slipdir)) cr.state &= ~CS_HASMOVED;
                    }
                }
                if ((cr.state & (CS_SLIP | CS_SLIDE)) != 0) {
                    endfloormovement(cr);
                    startfloormovement(cr, map[cr.pos].bot.id);
                }
            }
            if (checkforending() != 0) return;
            if ((cr.state & (CS_SLIP | CS_SLIDE)) == 0 && cr.id != Chip && slips.size() == saved + 1) ++n;
        }
    }

    void createclones() {
        for (Cr c : creatures) if ((c.state & CS_CLONING) != 0) c.state &= ~CS_CLONING;
    }

    void initialhousekeeping() {
        if ((currenttime & 3) == 0) {
            for (int n = 1; n < creatures.size(); n++) {
                Cr c = creatures.get(n);
                if ((c.state & CS_TURNING) != 0) { c.state &= ~(CS_TURNING | CS_HASMOVED); updatecreature(c); }
            }
            if (++chipwait > 3) { chipwait = 3; chip().dir = S; updatecreature(chip()); }
        }
    }

    void preparedisplay() { showhint = map[chip().pos].bot.id == HintButton; }

    // ================= initgame / advancegame =================
    private void init() {
        int pos, n;
        Cell cell;
        for (pos = 0; pos < 1024; pos++) {
            cell = map[pos];
            if (cell.top.id <= Floor_Reserved1 || creatureid(cell.top.id) == Chip || creatureid(cell.top.id) == Block)
                if (cell.bot.id == Teleport || cell.bot.id == SwitchWall_Open || cell.bot.id == SwitchWall_Closed)
                    cell.bot.st |= FS_BROKEN;
        }
        Cr chip = new Cr();
        chip.pos = 0; chip.id = Chip; chip.dir = S;
        creatures.add(chip);
        for (n = 0; n < crN; n++) {
            pos = crlist[n];
            if (pos < 0 || pos >= 1024) continue;
            cell = map[pos];
            if (!iscreature(cell.top.id)) continue;
            if (creatureid(cell.top.id) != Block && cell.bot.id != CloneMachine) {
                Cr cr = new Cr();
                cr.pos = pos; cr.id = creatureid(cell.top.id); cr.dir = creaturedirid(cell.top.id);
                creatures.add(cr);
                if (iscreature(cell.bot.id) && creatureid(cell.bot.id) == Chip) {
                    chip.pos = pos;
                    chip.dir = creaturedirid(cell.bot.id);
                }
            }
            cell.top.st |= FS_MARKER;
        }
        for (pos = 0; pos < 1024; pos++) {
            cell = map[pos];
            if ((cell.top.st & FS_MARKER) != 0) cell.top.st &= ~FS_MARKER;
            else if (iscreature(cell.top.id) && creatureid(cell.top.id) == Chip) {
                chip.pos = pos;
                chip.dir = creaturedirid(cell.bot.id);
            }
        }
        for (n = 0; n < trapN; n++)
            if (trapTo[n] == chip.pos || (trapTo[n] < 1024 && map[trapTo[n]].top.id == Block_Static) || istrapbuttondown(trapFrom[n]))
                springtrap(trapFrom[n]);
        chipwait = 0; completed = false; chipstatus = CHIP_OKAY; controllerdir = NIL; lastslipdir = NIL;
        preparedisplay();
    }

    // ================= Undo (im Original nicht vorhanden) =================
    /** Momentaufnahme des veraenderlichen Spielzustands (Karte, Inventar, Kreaturen, Zufall). */
    int[] save() {
        int[] s = new int[4096 + 23 + 6 * (creatures.size() + blocks.size()) + 2 * slips.size()];
        int p = 0;
        for (Cell c : map) { s[p++] = c.top.id; s[p++] = c.top.st; s[p++] = c.bot.id; s[p++] = c.bot.st; }
        for (int v : inv) s[p++] = v;
        for (int v : new int[] {chipsneeded, currenttime, timeoffset, input, sfx, showhint ? 1 : 0, (int) rnd,
                chipwait, chipstatus, controllerdir, lastslipdir, completed ? 1 : 0}) s[p++] = v;
        for (int k = 0; k < 2; k++) {
            ArrayList<Cr> l = k == 0 ? creatures : blocks;
            s[p++] = l.size();
            for (Cr c : l) {
                s[p++] = c.pos; s[p++] = c.id; s[p++] = c.dir; s[p++] = c.tdir; s[p++] = c.state; s[p++] = c.hidden ? 1 : 0;
            }
        }
        s[p++] = slips.size();
        for (Slip x : slips) {
            int i = creatures.indexOf(x.cr);
            s[p++] = i >= 0 ? i : creatures.size() + blocks.indexOf(x.cr);
            s[p++] = x.dir;
        }
        return s;
    }

    /** Stellt eine mit save() gemachte Momentaufnahme wieder her. */
    void load(int[] s) {
        int p = 0;
        for (Cell c : map) { c.top.id = s[p++]; c.top.st = s[p++]; c.bot.id = s[p++]; c.bot.st = s[p++]; }
        for (int i = 0; i < 8; i++) inv[i] = s[p++];
        chipsneeded = s[p++]; currenttime = s[p++]; timeoffset = s[p++]; input = s[p++]; sfx = s[p++];
        showhint = s[p++] != 0; rnd = s[p++]; chipwait = s[p++]; chipstatus = s[p++];
        controllerdir = s[p++]; lastslipdir = s[p++]; completed = s[p++] != 0;
        creatures.clear(); blocks.clear(); slips.clear();
        for (int k = 0; k < 2; k++)
            for (int n = s[p++]; n > 0; n--) {
                Cr c = new Cr();
                c.pos = s[p++]; c.id = s[p++]; c.dir = s[p++]; c.tdir = s[p++]; c.state = s[p++]; c.hidden = s[p++] != 0;
                (k == 0 ? creatures : blocks).add(c);
            }
        for (int n = s[p++]; n > 0; n--) {
            int i = s[p++];
            slips.add(new Slip(i < creatures.size() ? creatures.get(i) : blocks.get(i - creatures.size()), s[p++]));
        }
    }

    /** Ein Tick (doturn + advancegame). Rueckgabe: +1 gewonnen, -1 verloren, 0 weiter. */
    int turn(int tick, int cmd) {
        sfx &= ~((1 << 18) - 1);
        currenttime = tick;
        input = cmd;
        return advance();
    }

    private int advance() {
        int r = 0, n;
        Cr cr;
        timeoffset = -1;
        initialhousekeeping();
        done:
        {
            if (currenttime != 0 && (currenttime & 1) == 0) {
                controllerdir = NIL;
                for (n = 0; n < creatures.size(); n++) {
                    cr = creatures.get(n);
                    if (cr.hidden || (cr.state & CS_CLONING) != 0 || cr.id == Chip) continue;
                    choosemove(cr);
                    if (cr.tdir != NIL) advancecreature(cr, cr.tdir);
                }
                if ((r = checkforending()) != 0) break done;
            }
            if (currenttime != 0 && (currenttime & 1) == 0) {
                floormovements();
                if ((r = checkforending()) != 0) break done;
            }
            updatesliplist();
            timeoffset = 0;
            int limit = time * 20;
            if (limit != 0) {
                if (currenttime >= limit) {
                    chipstatus = CHIP_OUTOFTIME;
                    sound(SND_TIME_OUT);
                    return -1;
                } else if (limit - currenttime <= 15 * 20 && currenttime % 20 == 0) sound(SND_TIME_LOW);
            }
            cr = chip();
            choosemove(cr);
            if (cr.tdir != NIL) {
                if (advancecreature(cr, cr.tdir)) if ((r = checkforending()) != 0) break done;
                cr.state |= CS_HASMOVED;
            }
            updatesliplist();
            createclones();
        }
        preparedisplay();
        return r;
    }
}
