package dev.statecraft.client.state;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.UserError;
import java.util.List;
import java.util.Optional;

public record ClaimMapLayout(Rect map, int cellSize, int radius, Rect zoomIn, Rect zoomOut,
                             Rect target, Rect refresh, Rect status, Rect review, Rect clear, Rect back,
                             int contextY, int northY, int captionY) {
    public static final int MIN_RADIUS = 3;
    public static final int MAX_RADIUS = 8;

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < right() && py < bottom();
        }
    }

    public static ClaimMapLayout fit(int width, int height, int requestedRadius) {
        int radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, requestedRadius));
        int diameter = radius * 2 + 1;
        int cell = Math.max(1, Math.min(32, Math.min((width - 20) / diameter, (height - 135) / diameter)));
        int side = diameter * cell;
        int buttonWidth = (width - 28) / 3;
        return new ClaimMapLayout(new Rect((width - side) / 2, 47, side, side), cell, radius,
                new Rect(width - 52, 23, 18, 18), new Rect(width - 30, 23, 18, 18),
                new Rect(10, height - 68, width - 88, 18), new Rect(width - 74, height - 68, 64, 18),
                new Rect(10, height - 46, width - 20, 14),
                new Rect(10, height - 28, buttonWidth, 20),
                new Rect(14 + buttonWidth, height - 28, buttonWidth, 20),
                new Rect(18 + buttonWidth * 2, height - 28, buttonWidth, 20),
                25, 36, height - 82);
    }

    public int diameter() { return radius * 2 + 1; }
    public List<Rect> controls() { return List.of(zoomIn, zoomOut, target, refresh, status, review, clear, back); }

    public Optional<ChunkKey> atPixel(String dimension, int centerX, int centerZ, double x, double y) {
        if (!map.contains(x, y)) return Optional.empty();
        return atCell(dimension, centerX, centerZ, (int) ((x - map.x()) / cellSize), (int) ((y - map.y()) / cellSize));
    }

    public Optional<ChunkKey> atCell(String dimension, int centerX, int centerZ, int column, int row) {
        if (column < 0 || row < 0 || column >= diameter() || row >= diameter()) return Optional.empty();
        long x = (long) centerX - radius + column;
        long z = (long) centerZ - radius + row;
        if (x < -ChunkKey.MAX_COORDINATE || x > ChunkKey.MAX_COORDINATE
                || z < -ChunkKey.MAX_COORDINATE || z > ChunkKey.MAX_COORDINATE) return Optional.empty();
        try {
            return Optional.of(new ChunkKey(dimension, (int) x, (int) z));
        } catch (UserError invalidDimension) {
            return Optional.empty();
        }
    }

    public static int chunkCoordinate(double blockCoordinate) {
        if (!Double.isFinite(blockCoordinate) || blockCoordinate < Integer.MIN_VALUE || blockCoordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid block coordinate.");
        }
        return (int) Math.floor(blockCoordinate / 16.0);
    }
}
