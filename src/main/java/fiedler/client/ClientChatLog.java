package fiedler.client;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** Remembers the system messages the client receives, so client tests can check what the player actually saw. */
public final class ClientChatLog {
    private static final int CAPACITY = 200;
    private static final Queue<String> MESSAGES = new ConcurrentLinkedQueue<>();

    private ClientChatLog() {}

    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        MESSAGES.add(event.getMessage().getString());
        while (MESSAGES.size() > CAPACITY) MESSAGES.poll();
    }

    public static void clear() {
        MESSAGES.clear();
    }

    public static List<String> snapshot() {
        return List.copyOf(MESSAGES);
    }
}
