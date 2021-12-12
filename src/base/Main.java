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
    private static MessageDigest messageDigest;
    private static final HashMap<String, BMIData> cache = new HashMap<>();
    private static class BMIData {
        public final int httpResponse;
        public final JSONObject data;
        private final long expiration;

        public BMIData(int httpResponse, JSONObject data) {
            this.httpResponse = httpResponse;
            this.data = data;
            this.expiration = System.currentTimeMillis() + (24 * 60 * 60 * 1000L);
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
                    CompletableFuture.runAsync(() -> {
                        String[] check = Core.settings.getBool("gib_complexSearch", false) ? lb.code.split("drawflush display.\n") : new String[]{lb.code};
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
                                    con.setConnectTimeout(Core.settings.getInt("gib_ConnectionTimeout", 1000));
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
                                        handleHit(player, event.tile, json);
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
        handler.register("gibtcs", "Toggle Complex Search", args -> {
            Core.settings.put("gib_complexSearch", !Core.settings.getBool("gib_complexSearch", false));
            Log.info("gib_kickOnHit set to " + Core.settings.getBool("gib_complexSearch", false));
        });
        handler.register("gibtkoh", "Toggle Kick on Hit", args -> {
            Core.settings.put("gib_kickOnHit", !Core.settings.getBool("gib_kickOnHit", false));
            Log.info("gib_kickOnHit set to " + Core.settings.getBool("gib_kickOnHit", false));

        });
        handler.register("gibcbt", "<milliseconds>", "Configure Broadcast Timeout", args -> {
            if (Strings.canParseInt(args[0])) {
                Core.settings.put("gib_BroadcastTimeout", Strings.parseInt(args[0]));
                System.out.println("Broadcast Timeout set to " + args[0].trim() + "ms");
            } else {
                System.out.println("Must be a number!");
            }
        });
        handler.register("gibcct", "<milliseconds>", "Configure Connection Time Out", args -> {
            if (Strings.canParseInt(args[0])) {
                Core.settings.put("gib_ConnectionTimeout", Strings.parseInt(args[0]));
                System.out.println("Connection Timeout set to " + args[0].trim() + "ms");
            } else {
                System.out.println("Must be a number!");
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {

    }

    private static void handleHit(Player player, Tile t, JSONObject json) {
        if (Core.settings.getBool("gib_kickOnHit", false)) {
            player.con.kick("[scarlet]Built banned logic image\n[lightgray]ID: " + json.get("id") + "\nBID: " + json.get("bid"), 3 * 60 * 60 * 1000L);
            Log.info(colorless(player.name) + " was kicked for placing banned image! BID: " + json.get("bid") + " ID: " + json.get("id"));
        }
        if (lastBroadcast < System.currentTimeMillis()) {
            lastBroadcast = System.currentTimeMillis() + Core.settings.getInt("gib_BroadcastTimeout", 2000);
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
