# JNI/API contract

The constructor stays unchanged:

```text
WindowsMediaSession(MediaInfo media, String owner, int index)
```

`owner` is the raw Windows `SourceAppUserModelId`.

The DLL additionally writes these Java fields after construction:

- `sourceAppId`
- `sourceAppName`
- `sourceName`
- `sourceType`
- `serviceName`
- `repeatSupported`

Repeat values:

- `-1` unsupported / unknown
- `0` Off / None
- `1` Track
- `2` List

Native repeat API:

```text
int getCycleType()
void swapCycle()
boolean setCycleType(int mode)
```

`swapCycle()` cycles `0 -> 1 -> 2 -> 0`, skipping a mode if the player advertises repeat but rejects that particular mode. `setCycleType()` directly requests Off, Track, or List and returns whether the SMTC session accepted the change.

Lifecycle API:

```text
void cleanup()
```

`cleanup()` closes the bridge for the remainder of the process. No SMTC manager/session is stored globally, so there is no long-lived native media object to release at JVM exit.
