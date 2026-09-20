package net.kdt.pojavlaunch.modloaders.modpacks.models;

public class Constants {
    private Constants(){}

    /** Types of modpack apis */
    public static final int SOURCE_MODRINTH = 0x0;
    public static final int SOURCE_CURSEFORGE = 0x1;
    public static final int SOURCE_TECHNIC = 0x2;

    /** Mod loader filter values, these are also the Modrinth category names */
    public static final String LOADER_FABRIC = "fabric";
    public static final String LOADER_FORGE = "forge";
    public static final String LOADER_NEOFORGE = "neoforge";
    public static final String LOADER_QUILT = "quilt";

    /** Modrinth api, file environments */
    public static final String MODRINTH_FILE_ENV_REQUIRED = "required";
    public static final String MODRINTH_FILE_ENV_OPTIONAL = "optional";
    public static final String MODRINTH_FILE_ENV_UNSUPPORTED = "unsupported";

}
