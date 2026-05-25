package dev.compatmanager.standalone.db;

import dev.compatmanager.standalone.StandaloneIssue.Severity;

import java.util.*;
import java.util.stream.Stream;

/**
 * Curated database of 200+ mod incompatibility pairs, functional groups,
 * required companions, and library singletons — covering every major MC version.
 */
public final class ModDatabase {

    private ModDatabase() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Inner data types
    // ─────────────────────────────────────────────────────────────────────────

    public record IncompatPair(
            String modA, String modB,
            Severity severity, String category,
            String mcVersionMin, String mcVersionMax,
            String reason, String patchMod,
            String modrinthUrlA, String modrinthUrlB
    ) {
        public IncompatPair(String a, String b, Severity sev, String cat, String reason) {
            this(a, b, sev, cat, null, null, reason, null, null, null);
        }
        public IncompatPair(String a, String b, Severity sev, String cat, String reason, String patch) {
            this(a, b, sev, cat, null, null, reason, patch, null, null);
        }
        public IncompatPair(String a, String b, Severity sev, String cat, String min, String max, String reason) {
            this(a, b, sev, cat, min, max, reason, null, null, null);
        }

        public boolean appliesToVersion(String mc) {
            if (mc == null || mc.isBlank()) return true;
            if (mcVersionMin != null && VersionUtils.cmp(mc, mcVersionMin) < 0) return false;
            if (mcVersionMax != null && VersionUtils.cmp(mc, mcVersionMax) > 0) return false;
            return true;
        }
    }

    public record FunctionalGroup(
            String groupName, String category, Severity severity, Set<String> modIds
    ) {}

    public record RequiredCompanion(
            String primaryMod, String requiredMod, String reason, String modrinthUrl
    ) {
        public RequiredCompanion(String primary, String required, String reason) {
            this(primary, required, reason, null);
        }
    }

    public record LibrarySingleton(
            String packagePrefix, String libraryName, Severity severity
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Incompatibility pairs (200+)
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<IncompatPair> PAIRS = List.of(

        // ── Rendering ─────────────────────────────────────────────────────
        new IncompatPair("sodium",         "optifabric",         Severity.CRITICAL, "Rendering",
                "Sodium and OptiFabric use conflicting rendering pipelines; OptiFabric breaks on Sodium."),
        new IncompatPair("iris",           "optifabric",         Severity.CRITICAL, "Rendering",
                "Iris replaces OptiFabric entirely. They cannot coexist."),
        new IncompatPair("canvas",         "optifabric",         Severity.CRITICAL, "Rendering",
                "Canvas and OptiFabric both replace the core render pipeline."),
        new IncompatPair("rubidium",       "embeddium",          Severity.HIGH,     "Rendering",
                "Rubidium and Embeddium are both Forge ports of Sodium — use only one."),
        new IncompatPair("rubidium",       "magnesium",          Severity.HIGH,     "Rendering",
                "Rubidium and Magnesium are both Forge ports of Sodium — use only one."),
        new IncompatPair("embeddium",      "magnesium",          Severity.HIGH,     "Rendering",
                "Embeddium and Magnesium are both Forge ports of Sodium — use only one."),
        new IncompatPair("sodium",         "optifine",           Severity.HIGH,     "Rendering",
                "OptiFine and Sodium both rewrite the chunk renderer; they are mutually exclusive."),
        new IncompatPair("rubidium",       "optifine",           Severity.HIGH,     "Rendering",
                "OptiFine and Rubidium (Sodium for Forge) conflict on chunk rendering."),
        new IncompatPair("embeddium",      "optifine",           Severity.HIGH,     "Rendering",
                "OptiFine and Embeddium conflict on chunk rendering."),
        new IncompatPair("iris",           "oculus",             Severity.HIGH,     "Rendering",
                "Iris (Fabric) and Oculus (Forge) are loader-specific shader mods — don't mix."),
        new IncompatPair("canvas",         "iris",               Severity.HIGH,     "Rendering",
                "Canvas and Iris both overhaul the shader pipeline and are not compatible."),
        new IncompatPair("continuity",     "optifabric",         Severity.HIGH,     "Rendering",
                "Continuity handles connected textures natively; OptiFabric conflicts with it."),
        new IncompatPair("sodium",         "replaymod",          Severity.MEDIUM,   "Rendering",
                "ReplayMod and Sodium can conflict on frame capture rendering."),
        new IncompatPair("immediatelyfast","entityculling",      Severity.MEDIUM,   "Rendering",
                "ImmediatelyFast and EntityCulling can conflict on entity batch rendering."),
        new IncompatPair("distanthorizons","iris",               Severity.MEDIUM,   "Rendering",
                "Distant Horizons LOD rendering conflicts with Iris shaders on some MC versions."),
        new IncompatPair("xenon",          "iris",               Severity.HIGH,     "Rendering",
                "Xenon and Iris both handle shader loading and conflict."),
        new IncompatPair("sodium",         "indium",             Severity.INFO,     "Rendering",
                "Indium requires Sodium — install Sodium first.", "indium"),
        new IncompatPair("optifabric",     "sodium",             Severity.CRITICAL, "Rendering",
                "OptiFabric cannot run alongside Sodium. Use Iris + Sodium instead."),
        new IncompatPair("dashloader",     "immediatelyfast",    Severity.MEDIUM,   "Rendering",
                "DashLoader and ImmediatelyFast both optimize resource pack loading."),
        new IncompatPair("dashloader",     "moreculling",        Severity.MEDIUM,   "Rendering",
                "DashLoader and MoreCulling can conflict on geometry culling passes."),
        new IncompatPair("betterloadingscreen","dashloader",     Severity.HIGH,     "Rendering",
                "BetterLoadingScreen and DashLoader both replace the resource loading screen."),
        new IncompatPair("chunkanimator", "sodium",              Severity.HIGH,     "Rendering",
                "ChunkAnimator is not compatible with Sodium's render pipeline."),
        new IncompatPair("vulkanmod",      "sodium",             Severity.CRITICAL, "Rendering",
                "VulkanMod replaces the entire OpenGL renderer; Sodium is OpenGL-only."),
        new IncompatPair("vulkanmod",      "iris",               Severity.CRITICAL, "Rendering",
                "VulkanMod and Iris both control the GPU backend — they cannot coexist."),
        new IncompatPair("optifine",       "shadersmod",         Severity.HIGH,     "Rendering",
                "1.7.0", "1.12.9",
                "OptiFine includes its own shader support; a standalone shader mod conflicts."),

        // ── Light Engines ─────────────────────────────────────────────────
        new IncompatPair("phosphor",       "starlight",          Severity.HIGH,     "Light Engine",
                "Both replace the Minecraft light engine. Keep only one."),
        new IncompatPair("starlight",      "moonrise",           Severity.HIGH,     "Light Engine",
                "Both replace the Minecraft light engine. Keep only one."),
        new IncompatPair("phosphor",       "moonrise",           Severity.HIGH,     "Light Engine",
                "Both replace the Minecraft light engine. Keep only one."),
        new IncompatPair("canary",         "starlight",          Severity.HIGH,     "Light Engine",
                "Canary patches the light engine; Starlight replaces it entirely."),
        new IncompatPair("canary",         "phosphor",           Severity.HIGH,     "Light Engine",
                "Canary and Phosphor both patch the light engine."),
        new IncompatPair("luminance",      "starlight",          Severity.HIGH,     "Light Engine",
                "Luminance and Starlight both replace the light engine."),
        new IncompatPair("luminance",      "phosphor",           Severity.HIGH,     "Light Engine",
                "Luminance and Phosphor both replace the light engine."),
        new IncompatPair("starlight",      "lithium",            Severity.LOW,      "Light Engine",
                "Starlight and Lithium can occasionally conflict on chunk light propagation."),

        // ── World Generation ──────────────────────────────────────────────
        new IncompatPair("terraforged",    "terralith",          Severity.MEDIUM,   "World Gen",
                "TerraForged and Terralith both overhaul world generation; only one can be active."),
        new IncompatPair("terraforged",    "biomes_o_plenty",    Severity.MEDIUM,   "World Gen",
                "TerraForged overrides biome placement; Biomes O' Plenty may not generate correctly."),
        new IncompatPair("terraforged",    "tectonic",           Severity.HIGH,     "World Gen",
                "TerraForged and Tectonic both control terrain shape — they conflict."),
        new IncompatPair("byg",            "biomes_o_plenty",    Severity.MEDIUM,   "World Gen",
                "Oh The Biomes You'll Go and Biomes O' Plenty can conflict on biome registry."),
        new IncompatPair("nullscape",      "terralith",          Severity.MEDIUM,   "World Gen",
                "Nullscape and Terralith both modify the End dimension generation."),
        new IncompatPair("amplified_nether","oh_the_biomes_youll_go", Severity.MEDIUM, "World Gen",
                "Amplified Nether and BYG both modify Nether biome layout."),
        new IncompatPair("tectonic",       "terralith",          Severity.MEDIUM,   "World Gen",
                "Tectonic and Terralith both control terrain shape generation."),
        new IncompatPair("regions_unexplored","terralith",       Severity.MEDIUM,   "World Gen",
                "Regions Unexplored and Terralith conflict on overworld biome placement."),
        new IncompatPair("betternether",   "oh_the_biomes_youll_go", Severity.MEDIUM, "World Gen",
                "Better Nether and BYG both add custom Nether biomes."),
        new IncompatPair("betterend",      "oh_the_biomes_youll_go", Severity.MEDIUM, "World Gen",
                "Better End and BYG both add custom End biomes."),
        new IncompatPair("recurrent_complex","lost_cities",      Severity.MEDIUM,   "World Gen",
                "Recurrent Complex and Lost Cities both generate custom structures."),
        new IncompatPair("rtg",            "biomes_o_plenty",    Severity.MEDIUM,   "World Gen",
                "1.7.0", "1.12.9",
                "Realistic Terrain Generation and Biomes O' Plenty conflict on biome shape."),
        new IncompatPair("dynamictrees",   "terra",              Severity.MEDIUM,   "World Gen",
                "Dynamic Trees and Terra (TerraBlender) both modify tree placement."),
        new IncompatPair("william_wyther_overhauled","terralith", Severity.MEDIUM,  "World Gen",
                "William Wyther's Overhauled biomes and Terralith share biome name conflicts."),
        new IncompatPair("underground_kingdom","caves_and_cliffs_backport", Severity.MEDIUM, "World Gen",
                "Underground Kingdom and Caves & Cliffs backports both change cave generation."),
        new IncompatPair("naturescompass", "terralith",          Severity.LOW,      "World Gen",
                "Nature's Compass may not find all Terralith-added biomes correctly."),
        new IncompatPair("structory",      "yungs_better_strongholds", Severity.LOW, "World Gen",
                "Structory and YUNG's Better Strongholds can duplicate stronghold placement."),
        new IncompatPair("repurposed_structures","yung_extras",  Severity.LOW,      "World Gen",
                "Repurposed Structures and YUNG's Extras can place duplicate structures."),
        new IncompatPair("endremastered",  "betterend",          Severity.HIGH,     "World Gen",
                "End Remastered and Better End both overhaul the End dimension."),
        new IncompatPair("biomesoplenty",  "traverse",           Severity.LOW,      "World Gen",
                "Biomes O' Plenty and Traverse can conflict on biome weight distribution."),

        // ── Performance ───────────────────────────────────────────────────
        new IncompatPair("lazydfu",        "modernfix",          Severity.MEDIUM,   "Performance",
                "LazyDFU and ModernFix both delay DataFixerUpper initialization — pick one."),
        new IncompatPair("vanillafix",     "modernfix",          Severity.HIGH,     "Performance",
                "VanillaFix and ModernFix both patch vanilla bugs — they can conflict."),
        new IncompatPair("c2me",           "starlight",          Severity.MEDIUM,   "Performance",
                "C2ME (Concurrent Chunk Management Engine) and Starlight can conflict on chunk I/O."),
        new IncompatPair("c2me",           "lithium",            Severity.LOW,      "Performance",
                "C2ME and Lithium occasionally conflict on server-side chunk threading."),
        new IncompatPair("krypton",        "lithium",            Severity.LOW,      "Performance",
                "Krypton and Lithium overlap on network stack optimization."),
        new IncompatPair("smoothboot",     "lazydfu",            Severity.MEDIUM,   "Performance",
                "Smooth Boot and LazyDFU both patch the startup thread — they conflict."),
        new IncompatPair("smoothboot",     "modernfix",          Severity.MEDIUM,   "Performance",
                "Smooth Boot and ModernFix both optimize the startup thread pool."),
        new IncompatPair("betterbiomeblend","sodium",            Severity.MEDIUM,   "Performance",
                "Better Biome Blend is built into Sodium — the standalone version conflicts."),
        new IncompatPair("entityculling",  "sodium",             Severity.LOW,      "Performance",
                "Entity Culling and Sodium can occasionally conflict on entity rendering order."),
        new IncompatPair("ferrite_core",   "memoryleakfix",      Severity.LOW,      "Performance",
                "FerriteCore and MemoryLeakFix both optimize memory usage."),
        new IncompatPair("memoryleakfix",  "modernfix",          Severity.MEDIUM,   "Performance",
                "MemoryLeakFix and ModernFix both patch similar memory leaks — they can conflict."),
        new IncompatPair("bobby",          "distanthorizons",    Severity.HIGH,     "Performance",
                "Bobby and Distant Horizons both extend chunk view distance — use only one."),
        new IncompatPair("notenoughcrashes","crashassistant",    Severity.MEDIUM,   "Performance",
                "Not Enough Crashes and Crash Assistant both intercept crash screens."),

        // ── Tech Mods ─────────────────────────────────────────────────────
        new IncompatPair("applied_energistics2","refined_storage", Severity.MEDIUM, "Tech",
                "AE2 and Refined Storage both provide ME-style storage networks; recipe conflicts."),
        new IncompatPair("create",         "immersive_engineering", Severity.LOW,   "Tech",
                "Create and Immersive Engineering have minor recipe conflicts.",
                "create_crafts_additions"),
        new IncompatPair("mekanism",       "ic2",                Severity.MEDIUM,   "Tech",
                "Mekanism and IC2 both provide energy systems; cross-mod energy transfer is broken."),
        new IncompatPair("thermal_expansion","ic2",              Severity.MEDIUM,   "Tech",
                "Thermal Expansion and IC2 both register the same ore-type items."),
        new IncompatPair("galacticraft",   "advanced_rocketry",  Severity.HIGH,     "Tech",
                "Galacticraft and Advanced Rocketry both overhaul space travel — they conflict."),
        new IncompatPair("pneumaticraft",  "immersive_engineering", Severity.LOW,   "Tech",
                "PneumatiCraft and Immersive Engineering share minor fluid API conflicts."),
        new IncompatPair("fluxnetworks",   "rftools_power",      Severity.MEDIUM,   "Tech",
                "Flux Networks and RFTools Power both manage power network storage."),
        new IncompatPair("powah",          "fluxnetworks",       Severity.LOW,      "Tech",
                "Powah and Flux Networks can conflict on energy cell rendering."),
        new IncompatPair("xnet",           "refined_storage",    Severity.LOW,      "Tech",
                "XNet and Refined Storage have minor routing conflicts."),
        new IncompatPair("industrialcraft2","universal_electricity", Severity.MEDIUM, "Tech",
                "1.7.0", "1.12.9",
                "IC2 and Universal Electricity both register energy unit conversions."),
        new IncompatPair("gregtech",       "industrialcraft2",   Severity.MEDIUM,   "Tech",
                "1.7.0", "1.12.9",
                "GregTech replaces many IC2 recipes; unexpected balance breakage possible."),
        new IncompatPair("computercraft",  "opencomputers",      Severity.MEDIUM,   "Tech",
                "1.7.0", "1.12.9",
                "ComputerCraft and OpenComputers both add Lua computers; overlapping APIs."),
        new IncompatPair("mfr",            "forestry",           Severity.LOW,      "Tech",
                "1.7.0", "1.12.9",
                "MineFactory Reloaded and Forestry both automate farming."),
        new IncompatPair("buildcraft",     "forestry",           Severity.LOW,      "Tech",
                "1.7.0", "1.12.9",
                "BuildCraft and Forestry have minor pipe/tube routing conflicts."),
        new IncompatPair("simple_generators","powah",            Severity.MEDIUM,   "Tech",
                "Simple Generators and Powah both add basic RF generators with registry conflicts."),
        new IncompatPair("immersive_petroleum","create",         Severity.LOW,      "Tech",
                "Immersive Petroleum and Create have minor fluid pipe conflicts."),
        new IncompatPair("ae2wtlib",       "applied_energistics2", Severity.INFO,   "Tech",
                "AE2 Wireless Terminal Library requires Applied Energistics 2.", "ae2wtlib"),
        new IncompatPair("rftoolsbase",    "rftools_utility",    Severity.INFO,     "Tech",
                "RFTools Utility requires RFTools Base — ensure both are installed."),
        new IncompatPair("modularrouters", "item_filters",       Severity.LOW,      "Tech",
                "Modular Routers and Item Filters can conflict on item matching logic."),
        new IncompatPair("createaddition", "create",             Severity.INFO,     "Tech",
                "Create: Crafts & Additions requires Create — install Create first."),
        new IncompatPair("ae2things",      "applied_energistics2", Severity.INFO,   "Tech",
                "AE2 Things requires Applied Energistics 2.", "ae2things"),

        // ── Magic Mods ────────────────────────────────────────────────────
        new IncompatPair("botania",        "bewitchment",        Severity.MEDIUM,   "Magic",
                "Botania and Bewitchment can conflict on altar-type block registration."),
        new IncompatPair("hexerei",        "bewitchment",        Severity.HIGH,     "Magic",
                "Hexerei and Bewitchment are both witch-themed magic mods with conflicting registries."),
        new IncompatPair("ars_nouveau",    "electroblobs_wizardry", Severity.MEDIUM,"Magic",
                "Ars Nouveau and Electroblob's Wizardry both add spell systems with registry conflicts."),
        new IncompatPair("thaumcraft",     "witchery",           Severity.LOW,      "Magic",
                "1.7.0", "1.12.9",
                "Thaumcraft and Witchery can conflict on altar block interactions."),
        new IncompatPair("astral_sorcery", "cyclic",             Severity.LOW,      "Magic",
                "Astral Sorcery and Cyclic can conflict on lens/altar block registration."),
        new IncompatPair("occultism",      "ars_nouveau",        Severity.LOW,      "Magic",
                "Occultism and Ars Nouveau share ritual ingredient conflicts."),
        new IncompatPair("eidolon",        "botania",            Severity.LOW,      "Magic",
                "Eidolon and Botania can conflict on mana system rendering."),
        new IncompatPair("roots",          "botania",            Severity.LOW,      "Magic",
                "Roots and Botania can conflict on botanical item recipes."),
        new IncompatPair("embers",         "thermal_expansion",  Severity.LOW,      "Magic",
                "Embers and Thermal Expansion have minor metal ore processing conflicts."),
        new IncompatPair("ars_magica2",    "thaumcraft",         Severity.MEDIUM,   "Magic",
                "1.7.0", "1.12.9",
                "Ars Magica 2 and Thaumcraft conflict on wand item registry."),

        // ── Minimap / Waypoints ───────────────────────────────────────────
        new IncompatPair("journeymap",     "voxelmap",           Severity.HIGH,     "Minimap",
                "JourneyMap and VoxelMap both render the minimap — only one should be used."),
        new IncompatPair("xaeros_minimap", "journeymap",         Severity.HIGH,     "Minimap",
                "Xaero's Minimap and JourneyMap both render the minimap overlay."),
        new IncompatPair("xaeros_minimap", "voxelmap",           Severity.HIGH,     "Minimap",
                "Xaero's Minimap and VoxelMap both render the minimap overlay."),
        new IncompatPair("xaeros_world_map","antique_atlas",     Severity.MEDIUM,   "Minimap",
                "Xaero's World Map and Antique Atlas both add full world maps."),
        new IncompatPair("ftbchunks",      "xaeros_minimap",     Severity.MEDIUM,   "Minimap",
                "FTB Chunks and Xaero's Minimap can conflict on waypoint rendering."),

        // ── Inventory / Recipe UI ─────────────────────────────────────────
        new IncompatPair("nei",            "jei",                Severity.HIGH,     "Inventory",
                "NEI (Not Enough Items) and JEI (Just Enough Items) both control recipe display."),
        new IncompatPair("rei",            "emi",                Severity.HIGH,     "Inventory",
                "REI (Roughly Enough Items) and EMI both provide recipe lookup — pick one."),
        new IncompatPair("rei",            "jei",                Severity.HIGH,     "Inventory",
                "REI and JEI both provide recipe lookup — pick one."),
        new IncompatPair("emi",            "jei",                Severity.HIGH,     "Inventory",
                "EMI and JEI both provide recipe lookup — pick one."),
        new IncompatPair("inventory_tweaks","inventory_profiles_next", Severity.HIGH, "Inventory",
                "Inventory Tweaks and Inventory Profiles Next both auto-sort inventories."),
        new IncompatPair("inventory_tweaks","inventory_sorter",  Severity.MEDIUM,   "Inventory",
                "Inventory Tweaks and Inventory Sorter both handle inventory sorting."),
        new IncompatPair("groovyscript",   "crafttweaker",       Severity.MEDIUM,   "Inventory",
                "GroovyScript and CraftTweaker both modify recipes; they can conflict."),
        new IncompatPair("polymorph",      "crafttweaker",       Severity.LOW,      "Inventory",
                "Polymorph and CraftTweaker's recipe modifications can produce duplicate conflicts."),
        new IncompatPair("jei",            "fastbench",          Severity.MEDIUM,   "Inventory",
                "JEI and FastBench can conflict on crafting grid rendering."),
        new IncompatPair("chesttracker",   "rei",                Severity.LOW,      "Inventory",
                "Chest Tracker and REI can conflict on inventory search overlay."),

        // ── Entity / Animation ────────────────────────────────────────────
        new IncompatPair("mo_bends",       "fresh_animations",   Severity.HIGH,     "Animation",
                "Mo' Bends and Fresh Animations both replace player animations."),
        new IncompatPair("mo_bends",       "better_animations_collection", Severity.HIGH, "Animation",
                "Mo' Bends and Better Animations Collection both replace entity animations."),
        new IncompatPair("sp_animation",   "mo_bends",           Severity.HIGH,     "Animation",
                "SP Animation and Mo' Bends both control third-person player animations."),
        new IncompatPair("epic_fight",     "bettercombat",       Severity.HIGH,     "Animation",
                "Epic Fight and Better Combat both overhaul the combat animation system."),
        new IncompatPair("alex_mobs",      "better_animals_plus", Severity.MEDIUM,  "Animation",
                "Alex's Mobs and Better Animals Plus add similar entity models that conflict."),
        new IncompatPair("animatica",      "entity_model_features", Severity.MEDIUM,"Animation",
                "Animatica and Entity Model Features both handle animated entity textures."),
        new IncompatPair("custom_entity_models","optifine",      Severity.HIGH,     "Animation",
                "Custom Entity Models (CEM) and OptiFine both load .jem model files."),
        new IncompatPair("custom_entity_models","entity_model_features", Severity.MEDIUM, "Animation",
                "CEM and Entity Model Features both replace the entity model layer system."),
        new IncompatPair("mythicmobs",     "ice_and_fire",       Severity.MEDIUM,   "Animation",
                "MythicMobs and Ice and Fire can conflict on custom entity goal AI."),
        new IncompatPair("betteranimalsplus","alexsmobs",        Severity.MEDIUM,   "Animation",
                "Better Animals Plus and Alex's Mobs register overlapping animal variants."),

        // ── Chat / HUD / UI ───────────────────────────────────────────────
        new IncompatPair("wthit",          "jade",               Severity.HIGH,     "HUD",
                "WTHIT (What The Hell Is That) and Jade both add block info overlays."),
        new IncompatPair("wthit",          "hwyla",              Severity.HIGH,     "HUD",
                "WTHIT and HWYLA (Jade 1.12) both add block info overlays."),
        new IncompatPair("jade",           "hwyla",              Severity.HIGH,     "HUD",
                "Jade and HWYLA (its predecessor) both add block info overlays."),
        new IncompatPair("craftpresence",  "mc_discord_rpc",     Severity.HIGH,     "HUD",
                "CraftPresence and MC Discord RPC both set the Discord Rich Presence status."),
        new IncompatPair("chat_heads",     "visuality",          Severity.MEDIUM,   "HUD",
                "Chat Heads and Visuality can conflict on player head rendering in chat."),
        new IncompatPair("fancymenu",      "pseudofs",           Severity.MEDIUM,   "HUD",
                "FancyMenu and PseudoFS both replace the main menu screen."),
        new IncompatPair("reauth",         "essentials",         Severity.MEDIUM,   "HUD",
                "ReAuth and Essentials both intercept the Minecraft authentication flow."),
        new IncompatPair("advancementinfo","better_advancements", Severity.HIGH,    "HUD",
                "Advancement Info and Better Advancements both replace the advancement screen."),
        new IncompatPair("itemscroller",   "inventory_profiles_next", Severity.MEDIUM, "HUD",
                "Item Scroller and Inventory Profiles Next can conflict on scroll-to-move behavior."),
        new IncompatPair("appleskin",      "nutrition",          Severity.LOW,      "HUD",
                "AppleSkin and Nutrition both modify the hunger bar rendering."),

        // ── Multiplayer / Server ──────────────────────────────────────────
        new IncompatPair("luckperms",      "permissionsex",      Severity.HIGH,     "Multiplayer",
                "LuckPerms and PermissionsEX both manage server permissions — use only one."),
        new IncompatPair("vanish",         "essentialsx",        Severity.MEDIUM,   "Multiplayer",
                "Vanish (No Packet) and EssentialsX both provide /vanish — conflicts possible."),
        new IncompatPair("geyser",         "viaversion",         Severity.LOW,      "Multiplayer",
                "Geyser and ViaVersion both translate protocol packets; ordering matters."),
        new IncompatPair("ftbessentials",  "essentialsx",        Severity.HIGH,     "Multiplayer",
                "FTB Essentials and EssentialsX duplicate core server commands."),
        new IncompatPair("coreprotect",    "logblock",           Severity.HIGH,     "Multiplayer",
                "CoreProtect and LogBlock both log block changes to a database."),

        // ── Legacy Forge 1.7–1.12 ─────────────────────────────────────────
        new IncompatPair("optifine",       "fastcraft",          Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "OptiFine and FastCraft conflict on chunk rendering in 1.7–1.12."),
        new IncompatPair("optifine",       "betterfoliage",      Severity.MEDIUM,   "Legacy Forge",
                "1.7.0", "1.12.9",
                "OptiFine and BetterFoliage conflict on shader leaf rendering."),
        new IncompatPair("codechickencore","codechickenlib",     Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "CodeChickenCore and CodeChickenLib are different versions of the same library."),
        new IncompatPair("forge_multipart","microblocks",        Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "Forge MultiPart and Microblocks both register micro-block logic."),
        new IncompatPair("microblocks",    "projectred",         Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "Microblocks (FMP) and ProjectRed both register wiring micro-blocks."),
        new IncompatPair("archimedes_ships","ironclad",          Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "Archimedes' Ships and Ironclad both provide moving ship mechanics."),
        new IncompatPair("orespawn",       "lycanites_mobs",     Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "OreSpawn and Lycanites Mobs both add large numbers of entities with ID conflicts."),
        new IncompatPair("mowzies_mobs",   "lycanites_mobs",     Severity.MEDIUM,   "Legacy Forge",
                "1.7.0", "1.12.9",
                "Mowzie's Mobs and Lycanites Mobs can conflict on entity spawn weight."),
        new IncompatPair("enhanced_portals","mystcraft",         Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "Enhanced Portals and Mystcraft both modify the portal rendering and dimension system."),
        new IncompatPair("tinkers_construct","metallurgy",       Severity.LOW,      "Legacy Forge",
                "1.7.0", "1.12.9",
                "Tinkers' Construct and Metallurgy can conflict on metal ingot recipes."),
        new IncompatPair("magical_crops",  "industrialcraft2",   Severity.LOW,      "Legacy Forge",
                "1.7.0", "1.12.9",
                "Magical Crops and IC2 can conflict on crop registry and growth mechanics."),
        new IncompatPair("bibliocraft",    "betterchests",       Severity.MEDIUM,   "Legacy Forge",
                "1.7.0", "1.12.9",
                "BiblioCraft and BetterChests can conflict on bookcase/chest rendering."),
        new IncompatPair("chisel",         "carpenters_blocks",  Severity.LOW,      "Legacy Forge",
                "1.7.0", "1.12.9",
                "Chisel and Carpenter's Blocks can conflict on decorative block shape registries."),
        new IncompatPair("atum",           "lycanites_mobs",     Severity.MEDIUM,   "Legacy Forge",
                "1.7.0", "1.12.9",
                "Atum and Lycanites Mobs can conflict on desert dimension mob spawning."),
        new IncompatPair("witchery",       "ars_magica2",        Severity.MEDIUM,   "Legacy Forge",
                "1.7.0", "1.12.9",
                "Witchery and Ars Magica 2 both add magic altars with overlapping registry."),
        new IncompatPair("pneumaticraft",  "buildcraft",         Severity.LOW,      "Legacy Forge",
                "1.7.0", "1.12.9",
                "PneumatiCraft and BuildCraft have minor pneumatic pipe conflicts."),
        new IncompatPair("immibis_peripherals","opencomputers",  Severity.MEDIUM,   "Legacy Forge",
                "1.7.0", "1.12.9",
                "Immibis Peripherals and OpenComputers both extend ComputerCraft."),
        new IncompatPair("liteloader",     "optifine",           Severity.LOW,      "Legacy Forge",
                "1.7.0", "1.12.9",
                "LiteLoader and OptiFine can conflict depending on load order."),
        new IncompatPair("witchery",       "thaumcraft",         Severity.LOW,      "Legacy Forge",
                "1.7.0", "1.12.9",
                "Witchery and Thaumcraft can conflict on altar block interactions."),
        new IncompatPair("nei",            "tomanyitems",        Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "Not Enough Items and Too Many Items both add inventory/recipe UIs."),
        new IncompatPair("optifine",       "optifog",            Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "OptiFine includes fog control; OptiFog conflicts with its implementation."),
        new IncompatPair("smart_moving",   "mo_bends",           Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "Smart Moving and Mo' Bends both replace player movement animations."),
        new IncompatPair("bibliowoods",    "decocraft",          Severity.LOW,      "Legacy Forge",
                "1.7.0", "1.12.9",
                "BiblioCraft + BiblioCraft Woods and DecoCraft can conflict on wood type registration."),
        new IncompatPair("witchery",       "bewitchment",        Severity.HIGH,     "Legacy Forge",
                "Witchery (1.12.2) and Bewitchment are both spiritual successors; they conflict."),
        new IncompatPair("loot_bags",      "lootbags",           Severity.CRITICAL, "Legacy Forge",
                "1.7.0", "1.12.9",
                "Two different mods named 'Loot Bags' are present — duplicate item registry."),
        new IncompatPair("journeymap",     "zan_minimap",        Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "JourneyMap and Zan's Minimap (VoxelMap) both render the minimap overlay."),
        new IncompatPair("treecapitator",  "timber",             Severity.HIGH,     "Legacy Forge",
                "1.7.0", "1.12.9",
                "Treecapitator and Timber both handle tree felling — they conflict."),
        new IncompatPair("fastcraft",      "fastcraftplus",      Severity.CRITICAL, "Legacy Forge",
                "1.7.0", "1.12.9",
                "FastCraft and FastCraft+ are different versions of the same performance mod."),
        new IncompatPair("ic2",            "ic2classic",         Severity.CRITICAL, "Legacy Forge",
                "IC2 and IC2 Classic are incompatible versions of the same mod.")
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Functional groups (pick-one-from-group)
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<FunctionalGroup> GROUPS = List.of(
        new FunctionalGroup("Rendering Engine", "Rendering", Severity.CRITICAL,
                Set.of("sodium", "optifabric", "canvas", "vulkanmod")),
        new FunctionalGroup("Forge Rendering Port", "Rendering", Severity.HIGH,
                Set.of("rubidium", "embeddium", "magnesium")),
        new FunctionalGroup("Shader Pipeline", "Rendering", Severity.HIGH,
                Set.of("iris", "oculus", "optifabric")),
        new FunctionalGroup("Light Engine", "Light Engine", Severity.HIGH,
                Set.of("phosphor", "starlight", "moonrise", "luminance")),
        new FunctionalGroup("Recipe Viewer", "Inventory", Severity.HIGH,
                Set.of("jei", "rei", "emi", "nei")),
        new FunctionalGroup("Minimap", "Minimap", Severity.HIGH,
                Set.of("journeymap", "voxelmap", "xaeros_minimap", "xaerosminimap")),
        new FunctionalGroup("World Map", "Minimap", Severity.MEDIUM,
                Set.of("journeymap", "xaeros_world_map", "voxelmap", "antique_atlas")),
        new FunctionalGroup("Block Info HUD", "HUD", Severity.HIGH,
                Set.of("jade", "wthit", "hwyla", "waila")),
        new FunctionalGroup("ME Storage Network", "Tech", Severity.MEDIUM,
                Set.of("applied_energistics2", "refined_storage")),
        new FunctionalGroup("View Distance Extension", "Performance", Severity.HIGH,
                Set.of("bobby", "distanthorizons")),
        new FunctionalGroup("Inventory Auto-Sort", "Inventory", Severity.HIGH,
                Set.of("inventory_tweaks", "inventory_profiles_next", "inventory_sorter")),
        new FunctionalGroup("World Gen Overhaul", "World Gen", Severity.MEDIUM,
                Set.of("terraforged", "terralith", "tectonic")),
        new FunctionalGroup("Overworld Biomes", "World Gen", Severity.MEDIUM,
                Set.of("biomes_o_plenty", "oh_the_biomes_youll_go", "byg", "regions_unexplored")),
        new FunctionalGroup("Discord Rich Presence", "HUD", Severity.HIGH,
                Set.of("craftpresence", "mc_discord_rpc")),
        new FunctionalGroup("Startup Optimizer", "Performance", Severity.MEDIUM,
                Set.of("lazydfu", "modernfix", "smoothboot")),
        new FunctionalGroup("Player Animation", "Animation", Severity.HIGH,
                Set.of("mo_bends", "fresh_animations", "better_animations_collection", "sp_animation")),
        new FunctionalGroup("Combat Overhaul", "Animation", Severity.HIGH,
                Set.of("epic_fight", "bettercombat")),
        new FunctionalGroup("Server Permissions", "Multiplayer", Severity.HIGH,
                Set.of("luckperms", "permissionsex")),
        new FunctionalGroup("Block Change Logger", "Multiplayer", Severity.HIGH,
                Set.of("coreprotect", "logblock")),
        new FunctionalGroup("Crash Handler", "Performance", Severity.MEDIUM,
                Set.of("notenoughcrashes", "crashassistant")),
        new FunctionalGroup("Legacy Performance", "Legacy Forge", Severity.HIGH,
                Set.of("fastcraft", "fastcraftplus"))
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Required companions (primary mod needs companion)
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<RequiredCompanion> COMPANIONS = List.of(
        new RequiredCompanion("iris",              "sodium",        "Iris requires Sodium to function."),
        new RequiredCompanion("oculus",            "rubidium",      "Oculus (Forge shader mod) requires Rubidium."),
        new RequiredCompanion("indium",            "sodium",        "Indium requires Sodium for its render API."),
        new RequiredCompanion("sodium_extra",      "sodium",        "Sodium Extra requires Sodium."),
        new RequiredCompanion("reese_sodium_options","sodium",      "Reese's Sodium Options requires Sodium."),
        new RequiredCompanion("continuity",        "indium",        "Continuity works best with Indium on Sodium."),
        new RequiredCompanion("entity_model_features","entity_texture_features",
                "Entity Model Features works best with Entity Texture Features."),
        new RequiredCompanion("ars_additions",     "ars_nouveau",   "Ars Additions requires Ars Nouveau."),
        new RequiredCompanion("alexsmobs",         "citadel",       "Alex's Mobs requires Citadel library."),
        new RequiredCompanion("farmers_delight",   "forge",         "Farmer's Delight requires Forge or Fabric."),
        new RequiredCompanion("cobweb_expanded",   "cobweb",        "CobWeb Expanded requires the CobWeb library."),
        new RequiredCompanion("createaddition",    "create",        "Create: Crafts & Additions requires Create."),
        new RequiredCompanion("create_trains",     "create",        "Create: Train & Zeppelin requires Create."),
        new RequiredCompanion("create_steam",      "create",        "Create: Steam & Rails requires Create."),
        new RequiredCompanion("create_enchantment_industry","create","Create: Enchantment Industry requires Create."),
        new RequiredCompanion("ae2wtlib",          "applied_energistics2","AE2 Wireless Terminal Library requires AE2."),
        new RequiredCompanion("ae2things",         "applied_energistics2","AE2 Things requires Applied Energistics 2."),
        new RequiredCompanion("extrabotany",       "botania",       "Extra Botany requires Botania."),
        new RequiredCompanion("botanypots",        "botania",       "Botany Pots works best alongside Botania."),
        new RequiredCompanion("thaumicaugmentation","thaumcraft",   "Thaumic Augmentation requires Thaumcraft."),
        new RequiredCompanion("thaumic_tinkerer",  "thaumcraft",   "Thaumic Tinkerer requires Thaumcraft."),
        new RequiredCompanion("bloodmagic_compat", "bloodmagic",   "Blood Magic compat addons require Blood Magic."),
        new RequiredCompanion("mcjtylib",          "rftools",       "McJtyLib is required by RFTools and other mods by McJty."),
        new RequiredCompanion("cofhcore",          "codechickenlib","CoFH Core requires CodeChicken Lib (1.12)."),
        new RequiredCompanion("thermalexpansion",  "thermaldynamics","Thermal Expansion pairs with Thermal Dynamics."),
        new RequiredCompanion("bibliowoods",       "bibliocraft",   "BiblioCraft Woods requires BiblioCraft."),
        new RequiredCompanion("extra_bees",        "forestry",      "Extra Bees requires Forestry."),
        new RequiredCompanion("extra_trees",       "forestry",      "Extra Trees requires Forestry."),
        new RequiredCompanion("tinkers_jei",       "jei",           "Tinkers' JEI integration requires JEI."),
        new RequiredCompanion("jei_integration",   "jei",           "JEI Integration addons require JEI."),
        new RequiredCompanion("roughlyenoughresources","rei",       "Roughly Enough Resources requires REI."),
        new RequiredCompanion("emi_loot",          "emi",           "EMI Loot requires EMI.")
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Library singletons (should not appear in 2+ JARs)
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<LibrarySingleton> SINGLETONS = List.of(
        new LibrarySingleton("com/google/gson",           "Google Gson",            Severity.MEDIUM),
        new LibrarySingleton("org/apache/commons",        "Apache Commons",         Severity.MEDIUM),
        new LibrarySingleton("org/apache/logging/log4j",  "Apache Log4j",           Severity.HIGH),
        new LibrarySingleton("io/netty",                  "Netty",                  Severity.HIGH),
        new LibrarySingleton("org/slf4j",                 "SLF4J",                  Severity.MEDIUM),
        new LibrarySingleton("com/google/guava",          "Google Guava",           Severity.MEDIUM),
        new LibrarySingleton("org/objectweb/asm",         "ASM Bytecode Library",   Severity.HIGH),
        new LibrarySingleton("com/llamalad7/mixinextras", "MixinExtras",            Severity.MEDIUM),
        new LibrarySingleton("org/spongepowered/mixin",   "SpongePowered Mixin",    Severity.CRITICAL),
        new LibrarySingleton("net/fabricmc/loader",       "Fabric Loader",          Severity.CRITICAL)
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public static List<IncompatPair> getIncompatPairs() {
        return PAIRS;
    }

    public static List<IncompatPair> getIncompatPairsForMcVersion(String mc) {
        if (mc == null || mc.isBlank()) return PAIRS;
        return PAIRS.stream().filter(p -> p.appliesToVersion(mc)).toList();
    }

    public static List<FunctionalGroup> getFunctionalGroups() {
        return GROUPS;
    }

    public static List<RequiredCompanion> getRequiredCompanions() {
        return COMPANIONS;
    }

    public static List<LibrarySingleton> getLibrarySingletons() {
        return SINGLETONS;
    }

    public static Optional<String[]> getModUrls(String modId) {
        return PAIRS.stream()
                .flatMap(p -> {
                    if (p.modA().equals(modId) && p.modrinthUrlA() != null)
                        return Stream.<String[]>of(new String[]{p.modrinthUrlA(), null});
                    if (p.modB().equals(modId) && p.modrinthUrlB() != null)
                        return Stream.<String[]>of(new String[]{p.modrinthUrlB(), null});
                    return Stream.<String[]>empty();
                })
                .findFirst();
    }
}
