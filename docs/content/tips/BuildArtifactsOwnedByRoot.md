# Build artifacts are owned by root

!!! tip "tl;dr"
    If a container produces build artifacts in a mounted volume, enable `run_as_current_user`, otherwise they'll be owned by the `root`
    Unix user

On Linux, by default, the Docker daemon runs as root, and so all containers run as root. This means that when a container writes a file to a mounted volume,
it is owned by the `root` Unix user, making it difficult for other users to modify or delete the files. This most often comes up when a build task produces
an artifact and writes that artifact to a mounted volume.

(On macOS and Windows, the Docker daemon runs as the currently logged-in user and so any files created in mounted volumes are owned by that user, so this is not an issue.)

To fix this issue, batect can run containers in 'run as current user' mode, ensuring that all files written to a mounted volume are created by the current
user, not root. This mode can be enabled on a per-container basis with the [`run_as_current_user` option](../config/Containers.md#run_as_current_user).

When enabled, the following configuration changes are made:

  * The container is run with the current user's UID and GID (equivalent to passing `--user $(id -u):$(id -g)` to `docker run`)

  * An empty directory is mounted into the container at `home_directory` for the user's home directory.

    !!! warning
        If the directory given by `home_directory` already exists inside the image for this container, it is overwritten.

  * A new `/etc/passwd` file is mounted into the container with two users: root and the current user. The current user's home directory is set to the
    value of `home_directory`. (If batect is running as root, then just root is listed and it takes the home directory provided in `home_directory`.)

    This means that any other users defined in the container's image are effectively lost. Under most circumstances, this is not an issue.

  * Similarly, a new `/etc/group` file is mounted into the container with two groups: root and the current user's primary group (usually `staff` on
    macOS, and the user's name on Linux). If batect is running as root, then just root is listed.

    Again, this means that any other groups defined in the container's image are effectively lost. Under most circumstances, this is not an issue.

While this is really only useful on Linux, for consistency, batect makes the same configuration changes regardless of the host operating system.
These configuration changes are harmless on macOS and Windows.

## Special notes for Windows

On Windows, the container is run with UID 0 and GID 0 and the user and group name `root`. This is because any mounted directories
are always owned by root when running on Windows, and so running as another user can cause issues interacting with mounted directories.

For consistency, root's home directory inside the container is set to match the directory specified by `home_directory`.

See [docker/for-win#63](https://github.com/docker/for-win/issues/63) and [docker/for-win#39](https://github.com/docker/for-win/issues/39)
for more details on this limitation.
