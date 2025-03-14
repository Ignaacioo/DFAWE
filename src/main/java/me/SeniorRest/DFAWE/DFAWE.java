package me.SeniorRest.DFAWE;

import com.denizenscript.denizen.Denizen;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import org.bukkit.plugin.java.JavaPlugin;

public final class DFAWE extends JavaPlugin {

    public static DFAWE instance;
    public static Denizen denizen;

    @Override
    public void onEnable() {
        denizen = (Denizen) getServer().getPluginManager().getPlugin("Denizen");

        if (denizen == null) {
            getLogger().severe("Denizen не найден");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Debug.log("DFAWE: Загрузка...");
        saveDefaultConfig();
        instance = this;

        DenizenCore.commandRegistry.registerCommand(FAWEsetCommand.class);
        DenizenCore.commandRegistry.registerCommand(FAWEschemCommand.class);
        Debug.log("DFAWE загружен!");
    }

    @Override
    public void onDisable() {
        denizen = null;
        Denizen.getInstance().onDisable();
    }
}
