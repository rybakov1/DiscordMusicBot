import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import net.dv8tion.jda.api.entities.*;
import org.apache.commons.lang3.ArrayUtils;


public class TrackScheduler extends AudioEventAdapter {

    private AudioPlayer player;
    private Queue<AudioInfo> queue;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
    }

    int[] arr = new int[]{3, 5, 1, 4, 2};
    int indexOfTwo = ArrayUtils.indexOf(arr, 2);

    public void queue(AudioTrack track, Member member) {
        AudioInfo info = new AudioInfo(track, member);
        queue.add(info);

        if (player.getPlayingTrack() == null) {
            player.playTrack(track);
        }
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {
        // Player was paused
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {
        // Player was resumed
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        AudioInfo info = queue.element();
        VoiceChannel vChan = (VoiceChannel) info.getMember().getVoiceState().getChannel();
        TextChannel channel = info.getMember().getGuild().getTextChannelsByName("music-makes-me-lose-control", false).get(0);

        try {
            byte[] bytes = ("Трек: **").getBytes("Cp1251");
            byte[] bytes2 = ("** играет прямо сейчас! (").getBytes("Cp1251");

            String trackS = new String(bytes, StandardCharsets.UTF_8);
            String mem = new String(bytes2, StandardCharsets.UTF_8);

            if (vChan == null) {
                player.stopTrack();
            } else {
                info.getMember().getGuild().getAudioManager().openAudioConnection(vChan);
            }
            String output = trackS + info.getTrack().getInfo().title + mem + getTimestamp(info.getTrack().getDuration()) + ")";

            channel.editMessageById(channel.getLatestMessageId(), output).queue();
            channel.getManager().setTopic(output).queue();
        } catch (Exception ignored) {
        }
    }

    private String getTimestamp(long milis) {
        long seconds = milis / 1000;
        long hours = Math.floorDiv(seconds, 3600);
        seconds = seconds - (hours * 3600);
        long mins = Math.floorDiv(seconds, 60);
        seconds = seconds - (mins * 60);
        return (hours == 0 ? "" : hours + ":") + String.format("%02d", mins) + ":" + String.format("%02d", seconds);
    }

    public void shuffleQueue() {
        List<AudioInfo> tQueue = new ArrayList<>(getQueuedTracks());
        AudioInfo current = tQueue.get(0);
        tQueue.remove(0);
        Collections.shuffle(tQueue);
        tQueue.add(0, current);
        purgeQueue();
        queue.addAll(tQueue);
    }

    public Set<AudioInfo> getQueuedTracks() {
        return new LinkedHashSet<>(queue);
    }

    public void purgeQueue() {
        queue.clear();
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        try {
            Guild g = queue.poll().getMember().getGuild();
            if (queue.isEmpty()) {
                int a = 0;
            } else {
                player.playTrack(queue.element().getTrack());
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        // An already playing track threw an exception (track end event will still be received separately)
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        // Audio track has been unable to provide us any audio, might want to just start a new track
    }
}