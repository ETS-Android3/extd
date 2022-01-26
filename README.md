# ExtD System Installation

After cloning the repo, do

```
   git submodule update --init
```

The system uses make to setup all needed components for the application.
The configuration is located in [config.mk](config.mk). Edit this file before installation.

In order to install:

```
$ sudo make install
```

if system was installed before use:

```
$ sudo make uninstall
```

this will remove all system files, users and groups

after installation, make file produces executable files in the `~/.local/bin/` folder of the user configured in the [config.mk](config.mk).
you need to take care of adding this path to your PATH, so the extd_manager script will be available to you in a commandline.

once system is installed, you can use extd_manager to manage it:

```
$ extd_manager daemon start
$ extd_manager daemon status
$ extd_manager daemon stop
```

to add new connection:

```
$ extd_manager add
```

this will print the qr code for the app.

The system needs the following dependencies before it can run on your machine:
  - an active X server display
  - python3
    - and packages in [requirements.txt](./requirements.txt)
  - sudo
  - active ssh
  - standard GNU programs like sed, cut, etc.
  - ssh_keygen
  - active avahi daemon

To open the android studio project at [app](./app) you need to set up libraries first:
```
./prepareLibreSSL.sh
```