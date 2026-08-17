package me.pompel.elauncher;

/** Boundary for a natural-language model; implementations return JSON only. */
public interface LlmCommandInterpreter {
    String SYSTEM_PROMPT = "You are the command interpreter for a minimal Android launcher. "
            + "Convert the user's natural-language request into a single JSON object "
            + "describing one launcher command. Respond with ONLY the JSON object, no prose. "
            + "Valid commands: call, text, hermes, timer, alarm, todo, todos, note, notes, "
            + "grocery, groceries, event, torch, camera. "
            + "If the request does not match any command, respond {\"command\":\"none\"}. "
            + "Normalize times to 24-hour HH:MM and durations to a compact form like 30s, 5m, 1h30m.";

    String interpret(String text);
}
