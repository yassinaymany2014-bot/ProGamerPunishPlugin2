package com.progamermc.punish;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Store {
    public record Entry(String id, String target, String name, String reason, String staff, long created, long expires) {
        boolean expired() { return expires > 0 && System.currentTimeMillis() >= expires; }
    }

    private final File file;
    private final Map<String, Entry> bans = new ConcurrentHashMap<>();
    private final Map<String, Entry> ipbans = new ConcurrentHashMap<>();
    private final Map<String, Entry> mutes = new ConcurrentHashMap<>();
    private final Map<String, List<Entry>> warns = new ConcurrentHashMap<>();
    private final Map<String, String> ips = new ConcurrentHashMap<>();

    Store(File file) { this.file = file; }

    Map<String, Entry> bans() { purge(bans); return bans; }
    Map<String, Entry> ipbans() { purge(ipbans); return ipbans; }
    Map<String, Entry> mutes() { purge(mutes); return mutes; }

    Entry ban(String uuid) { return get(bans, uuid); }
    Entry ipban(String ip) { return get(ipbans, ip); }
    Entry mute(String uuid) { return get(mutes, uuid); }

    void putBan(Entry e) { bans.put(e.target(), e); save(); }
    void putIpBan(Entry e) { ipbans.put(e.target(), e); save(); }
    void putMute(Entry e) { mutes.put(e.target(), e); save(); }
    void remove(Map<String, Entry> map, Entry e) { map.remove(e.target()); save(); }

    void addWarn(Entry e) { warns.computeIfAbsent(e.target(), k -> new CopyOnWriteArrayList<>()).add(e); save(); }
    List<Entry> warns(String uuid) { return new ArrayList<>(warns.getOrDefault(uuid, List.of())); }
    void clearWarns(String uuid) { warns.remove(uuid); save(); }

    String lastIp(String uuid) { return ips.get(uuid); }
    void setIp(String uuid, String ip) { if (!ip.equals(ips.put(uuid, ip))) save(); }

    private Entry get(Map<String, Entry> map, String key) {
        Entry e = map.get(key);
        if (e != null && e.expired()) { map.remove(key); save(); return null; }
        return e;
    }

    private void purge(Map<String, Entry> map) {
        if (map.values().removeIf(Entry::expired)) save();
    }

    void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Entry e : read(y, "bans")) bans.put(e.target(), e);
        for (Entry e : read(y, "ipbans")) ipbans.put(e.target(), e);
        for (Entry e : read(y, "mutes")) mutes.put(e.target(), e);
        for (Entry e : read(y, "warns")) warns.computeIfAbsent(e.target(), k -> new CopyOnWriteArrayList<>()).add(e);
        ConfigurationSection sec = y.getConfigurationSection("ips");
        if (sec != null) for (String k : sec.getKeys(false)) ips.put(k, sec.getString(k, ""));
    }

    synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("bans", write(bans.values()));
        y.set("ipbans", write(ipbans.values()));
        y.set("mutes", write(mutes.values()));
        List<Entry> all = new ArrayList<>();
        warns.values().forEach(all::addAll);
        y.set("warns", write(all));
        ips.forEach((k, v) -> y.set("ips." + k, v));
        try { y.save(file); } catch (IOException ex) { ex.printStackTrace(); }
    }

    private List<Entry> read(YamlConfiguration y, String path) {
        List<Entry> out = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList(path)) {
            out.add(new Entry(str(m, "id"), str(m, "target"), str(m, "name"), str(m, "reason"),
                    str(m, "staff"), num(m, "created"), num(m, "expires")));
        }
        return out;
    }

    private static String str(Map<?, ?> m, String k) { Object o = m.get(k); return o == null ? "" : String.valueOf(o); }
    private static long num(Map<?, ?> m, String k) { Object o = m.get(k); return o instanceof Number n ? n.longValue() : 0L; }

    private List<Map<String, Object>> write(Collection<Entry> c) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Entry e : c) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id()); m.put("target", e.target()); m.put("name", e.name());
            m.put("reason", e.reason()); m.put("staff", e.staff());
            m.put("created", e.created()); m.put("expires", e.expires());
            l.add(m);
        }
        return l;
    }
}
