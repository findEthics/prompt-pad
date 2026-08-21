package me.pompel.elauncher;

final class RepositorySupport {
    private RepositorySupport() {
    }

    static String normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return text.trim();
    }
}
