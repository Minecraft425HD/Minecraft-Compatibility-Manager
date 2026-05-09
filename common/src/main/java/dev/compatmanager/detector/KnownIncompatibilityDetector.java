package dev.compatmanager.detector;

import dev.compatmanager.api.*;
import dev.compatmanager.platform.Services;

import java.util.ArrayList;
import java.util.List;

public class KnownIncompatibilityDetector implements CompatibilityDetector {

    record Pair(String a, String b, IssueSeverity sev, String reason, String patch) {}

    /**
     * Community-curated list of known bad mod pairings, loader-agnostic
     * (mod IDs are stable across Fabric/Forge/NeoForge ports where applicable).
     */
    private static final List<Pair> DATABASE = List.of(
            // ── Rendering / shaders ──────────────────────────────────────────
            new Pair("sodium",           "optifabric",      IssueSeverity.CRITICAL,
                    "Sodium and OptiFabric use conflicting rendering pipelines. Use Iris for shaders instead.", null),
            new Pair("iris",             "optifabric",      IssueSeverity.CRITICAL,
                    "Iris replaces OptiFabric's shader system entirely.", null),
            new Pair("rubidium",         "embeddium",       IssueSeverity.HIGH,
                    "Rubidium and Embeddium are both Forge ports of Sodium — pick one.", null),
            new Pair("sodium",           "rubidium",        IssueSeverity.CRITICAL,
                    "Sodium (Fabric) and Rubidium (Forge) cannot coexist on the same loader.", null),
            // ── Light engines ────────────────────────────────────────────────
            new Pair("phosphor",         "starlight",       IssueSeverity.HIGH,
                    "Phosphor and Starlight both replace the MC light engine — keep only one.", null),
            new Pair("starlight",        "moonrise",        IssueSeverity.HIGH,
                    "Starlight and Moonrise both replace the light engine.", null),
            // ── Startup optimisers ───────────────────────────────────────────
            new Pair("smoothboot-fabric","smoothboot",      IssueSeverity.HIGH,
                    "Two versions of SmoothBoot detected (Fabric and legacy).", null),
            // ── World generation ────────────────────────────────────────────
            new Pair("terraforged",      "terralith",       IssueSeverity.MEDIUM,
                    "TerraForged and Terralith both overhaul world generation — pick one.", null),
            new Pair("oh_the_biomes_youll_go", "biomes_o_plenty", IssueSeverity.LOW,
                    "BYG and BOP can conflict on biome assignment weights.", "biomesoplenty_byg_compat"),
            // ── Tech mods ────────────────────────────────────────────────────
            new Pair("create",           "immersive_engineering", IssueSeverity.LOW,
                    "Minor recipe conflicts. A compat patch is available.", "create_crafts_additions"),
            new Pair("applied_energistics2","refined_storage", IssueSeverity.MEDIUM,
                    "AE2 and RS both provide storage networks — recipe conflicts possible.", null),
            // ── Magic mods ───────────────────────────────────────────────────
            new Pair("botania",          "botanypots",      IssueSeverity.LOW,
                    "Recipe overlaps between Botania and Botany Pots.", null),
            // ── Entity rendering ─────────────────────────────────────────────
            new Pair("betteranimalsplus","alexsmobs",       IssueSeverity.LOW,
                    "Possible entity renderer conflicts on shared species.", null),
            // ── Chat / HUD ───────────────────────────────────────────────────
            new Pair("chat_heads",       "visuality",       IssueSeverity.LOW,
                    "Minor HUD overlay z-order issues.", null)
    );

    @Override public String getId()   { return "compatmanager:known_incompatibility"; }
    @Override public String getName() { return "Known Incompatibility Database"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();

        for (Pair p : DATABASE) {
            if (Services.PLATFORM.isModLoaded(p.a()) && Services.PLATFORM.isModLoaded(p.b())) {
                var builder = CompatibilityIssue.builder(IssueType.INCOMPATIBLE_MODS, p.sev())
                        .description("compatmanager.issue.incompatible_mods", p.a(), p.b())
                        .affectedMod(p.a()).affectedMod(p.b())
                        .technicalDetail(p.reason())
                        .solution(Solution.manual("compatmanager.solution.remove_mod", p.a()))
                        .solution(Solution.manual("compatmanager.solution.remove_mod", p.b()));

                if (p.patch() != null)
                    builder.solution(Solution.manual("compatmanager.solution.use_compat_mod", p.patch()));

                issues.add(builder.build());
            }
        }
        return issues;
    }
}
