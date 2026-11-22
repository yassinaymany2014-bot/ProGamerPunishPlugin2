package com.progamermc.punish;

import com.progamermc.punish.Store.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Cmds implements TabExecutor {
    private enum Kind { BAN, BANIP, KICK, MUTE, WARN }

    private final PunishPlugin pl;
    private final Store st;

    Cmds(PunishPlugin pl, Store st) { this.pl = pl; this.st = st; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase()) {
            case "ban" -> punish(s, Kind.BAN, a);
            case "banip" -> punish(s, Kind.BANIP, a);
            case "kick" -> punish(s, Kind.KICK, a);
            case "mute" -> punish(s, Kind.MUTE, a);
            case "warn" -> punish(s, Kind.WARN, a);
            case "unban" -> undo(s, a, st.bans(), "unban");
            case "unbanip" -> undo(s, a, st.ipbans(), "unbanip");
            case "unmute" -> undo(s, a, st.mutes(), "unmute");
            case "warnings" -> warnings(s, a);
            case "punishreload" -> { pl.reloadConfig(); s.sendMessage(pl.comp("messages.reloaded")); }
            default -> { }
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void punish(CommandSender s, Kind k, String[] a) {
        String kn = k.name().toLowerCase();
        if (a.length < 1) { s.sendMessage(pl.comp("messages.usage-" + kn)); return; }

        // /cmd <target> [duration] [reason...] - no duration = permanent, no reason = default
        long dur = 0;
        int i = 1;
        if ((k == Kind.BAN || k == Kind.BANIP || k == Kind.MUTE) && a.length > 1) {
            Long d = Dur.parse(a[1]);
            if (d != null) { dur = d; i = 2; }
        }
        String reason = a.length > i
                ? String.join(" ", Arrays.copyOfRange(a, i, a.length))
                : pl.getConfig().getString("default-reason", "No reason given");
        long now = System.currentTimeMillis();
        long exp = dur == 0 ? 0 : now + dur;
        String staff = s.getName();

        if (k == Kind.BANIP) { banIp(s, a[0], reason, staff, now, exp); return; }

        Player online = Bukkit.getPlayerExact(a[0]);
        OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayer(a[0]);
        if (online == null && !op.hasPlayedBefore() && !pl.getConfig().getBoolean("allow-unknown-players", false)) {
            s.sendMessage(pl.comp("messages.unknown-player", ph("player", a[0])));
            return;
        }
        if (k == Kind.KICK && online == null) { s.sendMessage(pl.comp("messages.not-online", ph("player", a[0]))); return; }
        if (online != null && online.hasPermission("punish.bypass") && s instanceof Player) {
            s.sendMessage(pl.comp("messages.bypass"));
            return;
        }
        String name = op.getName() != null ? op.getName() : a[0];
        Entry e = new Entry(newId(), op.getUniqueId().toString(), name, reason, staff, now, exp);
        switch (k) {
            case BAN -> { st.putBan(e); if (online != null) online.kick(pl.comp("screens.ban", pl.ph(e))); }
            case MUTE -> { st.putMute(e); if (online != null) online.sendMessage(pl.comp("messages.you-muted", pl.ph(e))); }
            case KICK -> online.kick(pl.comp("screens.kick", pl.ph(e)));
            case WARN -> warn(e, online);
            default -> { }
        }
        announce(k.name(), e);
    }

    @SuppressWarnings("deprecation")
    private void banIp(CommandSender s, String arg, String reason, String staff, long now, long exp) {
        String ip;
        if (arg.contains(".") || arg.contains(":")) {
            ip = arg;
        } else {
            Player p = Bukkit.getPlayerExact(arg);
            if (p != null && p.hasPermission("punish.bypass") && s instanceof Player) {
                s.sendMessage(pl.comp("messages.bypass"));
                return;
            }
            ip = p != null && p.getAddress() != null
                    ? p.getAddress().getAddress().getHostAddress()
                    : st.lastIp(Bukkit.getOfflinePlayer(arg).getUniqueId().toString());
            if (ip == null) { s.sendMessage(pl.comp("messages.no-ip", ph("player", arg))); return; }
        }
        Entry e = new Entry(newId(), ip, arg, reason, staff, now, exp);
        st.putIpBan(e);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getAddress() != null && ip.equals(p.getAddress().getAddress().getHostAddress())) {
                p.kick(pl.comp("screens.ipban", pl.ph(e)));
            }
        }
        announce("BANIP", e);
    }

    private void warn(Entry e, Player online) {
        st.addWarn(e);
        if (online != null) online.sendMessage(pl.comp("messages.you-warned", pl.ph(e)));
        int limit = pl.getConfig().getInt("warn-threshold", 0);
        if (limit > 0 && st.warns(e.target()).size() >= limit) {
            Long d = Dur.parse(pl.getConfig().getString("warn-threshold-ban", "1d"));
            long now = System.currentTimeMillis();
            Entry b = new Entry(newId(), e.target(), e.name(),
                    pl.getConfig().getString("warn-threshold-reason", "Too many warnings"),
                    "AutoMod", now, d == null || d == 0 ? 0 : now + d);
            st.putBan(b);
            st.clearWarns(e.target());
            if (online != null) online.kick(pl.comp("screens.ban", pl.ph(b)));
            announce("BAN", b);
        }
    }

    private void undo(CommandSender s, String[] a, Map<String, Entry> map, String kn) {
        if (a.length < 1) { s.sendMessage(pl.comp("messages.usage-" + kn)); return; }
        String q = a[0].replace("#", "");
        Entry found = null;
        for (Entry e : map.values()) {
            if (e.name().equalsIgnoreCase(q) || e.id().equalsIgnoreCase(q) || e.target().equals(q)) found = e;
        }
        if (found == null) { s.sendMessage(pl.comp("messages.not-found", ph("player", a[0]))); return; }
        st.remove(map, found);
        s.sendMessage(pl.comp("messages." + kn + "-done", pl.ph(found)));
        if (kn.equals("unmute")) {
            Player p = Bukkit.getPlayer(UUID.fromString(found.target()));
            if (p != null) p.sendMessage(pl.comp("messages.you-unmuted"));
        }
    }

    @SuppressWarnings("deprecation")
    private void warnings(CommandSender s, String[] a) {
        if (a.length < 1) { s.sendMessage(pl.comp("messages.usage-warnings")); return; }
        List<Entry> l = st.warns(Bukkit.getOfflinePlayer(a[0]).getUniqueId().toString());
        s.sendMessage(pl.comp("messages.warnings-header",
                TagResolver.resolver(ph("player", a[0]), ph("count", String.valueOf(l.size())))));
        for (Entry e : l) s.sendMessage(pl.comp("messages.warnings-line", pl.ph(e)));
    }

    private void announce(String type, Entry e) {
        TagResolver r = TagResolver.resolver(pl.ph(e), Placeholder.unparsed("type", type));
        Component m = pl.comp("messages.notify", r);
        Bukkit.getConsoleSender().sendMessage(m);
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("punish.notify")) p.sendMessage(m);
        if (pl.getConfig().getBoolean("public-broadcast", false)) Bukkit.broadcast(pl.comp("messages.public", r));
    }

    private static TagResolver ph(String k, String v) { return Placeholder.unparsed(k, v); }

    private static String newId() { return String.format("%06X", ThreadLocalRandom.current().nextInt(0x1000000)); }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        String n = c.getName().toLowerCase();
        if (a.length == 1) {
            List<String> out = new ArrayList<>();
            switch (n) {
                case "unban" -> st.bans().values().forEach(e -> out.add(e.name()));
                case "unbanip" -> st.ipbans().values().forEach(e -> out.add(e.name()));
                case "unmute" -> st.mutes().values().forEach(e -> out.add(e.name()));
                default -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
            return filter(out, a[0]);
        }
        if (a.length == 2 && List.of("ban", "banip", "mute").contains(n)) {
            return filter(List.of("30m", "1h", "1d", "7d", "30d", "perm"), a[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> l, String prefix) {
        String q = prefix.toLowerCase();
        return l.stream().filter(x -> x.toLowerCase().startsWith(q)).toList();
    }
}
