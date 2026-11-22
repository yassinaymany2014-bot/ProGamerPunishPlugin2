package com.progamermc.punish;

import com.progamermc.punish.Store.Entry;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class Events implements Listener {
    private final PunishPlugin pl;
    private final Store st;

    Events(PunishPlugin pl, Store st) { this.pl = pl; this.st = st; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Entry b = st.ban(e.getUniqueId().toString());
        if (b != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, pl.comp("screens.ban", pl.ph(b)));
            return;
        }
        Entry ib = st.ipban(e.getAddress().getHostAddress());
        if (ib != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, pl.comp("screens.ipban", pl.ph(ib)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.getAddress() != null) st.setIp(p.getUniqueId().toString(), p.getAddress().getAddress().getHostAddress());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Entry m = st.mute(e.getPlayer().getUniqueId().toString());
        if (m == null) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(pl.comp("messages.muted-chat", pl.ph(m)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Entry m = st.mute(e.getPlayer().getUniqueId().toString());
        if (m == null) return;
        String first = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        int ix = first.indexOf(':');
        if (ix >= 0) first = first.substring(ix + 1);
        if (pl.getConfig().getStringList("muted-blocked-commands").contains(first)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(pl.comp("messages.muted-command", pl.ph(m)));
        }
    }
}
