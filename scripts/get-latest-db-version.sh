#!/bin/bash
set -o errexit -o nounset -o pipefail

echo -n $(grep -R "const val CURRENT_DATABASE_VERSION" app/src/ | cut -d "=" -f 2 | xargs echo -n)
