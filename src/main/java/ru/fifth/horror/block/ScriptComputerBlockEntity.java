package ru.fifth.horror.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.FifthMod;

public class ScriptComputerBlockEntity extends BlockEntity {
    private String scriptName = "main";
    private String script = "// FifthScript (.fifth.php)\n// NPC без startAi() остаётся статуей.\n\nnpc(\"silvi\")->startAi();\n";
    public ScriptComputerBlockEntity(BlockPos pos, BlockState state) { super(FifthMod.SCRIPT_COMPUTER_BE, pos, state); }
    public String getScriptName() { return scriptName; }
    public String getScript() { return script; }
    public void setScriptName(String name) { scriptName = name == null || name.isBlank() ? "main" : name.trim(); }
    public void setScript(String script) { this.script = script == null ? "" : script; }
    @Override public void writeNbt(NbtCompound nbt) { super.writeNbt(nbt); nbt.putString("ScriptName", scriptName); nbt.putString("Script", script); }
    @Override public void readNbt(NbtCompound nbt) { super.readNbt(nbt); scriptName = nbt.getString("ScriptName"); script = nbt.getString("Script"); }
}
