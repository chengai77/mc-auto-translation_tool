package org.universaltranslator.neoforge;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.universaltranslator.core.SpatialHologramLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 聚合同锚点全息片段。 */
public final class HologramTextDisplayGroups {
    private static final long GROUP_STALE_MILLIS = 3_000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 5_000L;
    private static final int MAX_GROUPS = 2_048;
    private static final int MAX_FRAGMENTS = 64;

    private static final ConcurrentHashMap<GroupKey, GroupState> GROUPS =
            new ConcurrentHashMap<GroupKey, GroupState>();
    private static final AtomicLong NEXT_CLEANUP = new AtomicLong();

    private HologramTextDisplayGroups() {
    }

    public static Result translate(
            int worldIdentity,
            int entityId,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int orientationHash,
            Text source,
            float localX,
            float localY,
            float scaleX,
            int pixelWidth,
            int alignment
    ) {
        if (source == null) {
            return Result.original(null);
        }
        long now = System.currentTimeMillis();
        cleanup(now);
        GroupKey key = new GroupKey(
                worldIdentity, x, y, z, yaw, pitch, orientationHash);
        GroupState group = GROUPS.computeIfAbsent(key, ignored -> new GroupState());
        FragmentState fragment = new FragmentState(
                new SpatialHologramLayout.Fragment(
                        entityId, source.getString(), localX, localY,
                        scaleX, pixelWidth, alignment),
                source);
        Lookup lookup = group.record(fragment, now);
        if (!lookup.stable()) {
            return Result.original(source);
        }
        PreparedRow row = lookup.row();
        if (row == null) {
            return Result.original(RenderedTextBridge.translateHologramText(source));
        }
        Text translated = row.translated();
        if (translated == null) {
            return Result.original(source);
        }
        if (row.hostId() != entityId) {
            return Result.hiddenResult();
        }
        return Result.grouped(translated, row.centerX() - localX);
    }

    static void clear() {
        GROUPS.clear();
        NEXT_CLEANUP.set(0L);
    }

    private static void cleanup(long now) {
        long next = NEXT_CLEANUP.get();
        if (now < next || !NEXT_CLEANUP.compareAndSet(next, now + CLEANUP_INTERVAL_MILLIS)) {
            return;
        }
        if (GROUPS.size() > MAX_GROUPS) {
            GROUPS.clear();
            return;
        }
        for (java.util.Map.Entry<GroupKey, GroupState> entry : GROUPS.entrySet()) {
            if (now - entry.getValue().lastSeen() > GROUP_STALE_MILLIS) {
                GROUPS.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public static final class Result {
        private final Text text;
        private final float horizontalOffset;
        private final boolean hidden;
        private final boolean centered;

        private Result(Text text, float horizontalOffset, boolean hidden, boolean centered) {
            this.text = text;
            this.horizontalOffset = horizontalOffset;
            this.hidden = hidden;
            this.centered = centered;
        }

        static Result original(Text text) {
            return new Result(text, 0.0F, false, false);
        }

        static Result hiddenResult() {
            return new Result(Text.empty(), 0.0F, true, false);
        }

        static Result grouped(Text text, float horizontalOffset) {
            return new Result(text, horizontalOffset, false, true);
        }

        public Text text() { return text; }
        public float horizontalOffset() { return horizontalOffset; }
        public boolean hidden() { return hidden; }
        public boolean centered() { return centered; }
    }

    private static final class GroupState {
        private final ConcurrentHashMap<Integer, FragmentState> cycle =
                new ConcurrentHashMap<Integer, FragmentState>();
        private final ConcurrentHashMap<Integer, FragmentState> stable =
                new ConcurrentHashMap<Integer, FragmentState>();
        private final ConcurrentHashMap<Integer, PreparedRow> rows =
                new ConcurrentHashMap<Integer, PreparedRow>();
        private volatile long lastSeen;

        synchronized Lookup record(FragmentState fragment, long now) {
            if (now - lastSeen > GROUP_STALE_MILLIS || cycle.size() >= MAX_FRAGMENTS) {
                cycle.clear();
                stable.clear();
                rows.clear();
            }
            if (cycle.containsKey(fragment.id())) {
                prepareStable();
                cycle.clear();
            }
            cycle.put(fragment.id(), fragment);
            lastSeen = now;
            FragmentState ready = stable.get(fragment.id());
            if (ready == null || !ready.matches(fragment)) {
                return Lookup.pending();
            }
            return Lookup.ready(rows.get(fragment.id()));
        }

        long lastSeen() {
            return lastSeen;
        }

        private void prepareStable() {
            stable.clear();
            stable.putAll(cycle);
            rows.clear();
            List<SpatialHologramLayout.Fragment> layout =
                    new ArrayList<SpatialHologramLayout.Fragment>(stable.size());
            for (FragmentState fragment : stable.values()) {
                layout.add(fragment.layout());
            }
            for (SpatialHologramLayout.Row row : SpatialHologramLayout.rows(layout)) {
                PreparedRow prepared = prepareRow(row);
                if (prepared == null) {
                    continue;
                }
                for (SpatialHologramLayout.Fragment fragment : row.fragments()) {
                    rows.put(fragment.id(), prepared);
                }
            }
        }

        private PreparedRow prepareRow(SpatialHologramLayout.Row row) {
            MutableText combined = Text.empty();
            for (int index = 0; index < row.fragments().size(); index++) {
                SpatialHologramLayout.Fragment layout = row.fragments().get(index);
                FragmentState fragment = stable.get(layout.id());
                if (fragment == null) {
                    return null;
                }
                if (row.spaceBefore(index)) {
                    combined.append(Text.literal(" "));
                }
                combined.append(fragment.text().copy());
            }
            return row.text().equals(combined.getString())
                    ? new PreparedRow(row.hostId(), row.centerX(), combined) : null;
        }
    }

    private static final class FragmentState {
        private final SpatialHologramLayout.Fragment layout;
        private final Text text;

        private FragmentState(SpatialHologramLayout.Fragment layout, Text text) {
            this.layout = layout;
            this.text = text;
        }

        int id() { return layout.id(); }
        SpatialHologramLayout.Fragment layout() { return layout; }
        Text text() { return text; }

        boolean matches(FragmentState other) {
            return other != null
                    && layout.text().equals(other.layout.text())
                    && Float.compare(layout.x(), other.layout.x()) == 0
                    && Float.compare(layout.y(), other.layout.y()) == 0;
        }
    }

    private static final class PreparedRow {
        private final int hostId;
        private final float centerX;
        private final Text source;
        private volatile Text translated;

        private PreparedRow(int hostId, float centerX, Text source) {
            this.hostId = hostId;
            this.centerX = centerX;
            this.source = source;
        }

        int hostId() { return hostId; }
        float centerX() { return centerX; }

        synchronized Text translated() {
            if (translated != null) {
                return translated;
            }
            Text candidate = RenderedTextBridge.translatePlainHologramText(source);
            if (candidate != source && !source.getString().equals(candidate.getString())) {
                translated = candidate;
            }
            return translated;
        }
    }

    private static final class Lookup {
        private static final Lookup PENDING = new Lookup(false, null);
        private final boolean stable;
        private final PreparedRow row;

        private Lookup(boolean stable, PreparedRow row) {
            this.stable = stable;
            this.row = row;
        }

        static Lookup pending() { return PENDING; }
        static Lookup ready(PreparedRow row) { return new Lookup(true, row); }
        boolean stable() { return stable; }
        PreparedRow row() { return row; }
    }

    private static final class GroupKey {
        private final int worldIdentity;
        private final long x;
        private final long y;
        private final long z;
        private final int yaw;
        private final int pitch;
        private final int orientationHash;

        private GroupKey(
                int worldIdentity, double x, double y, double z,
                float yaw, float pitch, int orientationHash) {
            this.worldIdentity = worldIdentity;
            this.x = quantize(x, 64.0D);
            this.y = quantize(y, 64.0D);
            this.z = quantize(z, 64.0D);
            this.yaw = (int) quantize(yaw, 10.0D);
            this.pitch = (int) quantize(pitch, 10.0D);
            this.orientationHash = orientationHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GroupKey)) {
                return false;
            }
            GroupKey key = (GroupKey) other;
            return worldIdentity == key.worldIdentity
                    && x == key.x && y == key.y && z == key.z
                    && yaw == key.yaw && pitch == key.pitch
                    && orientationHash == key.orientationHash;
        }

        @Override
        public int hashCode() {
            int result = worldIdentity;
            result = 31 * result + Long.hashCode(x);
            result = 31 * result + Long.hashCode(y);
            result = 31 * result + Long.hashCode(z);
            result = 31 * result + yaw;
            result = 31 * result + pitch;
            return 31 * result + orientationHash;
        }

        private static long quantize(double value, double scale) {
            return Math.round(value * scale);
        }
    }
}
