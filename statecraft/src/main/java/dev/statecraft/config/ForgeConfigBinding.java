package dev.statecraft.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.statecraft.api.UserError;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public final class ForgeConfigBinding<T> {
    private final Supplier<T> defaults;
    private final Map<Field, ForgeConfigSpec.ConfigValue<?>> values = new LinkedHashMap<>();
    private final ForgeConfigSpec spec;
    private ModConfig loaded;

    public ForgeConfigBinding(Supplier<T> defaults) {
        this.defaults = defaults;
        T initial = defaults.get();
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        try {
            for (Field field : initial.getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                Object value = field.get(initial);
                builder.comment(description(field.getName()));
                ForgeConfigSpec.ConfigValue<?> entry;
                if (value instanceof Boolean bool) {
                    entry = builder.define(field.getName(), bool);
                } else if (value instanceof Integer integer) {
                    entry = builder.defineInRange(field.getName(), integer, Integer.MIN_VALUE, Integer.MAX_VALUE);
                } else if (value instanceof Long number) {
                    entry = builder.defineInRange(field.getName(), number, Long.MIN_VALUE, Long.MAX_VALUE);
                } else if (value instanceof Double number) {
                    entry = builder.defineInRange(field.getName(), number, -Double.MAX_VALUE, Double.MAX_VALUE);
                } else if (value instanceof String text) {
                    entry = builder.define(field.getName(), text);
                } else {
                    throw new IllegalArgumentException("Unsupported configuration field: " + field.getName());
                }
                values.put(field, entry);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Configuration fields must be public.", e);
        }
        spec = builder.build();
    }

    private static String description(String name) {
        String label = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        if (name.endsWith("Cents") || name.endsWith("Fee")) {
            return label + " (integer cents; 100 cents = $1).";
        }
        if (name.endsWith("Bps")) {
            return label + " (basis points; 100 = 1%, 10000 = 100%).";
        }
        if (name.endsWith("Millis") || name.endsWith("Ms")) {
            return label + " (milliseconds).";
        }
        return label + ". Invalid combinations are rejected by the mod.";
    }

    public ForgeConfigSpec spec() {
        return spec;
    }

    public void loaded(ModConfig config) {
        if (config.getSpec() == spec) {
            loaded = config;
        }
    }

    public T read() {
        T result = defaults.get();
        try {
            for (Map.Entry<Field, ForgeConfigSpec.ConfigValue<?>> entry : values.entrySet()) {
                entry.getKey().set(result, entry.getValue().get());
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read mod configuration.", e);
        }
        return result;
    }

    public T reload() {
        if (loaded == null || !(loaded.getConfigData() instanceof CommentedFileConfig file)) {
            throw new UserError("The world configuration is not available for disk reload.");
        }
        file.load();
        if (!spec.isCorrect(file)) {
            throw new UserError("Invalid configuration. Correct the server TOML before reloading.");
        }
        spec.afterReload();
        return read();
    }
}
