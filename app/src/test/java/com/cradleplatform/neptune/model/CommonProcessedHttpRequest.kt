package com.cradleplatform.neptune.model

import okhttp3.internal.immutableListOf

// Test data for compressed/encrypted/encoded Http Request Data
object CommonProcessedHttpRequest {

    val testData1 = """
        Rm9yIHNoYW1lIGRlbnkgdGhhdCB0aG91IGJlYXLigJlzdCBsb3ZlIHRvIGFueSwKV2hvIGZvciB0aHkgc2VsZiBhcn
        Qgc28gdW5wcm92aWRlbnQuCkdyYW50LCBpZiB0aG91IHdpbHQsIHRob3UgYXJ0IGJlbG92ZWQgb2YgbWFueSwKQnV0
        IHRoYXQgdGhvdSBub25lIGxvduKAmXN0IGlzIG1vc3QgZXZpZGVudDoKRm9yIHRob3UgYXJ0IHNvIHBvc3Nlc3NlZC
        B3aXRoIG11cmRlcm91cyBoYXRlLApUaGF0IOKAmGdhaW5zdCB0aHkgc2VsZiB0aG91IHN0aWNr4oCZc3Qgbm90IHRv
        IGNvbnNwaXJlLApTZWVraW5nIHRoYXQgYmVhdXRlb3VzIHJvb2YgdG8gcnVpbmF0ZQpXaGljaCB0byByZXBhaXIgc2
        hvdWxkIGJlIHRoeSBjaGllZiBkZXNpcmUuCk8hIGNoYW5nZSB0aHkgdGhvdWdodCwgdGhhdCBJIG1heSBjaGFuZ2Ug
        bXkgbWluZDoKU2hhbGwgaGF0ZSBiZSBmYWlyZXIgbG9kZ2VkIHRoYW4gZ2VudGxlIGxvdmU/CkJlLCBhcyB0aHkgcH
        Jlc2VuY2UgaXMsIGdyYWNpb3VzIGFuZCBraW5kLApPciB0byB0aHlzZWxmIGF0IGxlYXN0IGtpbmQtaGVhcnRlZCBw
        cm92ZToKTWFrZSB0aGVlIGFub3RoZXIgc2VsZiBmb3IgbG92ZSBvZiBtZSwKVGhhdCBiZWF1dHkgc3RpbGwgbWF5IG
        xpdmUgaW4gdGhpbmUgb3IgdGhlZS4g
    """.trimIndent().replace("\n", "")

    val formattedTestData1 = immutableListOf<String>(
        """
            01-CRADLE-008581-006-Rm9yIHNoYW1lIGRlbnkgdGhhdCB0aG91IGJlYXLigJlzdCBsb3ZlIHRvIGFueSwKV2hvIGZvciB0aHkgc2VsZiBhcnQgc28gdW5wcm92aWRlbnQuCkdyYW50LCBpZiB0aG91
        """.trimIndent().replace("\n", ""),
        """
            001-IHdpbHQsIHRob3UgYXJ0IGJlbG92ZWQgb2YgbWFueSwKQnV0IHRoYXQgdGhvdSBub25lIGxvduKAmXN0IGlzIG1vc3QgZXZpZGVudDoKRm9yIHRob3UgYXJ0IHNvIHBvc3Nlc3NlZCB3aXRoIG11c
        """.trimIndent().replace("\n", ""),
        """
            002-mRlcm91cyBoYXRlLApUaGF0IOKAmGdhaW5zdCB0aHkgc2VsZiB0aG91IHN0aWNr4oCZc3Qgbm90IHRvIGNvbnNwaXJlLApTZWVraW5nIHRoYXQgYmVhdXRlb3VzIHJvb2YgdG8gcnVpbmF0ZQpXaG
        """.trimIndent().replace("\n", ""),
        """
            003-ljaCB0byByZXBhaXIgc2hvdWxkIGJlIHRoeSBjaGllZiBkZXNpcmUuCk8hIGNoYW5nZSB0aHkgdGhvdWdodCwgdGhhdCBJIG1heSBjaGFuZ2UgbXkgbWluZDoKU2hhbGwgaGF0ZSBiZSBmYWlyZXI
        """.trimIndent().replace("\n", ""),
        """
            004-gbG9kZ2VkIHRoYW4gZ2VudGxlIGxvdmU/CkJlLCBhcyB0aHkgcHJlc2VuY2UgaXMsIGdyYWNpb3VzIGFuZCBraW5kLApPciB0byB0aHlzZWxmIGF0IGxlYXN0IGtpbmQtaGVhcnRlZCBwcm92ZToK
        """.trimIndent().replace("\n", ""),
        """
            005-TWFrZSB0aGVlIGFub3RoZXIgc2VsZiBmb3IgbG92ZSBvZiBtZSwKVGhhdCBiZWF1dHkgc3RpbGwgbWF5IGxpdmUgaW4gdGhpbmUgb3IgdGhlZS4g
        """.trimIndent().replace("\n", ""),
    )


}