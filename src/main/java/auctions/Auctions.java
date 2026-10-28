package auctions;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(Auctions.MOD_ID)
public final class Auctions {
    public static final String MOD_ID = "hypixel";

    private static boolean firstClick = true;
    private static HypixelClient client;
    private static Database db;
    private static ScheduledExecutorService updateService;

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onKeyMappingsEvent(RegisterKeyMappingsEvent event) {
            event.register(Keys.TOGGLE_DB);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent.Post event) throws SQLException {
            while (Keys.TOGGLE_DB.consumeClick()) {

                if (firstClick) {
                    firstClick = false;

                    client = HypixelClient
                            .draft()
                            .apiKey("placeholder") //replace with your own api key
                            .freezeObject();

                    db = Database
                            .draft()
                            .api(client.getApi())
                            .path("auctions.db")
                            .init()
                            .freezeObject();

                    System.out.println(Thread.currentThread().getName() + ">> Database initialized");
                }
                if (updateService == null || updateService.isTerminated()) startUpdateThread(); else updateService.shutdownNow();
            }
        }

        @SubscribeEvent
        public static void onGameShutdown(GameShuttingDownEvent event) throws SQLException, InterruptedException {
            if (updateService != null) {
                updateService.shutdownNow();
                updateService.awaitTermination(5, TimeUnit.SECONDS);
            }
            if (db != null) db.close();
            if (client != null) client.shutdownApi();
        }
    }

    private static void startUpdateThread() {
        updateService = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("update-thread-", 0).factory());

        updateService.scheduleWithFixedDelay(() -> {
            // This runs in a virtual thread every 60 seconds
            try {
                System.out.println(Thread.currentThread().getName() + ">> Updating...");
                db.update();
                System.out.println(Thread.currentThread().getName() + ">> Update complete! Next one in 60s...");
            } catch (SQLException e) {
                System.err.println(Thread.currentThread().getName() + ">> Error : " + e.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS);
    }
}
