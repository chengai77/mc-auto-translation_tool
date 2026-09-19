package org.universaltranslator.core;

import java.util.concurrent.ConcurrentHashMap;

/** 跟随玩家全息追踪 */
public final class PlayerFollowHologramTracker {
    private static final double MIN_MOVEMENT_SQUARED = 0.0064D;
    private static final double MAX_TRACKING_ERROR_SQUARED = 0.1225D;
    private static final int REQUIRED_OBSERVATIONS = 3;
    private static final int MAX_MISSES = 10;

    private final ConcurrentHashMap<Long, Sample> samples =
            new ConcurrentHashMap<Long, Sample>();
    private final int maximumEntries;

    public PlayerFollowHologramTracker(int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
    }

    public synchronized Integer confirmedPlayerId(long entityKey) {
        Sample sample = samples.get(entityKey);
        return sample != null && sample.confirmed
                ? Integer.valueOf(sample.playerId) : null;
    }

    public synchronized Match observe(
            long entityKey,
            int playerId,
            boolean localPlayer,
            double entityX,
            double entityY,
            double entityZ,
            double playerX,
            double playerY,
            double playerZ
    ) {
        Sample previous = samples.get(entityKey);
        if (previous == null || previous.playerId != playerId) {
            samples.put(entityKey, new Sample(
                    playerId, localPlayer,
                    entityX, entityY, entityZ,
                    playerX, playerY, playerZ,
                    1, false, 0));
            trim();
            return Match.NONE;
        }

        int observations = previous.observations + 1;
        double playerMoveX = playerX - previous.originPlayerX;
        double playerMoveY = playerY - previous.originPlayerY;
        double playerMoveZ = playerZ - previous.originPlayerZ;
        double entityMoveX = entityX - previous.originEntityX;
        double entityMoveY = entityY - previous.originEntityY;
        double entityMoveZ = entityZ - previous.originEntityZ;
        double playerMovement = squaredLength(playerMoveX, playerMoveY, playerMoveZ);
        double entityMovement = squaredLength(entityMoveX, entityMoveY, entityMoveZ);
        double trackingError = squaredLength(
                entityMoveX - playerMoveX,
                entityMoveY - playerMoveY,
                entityMoveZ - playerMoveZ);
        boolean confirmed = previous.confirmed
                || (observations >= REQUIRED_OBSERVATIONS
                && playerMovement >= MIN_MOVEMENT_SQUARED
                && entityMovement >= MIN_MOVEMENT_SQUARED
                && trackingError <= MAX_TRACKING_ERROR_SQUARED);
        samples.put(entityKey, new Sample(
                playerId, localPlayer,
                previous.originEntityX, previous.originEntityY, previous.originEntityZ,
                previous.originPlayerX, previous.originPlayerY, previous.originPlayerZ,
                observations, confirmed, 0));
        trim();
        return confirmed ? new Match(true, localPlayer) : Match.NONE;
    }

    public synchronized Match miss(long entityKey) {
        Sample previous = samples.get(entityKey);
        if (previous == null) {
            return Match.NONE;
        }
        int misses = previous.misses + 1;
        if (misses > MAX_MISSES) {
            samples.remove(entityKey);
            return Match.NONE;
        }
        samples.put(entityKey, previous.withMisses(misses));
        return previous.confirmed
                ? new Match(true, previous.localPlayer) : Match.NONE;
    }

    private void trim() {
        while (samples.size() > maximumEntries) {
            samples.remove(samples.keys().nextElement());
        }
    }

    private static double squaredLength(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    private static final class Sample {
        private final int playerId;
        private final boolean localPlayer;
        private final double originEntityX;
        private final double originEntityY;
        private final double originEntityZ;
        private final double originPlayerX;
        private final double originPlayerY;
        private final double originPlayerZ;
        private final int observations;
        private final boolean confirmed;
        private final int misses;

        private Sample(
                int playerId,
                boolean localPlayer,
                double originEntityX,
                double originEntityY,
                double originEntityZ,
                double originPlayerX,
                double originPlayerY,
                double originPlayerZ,
                int observations,
                boolean confirmed,
                int misses
        ) {
            this.playerId = playerId;
            this.localPlayer = localPlayer;
            this.originEntityX = originEntityX;
            this.originEntityY = originEntityY;
            this.originEntityZ = originEntityZ;
            this.originPlayerX = originPlayerX;
            this.originPlayerY = originPlayerY;
            this.originPlayerZ = originPlayerZ;
            this.observations = observations;
            this.confirmed = confirmed;
            this.misses = misses;
        }

        private Sample withMisses(int misses) {
            return new Sample(
                    playerId, localPlayer,
                    originEntityX, originEntityY, originEntityZ,
                    originPlayerX, originPlayerY, originPlayerZ,
                    observations, confirmed, misses);
        }
    }

    public static final class Match {
        private static final Match NONE = new Match(false, false);

        private final boolean following;
        private final boolean localPlayer;

        private Match(boolean following, boolean localPlayer) {
            this.following = following;
            this.localPlayer = localPlayer;
        }

        public boolean following() {
            return following;
        }

        public boolean localPlayer() {
            return localPlayer;
        }
    }
}
