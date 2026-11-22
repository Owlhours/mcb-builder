package mcb.bot;

import mcb.build.ItemInfo;
import mcb.game.Stack;
import mcb.game.SlotClick;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mcb.build.Site;
import mcb.build.StateInfo;
import mcb.game.Client;
import mcb.game.Oracle;

/** Trips to the supply chests: what to carry for the work ahead, what to put back, in which order to fetch. */
abstract class Supplies extends Scaffolds {
    Supplies(Client c, Grid g, Site site, Oracle oracle, Rules rules) { super(c, g, site, oracle, rules); }

    int scaffoldTrips, towerTrips, chestLogs;

    /** OFF_STOCK_A / OFF_STOCK_B (candidate "offstock"; off, flips to System.getenv("MCB_NO_OFFSTOCK") == null on acceptance; MCB_OFFSTOCK_A / MCB_OFFSTOCK_B
     *  switch one part on): see restock and Builder.deferredPass. */
    static final boolean OFF_STOCK_A = System.getenv("MCB_OFFSTOCK") != null || System.getenv("MCB_OFFSTOCK_A") != null;
    static final boolean OFF_STOCK_B = System.getenv("MCB_OFFSTOCK") != null || System.getenv("MCB_OFFSTOCK_B") != null;

    int missingNow(Map<Integer, Integer> need, int it) {
        Integer n = need.get(it);
        return n == null ? 0 : n - count(it);
    }

    /** Goes to the chests and loads up for the cells still to do, lowest layers first. */
    void restock(int fromLayer) {
        restocking = true;
        try {
            restock0(fromLayer);
        } finally {
            restocking = false;
        }
        if (verbose) log("after restock: " + inventorySummary());
        if (verbose && count(scaffoldItem) == 0) {
            StringBuilder sb = new StringBuilder("no loose blocks after restock; item=" + scaffoldItem + " depot:");
            int n = 0;
            for (Site.Stock st : stock) if (st.item == scaffoldItem && n++ < 6) sb.append(' ').append(st.count).append('@').append(rel(st.chestX, st.chestY, st.chestZ)).append('/').append(st.slot);
            log(sb.toString());
        }
    }

    void restock0(int fromLayer) {
        String old = enter("restock");
        try { restock0Impl(fromLayer); } finally { activity = old; }
    }

    void restock0Impl(int fromLayer) {
        looseFails.clear();
        looseBlocked.clear();
        nav.blockedEdges.clear();   // (steps that failed once: the build has moved on since, let them be tried again)
        endLean();
        if (!rope.isEmpty()) undoRope();
        // (WALK_WAY: Rust takes its walkway down here when wing_recs is not empty - only with `walkdown`, left out)
        trips++;
        Map<Integer, Integer> need = new LinkedHashMap<>();
        int slotsUsed = 0;
        // first what the cell in hand needs, and what the cells put off until later need
        if (mustFetch != ItemInfo.NONE) { need.put(mustFetch, ItemInfo.maxStack(mustFetch)); slotsUsed++; }
        // scaffolding is used up (broken blocks drop nothing): carry more of it the more the build turns out to need
        int wantLoose = Math.min(64 * 4, 64 + 64 * scaffoldTrips), wantTower = Math.min(64 * 6, 128 + 64 * towerTrips);
        need.merge(scaffoldItem, wantLoose, Integer::sum);
        if (count(scaffoldItem) < 16) scaffoldTrips++;
        need.merge(towerItem, wantTower, Integer::sum);
        if (count(towerItem) < 16) towerTrips++;
        int reserved = (wantLoose + 63) / 64 + (wantTower + 63) / 64;
        // (what was put off is fetched when its time comes, at the end; until then it would only fill the inventory)
        for (int cell : deferred) {
            if (!mainDone || !needs(cell) || slotsUsed >= 16) continue;
            if (!lateToo && isLate(cell)) continue;
            int t = g.tgt[cell];
            int it = StateInfo.item[t];
            if (it == ItemInfo.AIR || StateInfo.itemCount[t] == 0) continue;
            int now = need.getOrDefault(it, 0);
            if (now == 0) slotsUsed++;
            need.put(it, now + StateInfo.itemCount[t]);
        }
        int capacity = 35 - reserved;
        // then what the bot will be doing next: the cells that may go in now, nearest first; then what follows them
        // in their columns; then the same for columns that have to wait for a neighbour - until the inventory is full
        int bx = Math.max(g.ox, Math.min(g.ox + g.sx - 1, cx())), bz = Math.max(g.oz, Math.min(g.oz + g.sz - 1, cz()));
        int maxR = Math.max(g.sx, g.sz);
        long budget = 36L * 64 * 3;
        int topY = g.oy + g.sy;
        outer:
        for (int pass = 0; pass < 4 && colNext != null; pass++) {
            int from = pass < 2 ? 0 : pass == 2 ? 1 : 4, to = pass < 2 ? 1 : pass == 2 ? 4 : 10;   // cells above the column's next one
            if (slotsUsed >= capacity && pass >= 2) break;
            for (int r = 0; r <= maxR; r++) {
                for (int dz = -r; dz <= r; dz++) {
                    int z = bz + dz;
                    if (z < g.oz || z >= g.oz + g.sz) continue;
                    int step = (dz == -r || dz == r) ? 1 : 2 * r;
                    for (int dx = -r; dx <= r; dx += Math.max(1, step)) {
                        int x = bx + dx;
                        if (x < g.ox || x >= g.ox + g.sx) continue;
                        int y0 = colNext[col(x, z)];
                        if (y0 == NONE) continue;
                        if (pass == 0 && !eligible(x, z)) continue;
                        if (pass == 1 && eligible(x, z)) continue;
                        for (int y = y0 + from; y < Math.min(y0 + to, topY); y++) {
                            int i = g.idx(x, y, z);
                            if (!needs(i) || deferred.contains(i)) continue;
                            if (blocked.containsKey(i) && blocked.get(i) == surroundings(i)) continue;   // cannot go in as things stand
                            int t = g.tgt[i];
                            int it = StateInfo.item[t];
                            if (it == ItemInfo.AIR || StateInfo.itemCount[t] == 0 || isLate(i) || !spec(t).placeable) continue;
                            int max = ItemInfo.maxStack(it);
                            int now = need.getOrDefault(it, 0);
                            int add = StateInfo.itemCount[t];
                            if (now == 0 || (now + add - 1) / max != (now - 1) / max) {
                                if (slotsUsed + 1 > capacity) continue;   // no room for another stack: this cell waits for the next trip
                                slotsUsed++;
                            }
                            need.put(it, now + add);
                            int extra = StateInfo.contentItem[t];
                            if (extra != ItemInfo.NONE) { if (!need.containsKey(extra)) slotsUsed++; need.merge(extra, 1, Integer::sum); }
                            if (--budget < 0) break outer;
                        }
                    }
                }
            }
        }
        // OFF_STOCK_A (candidate "offstock", part a): after the main pass the list is the sum of all put-off cells, and that does not
        // fit: the first kind on it took every slot and the next cell of another kind sent the bot back to swap the whole
        // load again. The load is planned by slots instead: what the bot is sent for and the kinds it holds first, one slot
        // each, then the slots left in proportion to what each kind's cells still take; nothing beyond its share is missing.
        if (mainDone && OFF_STOCK_A) {
            List<Integer> kinds = new ArrayList<>();
            List<Integer> slotsOf = new ArrayList<>();
            int total = 0;
            for (var e : need.entrySet()) {
                int it = e.getKey();
                if (it == scaffoldItem || it == towerItem || e.getValue() <= 0) continue;
                int st = ItemInfo.maxStack(it);
                int sl = (e.getValue() + st - 1) / st;
                kinds.add(it); slotsOf.add(sl); total += sl;
            }
            if (total > capacity && capacity > 0) {
                int nk = kinds.size();
                int[] held = new int[nk];
                Integer[] order = new Integer[nk];
                for (int k = 0; k < nk; k++) { held[k] = count(kinds.get(k)); order[k] = k; }
                // order: the kind the trip is for, kinds held (most first), the rest as listed (a stable sort)
                java.util.Arrays.sort(order, java.util.Comparator.<Integer, Boolean>comparing(k -> kinds.get(k) != mustFetch)
                        .thenComparing(k -> held[k] == 0).thenComparing(k -> -held[k]));
                int[] alloc = new int[nk];
                int left = capacity;
                for (int k : order) { if (left == 0) break; alloc[k] = 1; left--; }
                long rest = 0;
                for (int k : order) if (alloc[k] == 1) rest += slotsOf.get(k) - 1;
                if (rest > 0 && left > 0) {
                    long pool = left;
                    for (int k : order) if (alloc[k] == 1) { int add = (int) (pool * (slotsOf.get(k) - 1) / rest); alloc[k] += add; left -= add; }
                    for (int k : order) { if (left == 0) break; if (alloc[k] > 0 && alloc[k] < slotsOf.get(k)) { alloc[k]++; left--; } }
                }
                for (int k = 0; k < nk; k++) {
                    int it = kinds.get(k);
                    int cap = alloc[k] * ItemInfo.maxStack(it);
                    need.put(it, Math.min(need.get(it), cap));
                }
            }
        }
        List<Site.Stock> todo = new ArrayList<>();
        Map<Integer, Integer> missing = new LinkedHashMap<>();
        for (var e : need.entrySet()) { int m = e.getValue() - count(e.getKey()); if (m > 0) missing.put(e.getKey(), m); }
        if (missing.isEmpty()) return;
        for (Site.Stock s : stock) {
            if (s.count <= 0) continue;
            Integer m = missing.get(s.item);
            if (m == null || m <= 0) continue;
            todo.add(s);
            missing.put(s.item, m - s.count);
        }
        if (todo.isEmpty()) return;
        log("restock: " + todo.size() + " stacks");
        // most urgent first (the order in which the needs were collected)
        List<Integer> order = new ArrayList<>(need.keySet());
        todo.sort(java.util.Comparator.comparingInt(st -> order.indexOf(st.item)));
        boolean tidied = false;
        int k = 0;
        while (k < todo.size()) {
            Site.Stock first = todo.get(k);
            if (first.count <= 0) { k++; continue; }
            int ax = standX(first), ay = standY(first), az = standZ(first);
            if (!(cx() == ax && cy() == ay && cz() == az)) {
                boolean ok = false;
                for (int attempt = 0; attempt < 2 && !ok; attempt++) {
                    // a route may not need more scaffolding than the bot still carries
                    ok = search(ax, ay, az, 200000);
                    if (ok && shortOfScaffold()) { noScaffoldNav = true; ok = search(ax, ay, az, 200000); noScaffoldNav = false; }
                    ok = ok && travel();
                }
                for (int attempt = 0; attempt < 2 && !ok && !mendOnly; attempt++) {
                    // shut in: break a way out through the build (what is broken is put back at the end)
                    nav.mine = this::mineTicks;
                    nav.effort = 20;
                    ok = search(ax, ay, az, 200000);
                    if ((ok && shortOfScaffold()) || (!ok && attempt == 1)) { noScaffoldNav = true; ok = search(ax, ay, az, 400000); noScaffoldNav = false; }
                    if (!ok && attempt == 1) { pocket = enclosed(); desperate = true; ok = search(ax, ay, az, 400000); if (!ok && pocket && Moves.LAST_BREAK) { reckless = true; try { ok = search(ax, ay, az, 400000); } finally { reckless = false; } if (ok) recklessWays++; } desperate = false; if (ok && pocket) pocketWays++; pocket = false; }
                    nav.mine = null;
                    nav.effort = 1;
                    ok = ok && travel();
                }
                if (!ok) {
                    if (verbose) {
                        StringBuilder sb2 = new StringBuilder("cannot reach chest; pos=" + String.format("%.2f,%.2f,%.2f", c.x() - g.ox, c.y() - g.oy, c.z() - g.oz) + " ground=" + c.onGround() + " stand=" + g.stand16(cx(), cy(), cz()) + " expanded=" + nav.expanded + " around (y+2..y-1; rows z-1..z+1, cols x-1..x+1):");
                        for (int dy = 2; dy >= -1; dy--) { sb2.append(" | y").append(dy >= 0 ? "+" : "").append(dy).append(':'); for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) sb2.append(' ').append(StateInfo.name(g.get(cx() + dx, cy() + dy, cz() + dz))); }
                        log(sb2.toString());
                    }
                    stranded = true; strandedTick = ticks; return;
                }
                stranded = false;
            }
            settle();
            lookAt(lookX(first), lookY(first), lookZ(first));
            c.clickUse();
            tick();
            // (registered containers) the screen opens when the server says so: wait for it a while (latency, Timer)
            for (int w = 0; w < 40 && !c.containerOpen() && c.waitingForServer(w); w++) tick();
            if (!c.containerOpen()) { tick(); if (!c.containerOpen()) {
                if (verbose && chestLogs++ < 20) log("chest at " + rel(first.chestX, first.chestY, first.chestZ) + " did not open; hit=" + (c.lastHit() == null ? "none" : rel(c.lastHit().x, c.lastHit().y, c.lastHit().z) + " " + StateInfo.name(g.get(c.lastHit().x, c.lastHit().y, c.lastHit().z))) + " above=" + StateInfo.name(g.get(first.chestX, first.chestY + 1, first.chestZ)) + " pos=" + String.format("%.2f,%.2f,%.2f", c.x() - g.ox, c.y() - g.oy, c.z() - g.oz) + " screen=" + c.screenOpen() + " sneaking=" + c.sneaking());
                k++; continue; } }
            // make room: put back what this load does not call for, and whole stacks beyond what it calls for
            boolean deposited = false;
            for (int inv = 0; inv < 36 && !tidied; inv++) {
                Stack st = c.inventorySlot(inv);
                if (st.isEmpty()) continue;
                Integer want = need.get(st.getItem());
                if (want != null && count(st.getItem()) - st.getCount() < want) continue;
                int menuSlot = invSlot(inv);
                if (menuSlot >= c.menuSize()) break;
                int before = st.getCount();
                c.clickSlot(menuSlot, 0, SlotClick.QUICK_MOVE);
                tick();
                if (c.inventorySlot(inv).isEmpty() || c.inventorySlot(inv).getCount() != before) deposited = true;
            }
            // still crowded (the chest took nothing more)? then the tidying goes on at the next chest
            int unneeded = 0;
            for (int inv = 0; inv < 36; inv++) { Stack st = c.inventorySlot(inv); if (!st.isEmpty() && !need.containsKey(st.getItem())) unneeded++; }
            tidied = unneeded == 0 || freeSlots() >= 6;
            // everything on the list that this chest holds
            for (int j = k; j < todo.size(); j++) {
                Site.Stock s = todo.get(j);
                if (s.count <= 0 || s.chestX != first.chestX || s.chestY != first.chestY || s.chestZ != first.chestZ) continue;
                if (j != k && missingNow(need, s.item) <= 0) continue;
                Stack inSlot = c.menuSlot(s.slot);
                if (inSlot.isEmpty() || inSlot.getItem() != s.item) {
                    // not what the list says is here: believe the chest
                    log("chest slot " + s.slot + " at " + rel(s.chestX, s.chestY, s.chestZ) + " holds " + inSlot + ", expected " + s.item + " x" + s.count);
                    s.count = inSlot.isEmpty() || inSlot.getItem() != s.item ? 0 : inSlot.getCount();
                    continue;
                }
                int before = count(s.item);
                if (freeSlots() == 0 && order.indexOf(s.item) < 8) {
                    // no room for something urgent: the least urgent stack carried goes back into this chest
                    int victim = -1, worst = order.indexOf(s.item);
                    for (int inv = 0; inv < 36; inv++) {
                        Stack st = c.inventorySlot(inv);
                        if (st.isEmpty() || st.getItem() == scaffoldItem || st.getItem() == towerItem) continue;
                        int pr = order.indexOf(st.getItem());
                        if (pr < 0) pr = Integer.MAX_VALUE;
                        if (pr > worst) { worst = pr; victim = inv; }
                    }
                    if (victim >= 0) {
                        int menuSlot = invSlot(victim);
                        if (menuSlot < c.menuSize()) { c.clickSlot(menuSlot, 0, SlotClick.QUICK_MOVE); tick(); deposited = true; }
                    }
                }
                c.clickSlot(s.slot, 0, SlotClick.QUICK_MOVE);
                tick();
                s.count = c.menuSlot(s.slot).getCount();
                if (count(s.item) == before) { if (verbose) log("taking " + s.item + " from slot " + s.slot + " did not arrive; free=" + freeSlots()); if (j == k) s.count = -s.count - 1; }
            }
            // (a stack that would not come - no room - is left for the next trip)
            if (first.count < 0) { first.count = -first.count - 1; k++; }
            if (deposited && c.menuSize() > 36) {
                int nSlots = c.menuSize() - 36;
                // the chest's contents changed in ways the list does not know: read them off the open screen
                for (Site.Stock s : stock) if (s.chestX == first.chestX && s.chestY == first.chestY && s.chestZ == first.chestZ) s.count = 0;
                for (int slot = 0; slot < nSlots; slot++) {
                    Stack st = c.menuSlot(slot);
                    if (st.isEmpty()) continue;
                    Site.Stock found = null;
                    for (Site.Stock s : stock) if (s.chestX == first.chestX && s.chestY == first.chestY && s.chestZ == first.chestZ && s.slot == slot) { found = s; break; }
                    if (found == null) stock.add(new Site.Stock(st.getItem(), st.getCount(), first.chestX, first.chestY, first.chestZ, slot));
                    else { found.item = st.getItem(); found.count = st.getCount(); }
                }
            }
            c.closeScreen();
            tick();
        }
    }

    /** The open container screen's slot number of player inventory slot inv (0-8 hotbar, 9-35 main),
     *  for a container of any size (a chest, a double chest, a barrel, a shulker box). */
    int invSlot(int inv) {
        int n = c.menuSize() - 36;
        return inv < 9 ? n + 27 + inv : n + (inv - 9);
    }

    /** (The client mod) Reads every registered container once: walks there, opens it, writes down what is in it. */
    public void survey() {
        boolean wasRestocking = restocking;
        restocking = true;
        try { surveyImpl(); } finally { restocking = wasRestocking; }
    }
    private void surveyImpl() {
        java.util.List<Site.Stock> keys = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Site.Stock s : stock) if (seen.add(mcb.game.Pos.pack(s.chestX, s.chestY, s.chestZ))) keys.add(s);
        for (Site.Stock first : keys) {
            boolean ok = (cx() == standX(first) && cy() == standY(first) && cz() == standZ(first)) || (search(standX(first), standY(first), standZ(first), 200000) && travel());
            if (!ok) { surveyFailures.add(first.chestX + " " + first.chestY + " " + first.chestZ + ": no way there"); continue; }
            settle();
            lookAt(lookX(first), lookY(first), lookZ(first));
            c.clickUse();
            tick();
            for (int w = 0; w < 40 && !c.containerOpen() && c.waitingForServer(w); w++) tick();
            if (!c.containerOpen()) { surveyFailures.add(first.chestX + " " + first.chestY + " " + first.chestZ + ": did not open"); continue; }
            for (int w = 0; w < 5; w++) tick();   // (the contents come just after the screen)
            int nSlots = c.menuSize() - 36;
            stock.removeIf(s -> s.chestX == first.chestX && s.chestY == first.chestY && s.chestZ == first.chestZ);
            boolean any = false;
            for (int slot = 0; slot < nSlots; slot++) {
                Stack st = c.menuSlot(slot);
                if (st.isEmpty()) continue;
                Site.Stock ns = new Site.Stock(st.getItem(), st.getCount(), first.chestX, first.chestY, first.chestZ, slot);
                ns.setAccess(first);
                stock.add(ns);
                any = true;
            }
            if (!any) { Site.Stock ns = new Site.Stock(ItemInfo.AIR, 0, first.chestX, first.chestY, first.chestZ, 0); ns.setAccess(first); stock.add(ns); }
            c.closeScreen();
            tick();
        }
        // the anchor (stock.get(0)) stays a real container
    }
    public final java.util.List<String> surveyFailures = new java.util.ArrayList<>();
}
