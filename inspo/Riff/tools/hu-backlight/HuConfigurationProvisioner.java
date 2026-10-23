package link.roadio.riff.huprovision;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Operator-only, same-signature provisioning; never packaged in the product APK. */
public class HuConfigurationProvisioner extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                throw new IllegalStateException("Strict AAOS cannot use this local workaround");
            }
            if (!Settings.System.canWrite(context)) throw new IllegalStateException("WRITE_SETTINGS special access missing");
            Class<?> storageClass = context.getClassLoader().loadClass("link.roadio.riff.storage.root.ApplicationDataStorage");
            Object storage = storageClass.getConstructor(Context.class,
                    context.getClassLoader().loadClass("link.roadio.riff.storage.publish.DirectorySync"),
                    context.getClassLoader().loadClass("kotlin.jvm.functions.Function0"), int.class,
                    context.getClassLoader().loadClass("kotlin.jvm.internal.DefaultConstructorMarker"))
                    .newInstance(context, null, null, 6, null);
            Object selection = storageClass.getMethod("selection").invoke(storage);
            if (!selection.getClass().getMethod("getLocation").invoke(selection).toString().equals("HU")) {
                throw new IllegalStateException("Select Internal storage before HU provisioning");
            }
            File root = (File) storageClass.getMethod("activeRoot").invoke(storage);
            Class<?> configClass = context.getClassLoader().loadClass("link.roadio.riff.config.ProjectConfiguration");
            Object configOwner = configClass.getField("INSTANCE").get(null);
            String filename = (String) configClass.getField("OVERRIDE_FILE_NAME").get(null);
            AtomicFile file = new AtomicFile(new File(root, "player/" + filename));
            JSONObject user = (file.getBaseFile().exists() || new File(file.getBaseFile().getPath() + ".bak").exists())
                    ? readUser(file)
                    : new JSONObject();
            // Validate the old file as well; never silently repair malformed or unknown user settings.
            configClass.getMethod("parse", String.class).invoke(configOwner, user.toString());
            JSONObject behavior = child(child(child(user, "skins"), "default"), "behavior");
            behavior.put("systemBrightnessWorkaroundEnabled", true);
            behavior.put("dimBacklightPercent", new JSONObject().put("day", 15).put("night", 1));
            Object expected = configClass.getMethod("parse", String.class).invoke(configOwner, user.toString());
            FileOutputStream output = file.startWrite();
            try {
                output.write((user.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
                file.finishWrite(output);
                Class<?> sync = context.getClassLoader().loadClass("link.roadio.riff.storage.publish.AndroidDirectorySync");
                sync.getMethod("sync", java.nio.file.Path.class).invoke(sync.getField("INSTANCE").get(null), file.getBaseFile().getParentFile().toPath());
            } catch (Throwable failure) { file.failWrite(output); throw failure; }
            Class<?> loader = context.getClassLoader().loadClass("link.roadio.riff.shared.config.AndroidProjectConfiguration");
            Object loaded = loader.getMethod("load", Context.class).invoke(loader.getField("INSTANCE").get(null), context);
            if (loaded.getClass().getMethod("getProblem").invoke(loaded) != null) throw new IllegalStateException("Startup configuration rejected");
            Object actual = loaded.getClass().getMethod("getSnapshot").invoke(loaded);
            JSONObject report = new JSONObject();
            for (String path : new String[]{"behavior.systemBrightnessWorkaroundEnabled", "behavior.idleTimeoutSeconds",
                    "indexer.pipelineDepth", "player.bookmarkMinSpacingSeconds", "colors.screenBackground"}) {
                Object value = actual.getClass().getMethod("value", String.class, boolean.class, boolean.class).invoke(actual, path, true, false);
                Object expectedValue = expected.getClass().getMethod("value", String.class, boolean.class, boolean.class).invoke(expected, path, true, false);
                if (!value.equals(expectedValue)) throw new IllegalStateException("Deep merge differs: " + path);
                report.put(path, value);
            }
            for (boolean night : new boolean[]{false, true}) {
                for (boolean wide : new boolean[]{false, true}) {
                    Object value = actual.getClass().getMethod("value", String.class, boolean.class, boolean.class)
                            .invoke(actual, "behavior.dimBacklightPercent", wide, night);
                    if (((Number) value).doubleValue() != (night ? 1.0 : 15.0)) {
                        throw new IllegalStateException("Dim percentage differs from HU provisioning");
                    }
                    report.put(night ? "dim_backlight_percent_night" : "dim_backlight_percent_day", value);
                }
            }
            report.put("internal_user_file", file.getBaseFile().getAbsolutePath());
            report.put("user_file", user);
            report.put("write_settings_allowed", Settings.System.canWrite(context));
            report.put("persisted_media_grants", context.getContentResolver().getPersistedUriPermissions().size());
            result.putString("hu_configuration", report.toString());
            finish(0, result);
        } catch (Throwable failure) {
            result.putString("provision_failure", failure.toString());
            finish(1, result);
        }
    }
    private static JSONObject readUser(AtomicFile file) throws Exception {
        try (FileInputStream input = file.openRead()) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    private static JSONObject child(JSONObject parent, String key) throws Exception {
        if (!parent.has(key)) parent.put(key, new JSONObject());
        return parent.getJSONObject(key);
    }
}
