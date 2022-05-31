import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.vk.api.sdk.actions.Board;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.board.GetCommentsSort;
import com.vk.api.sdk.objects.board.TopicComment;
import com.vk.api.sdk.objects.users.Fields;
import com.vk.api.sdk.objects.users.responses.GetResponse;
import com.vk.api.sdk.objects.wall.CommentAttachment;
import com.vk.api.sdk.queries.board.BoardGetCommentsQuery;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.List;

public class Main extends ListenerAdapter {
    static int GROUP_ID;
    static int TOPIC_ID;
    static int USER_ID;
    static String ACCESS_TOKEN;
    int checked_id = Integer.MIN_VALUE;

    private static final int PLAYLIST_LIMIT = 1000;
    private static final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private static final Map<String, Map.Entry<AudioPlayer, TrackScheduler>> players = new HashMap<>();

    private static final TransportClient transportClient = new HttpTransportClient();
    private static final VkApiClient vk = new VkApiClient(transportClient);
    private static final Board board = new Board(vk);
    private static UserActor actor;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        Properties mySettings = new Properties();
        InputStream in = ClassLoader.getSystemResourceAsStream("settings.properties");
        mySettings.load(in);
        in.close();

        String BOT_TOKEN = mySettings.getProperty("bot_token");
        GROUP_ID = Integer.parseInt(mySettings.getProperty("group_id"));
        TOPIC_ID = Integer.parseInt(mySettings.getProperty("topic_id"));
        USER_ID = Integer.parseInt(mySettings.getProperty("user_id"));
        ACCESS_TOKEN = mySettings.getProperty("access_token");


        JDA jda = JDABuilder.createDefault(BOT_TOKEN)
                .addEventListeners(new Main())
                .setActivity(Activity.playing("!help"))
                .build();
        jda.awaitReady();


        actor = new UserActor(USER_ID, ACCESS_TOKEN);
    }

    public Main() {
        AudioSourceManagers.registerRemoteSources(playerManager);
    }

    private boolean hasPlayer(Guild guild) {
        return players.containsKey(guild.getId());
    }

    private AudioPlayer getPlayer(Guild guild) {

        AudioPlayer p;
        if (hasPlayer(guild)) {
            p = players.get(guild.getId()).getKey();
        } else {
            p = createPlayer(guild);
        }
        return p;
    }

    private AudioPlayer createPlayer(Guild guild) {
        AudioPlayer player = playerManager.createPlayer();
        TrackScheduler scheduler = new TrackScheduler(player);
        player.addListener(scheduler);
        guild.getAudioManager().setSendingHandler(new MySendHandler(player));
        players.put(guild.getId(), new AbstractMap.SimpleEntry<>(player, scheduler));
        return player;
    }

    private TrackScheduler getTrackManager(Guild guild) {
        return players.get(guild.getId()).getValue();
    }

    private void loadTrack(String trackMsg, Member member) {

        Guild guild = member.getGuild();
        getPlayer(guild);

        playerManager.setFrameBufferDuration(5000);
        playerManager.loadItem(trackMsg, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                getTrackManager(guild).queue(track, member);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getSelectedTrack() != null) {
                    trackLoaded(playlist.getSelectedTrack());
                } else if (playlist.isSearchResult()) {
                    trackLoaded(playlist.getTracks().get(0));
                } else {
                    for (int i = 0; i < Math.min(playlist.getTracks().size(), PLAYLIST_LIMIT); i++) {
                        getTrackManager(guild).queue(playlist.getTracks().get(i), member);
                    }
                }
            }

            @Override
            public void noMatches() {
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
            }
        });
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String[] message = event.getMessage().getContentRaw().split(" ");
        Guild guild = event.getMember().getGuild();

        try {
            AudioManager manager = event.getGuild().getAudioManager();
            AudioSourceManagers.registerRemoteSources(playerManager);
            MessageChannel messageChannel = event.getChannel();

            switch (message[0].toLowerCase(Locale.ROOT)) {
                case "!play":
                case "!p":
                    VoiceChannel vc = event.getGuild().getVoiceChannelById(
                            event.getMember().getVoiceState().getChannel().getId());

                    String input = String.join(" ", Arrays.copyOfRange(message, 1, message.length));

                    if (input.startsWith("http")) input = message[1];
                    else input = "ytsearch: " + input;

                    loadTrack(input, event.getMember());

                    if (getPlayer(guild).isPaused())
                        getPlayer(guild).setPaused(false);

                    messageChannel.sendMessage("searching...").queue();
                    manager.openAudioConnection(vc);
                    break;
                case "!skip":
                case "!s":
                    getPlayer(guild).stopTrack();
                    break;
                case "!clear":
                case "!c":
                    getTrackManager(guild).purgeQueue();
                    messageChannel.sendMessage("It's finally cleared!!!! HAHAHAHHAHAHAHAH").queue();
                    break;
                case "!help":
                case "!h":
                    Role role = event.getMember().getGuild().getRolesByName("notifications", false).get(0);

                    event.getChannel().sendMessage("<@&" + role.getId() + ">").queue(
                            response -> response.editMessageEmbeds(sendHelpMessage(event)).queue()
                    );
                    break;
                case "!mix":
                case "!m":
                    try {
                        getTrackManager(guild).shuffleQueue();
                        messageChannel.sendMessage("It's finally mixed!!!! HAHAHAHHAHAHAHAH").queue();

                    } catch (Exception e) {
                        messageChannel.sendMessage("No queue!").queue();
                    }
                    break;
                case "!vk":
                case "!opalevamakesmefellsbetter":
                    BoardGetCommentsQuery jokes = board.getComments(actor, GROUP_ID, TOPIC_ID).sort(GetCommentsSort.REVERSE_CHRONOLOGICAL).count(5);
                    List<TopicComment> a = jokes.execute().getItems().stream().toList();
                    MessageChannel channel = event.getChannel();

                    for (TopicComment topicComment : a) {

                        int time = topicComment.getDate();
                        LocalDateTime datePublish = LocalDateTime.ofEpochSecond(time, 0, ZoneOffset.ofHours(3));

                        int currentDay = LocalDateTime.now().getDayOfMonth();

                        if (datePublish.getDayOfMonth() == currentDay
                                || datePublish.getDayOfMonth() == currentDay - 1) {

                            String s = topicComment.getText()
                                    .replaceFirst("_", " ")
                                    .replaceFirst("_", " ");

                            String url = "[This post](https://vk.com/topic-" + GROUP_ID + "_" + TOPIC_ID + "?post=" + topicComment.getId() + ")";

                            MessageEmbed mb = new EmbedBuilder()
                                    .setColor(new Color(22, 138, 233))
                                    .setDescription(" SOME FUCKING PAIRS \n\n")
                                    .addField("", url, false)
                                    .addField("", s, false)
                                    .build();

                            channel.sendMessageEmbeds(mb).queue();
                        }
                    }
                    break;
                case "!start":
                case "!st":
                    BoardGetCommentsQuery boardGetCommentsQuery = board.getComments(actor, GROUP_ID, TOPIC_ID).sort(GetCommentsSort.REVERSE_CHRONOLOGICAL).count(5);

                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                getIdPosts(event, boardGetCommentsQuery, GROUP_ID, TOPIC_ID);
                            } catch (ClientException | ApiException e) {
                                e.printStackTrace();
                            }
                        }
                    }, 0, 5 * 60 * 1000);
                    break;
            }
        } catch (ClientException | ApiException e) {
            e.printStackTrace();
        }
    }


    private void getIdPosts(MessageReceivedEvent event, BoardGetCommentsQuery boardGetCommentsQuery, int group, int topic) throws ClientException, ApiException {
        List<TopicComment> a = boardGetCommentsQuery.execute().getItems().stream().toList();
        String name;
        String surname;

        for (TopicComment topicComment : a) {
            if (topicComment.getId() > checked_id) {
                List<GetResponse> str = vk.users().get(new UserActor(USER_ID, ACCESS_TOKEN))
                        .userIds(topicComment.getFromId().toString())
                        .fields(Fields.FIRST_NAME_NOM)
                        .fields(Fields.LAST_NAME_NOM)
                        .execute().stream().toList();

                checked_id = topicComment.getId();
                System.out.println(checked_id);

                String s = topicComment.getText()
                        .replaceFirst("_", " ")
                        .replaceFirst("_", " ");


                String url = "[This post](https://vk.com/topic-" + group + "_" + topic + "?post=" + topicComment.getId() + ")";
                List<CommentAttachment> files = topicComment.getAttachments();

                int fileCount = 0;
                if (files != null) fileCount = files.size(); //qwfqw

                for (GetResponse getResponse : str) {
                    name = getResponse.getFirstName();
                    surname = getResponse.getLastName();

                    MessageEmbed mb = new EmbedBuilder()
                            .setColor(new Color(22, 138, 233))
                            .setDescription(" SOME FUCKING PAIRS \n\n")
                            .addField("", url + " (Found " + fileCount + " files)", false)
                            .addField(name + " " + surname, s, false)
                            .build();

                    Role notifications = event.getMember().getGuild().getRolesByName("notifications", false).get(0);

                    event.getChannel().sendMessage("<@&" + notifications.getId() + ">").queue(
                            response -> response.editMessageEmbeds(mb).queue()
                    );
                }
            }
        }
    }


    private MessageEmbed sendHelpMessage(MessageReceivedEvent event) {
        MessageEmbed helpEmbed = new EmbedBuilder()
                .setColor(new Color(22, 138, 233))
                .setDescription("  __**MUSIC PLAYER GUIDE**__  \n\n")
                .addField("!play <INPUT>", ":heavy_minus_sign: :heavy_minus_sign: :heavy_minus_sign:  ", false)
                .addField("!skip", ":heavy_minus_sign: :heavy_minus_sign: :heavy_minus_sign:  ", false)
                .addField("!clear", ":heavy_minus_sign: :heavy_minus_sign: :heavy_minus_sign:  ", false)
                .addField("!mix", ":heavy_minus_sign: :heavy_minus_sign: :heavy_minus_sign:  ", false)
                .addField("!vk", ":heavy_minus_sign: :heavy_minus_sign: :heavy_minus_sign:  ", false)
                .addField("!start", ":heavy_minus_sign: :heavy_minus_sign: :heavy_minus_sign:  ", false)
                .build();
        return helpEmbed;
    }
}
