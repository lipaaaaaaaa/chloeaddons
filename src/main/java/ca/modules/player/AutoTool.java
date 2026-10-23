package ca.modules.player;

import ca.Setting;
import ca.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoTool extends Module {
    private static AutoTool instance;

    private final Setting.Switch enabled = new Setting.Switch("Enabled", false);

    public AutoTool() {
        super("AutoTool", "Player");
        instance = this;
        settings.add(enabled);
    }

    /** Called by Triggerbot before it attacks. Does nothing while AutoTool is off. */
    public static void equipWeapon() {
        if (instance == null || !instance.enabled.value) return;
        Minecraft mc = Minecraft.getInstance();
        select(mc, instance.bestWeapon(mc));
    }

    @Override
    public void onTick() {
        if (!enabled.value) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        if (mc.player.isUsingItem() || !mc.options.keyAttack.isDown() || mc.hitResult == null) return;

        int slot = mc.hitResult.getType() == HitResult.Type.ENTITY
                ? bestWeapon(mc)
                : mc.hitResult.getType() == HitResult.Type.BLOCK ? bestTool(mc) : -1;
        select(mc, slot);
    }

    private int bestTool(Minecraft mc) {
        BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
        int best = -1;
        double bestScore = scoreTool(mc.player.getInventory().getSelectedItem(), state);
        for (int i = 0; i < 9; i++) {
            double score = scoreTool(mc.player.getInventory().getItem(i), state);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Speed first, then "can actually harvest", then your tier order as a tiebreak. */
    private double scoreTool(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 0;
        float speed = stack.getDestroySpeed(state);
        int correct = stack.isCorrectToolForDrops(state) ? 1 : 0;
        return speed * 1000.0 + correct * 50.0 + tier(stack);
    }

    // netherite > diamond > iron > gold > copper > wood > stone
    private static int tier(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.startsWith("netherite")) return 7;
        if (id.startsWith("diamond")) return 6;
        if (id.startsWith("iron")) return 5;
        if (id.startsWith("golden") || id.startsWith("gold")) return 4;
        if (id.startsWith("copper")) return 3;
        if (id.startsWith("wooden")) return 2;
        if (id.startsWith("stone")) return 1;
        return 0;
    }

    private int bestWeapon(Minecraft mc) {
        int best = -1;
        double bestScore = scoreWeapon(mc.player.getInventory().getSelectedItem());
        for (int i = 0; i < 9; i++) {
            double score = scoreWeapon(mc.player.getInventory().getItem(i));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private double scoreWeapon(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return 1.0 + modifier(stack);
    }

    private static double modifier(ItemStack stack) {
        double[] amount = {0};
        var attribute = Attributes.ATTACK_DAMAGE;
        stack.forEachModifier(EquipmentSlot.MAINHAND, (holder, mod) -> {
            if (holder.is(attribute)) amount[0] += mod.amount();
        });
        return amount[0];
    }

    /** Local slot only. MultiPlayerGameMode syncs it to the server before attack/destroy/use. */
    private static void select(Minecraft mc, int slot) {
        if (slot < 0 || slot > 8) return;
        mc.player.getInventory().setSelectedSlot(slot);
    }
}