package baritone.launch.mixins;

/**
 * Mixin class to handle server-side events for Baritone.
 */
//@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerPlayNetHandler {
    /*@Inject(
            method = "handleForgetLevelChunk",
            at = @At("HEAD")
    )
    private void preChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone != null) {
                ibaritone.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.PRE, ChunkEvent.Type.UNLOAD, packet.pos().x, packet.pos().z)
                );
            }
        }
    }

    @Inject(
            method = "handleForgetLevelChunk",
            at = @At("RETURN")
    )
    private void postChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone != null) {
                ibaritone.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.POST, ChunkEvent.Type.UNLOAD, packet.pos().x, packet.pos().z)
                );
            }
        }
    }

    @Inject(
            method = "handleBlockUpdate",
            at = @At("RETURN")
    )
    private void postHandleBlockChange(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (!Baritone.settings().repackOnAnyBlockChange.value) {
            return;
        }
        if (!CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(packet.getBlockState().getBlock())) {
            return;
        }
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone != null) {
                ibaritone.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.POST,
                                ChunkEvent.Type.POPULATE_FULL,
                                packet.getPos().getX() >> 4,
                                packet.getPos().getZ() >> 4
                        )
                );
            }
        }
    }

    @Inject(
            method = "handleChunkBlocksUpdate",
            at = @At("RETURN")
    )
    private void postHandleMultiBlockChange(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        List<Pair<BlockPos, BlockState>> changes = new ArrayList<>();
        packet.runUpdates((mutPos, state) -> {
            changes.add(new Pair<>(mutPos.immutable(), state));
        });
        if (changes.isEmpty()) {
            return;
        }
        for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (baritone != null) {
                baritone.getGameEventHandler().onBlockChange(new BlockChangeEvent(
                        new ChunkPos(changes.get(0).first()),
                        changes
                ));
            }
        }
    }

    @Inject(
            method = "handlePlayerCombatKill",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;shouldShowDeathScreen()Z"
            )
    )
    private void onPlayerDeath(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone != null) {
                ibaritone.getGameEventHandler().onPlayerDeath();
            }
        }
    }*/
}
