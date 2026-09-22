package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import git.artdeell.mojo.R;
import git.artdeell.mojoexec.MojoExec;

/**
 * MobileGlues (MG-ES) RenderSpec. MobileGlues is a self-contained OpenGL-to-OpenGL ES
 * translation layer, shipped as the {@code libmobileglues.so} library bundled inside the
 * MobileGlues AAR (see app_pojavlauncher/libs). Unlike {@link GLESRenderSpec}, it does not
 * need a {@link net.kdt.pojavlaunch.game.renderer.extra.GLESProvider} - it implements its
 * own EGL/GLES context, so it's wired up directly against MojoExec, similarly to MesaRenderSpec.
 */
public class MobileGluesRenderSpec implements RenderSpec {
    public String library() {
        return "libmobileglues.so";
    }
    public boolean compatibleDevice(Context context) {
        return new File(Tools.NATIVE_LIB_DIR, this.library()).exists();
    }
    public String name() {
        return "MobileGlues";
    }
    public int displayName() {
        return R.string.mcl_setting_renderer_mobileglues;
    }
    public String tag() {
        return Renderers.MOBILEGLUES_RENDERER;
    }
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        try {
            LauncherPreferences.writeMGRendererSettings();
        } catch (IOException e) {
            // Don't hard-fail renderer setup over a settings file write failure -
            // MobileGlues will just fall back to its own internal defaults.
            Log.e("MobileGluesRenderSpec", "Failed to write MG-ES renderer settings", e);
        }
        envMap.put("MG_DIR_PATH", Tools.DIR_DATA + "/MobileGlues");
    }
    public boolean setupRenderer() {
        // MobileGlues is bundled in the app's own native library directory (via the AAR),
        // so, like MesaRenderSpec, it doesn't need useGles/glesVersion from MojoExec.
        return MojoExec.prepareEgl(library(), true, false, 0);
    }
}
