package base;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class Main extends Plugin {
    private static final int toHours = 60 * 60 * 1000;

    private static MessageDigest messageDigest;
    private static final HashMap<String, BMIData> cache = new HashMap<>();
    private static long cacheExpireCheck = System.currentTimeMillis() + toHours;

    private static class BMIData {
        public final int httpResponse;
        public final JSONObject data;
        private final long expiration;

        public BMIData(int httpResponse, JSONObject data) {
            this.httpResponse = httpResponse;
            this.data = data;
            this.expiration = System.currentTimeMillis() + (24 * toHours);
        }

        public boolean expired() {
            return expiration < System.currentTimeMillis();
        }

        public boolean normalResponse() {
            return httpResponse == 200 || httpResponse == 404;
        }
    }

    private static long lastBroadcast = System.currentTimeMillis();

    public Main() {
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Log.err(Strings.neatError(e));
            return;
        }

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking || event.unit == null || event.unit.getPlayer() == null) return;
            final Player player = event.unit.getPlayer();
            if (event.tile.build instanceof LogicBlock.LogicBuild) {
                final LogicBlock.LogicBuild lb = (LogicBlock.LogicBuild) event.tile.build;
                lb.configure(event.config);
                //check if draws to display
                if (lb.code.contains("drawflush display")) {
                    //expire old cache
                    if (cacheExpireCheck < System.currentTimeMillis()) {
                        cacheExpireCheck = System.currentTimeMillis() + toHours;
                        cache.entrySet().removeIf(e -> e.getValue().expired());
                    }

                    String[] check = Config.ComplexSearch.bool() ? lb.code.split("drawflush display.\n") : new String[]{lb.code};
                    CompletableFuture.runAsync(() -> {
                        for (String c : check) {
                            try {
                                byte[] hash = messageDigest.digest(c.getBytes(StandardCharsets.UTF_8));
                                String b64Hash = Base64.getEncoder().encodeToString(hash);
                                Log.debug(b64Hash);
                                BMIData bmid = cache.get(b64Hash);
                                if (bmid != null && !bmid.expired() && bmid.normalResponse()) {
                                    Log.debug("b64hash found in cache.");
                                    if (bmid.httpResponse == 200) handleHit(player, event.tile, bmid.data);
                                } else {
                                    Log.debug("b64hash not found in cache, performing http get.");
                                    //http request
                                    URL url = new URL("http://c-n.ddns.net:9999/bmi/check/?b64hash=" + URLEncoder.encode(b64Hash, "UTF-8"));
                                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                                    con.setConnectTimeout(Config.ConnectionTimeout.num());
                                    con.setRequestMethod("GET");
                                    con.setDoOutput(true);
                                    con.setRequestProperty("Content-Type", "application/json");
                                    int httpResponse = con.getResponseCode();
                                    Log.debug(httpResponse);
                                    if (httpResponse == 200) {
                                        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                                        String inputLine;
                                        StringBuilder content = new StringBuilder();
                                        while ((inputLine = in.readLine()) != null) {
                                            content.append(inputLine);
                                        }
                                        in.close();
                                        JSONObject json = new JSONObject(content.toString());
                                        cache.put(b64Hash, new BMIData(httpResponse, json));
                                        Core.app.post(() -> handleHit(player, event.tile, json));
                                        break; //stop checking if hit
                                    } else {
                                        cache.put(b64Hash, new BMIData(httpResponse, new JSONObject()));
                                    }
                                }
                            } catch (Exception e) {
                                Log.err(Strings.neatError(e));
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("gibconfig", "[name] [value...]", "Configure server settings.", arg -> {
            if (arg.length == 0) {
                Log.info("All config values:");
                for (Config c : Config.all) {
                    Log.info("&lk| @: @", c.name(), "&lc&fi" + c.get());
                    Log.info("&lk| | &lw" + c.description);
                    Log.info("&lk|");
                }
                Log.info("use the command with the value set to \"default\" in order to use the default value.");
                return;
            }

            try {
                Config c = Config.valueOf(arg[0]);
                if (arg.length == 1) {
                    Log.info("'@' is currently @.", c.name(), c.get());
                } else {
                    if (arg[1].equals("default")) {
                        c.set(c.defaultValue);
                    } else if (c.isBool()) {
                        c.set(arg[1].equals("on") || arg[1].equals("true"));
                    } else if (c.isNum()) {
                        try {
                            c.set(Integer.parseInt(arg[1]));
                        } catch (NumberFormatException e) {
                            Log.err("Not a valid number: @", arg[1]);
                            return;
                        }
                    } else if (c.isString()) {
                        c.set(arg[1].replace("\\n", "\n"));
                    }

                    Log.info("@ set to @.", c.name(), c.get());
                    Core.settings.forceSave();
                }
            } catch (IllegalArgumentException e) {
                Log.err("Unknown config: '@'. Run the command with no arguments to get a list of valid configs.", arg[0]);
            }
        });
        handler.register("gibclearcache", "Clears cached hashes", args -> {
            Log.info(cache.size() + " entries removed.");
            cache.clear();
        });
    }

    public enum Config {
        ComplexSearch("Whether to perform a complex search on code. This prevents easy bypass of scan by editing few lines code.", false, "ComplexSearch"),
        NudityOnly("Whether the server check for nudity only, or nudity and erotic images", false, "NudityOnly"),
        KickBanMessage("What message to send when user is kicked/banned. BID, ID and BMI invite will still be sent.", "[scarlet]Built banned logic image", "KickBanMessage"),
        BanOnHit("Whether to ban players when a banned mindustry image is detected. Overrides KickOnHit if true.", false, "BanOnHit"),
        KickOnHit("Whether to kick players when a banned mindustry image is detected.", false, "KickOnHit"),
        BroadcastTimeout("How often, in millis, the server will broadcast when a placer is building nsfw.", 2000, "BroadcastTimeout"),
        ConnectionTimeout("How long, in millis, the server will wait for a http response before giving up.", 1000, "ConnectionTimeout"),
        KickDuration("How many minutes the player will kick be for.", 3 * 60, "KickDuration");

        public static final Config[] all = values();

        public final Object defaultValue;
        public final String key, description;
        final Runnable changed;

        Config(String description, Object def) {
            this(description, def, null, null);
        }

        Config(String description, Object def, String key) {
            this(description, def, key, null);
        }

        Config(String description, Object def, Runnable changed) {
            this(description, def, null, changed);
        }

        Config(String description, Object def, String key, Runnable changed) {
            this.description = description;
            this.key = "gib_" + (key == null ? name() : key);
            this.defaultValue = def;
            this.changed = changed == null ? () -> {
            } : changed;
        }

        public boolean isNum() {
            return defaultValue instanceof Integer;
        }

        public boolean isBool() {
            return defaultValue instanceof Boolean;
        }

        public boolean isString() {
            return defaultValue instanceof String;
        }

        public Object get() {
            return Core.settings.get(key, defaultValue);
        }

        public boolean bool() {
            return Core.settings.getBool(key, (Boolean) defaultValue);
        }

        public int num() {
            return Core.settings.getInt(key, (Integer) defaultValue);
        }

        public String string() {
            return Core.settings.getString(key, (String) defaultValue);
        }

        public void set(Object value) {
            Core.settings.put(key, value);
            changed.run();
        }
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {

    }

    private static void handleHit(Player player, Tile t, JSONObject json) {
        //check if we are in nudity only mode and if the image contains nudity
        if (Config.NudityOnly.bool() && !json.getBoolean("nudity")) {
            Log.debug("Hash found but not marked as nudity.");
            return;
        }
        //check if we kick players upon hit
        if (Config.KickOnHit.bool()) {
            player.con.kick(Config.KickBanMessage.string() + "\n[lightgray]ID: " + json.get("id") + "\nBID: " + json.get("bid") + "\n\n[lightgray]If you think this was a error please go to discord.gg/v7SyYd2D3y and report the ID and BID", Config.KickDuration.num() * 60 * 1000L);
            Log.warn(colorless(player.name) + " was kicked for placing banned image! BID: " + json.get("bid") + " ID: " + json.get("id"));
            Log.info("If you think this was an error, please go report it at discord.hh/v7SyYd2D3y");
        }
        //check if we can broadcast the message again
        if (lastBroadcast < System.currentTimeMillis()) {
            lastBroadcast = System.currentTimeMillis() + Config.BroadcastTimeout.num() + Config.BroadcastTimeout.num();
            //send message to everyone but the player building it
            for (Player p : Groups.player) {
                if (p != player)
                    p.sendMessage("[scarlet]> " + colorless(player.name) + " [scarlet]built banned image (prob NSFW) @ (" + t.x + "," + t.y + ")\n[gray]ID:" + json.get("id") + " | bid:" + json.get("bid"));
            }
        }
    }

    public static String colorless(String string) {
        return Strings.stripColors(string.replace("[[]", "[[[]]"));
    }
}
