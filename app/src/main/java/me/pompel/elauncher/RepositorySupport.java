package me.pompel.elauncher;

final class RepositorySupport {
    static final TimeSource SYSTEM_TIME = new TimeSource() {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    };

    private RepositorySupport() {
    }

    static String normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return text.trim();
    }
}
