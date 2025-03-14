package me.SeniorRest.DFAWE;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.fastasyncworldedit.core.FaweAPI;
import com.fastasyncworldedit.core.extent.processor.lighting.RelightMode;
import com.fastasyncworldedit.core.Fawe;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

public class FAWEschemCommand extends AbstractCommand implements Holdable {

    private static final Map<String, Clipboard> loadedSchematics = new HashMap<>();

    public FAWEschemCommand() {
        setName("faweschem");
        setSyntax("faweschem [load/unload/paste] [name:<name>] (file:<filename>) (<location>)");
        autoCompile();
    }

    // Поддерживаемые подкоманды
    private enum Type {
        LOAD, UNLOAD, PASTE
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("type") && arg.matchesEnum(Type.class)) {
                scriptEntry.addObject("type", new ElementTag(arg.getRawValue().toUpperCase()));
            }
            else if (!scriptEntry.hasObject("name") && arg.matchesPrefix("name")) {
                scriptEntry.addObject("name", arg.asElement());
            }
            else if (!scriptEntry.hasObject("file") && arg.matchesPrefix("file")) {
                scriptEntry.addObject("file", arg.asElement());
            }
            else if (!scriptEntry.hasObject("location") && arg.matchesArgumentType(LocationTag.class)) {
                scriptEntry.addObject("location", arg.asType(LocationTag.class));
            }
            else {
                arg.reportUnhandled();
            }
        }
        if (!scriptEntry.hasObject("type")) {
            throw new InvalidArgumentsException("Отсутствует подкоманда (load/unload/paste)!");
        }
        if (!scriptEntry.hasObject("name")) {
            throw new InvalidArgumentsException("Отсутствует обязательный аргумент name:<имя>!");
        }
        ElementTag type = scriptEntry.getElement("type");
        if (type.asString().equals("LOAD") && !scriptEntry.hasObject("file")) {
            throw new InvalidArgumentsException("Для load команды требуется указать аргумент file:<имя_файла>!");
        }
        if (type.asString().equals("PASTE") && !scriptEntry.hasObject("location")) {
            throw new InvalidArgumentsException("Для paste команды требуется указать локацию!");
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag type = scriptEntry.getElement("type");
        ElementTag name = scriptEntry.getElement("name");
        ElementTag fileName = scriptEntry.getElement("file"); // Может быть null для unload и paste
        LocationTag location = (LocationTag) scriptEntry.getObject("location");

        try {
            switch (Type.valueOf(type.asString())) {
                case LOAD:
                    handleLoad(scriptEntry, name.asString(), fileName.asString());
                    break;
                case UNLOAD:
                    handleUnload(scriptEntry, name.asString());
                    break;
                case PASTE:
                    handlePaste(scriptEntry, name.asString(), location);
                    break;
            }
        }
        catch (Exception e) {
            Debug.echoError(scriptEntry, "Ошибка в faweschem: " + e.getMessage());
        }
        scriptEntry.setFinished(true);
    }

    // Загрузка схемы в память
    private static void handleLoad(ScriptEntry scriptEntry, String name, String filePath) throws Exception {
        File schemFile = new File(Bukkit.getWorldContainer(), filePath);
        if (!schemFile.exists()) {
            throw new IllegalArgumentException("Файл схемы не найден: " + filePath);
        }
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();
            loadedSchematics.put(name.toUpperCase(), clipboard);
            Debug.log("Схема '" + name + "' успешно загружена из " + filePath);
        }
    }

    // Выгрузка схемы из памяти
    private static void handleUnload(ScriptEntry scriptEntry, String name) {
        if (loadedSchematics.remove(name.toUpperCase()) != null) {
            Debug.log("Схема '" + name + "' выгружена");
        } else {
            throw new IllegalArgumentException("Схема '" + name + "' не найдена в памяти");
        }
    }

    // Вставка схемы и исправление освещения через FaweAPI.fixLighting
    private static void handlePaste(ScriptEntry scriptEntry, String name, LocationTag location) throws Exception {
        Clipboard clipboard = loadedSchematics.get(name.toUpperCase());
        if (clipboard == null) {
            throw new IllegalArgumentException("Схема '" + name + "' не загружена");
        }
        BlockVector3 pastePoint = BukkitAdapter.asBlockVector(location);
        World weWorld = BukkitAdapter.adapt(location.getWorld());
        org.bukkit.World bukkitWorld = location.getWorld();

        try (EditSession editSession = Fawe.instance().getWorldEdit().newEditSession(weWorld)) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            holder.setTransform(new AffineTransform());
            Operation operation = holder.createPaste(editSession)
                    .to(pastePoint)
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(operation);
            editSession.commit();

            BlockVector3 dimensions = clipboard.getDimensions();
            BlockVector3 maxPoint = pastePoint.add(dimensions.subtract(BlockVector3.ONE));

            // Расширяем регион на 1 блок для обработки соседнего освещения
            CuboidRegion region = new CuboidRegion(
                    pastePoint.subtract(1, 1, 1),
                    maxPoint.add(1, 1, 1)
            );

            // Исправляем освещение
            FaweAPI.fixLighting(weWorld, region, null, RelightMode.ALL);
        }
    }
}
