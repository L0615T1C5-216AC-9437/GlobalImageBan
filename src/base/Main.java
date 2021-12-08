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

public class Main extends Plugin {
    private static MessageDigest messageDigest;

    public Main() {
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Log.err(Strings.neatError(e));
            return;
        }

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking || event.unit == null || event.unit.getPlayer() == null) return;
            Player player = event.unit.getPlayer();
            if (event.tile.build instanceof LogicBlock.LogicBuild) {
                LogicBlock.LogicBuild lb = (LogicBlock.LogicBuild) event.tile.build;
                lb.configure(event.config);
                //check if draws to display
                if (lb.code.contains("drawflush display")) {
                    String[] check = Core.settings.getBool("gib_complexSearch", false) ? lb.code.split("drawflush display.\n") : new String[]{ lb.code };
                    for (String c : check) {
                        try {
                            byte[] hash = messageDigest.digest(c.getBytes(StandardCharsets.UTF_8));
                            String b64Hash = Base64.getEncoder().encodeToString(hash);
                            Log.debug(b64Hash);
                            //http request
                            URL url = new URL("http://c-n.ddns.net:9999/bmi/check/?b64hash=" + URLEncoder.encode(b64Hash, "UTF-8"));
                            HttpURLConnection con = (HttpURLConnection) url.openConnection();
                            con.setConnectTimeout(1000);
                            con.setRequestMethod("GET");
                            con.setDoOutput(true);
                            con.setRequestProperty("Content-Type", "application/json");
                            Log.debug(con.getResponseCode());
                            if (con.getResponseCode() == 200) {
                                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                                String inputLine;
                                StringBuilder content = new StringBuilder();
                                while ((inputLine = in.readLine()) != null) {
                                    content.append(inputLine);
                                }
                                in.close();
                                JSONObject json = new JSONObject(content.toString());
                                if (Core.settings.getBool("gib_kickOnHit", false)) {
                                    player.con.kick("[scarlet]Built banned logic image\n[lightgray]ID: " + json.get("id") + "\nBID: " + json.get("bid"), 3 * 60 * 60 * 1000L);
                                }
                                //send message to everyone but the player building it
                                for (Player p : Groups.player) {
                                    if (p != player) p.sendMessage("[scarlet]> " + colorless(player.name) + " [scarlet]built banned image (prob NSFW) @ (" + event.tile.x + "," + event.tile.y + ")\n[gray]ID:" + json.get("id") + " | bid:" + json.get("bid"));
                                }
                                break; //stop checking if hit
                            }
                        } catch (Exception e) {
                            Log.err(Strings.neatError(e));
                        }
                    }
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
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {

    }

    public static String colorless(String string) {
        return Strings.stripColors(string.replace("[[]", "[[[]]"));
    }
}
