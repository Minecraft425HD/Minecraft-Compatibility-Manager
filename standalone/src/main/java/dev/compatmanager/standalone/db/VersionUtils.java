package dev.compatmanager.standalone.db;

public final class VersionUtils {

    private VersionUtils() {}

    /** Returns a negative, zero, or positive value like {@link Comparable#compareTo}. */
    public static int cmp(String a, String b) {
        if (a == null || b == null) return 0;
        String[] av = a.trim().split("[.+\\-]");
        String[] bv = b.trim().split("[.+\\-]");
        for (int i = 0; i < Math.min(av.length, bv.length); i++) {
            try {
                int d = Integer.compare(Integer.parseInt(av[i]), Integer.parseInt(bv[i]));
                if (d != 0) return d;
            } catch (NumberFormatException e) {
                int d = av[i].compareTo(bv[i]);
                if (d != 0) return d;
            }
        }
        return Integer.compare(av.length, bv.length);
    }

    /**
     * Returns true if {@code installed} satisfies the given range.
     * Range formats: {@code >=1.2}, {@code >1.2}, {@code <=1.2}, {@code <1.2},
     * {@code ~1.2} (same major.minor prefix), {@code [1.2,1.3)} / {@code (1.2,1.3]},
     * {@code *} (any), exact match.
     */
    public static boolean versionInRange(String installed, String range) {
        if (installed == null || range == null || range.isBlank() || range.equals("*")) return true;
        installed = installed.trim();
        range = range.trim();
        try {
            if (range.startsWith(">=")) return cmp(installed, range.substring(2).trim()) >= 0;
            if (range.startsWith(">"))  return cmp(installed, range.substring(1).trim()) >  0;
            if (range.startsWith("<=")) return cmp(installed, range.substring(2).trim()) <= 0;
            if (range.startsWith("<"))  return cmp(installed, range.substring(1).trim()) <  0;
            if (range.startsWith("~")) {
                String base = range.substring(1).trim();
                int lastDot = base.lastIndexOf('.');
                String prefix = lastDot > 0 ? base.substring(0, lastDot) : base;
                return installed.startsWith(prefix);
            }
            if (range.contains(",")) {
                char lo = range.charAt(0), hi = range.charAt(range.length() - 1);
                String inner = range.substring(1, range.length() - 1);
                String[] parts = inner.split(",", 2);
                String loVer = parts[0].trim();
                String hiVer = parts[1].trim();
                boolean loOk = loVer.isEmpty() || (lo == '[' ? cmp(installed, loVer) >= 0 : cmp(installed, loVer) > 0);
                boolean hiOk = hiVer.isEmpty() || (hi == ']' ? cmp(installed, hiVer) <= 0 : cmp(installed, hiVer) < 0);
                return loOk && hiOk;
            }
            return installed.equals(range);
        } catch (Exception e) {
            return true;
        }
    }
}
