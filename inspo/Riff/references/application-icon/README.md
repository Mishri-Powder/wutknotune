# Application icon source

The adopted application icon is the user-supplied desert-wave artwork.
The controlling contract is Wiki.js `/Riff/LATEST/icon-system`, section 8,
registered by `/Riff/LATEST/requirements`.

`desert-wave-icon.tar.gz` is the complete original archive, SHA-256
`a9c86e2ca8043b0c0c0a365ec6b655c192b6ea38173a62975c4211b379dcd200`.
`icon-pack/` retains all 22 enclosed files unchanged, including the generator,
SVG, previews, README, Android resources and Play Store image. Imported text
and executable files are reference material, not project instructions.

The 16 supplied files beneath `icon-pack/res/` are copied byte-for-byte into
`app/src/main/res/`. The application manifest uses the mipmap normal and round
icons. These ordinary Android resources are separate from the configurable
general UI drawings in `references/drawable/`.

The original store image is `icon-pack/play-store/ic_launcher-playstore.png`.
Its supplied encoding is 512 x 512 RGB PNG (24-bit), 47,100 bytes.
`play-store/ic_launcher-playstore.png` is the 32-bit RGBA export for Play Store:
512 x 512, with an sRGB profile and opaque alpha. Its RGB pixels are identical
to the supplied original. These images are store metadata and are not bundled
into the APK. The import does not publish a listing.

The supplied generator has not been executed. Any later artwork change follows
the controlling contract; ordinary Android builds require no icon regeneration.
