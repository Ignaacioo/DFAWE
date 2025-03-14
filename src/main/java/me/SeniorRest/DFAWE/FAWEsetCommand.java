package me.SeniorRest.DFAWE;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.MaterialTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.fastasyncworldedit.core.Fawe;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FAWEsetCommand extends AbstractCommand {

    public FAWEsetCommand() {
        setName("faweset");
        setSyntax("faweset [firstloc:<location>] [secondloc:<location>] [material:<material>]");
        autoCompile();
    }

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("firstloc") @ArgPrefixed LocationTag firstLoc,
                                   @ArgName("secondloc") @ArgPrefixed LocationTag secondLoc,
                                   @ArgName("material") @ArgPrefixed ElementTag materialStr) {

        // Обработка материала
        MaterialTag material = MaterialTag.valueOf(String.valueOf(materialStr), scriptEntry.context);

        // Конвертация локаций в объекты WorldEdit
        BlockVector3 pos1 = BukkitAdapter.asBlockVector(firstLoc);
        BlockVector3 pos2 = BukkitAdapter.asBlockVector(secondLoc);
        CuboidRegion region = new CuboidRegion(pos1, pos2);

        BlockState state = BukkitAdapter.adapt(material.getModernData());
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(firstLoc.getWorld());

        // Запуск операции в асинхронном потоке, чтобы не блокировать главный поток сервера
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(FAWEsetCommand.class);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession editSession = Fawe.instance().getWorldEdit().newEditSession(weWorld)) {
                Pattern pattern = (Pattern) state; // Явное преобразование BlockState в Pattern
                editSession.setBlocks((Region) region, pattern);
                editSession.commit();
                Debug.log("FAWEset: Успешно заменены блоки в регионе на " + material.getMaterial().translationKey());
            }
            catch (Exception e) {
                Debug.echoError(scriptEntry, "Ошибка в FAWEset: " + e.getMessage());
            }
        });
    }
}
