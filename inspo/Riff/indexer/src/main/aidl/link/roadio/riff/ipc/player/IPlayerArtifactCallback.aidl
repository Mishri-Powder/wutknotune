package link.roadio.riff.ipc.player;

import link.roadio.riff.ipc.indexer.IndexerArtifactHandle;

oneway interface IPlayerArtifactCallback {
    void receiveReliablePacket(String session, long seq, int type, in String[] fields);
    void receiveReliableAck(String session, long seq, in String[] fields, in @nullable IndexerArtifactHandle attachment);
    void onIndexerArtifactAvailable(String artifactKind, String volumeId, long generation);
    void onIndexerStatusChanged(String status);
    void onSafListingIssueDetected(int activeRootCount, in String[] activeFolderLabels, int unverifiedRootCount, in String[] unverifiedFolderLabels);
}
