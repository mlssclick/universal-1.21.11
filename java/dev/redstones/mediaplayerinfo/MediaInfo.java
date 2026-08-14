package dev.redstones.mediaplayerinfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public class MediaInfo implements Serializable {
    private final String title;
    private final String artist;
    private final byte[] artworkPng;
    private final long position;
    private final long duration;
    private final boolean playing;

    public MediaInfo(String title, String artist, byte[] artworkPng, long position, long duration, boolean playing) {
        this.title = title;
        this.artist = artist;
        this.artworkPng = artworkPng;
        this.position = position;
        this.duration = duration;
        this.playing = playing;
    }

    public String getTitle() {
        return this.title;
    }

    public String getArtist() {
        return this.artist;
    }

    public byte[] getArtworkPng() {
        return this.artworkPng;
    }

    public long getPosition() {
        return this.position;
    }

    public long getDuration() {
        return this.duration;
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public BufferedImage getArtwork() {
        try {
            return ImageIO.read(new ByteArrayInputStream(this.artworkPng));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MediaInfo mediaInfo = (MediaInfo) o;
        return this.position == mediaInfo.position
                && this.duration == mediaInfo.duration
                && this.playing == mediaInfo.playing
                && Objects.equals(this.title, mediaInfo.title)
                && Objects.equals(this.artist, mediaInfo.artist)
                && Arrays.equals(this.artworkPng, mediaInfo.artworkPng);
    }

    @Override
    public int hashCode() {
        return (31 * Objects.hash(this.title, this.artist, this.position, this.duration, this.playing)) + Arrays.hashCode(this.artworkPng);
    }

    @Override
    public String toString() {
        return "MediaInfo{title='" + title + "', artist='" + artist + "', position=" + position + ", duration=" + duration + ", playing=" + playing + "}";
    }
}
