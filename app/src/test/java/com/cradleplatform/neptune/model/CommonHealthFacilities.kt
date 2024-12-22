package com.cradleplatform.neptune.model

object CommonHealthFacilities {
    const val HEALTH_FACILITY_JSON =
        """
[
    {
        "location": "Sample Location",
        "type": "HOSPITAL",
        "about": "Sample health centre",
        "phoneNumber": "555-555-55555",
        "name": "H0000"
    },
    {
        "location": "District 1",
        "type": "HCF_2",
        "about": "Has minimal resources",
        "phoneNumber": "+256-413-837484",
        "name": "H1233"
    },
    {
        "location": "District 2",
        "type": "HCF_3",
        "about": "Can do full checkup",
        "phoneNumber": "+256-223-927484",
        "name": "H2555"
    },
    {
        "location": "District 3",
        "type": "HCF_4",
        "about": "Has specialized equipment",
        "phoneNumber": "+256-245-748573",
        "name": "H3445"
    },
    {
        "location": "District 4",
        "type": "HOSPITAL",
        "about": "Urgent requests only",
        "phoneNumber": "+256-847-0947584",
        "name": "H5123"
    }
]
    """
}