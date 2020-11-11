#!/bin/bash
set -o errexit -o nounset -o pipefail

PATH_TO_SCRIPT=${PWD##*/}
if [[ $PATH_TO_SCRIPT != "hooks" ]]; then
  echo "setup-hooks: running \"cd $(dirname ${BASH_SOURCE[0]})\" to run this script"
  cd $(dirname ${BASH_SOURCE[0]})
  [[ $? -eq  0 ]] || (echo "setup-hooks: couldn't find hooks directory" && exit 1)
fi

cd ../.git/hooks/
echo "setup-hooks: we are operating in $PWD"
rm -f pre-push
ln -s ../../hooks/pre-push.sh pre-push
chmod u+x pre-push

echo "setup-hooks: pre-push hook successfully setup in $PWD"
