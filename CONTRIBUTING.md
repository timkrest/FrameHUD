# Contributing

Bug reports, ideas and pull requests are all welcome.

## Building

```
./gradlew build
```

That compiles every module, runs the unit tests, applies ktlint and checks the public API against the
dumps under `api/`. It needs JDK 21 and an Android SDK. CI runs the same command, so a green local
build means a green CI.

To try a change on a device:

```
./gradlew :sample:installDebug
```

## Before opening a pull request

- Run `./gradlew build`.
- If you changed a public declaration, run `./gradlew apiDump` and commit the updated `.api` files.
  It has to be a separate invocation — Gradle refuses to write and verify the same dumps in one run.
- Keep `framehud-noop` in step with `framehud`: it mirrors the public API with empty bodies, and
  `checkApiParity` fails when the two drift apart.
- Add a line under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for anything a user would notice.

Contributions are covered by the [CLA](CLA.md); a bot asks you to sign it on your first pull request.

## Releasing

For maintainers:

1. Move the `Unreleased` entries under a `## [x.y.z] - yyyy-mm-dd` heading and add the matching link
   at the bottom of the changelog. The publish workflow refuses a tag with no changelog section.
2. Commit, then tag: `git tag -a vx.y.z -m "FrameHUD x.y.z" && git push origin vx.y.z`. The tag is the
   only source of a released version — `VERSION_NAME` in `gradle.properties` stays a snapshot.
3. The workflow builds, signs, uploads a deployment to the Central Portal and opens the GitHub release
   with the notes from the changelog.
4. Release the deployment by hand in the
   [Central Portal](https://central.sonatype.com/publishing/deployments). Nothing reaches Maven
   Central until it is released there.
5. Bump `VERSION_NAME` to the next `-SNAPSHOT`.
