package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.RenderSpec;

import java.io.File;
import java.util.Map;

import git.artdeell.mojoexec.MojoExec;

/**
 * Decorator {@link RenderSpec} for MobileGL-Dev/SimpleFPEWrapper (SFPEW) - a standalone
 * fixed-function-pipeline emulation layer that speaks legacy desktop GL to Minecraft and
 * forwards every call to whatever real EGL/GL backend it's told to wrap (see its
 * {@code SFPEW_EGL} environment variable, read in SimpleFPEWrapper/init.cpp).
 * <p>
 * Unlike AngelAuraMC/Amethyst-Android and TeamPojavLauncher/PojavLauncher, which only ever
 * point SFPEW at MobileGlues (and do so unconditionally, tied to that one renderer choice),
 * this class wraps *any* {@link RenderSpec} - GL4ES, LTW, Mesa, Zink, Freedreno, MobileGlues,
 * external Mesa/Zink plugins, whatever the user has picked in the instance editor. It:
 * <ol>
 *     <li>lets the wrapped spec run its own {@link #setupEnvironment(Context, Map)} untouched,
 *     so all of its renderer-specific environment (MESA_* overrides, MG_DIR_PATH, ANGLE/
 *     GLESProvider wiring, ...) is still applied exactly as if it were loaded directly;</li>
 *     <li>then points SFPEW's own backend at whatever library the wrapped spec would have
 *     loaded itself, via {@code SFPEW_EGL};</li>
 *     <li>and finally makes {@link net.kdt.pojavlaunch.game.renderer.GameRenderer} load
 *     {@code libSimpleFPEWrapper.so} instead of the wrapped renderer's own library, since
 *     SFPEW is what Minecraft/LWJGL will actually be talking to.</li>
 * </ol>
 * Construct with the renderer the user picked (from {@link net.kdt.pojavlaunch.game.renderer.GameRenderer#getCurrentRenderer()})
 * and swap it in with {@link net.kdt.pojavlaunch.game.renderer.GameRenderer#setCurrentRenderer(RenderSpec)}
 * when the per-instance "Use SimpleFPEWrapper" toggle is on - see InstanceEditorFragment /
 * Instance#useSFPEW and GameActivity.
 */
public class SFPEWRenderSpec implements RenderSpec {
    private static final String TAG = "SFPEWRenderSpec";
    /** The library SFPEW itself ships as, built from the SimpleFPEWrapper submodule. */
    private static final String LIBRARY = "libSimpleFPEWrapper.so";

    private final RenderSpec wrapped;

    public SFPEWRenderSpec(RenderSpec wrapped) {
        if (wrapped instanceof SFPEWRenderSpec) {
            // Don't wrap SFPEW in itself if this ever gets called twice by accident.
            this.wrapped = ((SFPEWRenderSpec) wrapped).wrapped;
        } else {
            this.wrapped = wrapped;
        }
    }

    /**
     * @return the renderer SFPEW is currently forwarding calls to
     */
    public RenderSpec getWrapped() {
        return wrapped;
    }

    /**
     * @return whether libSimpleFPEWrapper.so was built into this APK and can be used
     */
    public static boolean isAvailable() {
        return new File(Tools.NATIVE_LIB_DIR, LIBRARY).exists();
    }

    @Override
    public boolean compatibleDevice(Context context) {
        // if you are here reading this, good luck I dont even fucking know how tf this wokks
        // build is just failing HELP
        // i need to sleep so dont even try looking at this code, i dont even know what it does
        return Build.VERSION.SDK_INT >= 26 && isAvailable() && wrapped.compatibleDevice(context);
    }

    @Override
    public String name() {
        return wrapped.name() + " (SFPEW)";
    }

    @Override
    public int displayName() {
        // SFPEW is a toggle layered on top of the renderer spinner, not a spinner entry
        // of its own (see RendererCache) - this is only ever used if something asks for
        // it directly, so just fall back to whatever it's wrapping.
        return wrapped.displayName();
    }

    @Override
    public String tag() {
        return wrapped.tag();
    }

    @Override
    public String library() {
        return LIBRARY;
    }

    @Override
    public String librarySearchPath() {
        // Keep forwarding the wrapped renderer's extra search path (e.g. an external
        // Mesa/Zink plugin's directory) so MojoExec can still find *its* library when
        // SFPEW dlopen()s it as its backend.
        return wrapped.librarySearchPath();
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        // Let the wrapped renderer configure itself exactly as it normally would.
        wrapped.setupEnvironment(context, envMap);

        // Work out the path SFPEW should dlopen() as its backend. If the wrapped renderer
        // lives outside the launcher's own native library directory (an external plugin,
        // see MesaRenderSpec.ExtMesaRenderSpec/LegacyZinkRenderSpec), resolve it to an
        // absolute path the same way those specs do for MojoExec - a bare filename would
        // only be found via the launcher's own native lib dir otherwise.
        String searchPath = wrapped.librarySearchPath();
        String backendLibrary = searchPath != null
                ? new File(searchPath, wrapped.library()).getAbsolutePath()
                : wrapped.library();

        envMap.put("SFPEW_EGL", backendLibrary);
        Log.i(TAG, "Wrapping " + wrapped.name() + " (" + backendLibrary + ") with SimpleFPEWrapper");
    }

    @Override
    public boolean setupRenderer() {
        // Only load SFPEW itself here - it resolves its real backend lazily and on its
        // own, purely from SFPEW_EGL (see SimpleFPEWrapper/init.cpp). We deliberately
        // don't call wrapped.setupRenderer(), since that would load the wrapped
        // renderer's own EGL library directly instead of routing it through SFPEW.
        // Args mirror MobileGluesRenderSpec, the closest precedent for a renderer that
        // implements its own self-contained EGL/GLES surface rather than going through
        // GLESProvider: nsBypass=true, useGles=false, glesVersion=0 (let SFPEW decide).
        return MojoExec.prepareEgl(library(), true, false, 0);
    }
}