package com.bergerkiller.bukkit.coasters.editor;

import java.util.Arrays;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * Tracks player right-click input, automatically delaying the incoming
 * transformations to smoothen it out.
 */
public final class PlayerEditInput {
    /**
     * Amount of milliseconds without click input to indicate the player
     * stopped holding down his right-click mouse button.
     */
    private static final long INPUT_TIMEOUT_MS = 400;

    private final Player player;
    private boolean clicked;
    private boolean hasInput;
    private boolean finishedPlaying;
    private Matrix4x4[] playing;
    private Matrix4x4[] buffer;
    private int playingIdx;
    private int bufferSize;
    private long lastInputTime;
    private long startInputTime;

    public PlayerEditInput(Player player) {
        this.player = player;
        this.clicked = false;
        this.hasInput = false;
        this.finishedPlaying = true;
        this.bufferSize = 0;
        this.playingIdx = 0;
        this.lastInputTime = 0L;
        this.startInputTime = 0L;
        this.buffer = new Matrix4x4[(int) ((2*INPUT_TIMEOUT_MS)/20)];
        this.playing = new Matrix4x4[this.buffer.length];
        for (int i = 0; i < this.buffer.length; i++) {
            this.buffer[i] = new Matrix4x4();
            this.playing[i] = new Matrix4x4();
        }
        this.fill(this.playing[this.playingIdx]);
    }

    /**
     * Gets the current player input transformation sample
     * 
     * @return transform matrix
     */
    public Matrix4x4 get() {
        return this.playing[this.playingIdx];
    }

    /**
     * Gets whether the player is clicking and holding down
     * 
     * @return True if input is available
     */
    public boolean hasInput() {
        return this.hasInput;
    }

    /**
     * Gets for how long the player held down the button so far, in milliseconds
     * 
     * @return held down duration in milliseconds
     */
    public long heldDuration() {
        return this.hasInput ? (System.currentTimeMillis() - this.startInputTime) : 0L;
    }

    /**
     * Simulates a click input, dictating buffering delays
     */
    public void click() {
        this.clicked = true;
        this.lastInputTime = System.currentTimeMillis();
        if (!this.hasInput) {
            this.hasInput = true;
            this.startInputTime = this.lastInputTime;
        }
    }

    /**
     * Call every tick to update the player input
     */
    public void update() {
        // Next playing sample
        if (this.playingIdx > 0) {
            this.playingIdx--;
            this.finishedPlaying = false;
        } else {
            this.finishedPlaying = true;
        }

        // Fill buffer with input while input is active
        if (this.hasInput) {
            this.resize();
            this.fill(this.buffer[this.bufferSize++]);
            if (this.clicked) {
                this.clicked = false;

                // Move entire buffer to playing and clear it
                for (int i = 0; i < this.bufferSize; i++) {
                    this.playing[i].set(this.buffer[this.bufferSize - i - 1]);
                }
                this.playingIdx = this.bufferSize - 1;
                this.finishedPlaying = false;
                this.bufferSize = 0;
            } else if (this.finishedPlaying && (System.currentTimeMillis() - this.lastInputTime) > INPUT_TIMEOUT_MS) {
                this.hasInput = false;
                this.bufferSize = 0;
            }
        }
    }

    private final void fill(Matrix4x4 m) {
        m.setIdentity();
        m.translateRotate(this.player.getEyeLocation());
    }

    private final void resize() {
        if (this.bufferSize >= (this.buffer.length - 1)) {
            int new_size = this.buffer.length * 2;
            this.buffer = Arrays.copyOf(this.buffer, new_size);
            this.playing = Arrays.copyOf(this.playing, new_size);
            for (int i = 0; i < new_size; i++) {
                if (this.buffer[i] == null) this.buffer[i] = new Matrix4x4();
                if (this.playing[i] == null) this.playing[i] = new Matrix4x4();
            }
        }
    }
}
