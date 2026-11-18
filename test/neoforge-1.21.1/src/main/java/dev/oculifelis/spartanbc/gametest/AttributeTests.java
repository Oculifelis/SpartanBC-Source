package dev.oculifelis.spartanbc.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.bettercombat.api.AttributesContainer;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks the built jar against Better Combat's own registry: every weapon of a loaded port must resolve to
 * the same attributes as the preset build.py chose, which proves this jar's file won over the port's own.
 */
@Mod(AttributeTests.ID)
@GameTestHolder(AttributeTests.ID)
@PrefixGameTestTemplate(false)
public final class AttributeTests {
    public static final String ID = "spartan_bc_gametest";
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private static Map<String, String> expected() throws Exception {
        try (var in = AttributeTests.class.getResourceAsStream("/spartan_bc_expected.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var out = new java.util.TreeMap<String, String>();
            o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, AttributesContainer> containers() throws Exception {
        Field f = WeaponRegistry.class.getDeclaredField("containers");
        f.setAccessible(true);
        return (Map<ResourceLocation, AttributesContainer>) f.get(null);
    }

    /** Every weapon of every loaded port resolves to the preset from data/presets.json. */
    @GameTest(template = "arena")
    public static void everyLoadedWeaponUsesItsPreset(GameTestHelper helper) throws Exception {
        var containers = containers();
        List<String> bad = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (var e : expected().entrySet()) {
            ResourceLocation id = ResourceLocation.parse(e.getKey());
            if (!ModList.get().isLoaded(id.getNamespace())) continue;
            var item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty() || item.get() == Items.AIR) {
                missing.add(id.toString());  // listed in the port's data but not registered: Better Combat skips it
                continue;
            }
            WeaponAttributes attributes = WeaponRegistry.getAttributes(new ItemStack(item.get()));
            ResourceLocation presetId = ResourceLocation.fromNamespaceAndPath("bettercombat", e.getValue());
            AttributesContainer preset = containers.get(presetId);
            WeaponAttributes want = preset == null ? null : WeaponRegistry.resolveAttributes(presetId, preset);
            if (attributes == null) bad.add(id + " has no attributes");
            else if (want == null) bad.add(id + " preset " + presetId + " missing");
            else if (!GSON.toJson(want).equals(GSON.toJson(attributes))) bad.add(id + " differs from " + presetId + " (category " + attributes.category() + ")");
            checked++;
        }
        helper.assertTrue(checked >= 300, "checked only " + checked + " weapons");
        helper.assertTrue(bad.isEmpty(), bad.size() + " wrong, first: " + bad.subList(0, Math.min(5, bad.size())));
        System.out.println("SPARTAN_BC checked " + checked + " weapons, not registered: " + missing);
        helper.succeed();
    }

    /** The port maps battle hammers to mace; this jar maps them to hammer. Category shows whose file won. */
    @GameTest(template = "arena")
    public static void battleHammerUsesHammer(GameTestHelper helper) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("spartan_weaponry_unofficial", "iron_battle_hammer"));
        helper.assertTrue(item != Items.AIR, "iron_battle_hammer exists");
        WeaponAttributes a = WeaponRegistry.getAttributes(new ItemStack(item));
        helper.assertTrue(a != null && "hammer".equals(a.category()), "category " + (a == null ? null : a.category()));
        helper.succeed();
    }

    /** Thrown weapons keep the port's own attributes, so their throw still works. */
    @GameTest(template = "arena")
    public static void javelinKeepsPortAttributes(GameTestHelper helper) throws Exception {
        var id = ResourceLocation.fromNamespaceAndPath("spartan_weaponry_unofficial", "javelin_iron");
        var c = containers().get(id);
        helper.assertTrue(c == null || c.parent() == null || !c.parent().startsWith("bettercombat:"),
                "javelin parent " + (c == null ? null : c.parent()));
        helper.succeed();
    }
}
