package auctions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.hypixel.api.HypixelAPI;
import net.hypixel.api.reply.skyblock.SkyBlockAuctionsReply;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public final class Database {

    private final HypixelAPI api;
    private final String path;
    private final Connection connection;
    private final Statement statement;
    private final PreparedStatement inserting;
    private final DateTimeFormatter formatter;

    private Database(Draft d) {
        this.api = d.api;
        this.path = d.path;
        this.connection = d.connection;
        this.statement = d.statement;
        this.inserting = d.inserting;
        this.formatter = d.formatter;
    }

    public static final class Draft {
        private HypixelAPI api;
        private String path;
        private Connection connection;
        private Statement statement;
        private PreparedStatement inserting;
        private DateTimeFormatter formatter;

        public Draft api(HypixelAPI api) { this.api = api; return this; }
        public Draft path(String path) { this.path = path; return this; }
        public Draft init() {
            try {
                connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + path, new java.util.Properties());
                statement = connection.createStatement();
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

                statement.execute("PRAGMA journal_mode = WAL"); // benefit: non-blocking access for both writer and readers
                // fsync = how often SQLite forces data to physically reach the disk
                // NORMAL: reduce fsync calls for speed, at the cost of possibly losing the last few commits after a power loss or OS crash
                statement.execute("PRAGMA synchronous = NORMAL"); // other modes : FULL(always sync, safest, slowest), OFF(never sync, fastest, risky)
                statement.execute("PRAGMA cache_size = -1000000"); // 1GB: how much of the database SQLite keeps in memory per connection
                statement.execute("PRAGMA temp_store = MEMORY"); // RAM faster than default (0 = usually a temp file on disk)
                statement.execute("PRAGMA mmap_size = 2147483648"); // allows the OS to map up to 2 GB of the file into address space

                statement.execute("""
                CREATE TABLE IF NOT EXISTS auctions (
                    item_id TEXT PRIMARY KEY,
                    item_name TEXT,
                    item_tier TEXT,
                    item_price INTEGER,
                    item_status TEXT,
                    item_group TEXT,
                    profile_id TEXT,
                    profile_name TEXT,
                    started_at TEXT,
                    end_at TEXT,
                    saved_at TEXT
                )
                """);

                inserting = connection.prepareStatement("""
                INSERT OR IGNORE INTO auctions 
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return this;
        }
        public Database freezeObject() {
            return new Database(this);
        }
    }
    public static Draft draft() { return new Draft(); }


    public void update() throws SQLException {

        long startTime = System.currentTimeMillis();
        AtomicInteger newAuctions = new AtomicInteger(0);

        try {
            connection.setAutoCommit(false);
            int totalPages = api.getSkyBlockAuctions(0).join().getTotalPages();

            for (int page = 0; page < totalPages; page++) {
                Thread.sleep(1000);
                var pageReply = api.getSkyBlockAuctions(page).join();
                int newInPage = processAuctions(pageReply);
                if (newInPage > 0) newAuctions.addAndGet(newInPage);
            }
            connection.commit();

        } catch (Exception e) {
            connection.rollback();
            System.err.println(Thread.currentThread().getName() + ">> Error during update: " + e.getMessage());

        } finally {
            connection.setAutoCommit(true);
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            System.out.println(Thread.currentThread().getName() + ">> New auctions: " + newAuctions.get());
            System.out.println(Thread.currentThread().getName() + ">> Duration: " + duration + "s");
        }
    }


    private int processAuctions(SkyBlockAuctionsReply page) throws SQLException {
        int newAuctions = 0;

        try {
            for (JsonElement element : page.getAuctions()) {
                JsonObject auction = element.getAsJsonObject();

                boolean isBIN = auction.has("bin") && auction.get("bin").getAsBoolean();
                if(!isBIN) continue; // We only target BIN auctions

                String itemId = getStringOrDefault(auction, "uuid");
                String itemName = getStringOrDefault(auction, "item_name");
                String itemTier = getStringOrDefault(auction, "tier");
                long itemPrice = getLongOrDefault(auction, "starting_bid");
                String itemStatus = auction.has("claimed") && auction.get("claimed").getAsBoolean() ? "claimed" : "available";
                String itemGroup = getStringOrDefault(auction, "category");
                String profileId = getStringOrDefault(auction, "profile_id");
                String profileName = getStringOrDefault(auction, "auctioneer");
                String startedAt = formatMillis(getLongOrDefault(auction, "start"));
                String endAt = formatMillis(getLongOrDefault(auction, "end"));
                String savedAt = formatMillis(System.currentTimeMillis());

                inserting.setString(1, itemId);
                inserting.setString(2, itemName);
                inserting.setString(3, itemTier);
                inserting.setLong(4, itemPrice);
                inserting.setString(5, itemStatus);
                inserting.setString(6, itemGroup);
                inserting.setString(7, profileId);
                inserting.setString(8, profileName);
                inserting.setString(9, startedAt);
                inserting.setString(10, endAt);
                inserting.setString(11, savedAt);

                if (inserting.executeUpdate() > 0) newAuctions++;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return newAuctions;
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            System.out.println(Thread.currentThread().getName()  + ">> Database closed");
        }
    }

    private String getStringOrDefault(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : "UNKNOWN";
    }

    private long getLongOrDefault(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsLong()
                : 0L;
    }

    private String formatMillis(long millis) {
        return formatter.format(Instant.ofEpochMilli(millis));
    }
}
