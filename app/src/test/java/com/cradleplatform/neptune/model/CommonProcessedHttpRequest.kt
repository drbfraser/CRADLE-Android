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
            01-CRADLE-008581-POST-003-Rm9yIHNoYW1lIGRlbnkgdGhhdCB0aG91IGJlYXLigJlzdCBsb3ZlIHRvIGF
            ueSwKV2hvIGZvciB0aHkgc2VsZiBhcnQgc28gdW5wcm92aWRlbnQuCkdyYW50LCBpZiB0aG91IHdpbHQsIHRo
            b3UgYXJ0IGJlbG92ZWQgb2YgbWFueSwKQnV0IHRoYXQgdGhvdSBub25lIGxvduKAmXN0IGlzIG1vc3QgZXZpZ
            GVudDoKRm9yIHRob3UgYXJ0IHNvIHBvc3Nlc3NlZCB3aXRoIG11
        """.trimIndent().replace("\n", ""),
        """
            001-cmRlcm91cyBoYXRlLApUaGF0IOKAmGdhaW5zdCB0aHkgc2VsZiB0aG91IHN0aWNr4oCZc3Qgbm90IHRv
            IGNvbnNwaXJlLApTZWVraW5nIHRoYXQgYmVhdXRlb3VzIHJvb2YgdG8gcnVpbmF0ZQpXaGljaCB0byByZXBha
            XIgc2hvdWxkIGJlIHRoeSBjaGllZiBkZXNpcmUuCk8hIGNoYW5nZSB0aHkgdGhvdWdodCwgdGhhdCBJIG1heS
            BjaGFuZ2UgbXkgbWluZDoKU2hhbGwgaGF0ZSBiZSBmYWlyZXIgbG
        """.trimIndent().replace("\n", ""),
        """
            002-9kZ2VkIHRoYW4gZ2VudGxlIGxvdmU/CkJlLCBhcyB0aHkgcHJlc2VuY2UgaXMsIGdyYWNpb3VzIGFuZCB
            raW5kLApPciB0byB0aHlzZWxmIGF0IGxlYXN0IGtpbmQtaGVhcnRlZCBwcm92ZToKTWFrZSB0aGVlIGFub3Ro
            ZXIgc2VsZiBmb3IgbG92ZSBvZiBtZSwKVGhhdCBiZWF1dHkgc3RpbGwgbWF5IGxpdmUgaW4gdGhpbmUgb3Igd
            GhlZS4g
        """.trimIndent().replace("\n", ""),
    )


}