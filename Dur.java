package com.progamermc.punish;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Dur {
    private static final Pattern PART = Pattern.compile("(\\d+)(mo|y|w|d|h|m|s)");
    private static final Pattern FULL = Pattern.compile("(?:\\d+(?:mo|y|w|d|h|m|s))+");

    private Dur() {}

    /** null = not a duration, 0 = permanent, >0 = milliseconds */
    public static Long parse(String s) {
        if (s == null) return null;
        String l = s.toLowerCase();
        if (l.equals("perm") || l.equals("permanent") || l.equals("forever") || l.equals("-")) return 0L;
        if (!FULL.matcher(l).matches()) return null;
        try {
            long total = 0;
            Matcher m = PART.matcher(l);
            while (m.find()) {
                long n = Long.parseLong(m.group(1));
                long unit = switch (m.group(2)) {
                    case "s" -> 1000L;
                    case "m" -> 60_000L;
                    case "h" -> 3_600_000L;
                    case "d" -> 86_400_000L;
                    case "w" -> 604_800_000L;
                    case "mo" -> 2_592_000_000L;
                    default -> 31_536_000_000L;
                };
                total = Math.addExact(total, Math.multiplyExact(n, unit));
            }
            return total <= 0 ? null : total;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    public static String format(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000, d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, x = s % 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (h > 0) b.append(h).append("h ");
        if (m > 0) b.append(m).append("m ");
        if (x > 0 && d == 0) b.append(x).append("s");
        String r = b.toString().trim();
        return r.isEmpty() ? "0s" : r;
    }
}
