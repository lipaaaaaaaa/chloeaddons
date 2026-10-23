package ca.modules;

import ca.modules.combat.AimAssist;
import ca.modules.combat.AimLock;
import ca.modules.combat.Triggerbot;
import ca.modules.player.AlwaysSprint;
import ca.modules.player.AutoTool;
import ca.modules.player.Freecam;
import ca.modules.player.OldSneak;
import ca.modules.visuals.BlockESP;
import ca.modules.visuals.CustomBlockOutline;
import ca.modules.visuals.CustomFont;
import ca.modules.visuals.Fullbright;
import ca.modules.visuals.NoBreakParticles;
import ca.modules.visuals.NoSwing;
import ca.modules.visuals.PerformanceHud;
import ca.modules.visuals.PlayerVisuals;
import ca.modules.visuals.Trajectories;
import ca.modules.visuals.Viewmodel;
import ca.modules.combat.JumpReset;
import ca.modules.combat.KillAura;
import ca.modules.player.Scaffold;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        modules.add(new BlockESP());
        modules.add(new Fullbright());
        modules.add(new NoBreakParticles());
        modules.add(new CustomBlockOutline());
        modules.add(new PlayerVisuals());
        modules.add(new AimLock());
        modules.add(new AimAssist());
        modules.add(new AutoTool());
        modules.add(new AlwaysSprint());
        modules.add(new Freecam());
        modules.add(new CustomFont());
        modules.add(new Triggerbot());
        modules.add(new JumpReset());
        modules.add(new Scaffold());
        modules.add(new Trajectories());
        modules.add(new KillAura());
        modules.add(new PerformanceHud());
        modules.add(new OldSneak());
        modules.add(new Viewmodel());
        modules.add(new NoSwing());
    }

    public List<Module> getModules() { return modules; }
}