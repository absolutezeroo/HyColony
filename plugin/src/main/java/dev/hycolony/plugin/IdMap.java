package dev.hycolony.plugin;

import static dev.hycolony.plugin.IdChecks.byId;
import static dev.hycolony.plugin.IdChecks.check;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.plugin.farming.FarmingIds;
import dev.hycolony.plugin.food.FoodIds;
import dev.hycolony.plugin.item.BlockFamilies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Logical keys -> Hytale asset ids. The only place asset ids live (spec § 4.3). */
public final class IdMap {
    private record Data(
            Map<String, String> items,
            Map<String, String> blocks,
            Map<String, String> npcRoles,
            @Nullable Map<String, String> npcGroups,
            List<String> fireworks,
            List<String> precipitationParticles,
            Map<String, String> speedEffects,
            List<String> toggleableUseInteractions,
            List<String> potions,
            FarmingIds farming,
            @Nullable BlockFamilies construction,
            @Nullable FoodIds food,
            @Nullable List<String> hurtIgnoredCauses,
            @Nullable String highlightEffect,
            @Nullable String sleepParticle,
            @Nullable String placeholderFluid) {}

    private final Data data;

    private IdMap(Data data) {
        this.data = data;
    }

    /** The id-map read from {@code json}: the core's hycolony/id-map.json merged with the enabled packs' fragments. */
    public static IdMap of(JsonObject json) {
        return new IdMap(new Gson().fromJson(json, Data.class));
    }

    /** The particle system over a sleeping citizen (MC SleepingParticleMessage); empty if the map has none. */
    public Optional<String> sleepParticle() {
        return Optional.ofNullable(data.sleepParticle());
    }

    /** The entity effect that makes a highlighted block glow (a vanilla ModelVFX effect); empty when not set. */
    public Optional<String> highlightEffect() {
        return Optional.ofNullable(data.highlightEffect());
    }

    /** The fluid a blueprint's fluid placeholder gets (MC: the dimension's fluid); "" when the file has none. */
    public String placeholderFluid() {
        // No default id here (asset ids live in the id-map only): a missing key fails validate().
        return Objects.requireNonNullElse(data.placeholderFluid(), "");
    }

    /** True when {@code key} has both a hut item and a hut block, as every registered building type needs. */
    public boolean hasHut(String key) {
        return data.items().containsKey(key) && data.blocks().containsKey(key);
    }

    public String itemId(String key) {
        return require(data.items(), key);
    }

    public String blockId(String key) {
        return require(data.blocks(), key);
    }

    /** The NPC roles and groups (none from a file without the section; the core's always has npc.group.hostile). */
    public NpcIds npcs() {
        return new NpcIds(data.npcRoles(), Objects.requireNonNullElse(data.npcGroups(), Map.of()));
    }

    /** Particle systems fired when a building level rises. */
    public List<String> fireworks() {
        return List.copyOf(data.fireworks());
    }

    /** Weather particle systems that count as rain or snow (MC Level.isRaining). */
    public Set<String> precipitationParticles() {
        return Set.copyOf(precipitation());
    }

    /** Walking-speed factor -> infinite entity effect with that HorizontalSpeedMultiplier. */
    public Map<Double, String> speedEffects() {
        Map<Double, String> out = new HashMap<>();
        speeds().forEach((factor, id) -> out.put(Double.valueOf(factor), id));
        return Map.copyOf(out);
    }

    /** Root interactions run by using a door or a gate (MC BlockTags.DOORS and FENCE_GATES: ACCESS_TOGGLEABLES). */
    public Set<String> toggleableUseInteractions() {
        return Set.copyOf(Objects.requireNonNullElse(data.toggleableUseInteractions(), List.of()));
    }

    /** The construction section: MC's dirt tag, the cells any dirt answers, free-shape templates; none if absent. */
    public BlockFamilies construction() {
        return Objects.requireNonNullElse(data.construction(), BlockFamilies.NONE);
    }

    /** The farming section: seeds and crops, soils, fertilizer, hoes, field barriers; none in an older file. */
    public FarmingIds farming() {
        return Objects.requireNonNullElse(data.farming(), FarmingIds.NONE);
    }

    /** Damage causes that do not hurt a citizen's feelings (MC EntityCitizen.hurt: IS_FIRE, IS_LIGHTNING). */
    public Set<String> hurtIgnoredCauses() {
        return Set.copyOf(Objects.requireNonNullElse(data.hurtIgnoredCauses(), List.of()));
    }

    /** The food section: the foods, the cooking bench and the eating particle; none in an older file. */
    public FoodIds food() {
        return Objects.requireNonNullElse(data.food(), FoodIds.NONE);
    }

    /** The item a request for a tool of {@code type} shows (the type's crude tool, items {@code tool.<type>}). */
    public String toolIcon(ToolType type) {
        return itemId("tool." + type.name().toLowerCase(Locale.ROOT));
    }

    /** The field block's id, empty until the id-map has one ({@code block.field}). */
    public String fieldBlockId() {
        return data.blocks().getOrDefault("block.field", "");
    }

    /** Every potion item, drunk or thrown (Minecraft PotionItem, which MC checks for THROW_POTION). */
    public Set<String> potions() {
        return Set.copyOf(potionList());
    }

    private List<String> potionList() {
        return Objects.requireNonNullElse(data.potions(), List.of());
    }

    /** Absent from an older id-map file: no precipitation particle (never rains). */
    private List<String> precipitation() {
        return Objects.requireNonNullElse(data.precipitationParticles(), List.of());
    }

    /** Absent from an older id-map file: no speed effect (normal speed). */
    private Map<String, String> speeds() {
        return Objects.requireNonNullElse(data.speedEffects(), Map.of());
    }

    private static String require(Map<String, String> map, String key) {
        String id = map.get(key);
        if (id == null) {
            throw new IllegalArgumentException("Unknown id-map key " + key);
        }
        return id;
    }

    /** Every mapped id must exist in the loaded assets. Returns human-readable errors. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        Predicate<String> item = id -> Item.getAssetMap().getAsset(id) != null;
        Predicate<String> block = id -> BlockType.getAssetMap().getIndex(id) != Integer.MIN_VALUE;
        Predicate<String> particle = id -> ParticleSystem.getAssetMap().getAsset(id) != null;
        Predicate<String> effect = id -> EntityEffect.getAssetMap().getAsset(id) != null;
        Predicate<String> sound = id -> SoundEvent.getAssetMap().getIndex(id) != Integer.MIN_VALUE;
        Predicate<String> interaction = id -> RootInteraction.getAssetMap().getAsset(id) != null;
        check(errors, "item", data.items(), item);
        check(errors, "block", data.blocks(), block);
        check(errors, "particle system", byId(data.fireworks()), particle);
        check(errors, "precipitation particle system", byId(precipitation()), particle);
        check(errors, "speed effect", speeds(), effect);
        check(errors, "toggleable use interaction", byId(List.copyOf(toggleableUseInteractions())), interaction);
        check(errors, "potion item", byId(potionList()), item);
        check(errors, "npc role", data.npcRoles(), id -> NPCPlugin.get().hasRoleName(id));
        check(errors, "npc group", npcs().groups(), id -> NPCGroup.getAssetMap().getIndex(id) != Integer.MIN_VALUE);
        check(errors, "sound event", byId(farming().tillSoundEvent().stream().toList()), sound);
        IdChecks.checkConstruction(errors, construction(), block);
        check(errors, "entity effect", byId(highlightEffect().stream().toList()), effect);
        check(errors, "particle system", byId(sleepParticle().stream().toList()), particle);
        check(
                errors,
                "item quality",
                byId(List.copyOf(food().ranks().keySet())),
                id -> ItemQuality.getAssetMap().getIndexOrDefault(id, Integer.MIN_VALUE) != Integer.MIN_VALUE);
        check(errors, "food quality rank", food().ranks(), FoodIds::isRank);
        check(errors, "fuel item", byId(food().fuels()), item);
        check(errors, "particle system", byId(food().particle().stream().toList()), particle);
        check(
                errors,
                "fluid",
                byId(List.of(placeholderFluid())),
                id -> Fluid.getAssetMap().getAsset(id) != null);
        return errors;
    }
}
