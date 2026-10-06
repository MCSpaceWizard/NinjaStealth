package com.mcspacewizard.emergentstealth.ai.routine;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * An NPC's daily routine (design doc 15 §3): up to {@link #MAX_ENTRIES} time windows, each with an
 * activity. The first entry whose window contains the current hour wins.
 */
public record Schedule(List<Entry> entries) {
    public static final int MAX_ENTRIES = 8;
    public static final Schedule EMPTY = new Schedule(List.of());

    public static final Codec<Schedule> CODEC = Entry.CODEC.listOf().xmap(Schedule::new, Schedule::entries);

    /** Hours are 0-24 Minecraft clock hours (0 = midnight, 6 = sunrise). A window may wrap past midnight. */
    public record Entry(int fromHour, int toHour, Activity activity) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 24).fieldOf("from").forGetter(Entry::fromHour),
                Codec.intRange(0, 24).fieldOf("to").forGetter(Entry::toHour),
                Activity.CODEC.fieldOf("activity").forGetter(Entry::activity)
        ).apply(i, Entry::new));

        public boolean contains(int hour) {
            if (fromHour == toHour) {
                return true; // all day
            }
            return fromHour < toHour ? hour >= fromHour && hour < toHour : hour >= fromHour || hour < toHour;
        }
    }

    /** What an NPC does during a window. */
    public sealed interface Activity permits Route, Post, Wander {
        Codec<Activity> CODEC = Codec.STRING.dispatch("type", Activity::type, type -> switch (type) {
            case "route" -> Route.MAP_CODEC;
            case "post" -> Post.MAP_CODEC;
            case "wander" -> Wander.MAP_CODEC;
            default -> throw new IllegalArgumentException("Unknown routine activity: " + type);
        });

        String type();

        String describe();
    }

    public record Route(String route) implements Activity {
        static final MapCodec<Route> MAP_CODEC = Codec.STRING.fieldOf("route").xmap(Route::new, Route::route);

        @Override
        public String type() {
            return "route";
        }

        @Override
        public String describe() {
            return "route " + route;
        }
    }

    public record Post(BlockPos pos, float yaw) implements Activity {
        static final MapCodec<Post> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Post::pos),
                Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Post::yaw)
        ).apply(i, Post::new));

        @Override
        public String type() {
            return "post";
        }

        @Override
        public String describe() {
            return "post";
        }
    }

    public record Wander(int radius) implements Activity {
        static final MapCodec<Wander> MAP_CODEC = Codec.intRange(1, 64).optionalFieldOf("radius", 8).xmap(Wander::new, Wander::radius);

        @Override
        public String type() {
            return "wander";
        }

        @Override
        public String describe() {
            return "wander " + radius;
        }
    }

    public Optional<Activity> activeAt(int hour) {
        for (Entry entry : entries) {
            if (entry.contains(hour)) {
                return Optional.of(entry.activity());
            }
        }
        return Optional.empty();
    }

    /**
     * Adds an entry in front (first match wins, so the newest entry takes priority), replacing any entry with
     * the same window and dropping the oldest when full.
     */
    public Schedule with(Entry entry) {
        List<Entry> list = new java.util.ArrayList<>(entries);
        list.removeIf(e -> e.fromHour() == entry.fromHour() && e.toHour() == entry.toHour());
        list.addFirst(entry);
        while (list.size() > MAX_ENTRIES) {
            list.removeLast();
        }
        return new Schedule(List.copyOf(list));
    }

    /** Minecraft clock hour (0-23) from day time ticks (tick 0 = 06:00). */
    public static int hourOf(long dayTimeTicks) {
        return (int) (((dayTimeTicks % 24000L) / 1000L + 6L) % 24L);
    }
}
