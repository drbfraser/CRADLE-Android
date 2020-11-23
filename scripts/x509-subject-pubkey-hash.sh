#!/bin/bash
# Adapted from https://appmattus.medium.com/android-security-ssl-pinning-1db8acb6621e
#
# General overview
#
# SubjectPublicKeyInfo (SPKI) pinning is done by providing a set of certificates by hashes of their
# public keys (SubjectPublicKeyInfo of the X.509 certificate). Read
# https://developer.android.com/training/articles/security-config#CertificatePinning for an
# overview.
#
# Here's an overview of the process used in this script:
#
# Get the X.509 certificates from the server. Here, we use `cradleplatform.com`:
#
#     openssl s_client -servername cradleplatform.com -host cradleplatform.com -port 443 -showcerts < /dev/null
#
# Grab the certificate for the public key you want to pin against. Suppose you want to pin against the
# one with subject `cradleplatform.com`; we can identify it by the certificate that has a header of
# `s:CN = cradleplatform.com`:
#
#     0 s:CN = cradleplatform.com
#       i:C = US, O = Let's Encrypt, CN = Let's Encrypt Authority X3
#    -----BEGIN CERTIFICATE-----
#    MIIEkjCCA3qgAwIBAgISA9mvqxEAGGd7pohHI/tYBXVAMA0GCSqGSIb3DQEBCwUA
#    < rest of the contents omitted >
#    N/V9dwDa5xp/x7DSNEdk5VGzCQ5Ygw==
#    -----END CERTIFICATE-----
#
# Save the entire block delimited by `-----BEGIN CERTIFICATE-----` and `-----END CERTIFICATE-----`,
# including the delimiters. We assume that it's saved into a file called `cradleplatform.pem`. It
# should look like this:
#
#    $ cat cradleplatform.pem
#    -----BEGIN CERTIFICATE-----
#    MIIEkjCCA3qgAwIBAgISA9mvqxEAGGd7pohHI/tYBXVAMA0GCSqGSIb3DQEBCwUA
#    < rest of the contents omitted >
#    N/V9dwDa5xp/x7DSNEdk5VGzCQ5Ygw==
#    -----END CERTIFICATE-----
#
# To generate the hash of the public key, we convert the public key format from PEM to DER, generate
# a SHA-256 digest, and then encode as base64.
#
# To get the DER, we need to know the public key type. Run the following:
#
#    openssl x509 -in cradleplatform.pem -text | grep "Public Key Algorithm"
#
# * If it says `id-ecPublicKey`, we use `openssl ec` to convert to DER. The following generates the
#   SHA-256 hash to use as the pin:
#
#       openssl x509 -in cradleplatform.pem -pubkey -noout \
#           | openssl ec -pubin -outform DER 2>/dev/null | openssl dgst -sha256 -binary \
#           | openssl enc -base64
#
# * If it says `rsaEncryption`, we use `openssl rsa` to convert to DER. The following generates the
#   SHA-256 hash to use as the pin:
#
#       openssl x509 -in cradleplatform.pem -pubkey -noout \
#           | openssl rsa -pubin -outform DER 2>/dev/null | openssl dgst -sha256 -binary \
#           | openssl enc -base64
#

set -o errexit -o nounset -o pipefail

if [[ $# -ne 1 ]]; then
    cat <<EOF >&2
usage: $0 hostname

Gets a SHA-256 hash of the public keys (SubjectPublicKeyInfo) for the
X.509 TLS certificates in the certificate chain for a given hostname.

Note: openssl is used to obtain the certificates. The root certificate
isn't included here, as openssl doesn't show it. The certificates in PEM
format can also be obtained by a web browser. Google Chrome, for
example, has an Export button in the Certificate Viewer in the Details
tab. The root certificate in PEM format can be obtained there.

See https://developer.android.com/training/articles/security-config#CertificatePinning
for an overview.
EOF
    exit 1
fi

certs=$(openssl s_client -servername "$1" -connect "$1:443" -showcerts </dev/null 2>/dev/null \
        | sed -n '/Certificate chain/,/Server certificate/p')

rest=$certs
# Output SHA-256 hashes encoded in base64 for all the certs in the certificate chain returned by
# openssl.
while [[ "$rest" =~ '-----BEGIN CERTIFICATE-----' ]]; do
    # Extract the current certificate in PEM format.
    current_cert_pem_format="${rest%%-----END CERTIFICATE-----*}-----END CERTIFICATE-----"
    # Remove this from the certificate chain for the next loop
    rest=${rest#*-----END CERTIFICATE-----}

    # Output subject info
    echo "$current_cert_pem_format" | grep 's:' | sed 's/.*s:\(.*\)/\1/'

    # Determine the public key type
    type=$(echo "$current_cert_pem_format" | openssl x509 -text | grep "Public Key Algorithm" |
            sed "s/^.*Public Key Algorithm\: \(.*\)$/\1/")
    echo "public key type: $type"

    pub_key=$(echo "$current_cert_pem_format" | openssl x509 -pubkey -noout)
    # Use the right command for PEM to DER conversion based on the public key type.
    if [[ "$type" == "rsaEncryption" ]]; then
        echo "$pub_key" | openssl rsa -pubin -outform der 2>/dev/null |
                openssl dgst -sha256 -binary | openssl enc -base64
    elif [[ "$type" == "id-ecPublicKey" ]]; then
        echo "$pub_key" | openssl ec -pubin -outform der 2>/dev/null |
                openssl dgst -sha256 -binary | openssl enc -base64
    else
        echo "unexpected public key type"
    fi
done
