package auctions;

import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;

import java.util.UUID;

public final class HypixelClient {

    private final String key;
    private final HypixelAPI api;

    private HypixelClient(Draft d) {
        this.key = d.key;
        this.api = d.api;
    }

    public static final class Draft {
        private String key;
        private HypixelAPI api;

        public Draft apiKey(String key) {
            this.key = key;
            this.api = new HypixelAPI(new ApacheHttpClient(UUID.fromString(this.key)));
            return this;
        }

        public HypixelClient freezeObject() { return new HypixelClient(this); }
    }
    public static Draft draft() { return new Draft(); }

    public HypixelAPI getApi() {
        return api;
    }

    public void shutdownApi() {
        api.shutdown();
        System.out.println(Thread.currentThread().getName()  + ">> API down");
    }

}
