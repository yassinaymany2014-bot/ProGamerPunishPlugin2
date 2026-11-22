package com.progamermc.punish;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PunishPlugin extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter DF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Store store = new Store(new File(getDataFolder(), "data.yml"));
        store.load();
        Cmds cmds = new Cmds(this, store);
        for (String n : List.of("ban", "banip", "kick", "mute", "warn", "unban", "unbanip", "unmute", "warnings", "punishreload")) {
            PluginCommand pc = getCommand(n);
            if (pc != null) { pc.setExecutor(cmds); pc.setTabCompleter(cmds); }
        }
        getServer().getPluginManager().registerEvents(new Events(this, store), this);
    }

    public Component comp(String path) { return comp(path, TagResolver.empty()); }

    public Component comp(String path, TagResolver r) {
        String s = getConfig().getString(path, "<red>Missing config: " + path);
        TagResolver all = TagResolver.resolver(r, Placeholder.parsed("prefix", getConfig().getString("messages.prefix", "")));
        return MM.deserialize(s.replace("\r", "").replace("\n", "<newline>"), all);
    }

    public TagResolver ph(Store.Entry e) {
        long now = System.currentTimeMillis();
        String perm = getConfig().getString("words.permanent", "Permanent");
        String never = getConfig().getString("words.never", "Never");
        return TagResolver.resolver(
                Placeholder.unparsed("reason", e.reason()),
                Placeholder.unparsed("staff", e.staff()),
                Placeholder.unparsed("player", e.name()),
                Placeholder.unparsed("id", e.id()),
                Placeholder.unparsed("total", e.expires() == 0 ? perm : Dur.format(e.expires() - e.created())),
                Placeholder.unparsed("duration", e.expires() == 0 ? perm : Dur.format(e.expires() - now)),
                Placeholder.unparsed("expires", e.expires() == 0 ? never : DF.format(Instant.ofEpochMilli(e.expires()))),
                Placeholder.unparsed("date", DF.format(Instant.ofEpochMilli(e.created()))));
    }
}
