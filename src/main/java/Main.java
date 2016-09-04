import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.json.*;

public class Main {

    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";
    private static final String PLAYLIST_ID = getConfig().getProperty("playlistId");
    private static final String API_KEY = getConfig().getProperty("apiKey");

    private static Collection<String> playlist;

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) {

        // Set the local members to external resources
        try {
            playlist = getPlaylist(PLAYLIST_ID);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        while (true) {

            Collection<String> newPlaylist;

            try {

                newPlaylist = getPlaylist(PLAYLIST_ID);

                newPlaylist.removeAll(playlist);
                System.out.println("Diff: " + newPlaylist);

                // Loop through all new videos and save them as files
                for (String video : newPlaylist) {
                    final File file = copyToFile(video);

                    // Delete music after 1 day elapses
                    Runnable task = new Runnable() {
                        public void run() {
                            if (!file.delete()) {
                                System.out.println("ERROR: File " + file.getName() + " could not be deleted!");
                            }
                        }
                    };
                    executor.schedule(task, 1, TimeUnit.DAYS);

                }

                playlist.addAll(newPlaylist);
                System.out.println("Playlist: " + playlist);

                TimeUnit.SECONDS.sleep(1);

            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static Collection<String> getPlaylist(String playlistId) throws Exception {
        URL url = new URL(BASE_URL + "/playlistItems?part=contentDetails&playlistId=" + playlistId + "&key=" + API_KEY);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        if (conn.getResponseCode() != 200) {
            throw new Exception("Failed : HTTP error code : " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

        String line, output = "";
        while ((line = br.readLine()) != null) {
            output += line + "\n";
        }
        //System.out.println(output);
        conn.disconnect();

        JSONObject response = new JSONObject(output);

        // Parse the response for all videos and add them to a collection
        JSONArray videos = response.getJSONArray("items");
        Collection<String> playlist = new ArrayList<String>();

        for (int i = 0; i < videos.length(); i++) {
            playlist.add(videos.getJSONObject(i).getJSONObject("contentDetails").getString("videoId"));
        }

        return playlist;
    }

    private static String getVideoTitle(String id) throws Exception {
        URL url = new URL(BASE_URL + "/videos?part=snippet&id=" + id + "&key=" + API_KEY);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        if (conn.getResponseCode() != 200) {
            throw new Exception("Failed : HTTP error code : " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

        String line, output = "";
        while ((line = br.readLine()) != null) {
            output += line + "\n";
        }
        System.out.println(output);
        conn.disconnect();

        JSONObject response = new JSONObject(output);
        return response.getJSONArray("items").getJSONObject(0).getJSONObject("snippet").getString("title");

    }

    private static File copyToFile(String id) throws Exception {

        URL url = new URL("https://www.youtubeinmp3.com/fetch/?url=https://www.youtube.com/watch?v=" + id);
        File file = new File("music/" + getVideoTitle(id));

        FileUtils.copyURLToFile(url, file);

        return file;
    }

    private static Properties getConfig() {

        Properties properties = null;
        InputStream in = null;

        try {
            Properties prop = new Properties();
            String propFileName = "config/config.properties";

            in = Main.class.getClassLoader().getResourceAsStream(propFileName);

            if (in != null) {
                prop.load(in);
            } else {
                throw new FileNotFoundException("Property file '" + propFileName + "' not found in the classpath.");
            }

            properties = prop;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return properties;
    }
}
