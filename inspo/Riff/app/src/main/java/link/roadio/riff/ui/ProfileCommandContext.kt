package link.roadio.riff.ui

/** Captured at acceptance; later profile/store switches fence presentation, not write ownership. */
internal data class ProfileCommandContext<Store : Any>(
    val store: Store,
    val profileId: Int,
    val epoch: Long,
) {
    fun isCurrent(
        store: Store?,
        profileId: Int,
        epoch: Long,
    ): Boolean = this.store === store && this.profileId == profileId && this.epoch == epoch
}
