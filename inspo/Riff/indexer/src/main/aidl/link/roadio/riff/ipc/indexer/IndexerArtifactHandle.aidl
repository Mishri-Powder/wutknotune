package link.roadio.riff.ipc.indexer;

import android.os.ParcelFileDescriptor;

parcelable IndexerArtifactHandle {
    ParcelFileDescriptor descriptor;
    String artifactKind;
    String volumeId;
    long generation;
    long offset;
    long length;
    int flatBufferSchemaVersion;
    int semanticVersion;
    long xxh3;
    @nullable String cohortId;
}
