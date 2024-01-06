package com.bergerkiller.bukkit.coasters.tracks;

import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.TrackedSignLookup;
import com.google.common.collect.MapMaker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Tracks all the TC-Coasters signs that exist on the server, mapped to their
 * unique key. This can be used to retrieve a TCC sign when the unique key is
 * known.
 */
public class TrackNodeSignLookup {
    private final Map<TrackNodeSignKey, TrackNodeSign> byKey = new MapMaker().weakValues().makeMap();
    private Object supplier = null; // Note: is object to avoid class loading nightmares for older TC versions

    public void store(TrackNodeSign sign) {
        byKey.put(sign.getKey(), sign);
    }

    public void remove(TrackNodeSign sign) {
        byKey.remove(sign.getKey(), sign);
    }

    public TrackNodeSign get(TrackNodeSignKey key) {
        return byKey.get(key);
    }

    public void register() {
        // Only do this if this feature exists in TrainCarts
        try {
            Class.forName("com.bergerkiller.bukkit.tc.rails.TrackedSignLookup");
        } catch (ClassNotFoundException ex) {
            return;
        }
        registerImpl();
    }

    public void unregister() {
        // Only do this if this feature exists in TrainCarts
        try {
            Class.forName("com.bergerkiller.bukkit.tc.rails.TrackedSignLookup");
        } catch (ClassNotFoundException ex) {
            return;
        }
        unregisterImpl();
    }

    private void registerImpl() {
        if (supplier == null) {
            TCCSignSupplier signSupplier = new TCCSignSupplier(this);
            signSupplier.register(TrainCarts.plugin);
            supplier = signSupplier;
        }
    }

    private void unregisterImpl() {
        if (supplier instanceof TCCSignSupplier) {
            ((TCCSignSupplier) supplier).unregister(TrainCarts.plugin);
        }
    }

    private static final class TCCSignSupplier implements TrackedSignLookup.SignSupplier {
        private final TrackNodeSignLookup lookup;

        public TCCSignSupplier(TrackNodeSignLookup lookup) {
            this.lookup = lookup;
        }

        public void register(final TrainCarts traincarts) {
            StreamUtil.class.getDeclaredMethods(); // Ensure class-loaded up-front
            traincarts.getTrackedSignLookup().registerSerializer("tcc-sign", new TrackedSignLookup.KeySerializer<TrackNodeSignKey>() {
                @Override
                public Class<TrackNodeSignKey> getKeyType() {
                    return TrackNodeSignKey.class;
                }

                @Override
                public TrackNodeSignKey read(DataInputStream dataInputStream) throws IOException {
                    return TrackNodeSignKey.of(StreamUtil.readUUID(dataInputStream));
                }

                @Override
                public void write(DataOutputStream dataOutputStream, TrackNodeSignKey trackNodeSignKey) throws IOException {
                    StreamUtil.writeUUID(dataOutputStream, trackNodeSignKey.getUniqueId());
                }
            });

            traincarts.getTrackedSignLookup().register(this);
        }

        public void unregister(final TrainCarts traincarts) {
            traincarts.getTrackedSignLookup().unregister(this);
            traincarts.getTrackedSignLookup().unregisterSerializer("tcc-sign");
        }

        @Override
        public RailLookup.TrackedSign getTrackedSign(Object uniqueKey) {
            if (uniqueKey instanceof TrackNodeSignKey) {
                TrackNodeSign nodeSign = lookup.get((TrackNodeSignKey) uniqueKey);
                if (nodeSign != null && nodeSign.hasTrackedSign()) {
                    return nodeSign.getTrackedSign();
                }
            }
            return null;
        }
    }
}
