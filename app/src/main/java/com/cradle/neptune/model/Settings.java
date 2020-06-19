package com.cradle.neptune.model;

import android.content.SharedPreferences;

import javax.inject.Inject;

/**
 * Store app-wide settings.
 * To add a new setting:
 * - create key constant
 * - create field
 * - create accessor
 * - add to res/xml/preferences.xml
 */
public class Settings {

    // constants
    public static final int MIN_VHT_NAME_LENGTH = 2;
    // shared preference strings
    // (hard-coded strings found in res\xml\preferences.xml)
    public static final String PREF_KEY_VHT_NAME = "setting_vht_name";
    public static final String PREF_KEY_REGION = "setting_region";
    public static final String PREF_KEY_NUM_HEALTH_CENTRES = "setting_num_health_centres";
    public static final String PREF_KEY_HEALTH_CENTRE_NAME_ = "setting_health_centre_name_";
    public static final String PREF_KEY_HEALTH_CENTRE_CELL_ = "setting_health_centre_cell_";
    public static final String PREF_KEY_HEALTH_CENTRE_LAST_IDX = "setting_health_centre_last_idx";
    public static final String PREF_KEY_SERVER_URL = "settings_upload_server";
    public static final String PREF_KEY_SERVER_USERNAME = "settings_upload_username";
    public static final String PREF_KEY_SERVER_PASSWORD = "settings_upload_password";
    public static final String PREF_KEY_RSAPUBKEY = "settings_upload_rsapubkey";
    public static final String PREF_KEY_UPLOAD_IMAGES = "settings_upload_images";
    public static final String PREF_KEY_OCR_ENABLED = "setting_ocr_enabled";
    public static final String PREF_KEY_OCR_DEBUG_ENABLED = "setting_ocr_debug_enabled";
//    public static String DEFAULT_SERVER_URL = "https://cmpt373.csil.sfu.ca:8048/api";
    //testing with local server
    public static String DEFAULT_SERVER_URL = "https://cmpt373-lockdown.cs.surrey.sfu.ca/api";

    public static String DEFAULT_SERVER_USERNAME = "user";
    public static String DEFAULT_SERVER_USERPASSWORD = "just4testing";
    public static String authServerUrl = DEFAULT_SERVER_URL + "/user/auth";
    public static String patientGetAllInfoByUserIdUrl = DEFAULT_SERVER_URL + "/patient/allinfo";
    public static String healthFacilityUrl = DEFAULT_SERVER_URL + "/health_facility";
    // Temporary Upload Defaults:
    // todo: remove temporary upload defaults once 2D bar-code reading in place for settings.
    private static String LINEFEED = "\r\n";
    public static String DEFAULT_SERVER_RSA = "-----BEGIN PUBLIC KEY-----                                      " + LINEFEED +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8/2NBuEDyLiClu+wkHCP" + LINEFEED +
            "BVGxgwDj8PDLBah7Ge9bYgmM7Jcmc5F15VVQG/RWSFnxD/+/rTGYRju6JYxtnw6G" + LINEFEED +
            "JT0ZyzmoRQ4pArIfAvqwUYcQ08fDyA6mhqbny9FAOwhm643gg+0bnqUw7gPd6z58" + LINEFEED +
            "gW70abOJUfy81EZ2Q7CmuSh5WwMQKu6/q9umvn5iGC6rcxcrg/kqs6cW6E3tMZU8" + LINEFEED +
            "XzVRYK0ctV1m45LitL286h2cflTmIkyeGRPM7quBYuQ2PAN2QMdMaZGXyHJz9AqX" + LINEFEED +
            "w1wKQF+cc9G+pKNNWJDerGTKtmARge2N/3IpT6yQNhVpf6iezISnWrDgcj1jvemR" + LINEFEED +
            "3QIDAQAB                                                        " + LINEFEED +
            "-----END PUBLIC KEY-----                                        " + LINEFEED;
    // stored values managed by settings screen
    private String vhtName = "";
    private WorkLocation usualWorkLocation = WorkLocation.WORK_LOCATION_IN_COMMUNITY;
    private String region;
    private String pin;
    private int holdScreenAwakeForNewReading = 0;
    private boolean communityHealthOfficerGetsReferrals = false;
    private String communityHealthOfficerPhoneNumber;
    private String readingServerUrl = DEFAULT_SERVER_URL + "/patient/reading";
    private String referralsServerUrl = DEFAULT_SERVER_URL + "/referral";
    private String referralSummeriesServerUrl = DEFAULT_SERVER_URL + "/mobile/summarized/follow_up";
    private String serverUserName;
    private String serverPassword;
    private String rsaPubKey;
    private boolean shouldUploadImages;
    private Boolean ocrEnabled = false;
    private Boolean ocrDebugEnabled = false;

    // stored values managed by us
    private int lastHealthCentreSelectionIdx = 0;
    // data to operate
    private SharedPreferences sharedPref;

    // constructor
    @Inject
    public Settings(SharedPreferences sharedPreferences) {
        this.sharedPref = sharedPreferences;

        // load values
        loadFromSharedPrefs();
    }

    public void saveLastTimeFollowUpDownloaded(String time) {
        sharedPref.edit().putString("lastSavedTime", time).apply();
    }

    public String getLastTimeFollowUpDownloaded() {
        return sharedPref.getString("lastSavedTime", "No previous time");
    }

    public void loadFromSharedPrefs() {

        // Defaults set in preferences.xml
        // (except for things like health centres list, which is not directly in preferences)
        // vht name & region
        vhtName = sharedPref.getString(PREF_KEY_VHT_NAME, "no name available");
        region = sharedPref.getString(PREF_KEY_REGION, "no region available");

        lastHealthCentreSelectionIdx = sharedPref.getInt(PREF_KEY_HEALTH_CENTRE_LAST_IDX, 0);

        // upload
        // TODO: defaults should be removed from here
        //readingServerUrl = sharedPref.getString(PREF_KEY_SERVER_URL, DEFAULT_SERVER_URL);
        serverUserName = sharedPref.getString(PREF_KEY_SERVER_USERNAME, DEFAULT_SERVER_USERNAME);
        serverPassword = sharedPref.getString(PREF_KEY_SERVER_PASSWORD, DEFAULT_SERVER_USERPASSWORD);
        rsaPubKey = sharedPref.getString(PREF_KEY_RSAPUBKEY, DEFAULT_SERVER_RSA);
        shouldUploadImages = sharedPref.getBoolean(PREF_KEY_UPLOAD_IMAGES, true);

        // OCR
        ocrEnabled = sharedPref.getBoolean(PREF_KEY_OCR_ENABLED, true);
        ocrDebugEnabled = sharedPref.getBoolean(PREF_KEY_OCR_DEBUG_ENABLED, false);
    }

    // getters
    public String getVhtName() {
        return vhtName;
    }

    public String getRegion() {
        return region;
    }


    public void setLastHealthCentreSelectionIdx(int selectionIdx) {
        lastHealthCentreSelectionIdx = selectionIdx;
        sharedPref.edit().putInt(PREF_KEY_HEALTH_CENTRE_LAST_IDX, selectionIdx).apply();
    }

    public boolean getOcrDebugEnabled() {
        return ocrDebugEnabled;
    }

    public boolean getOcrEnabled() {
        return ocrEnabled;
    }

    // upload
    public String getReadingServerUrl() {
        return readingServerUrl;
    }

    public String getServerUserName() {
        if (serverUserName == null) {
            return "";
        } else {
            return serverUserName;
        }
    }

    public String getServerPassword() {
        if (serverPassword == null) {
            return "";
        } else {
            return serverPassword;
        }
    }

    public String getAuthServerUrl() {
        return authServerUrl;
    }

    public String getReferralsServerUrl() {
        return referralsServerUrl;
    }

    public String getRsaPubKey() {
        return rsaPubKey;
    }

    public boolean shouldUploadImages() {
        return shouldUploadImages;
    }

    //
    public boolean isAllRequiredDataOK() {
        boolean ok = true;
        if (vhtName == null || vhtName.length() <= MIN_VHT_NAME_LENGTH) {
            ok = false;
        }
        return ok;
    }

    public String getReferralSummeriesServerUrl() {
        return referralSummeriesServerUrl;
    }

    // types
    public enum WorkLocation {
        WORK_LOCATION_IN_COMMUNITY,
        WORK_LOCATION_IN_HEALTH_CENTRE
    }

    public static class NamedPair {
        public String name;
        public String value;

        public NamedPair(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
