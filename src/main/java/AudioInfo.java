import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Member;

public class AudioInfo {
    private final AudioTrack track;
    private final Member member;

    public AudioInfo(AudioTrack track, Member member) {
        this.track = track;
        this.member = member;
    }

    public AudioTrack getTrack() {
        return track;
    }

    public Member getMember() {
        return member;
    }
}
