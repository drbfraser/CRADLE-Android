package com.cradleVSA.neptune.model

/**
 * A class for the user rules in the CRADLE system.
 * Descriptions come from "iCradle Background" on the Google Drive.
 */
enum class UserRole {
    /**
     * Village Health Team. They are the first point of contact for patients.
     * They do not have a medical background; however, they take the initial readings, and make
     * referrals to health facilities if additional medical attention is needed.
     */
    VHT,

    /**
     * Health Care Worker. They are incharge of assessing incoming referrals from VHTs,
     * assessing the referrals, and provide follow up instructions for the VHTs and patients
     * if needed.
     */
    HCW,

    /**
     * The administrator of the CRADLE instance. They have full access to all data on the server.
     */
    ADMIN,

    /**
     * Community Health Officer. They are in charge of a set of VHTs whose work they oversee
     */
    CHO,

    /**
     * A placeholder for unknown roles so that the app can fall back to some behavior.
     */
    UNKNOWN;

    companion object {
        /**
         * Calls [valueOf] and returns [UNKNOWN] if the role is unknown instead of throwing an
         * [IllegalArgumentException].
         */
        fun safeValueOf(string: String): UserRole = try {
            valueOf(string)
        } catch (e: IllegalArgumentException) {
            UNKNOWN
        }
    }
}
