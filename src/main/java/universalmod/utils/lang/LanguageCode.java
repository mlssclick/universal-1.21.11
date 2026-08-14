package universalmod.utils.lang;

public enum LanguageCode {
    EN_US("English", "en_us"),
    RU_RU("Russian", "ru_ru"),
    UK_UA("Ukrainian", "uk_ua");

    private final String modeName;
    private final String fileName;

    LanguageCode(String modeName, String fileName) {
        this.modeName = modeName;
        this.fileName = fileName;
    }

    public String modeName() {
        return modeName;
    }

    public String fileName() {
        return fileName;
    }

    public static LanguageCode fromMode(String mode) {
        if (mode != null) {
            for (LanguageCode code : values()) {
                if (code.modeName.equalsIgnoreCase(mode)) {
                    return code;
                }
            }
        }
        return RU_RU;
    }
}
