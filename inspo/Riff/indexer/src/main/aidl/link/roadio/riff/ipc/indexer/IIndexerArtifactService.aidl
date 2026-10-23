package link.roadio.riff.ipc.indexer;

import link.roadio.riff.ipc.indexer.IndexerArtifactHandle;
import link.roadio.riff.ipc.player.IPlayerArtifactCallback;

interface IIndexerArtifactService {
    String openReliableChannel(IPlayerArtifactCallback callback, String playerEpoch);
    oneway void receiveReliablePacket(String session, long seq, int type, in String[] fields);
    oneway void receiveReliableAck(String session, long seq, in String[] fields);
    @nullable IndexerArtifactHandle getCurrentArtifact(String artifactKind, String volumeId);
    void registerPlayerArtifactCallback(IPlayerArtifactCallback callback);
    void unregisterPlayerArtifactCallback(IPlayerArtifactCallback callback);
    void requestLibraryRescan();
    boolean quiesceForSafGrantChange();
    boolean resumeAfterSafGrantChange(boolean requestFullPipeline);
    void requestDatabaseRebuild(int mode);
    @nullable String resolveGrantTreeUri(long rootBindingId);
    boolean quiesceForStorageMigration();
    boolean activateStorageRootAfterMigration(String location, String portableVolumeId, boolean requestFullPipeline);
}
