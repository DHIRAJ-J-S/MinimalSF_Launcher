#!/usr/bin/env bash
# Finds the release commit in a push, if there is one, and writes its details for the workflow.
# A release commit's first line is "v1.3" or "v1.3: Title"; everything else is an ordinary commit.
#
# Usage: find-release.sh <before-sha> <after-sha> <notes-file>
# Prints version=, tag=, title= and sha= lines (nothing when the push has no release commit).
set -euo pipefail

before=$1
after=$2
notes_file=$3

# A new branch or a force push has no usable "before": only the pushed commit counts
if git cat-file -e "${before}^{commit}" 2>/dev/null && git merge-base --is-ancestor "$before" "$after"; then
    commits=$(git rev-list "$before..$after")
else
    commits=$after
fi

subject_regex='^v([0-9]+(\.[0-9]+){1,2})([[:space:]]*(:|[[:space:]]-)[[:space:]]*(.*))?$'

# rev-list is newest first, so the newest release commit wins
for sha in $commits; do
    subject=$(git log -1 --format=%s "$sha")
    [[ $subject =~ $subject_regex ]] || continue

    version=${BASH_REMATCH[1]}
    name=${BASH_REMATCH[5]:-}
    title="v$version${name:+ - $name}"

    # The notes are the commit body, without trailers like Co-Authored-By
    git log -1 --format=%b "$sha" \
        | grep -viE '^(co-authored-by|signed-off-by):' \
        | sed -e :a -e '/^[[:space:]]*$/{$d;N;ba' -e '}' \
        | sed -e '/./,$!d' > "$notes_file" || true
    [[ -s $notes_file ]] || echo "Release v$version" > "$notes_file"

    echo "version=$version"
    echo "tag=v$version"
    echo "title=$title"
    echo "sha=$sha"
    exit 0
done
